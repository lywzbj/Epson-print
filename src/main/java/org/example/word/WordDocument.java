package org.example.word;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.example.config.PrintConfig;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Word (.docx) 文档解析器。
 * 基于 Apache POI，提取段落文本及格式信息。
 *
 * 使用方式：
 * <pre>
 *   WordDocument doc = WordDocument.load("report.docx");
 *   List<WordParagraph> paragraphs = doc.getAllParagraphs();
 *   int pages = doc.getTotalPages();
 * </pre>
 */
public class WordDocument {

    /** 针式打印机默认每页行数 */
    public static final int DEFAULT_LINES_PER_PAGE = 66;

    private final String filePath;
    private final List<WordParagraph> paragraphs;
    private int linesPerPage;
    private DocPageLayout pageLayout;
    private String headerText;
    private String footerText;

    private WordDocument(String filePath, int linesPerPage) {
        this.filePath = filePath;
        this.linesPerPage = linesPerPage;
        this.paragraphs = new ArrayList<>();
    }

    // ======== 工厂方法 ========

    /**
     * 加载 .docx 文件。
     * @param filePath 文件路径
     */
    public static WordDocument load(String filePath) throws IOException {
        return load(filePath, DEFAULT_LINES_PER_PAGE);
    }

    /**
     * 加载 .docx 文件并指定每页行数。
     * @param filePath      文件路径
     * @param linesPerPage  每页行数（用于页码估算）
     */
    public static WordDocument load(String filePath, int linesPerPage) throws IOException {
        WordDocument doc = new WordDocument(filePath, linesPerPage);
        doc.parse();
        return doc;
    }

    /**
     * 从 InputStream 加载（兼容 classpath 资源）。
     */
    public static WordDocument load(InputStream in, int linesPerPage) throws IOException {
        WordDocument doc = new WordDocument("(stream)", linesPerPage);
        doc.parse(in);
        return doc;
    }

    // ======== 解析逻辑 ========

    private void parse() throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            parse(fis);
        }
    }

    private void parse(InputStream in) throws IOException {
        paragraphs.clear();
        pageLayout = null;
        headerText = null;
        footerText = null;
        XWPFDocument doc = new XWPFDocument(in);

        // 提取文档级页面布局和页眉/页脚
        extractPageLayout(doc);
        extractHeadersFooters(doc);

        int tableCount = 0;  // 当前表格序号 (0-based)

        // 遍历所有 IBODY 元素（包括正文、页眉页脚、表格等）
        for (IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph para = (XWPFParagraph) element;
                WordParagraph wp = parseParagraph(para);
                if (wp != null) {
                    paragraphs.add(wp);
                }
            } else if (element instanceof XWPFTable) {
                XWPFTable table = (XWPFTable) element;
                int rowNum = 0;  // 当前表格内行号 (1-based)
                for (XWPFTableRow row : table.getRows()) {
                    rowNum++;
                    StringBuilder rowText = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph cp : cell.getParagraphs()) {
                            String cellText = extractText(cp);
                            if (!cellText.isEmpty()) {
                                if (rowText.length() > 0) rowText.append(" | ");
                                rowText.append(cellText);
                            }
                        }
                    }
                    if (rowText.length() > 0) {
                        paragraphs.add(new WordParagraph(rowText.toString(),
                                false, false, false, 0, null,
                                true, tableCount, rowNum));
                    }
                }
                tableCount++;
            }
        }

        doc.close();
    }

    /**
     * 解析单个段落。
     */
    private WordParagraph parseParagraph(XWPFParagraph para) {
        List<XWPFRun> runs = para.getRuns();
        if (runs.isEmpty()) {
            return new WordParagraph("");
        }

        // 收集文本与格式
        StringBuilder fullText = new StringBuilder();
        boolean isBold = false, isItalic = false, isUnderline = false;
        int maxFontSize = 0;
        String fontName = null;
        String fontNameEastAsia = null;

        for (XWPFRun run : runs) {
            String text = run.text();
            if (text != null && !text.isEmpty()) {
                fullText.append(text);
            }

            if (run.isBold()) isBold = true;
            if (run.isItalic()) isItalic = true;
            if (run.getUnderline() != UnderlinePatterns.NONE) isUnderline = true;

            int fs = run.getFontSize();  // half-points, -1 = 继承段落/文档默认值
            if (fs > maxFontSize) maxFontSize = fs;

            if (fontName == null && run.getFontFamily() != null) {
                fontName = run.getFontFamily();
            }
            if (fontNameEastAsia == null && run.getFontName() != null) {
                fontNameEastAsia = run.getFontName();
            }
        }

        // 如果所有 run 的 fontSize 都为 -1（继承自样式未显式设置），
        // 尝试从段落默认 Run 属性或文档默认样式中获取
        if (maxFontSize <= 0) {
            maxFontSize = resolveDefaultFontSize(para);
        }

        String text = fullText.toString();
        if (text.isEmpty()) {
            return new WordParagraph("");
        }

        // -- 段落级格式 --
        int spBetween, spBefore, spAfter, lineRule;
        int indentL, indentFL;
        int alignCode;

        // 行间距 — POI 返回 -1 表示继承自样式，需从样式链解析
        spBetween = resolveParagraphSpacing(para, "line");
        lineRule  = resolveParagraphLineRule(para);
        spBefore  = resolveParagraphSpacing(para, "before");
        spAfter   = resolveParagraphSpacing(para, "after");

        // 缩进
        indentL  = para.getIndentationLeft();       // twip
        indentFL = para.getIndentationFirstLine();  // twip

        // 对齐
        ParagraphAlignment pa = para.getAlignment();
        if (pa == ParagraphAlignment.CENTER)      alignCode = 1;
        else if (pa == ParagraphAlignment.RIGHT)  alignCode = 2;
        else if (pa == ParagraphAlignment.BOTH)   alignCode = 3;
        else                                       alignCode = 0;  // LEFT / null

        // 优先使用东亚字体名（中文环境）
        String effectiveFont = (fontNameEastAsia != null) ? fontNameEastAsia : fontName;

        return new WordParagraph(text, isBold, isItalic, isUnderline,
                maxFontSize, effectiveFont,
                false, -1, 0,                                   // 非表格行
                spBetween, lineRule,
                spBefore, spAfter, indentL, indentFL, alignCode);
    }

    /**
     * 仅提取段落纯文本。
     */
    private String extractText(XWPFParagraph para) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : para.getRuns()) {
            String t = run.text();
            if (t != null) sb.append(t);
        }
        return sb.toString();
    }

    /**
     * 解析段落的默认字号（当 run 级别未显式设置 fontSize 时使用）。
     *
     * <p>run.getFontSize() 返回 -1 表示继承自段落/文档样式。
     * 此方法按以下优先级尝试获取实际字号：
     * <ol>
     *   <li>段落自身的默认 Run 属性 (w:pPr/w:rPr)</li>
     *   <li>段落的 styleID → 文档 styles.xml 中该样式的 rPr/sz 定义</li>
     *   <li>样式继承链 (basedOn 追溯)</li>
     *   <li>文档默认样式 (w:docDefaults/w:rPrDefault)</li>
     *   <li>硬编码回退: 24 half-points = 12pt (Word 正文默认)</li>
     * </ol>
     */
    private int resolveDefaultFontSize(XWPFParagraph para) {
        // 1. 从段落自身的默认 Run 属性获取 (w:pPr/w:rPr)
        try {
            if (para.getCTP().getPPr() != null) {
                int sz = extractFontSizeFromRPrXml(para.getCTP().getPPr().toString());
                if (sz > 0) return sz;
            }
        } catch (Exception ignored) {
        }

        // 2. 通过段落样式 ID 查找文档 styles.xml 中的字号
        try {
            String styleId = para.getStyleID();
            if (styleId != null) {
                XWPFDocument doc = para.getDocument();
                XWPFStyles styles = doc.getStyles();
                XWPFStyle style = styles.getStyle(styleId);
                if (style != null) {
                    // 尝试从样式的 Run 属性获取字号 (half-points)
                    int szFromStyle = extractFontSizeFromStyle(style);
                    if (szFromStyle > 0) return szFromStyle;

                    // 继承链：follow base style
                    XWPFStyle base = style;
                    for (int i = 0; i < 5; i++) {  // 最多追溯 5 级继承
                        String baseId = base.getCTStyle().getBasedOn() != null
                                ? base.getCTStyle().getBasedOn().getVal() : null;
                        if (baseId == null) break;
                        base = styles.getStyle(baseId);
                        if (base == null) break;
                        int inherited = extractFontSizeFromStyle(base);
                        if (inherited > 0) return inherited;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 3. 从文档默认样式 (w:docDefaults/w:rPrDefault/w:rPr) 获取
        try {
            XWPFDocument doc = para.getDocument();
            // POI 5.2.5: XWPFStyles 无 getCTStyles()，
            // 通过 OPCPackage 直接读取 styles 部件的原始 XML
            String stylesXml = readStylesXml(doc);
            if (stylesXml != null) {
                // 在 styles.xml 中定位 docDefaults/rPrDefault/rPr 区域内的 sz 定义
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                        "<w:docDefaults>[\\s\\S]*?<w:rPrDefault>[\\s\\S]*?<w:rPr[\\s>]",
                        java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher dm = p.matcher(stylesXml);
                if (dm.find()) {
                    // 从匹配位置开始找第一个 sz/szCs
                    String section = stylesXml.substring(dm.start());
                    // 限制搜索范围: 不超过 </w:rPrDefault> 或 2000 字符
                    int endIdx = section.indexOf("</w:rPrDefault>");
                    if (endIdx < 0) endIdx = Math.min(2000, section.length());
                    section = section.substring(0, endIdx);
                    int sz = extractFontSizeFromRPrXml(section);
                    if (sz > 0) return sz;
                }
            }
        } catch (Exception ignored) {
        }

        // 4. 回退：24 half-points = 12pt
        return 24;
    }

    /**
     * 从 CTRPr XML 中提取字号 (half-points)，提取不到返回 -1。
     *
     * <p>POI 的 {@code CTRPr.toString()} 输出格式为 {@code <w:sz w:val="24"/>}，
     * 值在 {@code w:val} 属性中，单位已是 half-points（如 12pt → 24）。
     * 同时也匹配 {@code w:szCs} (complex script font size，中文文档常用)。
     */
    private static int extractFontSizeFromRPrXml(String rprXml) {
        if (rprXml == null || rprXml.isEmpty()) return -1;
        // 匹配 <w:sz w:val="24"/> 或 <w:szCs w:val="24"/>
        // 格式: w:sz[Cs] 空格 若干属性... w:val="数字"
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "w:sz(?:Cs)?\\s[^>]*w:val=\"(\\d+)\"");
        java.util.regex.Matcher m = p.matcher(rprXml);
        if (m.find()) {
            // w:val 的值就是 half-points（OOXML 标准），直接返回
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    /**
     * POI 5.2.5 兼容方式读取 styles.xml 原始内容。
     */
    private String readStylesXml(XWPFDocument doc) {
        return readPartXml(doc, "/word/styles.xml");
    }

    // ================================================================
    // 页面布局提取
    // ================================================================

    /**
     * 从 DOCX 的 section properties 提取页面尺寸和边距。
     * 通过 OPCPackage 读取 word/document.xml，解析 {@code <w:sectPr>} 区域。
     */
    private void extractPageLayout(XWPFDocument doc) {
        try {
            // 读取 word/document.xml
            String docXml = readPartXml(doc, "/word/document.xml");
            if (docXml == null) return;

            // 定位 <w:sectPr> 区域（文档级 section，通常在文件末尾）
            int sectIdx = docXml.lastIndexOf("<w:sectPr");
            if (sectIdx < 0) { sectIdx = docXml.indexOf("<w:sectPr"); }
            if (sectIdx < 0) return;  // 没有 section properties

            // 取 sectPr 片段（到 </w:sectPr> 为止）
            int endIdx = docXml.indexOf("</w:sectPr>", sectIdx);
            if (endIdx < 0) endIdx = docXml.indexOf("/>", sectIdx);
            if (endIdx < 0) endIdx = Math.min(sectIdx + 2000, docXml.length());
            else endIdx += "</w:sectPr>".length();

            String sectPrXml = docXml.substring(sectIdx, endIdx);
            this.pageLayout = DocPageLayout.fromSectPrXml(sectPrXml);
        } catch (Exception ignored) {
        }
    }

    /**
     * 通过 OPCPackage 读取 DOCX 中指定 URI 的部件内容。
     * @param doc     XWPFDocument
     * @param uri     部件 URI（如 "/word/document.xml", "/word/styles.xml"）
     * @return XML 字符串，失败返回 null
     */
    private String readPartXml(XWPFDocument doc, String uri) {
        try {
            for (Object obj : doc.getPackage().getParts()) {
                PackagePart part = (PackagePart) obj;
                if (part.getPartName().getName().equals(uri)) {
                    try (InputStream is = part.getInputStream()) {
                        byte[] bytes = is.readAllBytes();
                        return new String(bytes, StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // ================================================================
    // 页眉/页脚提取
    // ================================================================

    /** 提取默认页眉/页脚的纯文本 */
    private void extractHeadersFooters(XWPFDocument doc) {
        try {
            XWPFHeaderFooterPolicy hfPolicy = doc.getHeaderFooterPolicy();
            if (hfPolicy == null) return;

            // 默认页眉
            XWPFHeader header = hfPolicy.getDefaultHeader();
            if (header != null) {
                this.headerText = extractBodyText(header);
            }
            // 首页页眉（如果默认页眉为空）
            if ((this.headerText == null || this.headerText.isEmpty())
                    && hfPolicy.getFirstPageHeader() != null) {
                this.headerText = extractBodyText(hfPolicy.getFirstPageHeader());
            }

            // 默认页脚
            XWPFFooter footer = hfPolicy.getDefaultFooter();
            if (footer != null) {
                this.footerText = extractBodyText(footer);
            }
            if ((this.footerText == null || this.footerText.isEmpty())
                    && hfPolicy.getFirstPageFooter() != null) {
                this.footerText = extractBodyText(hfPolicy.getFirstPageFooter());
            }
        } catch (Exception ignored) {
        }
    }

    /** 从 IBody (页眉/页脚) 中提取纯文本 */
    private String extractBodyText(IBody body) {
        StringBuilder sb = new StringBuilder();
        for (IBodyElement element : body.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                String t = extractText((XWPFParagraph) element);
                if (!t.isEmpty()) {
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(t);
                }
            } else if (element instanceof XWPFTable) {
                for (XWPFTableRow row : ((XWPFTable) element).getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph cp : cell.getParagraphs()) {
                            String t = extractText(cp);
                            if (!t.isEmpty()) {
                                if (sb.length() > 0) sb.append(" | ");
                                sb.append(t);
                            }
                        }
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    // ================================================================
    // 页面信息 getter
    // ================================================================

    /** 文档页面布局信息（纸张尺寸、页边距等），可能为 null */
    public DocPageLayout getPageLayout() { return pageLayout; }

    /** 页眉纯文本，无页眉时为 null */
    public String getHeaderText() { return headerText; }

    /** 页脚纯文本，无页脚时为 null */
    public String getFooterText() { return footerText; }

    /**
     * 从文档页面属性自动生成打印配置。
     * 边距转换为 ESC/P 可用的行数和列数。
     */
    public PrintConfig derivePrintConfig() {
        DocPageLayout pl = (pageLayout != null) ? pageLayout : DocPageLayout.defaults();
        return PrintConfig.builder()
                .physicalPaper(pl.toPaperSize())
                .topMargin(pl.topMarginLines(6))
                .bottomMargin(Math.max(1, pl.bottomMarginLines(6)))
                .leftMargin(pl.leftMarginCols(10))
                .cpi(10)
                .lineSpacing(PrintConfig.LineSpacing.ONE_SIXTH)
                .build();
    }

    // ================================================================
    // 样式字号提取（续）
    // ================================================================

    /** 从 XWPFStyle 中提取 Run 级字号 (half-points)，提取不到返回 -1 */
    private int extractFontSizeFromStyle(XWPFStyle style) {
        try {
            if (style.getCTStyle().getRPr() == null) return -1;
            return extractFontSizeFromRPrXml(style.getCTStyle().getRPr().toString());
        } catch (Exception ignored) {
        }
        return -1;
    }

    // ================================================================
    // 段落间距解析（含样式继承）
    // ================================================================

    /**
     * 解析段落间距值（before/after/line），当段落直接值为 -1 时从样式链获取。
     *
     * <p>优先级：段落直接值 → 段落样式 pPr → basedOn 链 → docDefaults → 0
     *
     * @param para  POI 段落对象
     * @param which "before", "after", "line"
     * @return 解析后的间距值，未设置返回 0
     */
    private int resolveParagraphSpacing(XWPFParagraph para, String which) {
        // 1. 段落直接值
        int direct = getSpacingDirect(para, which);
        if (direct >= 0) return direct;  // 0 = 显式设为 0

        // 2. 从样式链解析
        int fromStyle = resolveParagraphSpacingFromStyle(para, which);
        if (fromStyle >= 0) return fromStyle;

        // 3. 从 docDefaults 解析
        int fromDefaults = resolveSpacingFromDocDefaults(para, which);
        if (fromDefaults >= 0) return fromDefaults;

        // 4. 回退
        return 0;
    }

    /** 获取段落直接间距值，-1 表示未设置 */
    private int getSpacingDirect(XWPFParagraph para, String which) {
        switch (which) {
            case "before": return (int) para.getSpacingBefore();
            case "after":  return (int) para.getSpacingAfter();
            case "line":   return (int) para.getSpacingBetween();
            default:       return -1;
        }
    }

    /**
     * 解析行距规则。
     * 优先级同 resolveParagraphSpacing，默认返回 0 (AUTO)。
     */
    private int resolveParagraphLineRule(XWPFParagraph para) {
        // 1. 段落直接值
        try {
            LineSpacingRule lsr = para.getSpacingLineRule();
            // POI 未设置时默认返回 AUTO，需要区分"显式设为 AUTO"和"继承"
            // 通过检查段落 pPr 是否有 spacing/lineRule 来判断
            if (para.getCTP().getPPr() != null
                    && para.getCTP().getPPr().getSpacing() != null
                    && para.getCTP().getPPr().getSpacing().isSetLineRule()) {
                return ruleCode(lsr);
            }
        } catch (Exception ignored) {}

        // 2. 从样式链解析
        int fromStyle = resolveLineRuleFromStyle(para);
        if (fromStyle >= 0) return fromStyle;

        // 3. 默认 AUTO
        return 0;
    }

    /** LineSpacingRule → int code: AUTO=0, EXACT=1, AT_LEAST=2 */
    private static int ruleCode(LineSpacingRule r) {
        if (r == null) return 0;
        switch (r) {
            case EXACT:    return 1;
            case AT_LEAST: return 2;
            default:       return 0;  // AUTO
        }
    }

    /** 从样式链解析间距值 */
    private int resolveParagraphSpacingFromStyle(XWPFParagraph para, String which) {
        try {
            String styleId = para.getStyleID();
            if (styleId == null) return -1;
            XWPFDocument doc = para.getDocument();
            XWPFStyles styles = doc.getStyles();
            XWPFStyle style = styles.getStyle(styleId);

            for (int level = 0; level < 6 && style != null; level++) {
                int val = extractSpacingFromStylePPr(style, which);
                if (val >= 0) return val;

                // follow basedOn
                String baseId = style.getCTStyle().getBasedOn() != null
                        ? style.getCTStyle().getBasedOn().getVal() : null;
                if (baseId == null) break;
                style = styles.getStyle(baseId);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    /** 从样式链解析行距规则 */
    private int resolveLineRuleFromStyle(XWPFParagraph para) {
        try {
            String styleId = para.getStyleID();
            if (styleId == null) return -1;
            XWPFDocument doc = para.getDocument();
            XWPFStyles styles = doc.getStyles();
            XWPFStyle style = styles.getStyle(styleId);

            for (int level = 0; level < 6 && style != null; level++) {
                int val = extractSpacingFromStylePPr(style, "lineRule");
                if (val >= 0) return val;

                String baseId = style.getCTStyle().getBasedOn() != null
                        ? style.getCTStyle().getBasedOn().getVal() : null;
                if (baseId == null) break;
                style = styles.getStyle(baseId);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    /**
     * 从 XWPFStyle 的 pPr/spacing 中提取间距值。
     * @param which "before", "after", "line", "lineRule"
     * @return 间距值，未设置返回 -1
     */
    private int extractSpacingFromStylePPr(XWPFStyle style, String which) {
        try {
            if (style.getCTStyle().getPPr() == null) return -1;
            if (style.getCTStyle().getPPr().getSpacing() == null) return -1;
            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing sp =
                    style.getCTStyle().getPPr().getSpacing();

            switch (which) {
                case "before":
                    return sp.isSetBefore() ? parseObjToInt(sp.getBefore()) : -1;
                case "after":
                    return sp.isSetAfter() ? parseObjToInt(sp.getAfter()) : -1;
                case "line":
                    return sp.isSetLine() ? parseObjToInt(sp.getLine()) : -1;
                case "lineRule":
                    if (sp.isSetLineRule()) {
                        // getLineRule() 返回 STLineSpacingRule.Enum 或 String
                        String name = sp.getLineRule().toString();
                        return ruleCode(LineSpacingRule.valueOf(name));
                    }
                    return -1;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    /** 安全地将 CTSpacing getter 返回的 Object 转为 int */
    private static int parseObjToInt(Object obj) {
        if (obj == null) return -1;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(obj.toString()); } catch (NumberFormatException e) { return -1; }
    }

    /** 从 docDefaults 的 pPrDefault/spacing 中提取间距值 */
    private int resolveSpacingFromDocDefaults(XWPFParagraph para, String which) {
        try {
            String stylesXml = readStylesXml(para.getDocument());
            if (stylesXml == null) return -1;

            // 定位 docDefaults/pPrDefault/pPr 区域
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "<w:docDefaults>[\\s\\S]*?<w:pPrDefault>[\\s\\S]*?<w:pPr[\\s>]",
                    java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher dm = p.matcher(stylesXml);
            if (!dm.find()) return -1;

            String section = stylesXml.substring(dm.start());
            int endIdx = section.indexOf("</w:pPrDefault>");
            if (endIdx < 0) endIdx = Math.min(2000, section.length());
            section = section.substring(0, endIdx);

            // 定位 <w:spacing ... />
            java.util.regex.Pattern spP = java.util.regex.Pattern.compile(
                    "<w:spacing\\s([^>]*)>");
            java.util.regex.Matcher spM = spP.matcher(section);
            if (!spM.find()) return -1;

            String attrs = spM.group(1);
            // 匹配对应属性: w:before="120", w:after="120", w:line="240", w:lineRule="auto"
            switch (which) {
                case "before": {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                            "w:before=\"(\\d+)\"").matcher(attrs);
                    return m.find() ? Integer.parseInt(m.group(1)) : -1;
                }
                case "after": {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                            "w:after=\"(\\d+)\"").matcher(attrs);
                    return m.find() ? Integer.parseInt(m.group(1)) : -1;
                }
                case "line": {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                            "w:line=\"(\\d+)\"").matcher(attrs);
                    return m.find() ? Integer.parseInt(m.group(1)) : -1;
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    // ======== 查询方法 ========

    /** 所有段落列表 */
    public List<WordParagraph> getAllParagraphs() {
        return new ArrayList<>(paragraphs);
    }

    /** 总段落数 */
    public int getParagraphCount() {
        return paragraphs.size();
    }

    /** 估计总页数 */
    public int getTotalPages() {
        return (int) Math.ceil((double) paragraphs.size() / linesPerPage);
    }

    /** 估计总行数 (≈ 段落数) */
    public int getTotalLines() {
        return paragraphs.size();
    }

    /** 获取指定页的段落 */
    public List<WordParagraph> getParagraphs(int page) {
        int start = (page - 1) * linesPerPage;
        int end = Math.min(start + linesPerPage, paragraphs.size());
        if (start >= paragraphs.size()) return new ArrayList<>();
        return new ArrayList<>(paragraphs.subList(start, end));
    }

    /** 获取页码范围段落 */
    public List<WordParagraph> getParagraphs(int pageStart, int pageEnd) {
        List<WordParagraph> result = new ArrayList<>();
        for (int p = pageStart; p <= pageEnd; p++) {
            result.addAll(getParagraphs(p));
        }
        return result;
    }

    /** 获取行范围段落 */
    public List<WordParagraph> getParagraphsByLines(int lineStart, int lineEnd) {
        int start = Math.max(0, lineStart - 1);
        int end = Math.min(lineEnd, paragraphs.size());
        if (start >= paragraphs.size()) return new ArrayList<>();
        return new ArrayList<>(paragraphs.subList(start, end));
    }

    /** 文件路径 */
    public String getFilePath() {
        return filePath;
    }

    /** 每页行数 */
    public int getLinesPerPage() {
        return linesPerPage;
    }

    public void setLinesPerPage(int linesPerPage) {
        this.linesPerPage = linesPerPage;
    }

    // ======== 表格查询 ========

    /**
     * 统计文档中的表格数量。
     * @return 表格总数
     */
    public int getTableCount() {
        int max = -1;
        for (WordParagraph wp : paragraphs) {
            if (wp.isTableRow && wp.tableIndex > max) {
                max = wp.tableIndex;
            }
        }
        return max + 1;
    }

    /**
     * 获取指定表格的全部行（按行号排序）。
     * @param tableIndex 表格序号 (0-based)
     * @return 该表格所有行的段落列表
     */
    public List<WordParagraph> getTableParagraphs(int tableIndex) {
        List<WordParagraph> result = new ArrayList<>();
        for (WordParagraph wp : paragraphs) {
            if (wp.isTableRow && wp.tableIndex == tableIndex) {
                result.add(wp);
            }
        }
        // 按行号排序
        result.sort((a, b) -> Integer.compare(a.rowInTable, b.rowInTable));
        return result;
    }

    /**
     * 获取指定表格的指定行。
     * @param tableIndex 表格序号 (0-based)
     * @param rowInTable 行号 (1-based)
     * @return 该行段落，找不到返回 null
     */
    public WordParagraph getTableRow(int tableIndex, int rowInTable) {
        for (WordParagraph wp : paragraphs) {
            if (wp.isTableRow && wp.tableIndex == tableIndex && wp.rowInTable == rowInTable) {
                return wp;
            }
        }
        return null;
    }

    /**
     * 获取文档中所有表格行段落（扁平列表）。
     */
    public List<WordParagraph> getAllTableRows() {
        List<WordParagraph> result = new ArrayList<>();
        for (WordParagraph wp : paragraphs) {
            if (wp.isTableRow) {
                result.add(wp);
            }
        }
        return result;
    }

    // ============ 内部类：WordParagraph ============

    /**
     * 代表 Word 文档中的一个段落，包含文本和格式信息。
     */
    public static class WordParagraph {

        private final String text;
        private final boolean bold;
        private final boolean italic;
        private final boolean underline;
        private final int fontSize;        // 单位：半点 (half-point)，如 24 = 12pt
        private final String fontName;

        // 表格来源信息
        private final boolean isTableRow;  // 是否来自表格
        private final int tableIndex;      // 来源表格序号 (0-based), 非表格=-1
        private final int rowInTable;      // 在表格中的行号 (1-based), 非表格=0

        // 段落间距与缩进（从 Word 段落属性提取）
        /** 行间距值。AUTO 模式 = 240×倍率, EXACT/AT_LEAST = twip。0 = 使用默认 */
        private final int spacingBetween;
        /** 行距规则：0=AUTO(倍数), 1=EXACT(精确值), 2=AT_LEAST(最小值)。对齐 Word LineSpacingRule */
        private final int lineSpacingRule;
        /** 段前间距 (twip) */
        private final int spacingBefore;
        /** 段后间距 (twip) */
        private final int spacingAfter;
        /** 左缩进 (twip) */
        private final int indentLeft;
        /** 首行缩进 (twip)。正=首行缩进, 负=悬挂缩进 */
        private final int indentFirstLine;
        /** 对齐方式 */
        private final int alignment;  // 0=left, 1=center, 2=right, 3=both

        // 中文检测正则
        private static final Pattern CHINESE_PATTERN =
                Pattern.compile("[\\u4e00-\\u9fff\\u3400-\\u4dbf]");

        /** 空段落 */
        public WordParagraph(String text) {
            this(text, false, false, false, 0, null, false, -1, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public WordParagraph(String text, boolean bold, boolean italic,
                             boolean underline, int fontSize, String fontName) {
            this(text, bold, italic, underline, fontSize, fontName, false, -1, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        /** 完整构造器（含表格来源信息） */
        public WordParagraph(String text, boolean bold, boolean italic,
                             boolean underline, int fontSize, String fontName,
                             boolean isTableRow, int tableIndex, int rowInTable) {
            this(text, bold, italic, underline, fontSize, fontName,
                    isTableRow, tableIndex, rowInTable, 0, 0, 0, 0, 0, 0, 0);
        }

        /** 全参数构造器（含段落间距与缩进） */
        public WordParagraph(String text, boolean bold, boolean italic,
                             boolean underline, int fontSize, String fontName,
                             boolean isTableRow, int tableIndex, int rowInTable,
                             int spacingBetween, int spacingBefore, int spacingAfter,
                             int indentLeft, int indentFirstLine, int alignment) {
            this(text, bold, italic, underline, fontSize, fontName,
                    isTableRow, tableIndex, rowInTable,
                    spacingBetween, 0, spacingBefore, spacingAfter,
                    indentLeft, indentFirstLine, alignment);
        }

        /** 全参数构造器 + 行距规则 */
        public WordParagraph(String text, boolean bold, boolean italic,
                             boolean underline, int fontSize, String fontName,
                             boolean isTableRow, int tableIndex, int rowInTable,
                             int spacingBetween, int lineSpacingRule,
                             int spacingBefore, int spacingAfter,
                             int indentLeft, int indentFirstLine, int alignment) {
            this.text = (text != null) ? text : "";
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.fontSize = fontSize;
            this.fontName = fontName;
            this.isTableRow = isTableRow;
            this.tableIndex = tableIndex;
            this.rowInTable = rowInTable;
            this.spacingBetween = spacingBetween;
            this.lineSpacingRule = lineSpacingRule;
            this.spacingBefore = spacingBefore;
            this.spacingAfter = spacingAfter;
            this.indentLeft = indentLeft;
            this.indentFirstLine = indentFirstLine;
            this.alignment = alignment;
        }

        // ======== Getters ========

        public String getText()             { return text; }
        public boolean isEmpty()            { return text.trim().isEmpty(); }
        public boolean isBold()             { return bold; }
        public boolean isItalic()           { return italic; }
        public boolean isUnderline()        { return underline; }
        public int getFontSize()            { return fontSize; }
        /** 字号转换为磅 (pt) */
        public int getFontSizePt()          { return fontSize / 2; }
        public String getFontName()         { return fontName; }
        public boolean containsChinese()    { return CHINESE_PATTERN.matcher(text).find(); }
        /** 字体是否为中文名称 */
        public boolean isChineseFont() {
            return fontName != null && CHINESE_PATTERN.matcher(fontName).find();
        }

        // ======== 表格来源 ========

        /** 是否来自 Word 表格 */
        public boolean isTableRow()          { return isTableRow; }
        /** 来源表格序号 (0-based)，非表格返回 -1 */
        public int getTableIndex()           { return tableIndex; }
        /** 在表格中的行号 (1-based)，非表格返回 0 */
        public int getRowInTable()           { return rowInTable; }

        // ======== 段落间距 / 缩进 ========

        /** 行间距 (twip, 1/20 pt)。0 = 使用默认值，AUTO 模式以 240 为倍率基数 */
        public int getSpacingBetween()       { return spacingBetween; }
        /** 行距规则：0=AUTO(倍数), 1=EXACT(精确twip), 2=AT_LEAST(最小twip) */
        public int getLineSpacingRule()       { return lineSpacingRule; }
        /** 是否为倍数行距 (AUTO 模式) */
        public boolean isAutoLineSpacing()    { return lineSpacingRule == 0; }
        /** 段前间距 (twip) */
        public int getSpacingBefore()        { return spacingBefore; }
        /** 段后间距 (twip) */
        public int getSpacingAfter()         { return spacingAfter; }
        /** 左缩进 (twip) */
        public int getIndentLeft()           { return indentLeft; }
        /** 首行缩进 (twip)，正=缩进，负=悬挂 */
        public int getIndentFirstLine()      { return indentFirstLine; }
        /** 对齐方式：0=left, 1=center, 2=right, 3=both */
        public int getAlignment()            { return alignment; }
        /** 是否有段落级缩进配置 */
        public boolean hasIndent()           { return indentLeft != 0 || indentFirstLine != 0; }
        /** 是否有段落间距配置 */
        public boolean hasSpacing()          { return spacingBefore != 0 || spacingAfter != 0 || spacingBetween != 0; }

        // ======== 格式适配 ========

        /**
         * 判断该段是否应以汉字模式打印。
         * 条件：包含中文字符 或 字体名含中文。
         */
        public boolean shouldUseChineseMode() {
            return containsChinese() || isChineseFont();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            if (bold) sb.append("B");
            if (italic) sb.append("I");
            if (underline) sb.append("U");
            if (sb.length() > 1 || bold || italic || underline) {
                // has format
            } else {
                sb.append("-");
            }
            sb.append("] ");
            if (fontSize > 0) sb.append('(').append(getFontSizePt()).append("pt) ");
            if (fontName != null) sb.append('[').append(fontName).append("] ");
            sb.append(text.length() > 60 ? text.substring(0, 57) + "..." : text);
            return sb.toString();
        }
    }
}
