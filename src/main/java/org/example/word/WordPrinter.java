package org.example.word;

import org.example.command.EscpCommand;
import org.example.config.PrintConfig;
import org.example.preview.PrintPreview;
import org.example.service.PrinterService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Word 文档打印桥接器。
 * 将 WordDocument 的段落内容按 PrintSelector 规则选择，
 * 并将格式映射为 ESC/P-K 命令，通过 PrinterService 发送到打印机。
 *
 * 使用方式：
 * <pre>
 *   WordDocument doc = WordDocument.load("report.docx");
 *   PrintSelector selector = new PrintSelector()
 *       .pageRange("1-3")
 *       .skipEmptyLines(true);
 *
 *   WordPrinter wordPrinter = new WordPrinter(printerService);
 *   wordPrinter.print(doc, selector);
 * </pre>
 */
public class WordPrinter {

    private final PrinterService printer;
    private boolean verbose = true;

    /** 当前打印状态追踪，用于减少冗余命令 */
    private boolean currentBold = false;
    private boolean currentItalic = false;
    private boolean currentUnderline = false;
    private String currentFont = null;
    private int currentFontSize = 0;

    public WordPrinter(PrinterService printer) {
        this.printer = printer;
    }

    public WordPrinter(PrinterService printer, boolean verbose) {
        this.printer = printer;
        this.verbose = verbose;
    }

    // ======== 主打印方法 ========

    /**
     * 打印整个 Word 文档（无过滤）。
     */
    public void print(WordDocument doc) throws IOException {
        print(doc, null);
    }

    /**
     * 按选择器规则打印 Word 文档。
     * @param doc      已加载的 Word 文档
     * @param selector 选择规则（为 null 时打印全部）
     */
    public void print(WordDocument doc, PrintSelector selector) throws IOException {
        long startTime = System.currentTimeMillis();

        if (selector != null) {
            selector.linesPerPage(doc.getLinesPerPage());
            log("文档: " + doc.getFilePath());
            log("规则: " + selector);
        } else {
            log("文档: " + doc.getFilePath() + " (打印全部)");
        }

        // 初始化打印机
        printer.init();
        printer.setLineSpacing1_6();
        resetFormatState();

        // 遍历段落
        List<WordDocument.WordParagraph> paragraphs = doc.getAllParagraphs();
        int totalLines = paragraphs.size();
        int printedLines = 0;
        int skippedLines = 0;

        for (int i = 0; i < totalLines; i++) {
            WordDocument.WordParagraph wp = paragraphs.get(i);
            int lineNumber = i + 1;  // 1-based

            // 选择器过滤
            if (selector != null && !selector.accept(lineNumber, wp)) {
                skippedLines++;
                // leaveBlank: 跳过的行仍然进纸留白
                if (selector.isLeaveBlank() && !wp.isEmpty()) {
                    printer.feedLines(1);
                }
                continue;
            }

            // 应用格式并打印
            printParagraph(wp, selector);
            printedLines++;
        }

        // 收尾
        printer.feedLines(2);
        log("------------------------------");
        log(String.format("完成: %d 行已打印, %d 行已跳过, 共 %d 行",
                printedLines, skippedLines, totalLines));
        log(String.format("耗时: %dms", System.currentTimeMillis() - startTime));
    }

    // ================================================================
    // 生成打印预览 (HTML)
    // ================================================================

    /**
     * 生成打印预览 — 不连接打印机，收集所有段落及其格式信息，
     * 调用 {@link PrintPreview#writeHtml(String)} 输出为独立 HTML 文件。
     *
     * <p>典型用法：
     * <pre>
     *   PrintPreview preview = new PrintPreview("test.docx");
     *   wordPrinter.generatePreview(doc, selector, preview);
     *   preview.writeHtml("E:\\tmp\\preview.html");
     *   System.out.println("预览: " + preview.getHtmlPath());
     * </pre>
     *
     * @param doc      已加载的 Word 文档
     * @param selector 行选择规则 (可为 null)
     * @param preview  预览记录器 (传入空实例，方法填充)
     */
    public void generatePreview(WordDocument doc, PrintSelector selector,
                                 PrintPreview preview) {
        long startTime = System.currentTimeMillis();

        log("文档: " + doc.getFilePath());
        log("规则: " + (selector != null ? selector.toString() : "全部"));

        List<WordDocument.WordParagraph> paragraphs = doc.getAllParagraphs();
        int totalLines = paragraphs.size();
        int recordedLines = 0;
        int skippedLines = 0;

        for (int i = 0; i < totalLines; i++) {
            WordDocument.WordParagraph wp = paragraphs.get(i);
            int lineNumber = i + 1;

            // 选择器过滤
            if (selector != null && !selector.accept(lineNumber, wp)) {
                skippedLines++;
                // leaveBlank: 预览中也标记为空白行（分隔符）
                if (selector.isLeaveBlank() && !wp.isEmpty()) {
                    preview.addSeparator();
                }
                continue;
            }

            // 表格文本格式化
            String text = selector != null ? selector.formatTableText(wp) : wp.getText();

            // 空段：记录一个分隔
            if (text.trim().isEmpty()) {
                preview.addSeparator();
                recordedLines++;
                continue;
            }

            // 对齐 + 首行缩进
            String aligned = applyAlignmentForPreview(wp, text);
            preview.addLine(wp, aligned, lineNumber);
            recordedLines++;
        }

        log("------------------------------");
        log(String.format("预览收集: %d 行已记录, %d 行已跳过, 共 %d 行",
                recordedLines, skippedLines, totalLines));
        log(String.format("耗时: %dms", System.currentTimeMillis() - startTime));
    }

    /**
     * 对齐和缩进的纯文本近似（预览使用，不依赖打印机）。
     */
    private String applyAlignmentForPreview(WordDocument.WordParagraph wp, String text) {
        int indentFL = wp.getIndentFirstLine();
        int align = wp.getAlignment();

        String result = text;

        // 首行缩进 → 前置空格 (1pt ≈ 1.33 空格)
        if (indentFL > 0) {
            int spaces = Math.max(0, indentFL / 15);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < spaces; i++) sb.append(' ');
            result = sb.append(text).toString();
        }

        // 对齐 (HTML 用 CSS text-align, 此处只做基本空格补齐)
        // 实际对齐效果由 HTML 的 text-align CSS 属性完成
        return result;
    }

    // ======== 段落打印 ========

    /**
     * 打印单个段落，自动应用 Word 格式 → ESC/P-K 命令。
     */
    private void printParagraph(WordDocument.WordParagraph wp, PrintSelector selector) throws IOException {
        String text = selector != null ? selector.formatTableText(wp) : wp.getText();

        if (text.trim().isEmpty()) {
            printer.feedLines(1);
            return;
        }

        // 1. 段落间距：段前行距 + 行间距
        applyParagraphSpacingBefore(wp);
        applyParagraphLineSpacing(wp);

        // 2. 段落缩进与对齐
        applyParagraphIndent(wp);
        String alignedText = applyParagraphAlignment(wp, text);

        // 3. 字体样式
        applyFormat(wp);

        // 4. 打印内容
        if (wp.shouldUseChineseMode()) {
            printer.enterChineseMode();
            if (wp.getFontName() != null) {
                applyChineseFont(wp.getFontName());
            }
            int fontSizePt = wp.getFontSizePt();
            if (fontSizePt > 0 && fontSizePt != currentChineseFontSize) {
                applyChineseFontSize(fontSizePt);
                currentChineseFontSize = fontSizePt;
            }
            printer.sendRaw(EscpCommand.encodeChinese(alignedText));
            printer.send(EscpCommand.carriageReturn());
            printer.send(EscpCommand.lineFeed());
            printer.exitChineseMode();
        } else {
            printer.printText(alignedText);
        }

        // 5. 恢复格式 + 段后间距
        resetFormat(wp);
        restoreParagraphIndent(wp);
        applyParagraphSpacingAfter(wp);
    }

    // ======== 格式映射 ========

    /**
     * 将 Word 段落格式应用到打印机。
     * 使用状态追踪，避免重复发送相同命令。
     */
    private void applyFormat(WordDocument.WordParagraph wp) throws IOException {
        // 加粗
        if (wp.isBold() != currentBold) {
            printer.bold(wp.isBold());
            currentBold = wp.isBold();
        }
        // 斜体
        if (wp.isItalic() != currentItalic) {
            printer.italic(wp.isItalic());
            currentItalic = wp.isItalic();
        }
        // 下划线
        boolean needUnderline = wp.isUnderline();
        if (needUnderline != currentUnderline) {
            if (needUnderline) {
                printer.underline(1);
            } else {
                printer.underlineOff();
            }
            currentUnderline = needUnderline;
        }

        // 字号映射（Word 字号 → 打印机控制）
        int fontSizePt = wp.getFontSizePt();
        if (fontSizePt > 0 && fontSizePt != currentFontSize) {
            applyFontSize(fontSizePt);
            currentFontSize = fontSizePt;
        }
    }

    /**
     * 将 Word 字号 (pt) 逐级映射到打印机能力范围内的最接近配置。
     *
     * <p>打印机可用档位（CPI + 倍宽 + 倍高 组合）及对应视觉大小：
     * <pre>
     *   15CPI             → 约  8pt  (极细注释)
     *   12CPI             → 约 10pt  (脚注/小字)
     *   10CPI             → 约 12pt  (正文)
     *   15CPI + 倍宽       → 约 16pt  (小节标题)
     *   12CPI + 倍宽       → 约 20pt  (节标题)
     *   10CPI + 倍宽       → 约 24pt  (章标题)
     *   10CPI + 倍宽 + 倍高 → 约 24pt×24pt (封面标题)
     * </pre>
     *
     * <p>算法：给定 Word pt 值，遍历所有组合计算误差，取误差最小的配置。
     * 同时高度维度独立判断是否需要倍高（≥18pt 开启）。
     */
    private void applyFontSize(int fontSizePt) throws IOException {
        if (fontSizePt <= 0) return;

        // 可用宽度配置：(cpi, doubleWidth) → 等效视觉 pt
        // 10 CPI ≈ 12pt 基准，宽度与 CPI 成反比
        final int[][] WIDTH_OPTIONS = {
            {15, 0},  // ~ 8pt
            {12, 0},  // ~10pt
            {10, 0},  // ~12pt
            {15, 1},  // ~16pt
            {12, 1},  // ~20pt
            {10, 1},  // ~24pt
        };
        final int[] WIDTH_PTS = {8, 10, 12, 16, 20, 24};

        // 找最接近的宽度配置
        int bestIdx = 0;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; i < WIDTH_PTS.length; i++) {
            int diff = Math.abs(fontSizePt - WIDTH_PTS[i]);
            if (diff < bestDiff) { bestDiff = diff; bestIdx = i; }
        }

        int bestCPI  = WIDTH_OPTIONS[bestIdx][0];
        int bestDW   = WIDTH_OPTIONS[bestIdx][1];
        // 高度维度：大字号自动倍高
        int bestDH   = (fontSizePt >= 18) ? 1 : 0;

        // 应用
        switch (bestCPI) {
            case 10: printer.select10CPI(); break;
            case 12: printer.select12CPI(); break;
            case 15: printer.select15CPI(); break;
        }
        printer.doubleWidth(bestDW == 1);
        printer.doubleHeight(bestDH == 1);
    }

    /**
     * 将 Word 字号映射为汉字字符大小 (FS S w h)。
     *
     * <p>FS S 基准：1×1 ≈ 10.5pt (宋体五号)
     * <pre>
     *   w/h = round(pt / 10.5)，范围 clamp 到 1~4
     *   如 12pt → 1×1, 16pt → 2×2, 22pt → 2×2, 26pt → 2×3, 36pt → 3×3, 42pt → 4×4
     * </pre>
     */
    private void applyChineseFontSize(int fontSizePt) throws IOException {
        if (fontSizePt <= 0) return;

        double baseline = 10.5;
        int w = (int) Math.round(fontSizePt / baseline);
        int h = (int) Math.round(fontSizePt / baseline);

        // 标题级别：高度比宽度多一档更接近 Word 排版比例
        if (fontSizePt >= 22 && h <= w && h < 4) {
            h = Math.min(4, w + 1);
        }

        w = Math.max(1, Math.min(4, w));
        h = Math.max(1, Math.min(4, h));

        printer.send(EscpCommand.setChineseCharacterSize(w, h));
        currentChineseW = w;
        currentChineseH = h;
    }

    /** 追踪当前汉字大小，避免冗余 FS S 命令 */
    private int currentChineseW = 1;
    private int currentChineseH = 1;

    /**
     * 汉字字体映射。
     * FontName 含 "宋体"/"SimSun"/"宋" → FS K 0
     * FontName 含 "黑体"/"SimHei"/"黑" → FS K 1
     */
    private void applyChineseFont(String fontName) throws IOException {
        if (fontName == null) return;

        String fn = fontName.toLowerCase();
        if (fn.contains("黑体") || fn.contains("simhei") || fn.contains("hei") || fn.equals("黑")) {
            printer.selectChineseFont(1);  // 黑体
        } else if (fn.contains("宋体") || fn.contains("simsun") || fn.contains("song") || fn.equals("宋")) {
            printer.selectChineseFont(0);  // 宋体
        } else if (fn.contains("楷") || fn.contains("kai")) {
            printer.selectChineseFont(0);  // 无独立楷体命令，用宋体
        } else {
            printer.selectChineseFont(0);  // 默认宋体
        }
        currentFont = fontName;
    }

    /**
     * 段落打印后恢复默认样式。
     * 仅在最后一段或格式变化时才重置，减少冗余命令。
     */
    private void resetFormat(WordDocument.WordParagraph wp) throws IOException {
        if (wp.isBold()) {
            printer.bold(false);
            currentBold = false;
        }
        if (wp.isItalic()) {
            printer.italic(false);
            currentItalic = false;
        }
        if (wp.isUnderline()) {
            printer.underlineOff();
            currentUnderline = false;
        }
        // 字号和倍宽倍高在 applyFontSize 中已按每段设置
    }

    /** 重置所有格式追踪状态 */
    private void resetFormatState() {
        currentBold = false;
        currentItalic = false;
        currentUnderline = false;
        currentFont = null;
        currentFontSize = 0;
        currentChineseW = 1;
        currentChineseH = 1;
        currentChineseFontSize = 0;
    }

    /** 当前汉字字号 (pt)，避免冗余 FS S 命令 */
    private int currentChineseFontSize = 0;

    // ================================================================
    // 段落间距与缩进（从 Word 段落属性提取并映射到 ESC/P-K）
    // ================================================================

    /** 当前左缩进列数，用于段落后恢复 */
    private int currentLeftIndentCols = 0;
    /** 当前行间距 n/216" 值，避免冗余 ESC 3 命令。默认 36 = 1/6" */
    private int currentLineSpacingN = 36;

    /**
     * 段前间距 — ESC J (进纸 n/216 英寸)。
     */
    private void applyParagraphSpacingBefore(WordDocument.WordParagraph wp) throws IOException {
        int before = wp.getSpacingBefore();
        if (before <= 0) return;
        int n = twipsTo216(before);
        if (n > 0) printer.send(EscpCommand.feedPaper216(n));
    }

    /**
     * 段后间距 — ESC J (进纸 n/216 英寸)。
     */
    private void applyParagraphSpacingAfter(WordDocument.WordParagraph wp) throws IOException {
        int after = wp.getSpacingAfter();
        if (after <= 0) return;
        int n = twipsTo216(after);
        if (n > 0) printer.send(EscpCommand.feedPaper216(n));
    }

    /**
     * 行间距 — ESC 3 n (n/216 英寸)。
     *
     * <p>Word 的 getSpacingBetween() 在不同规则下的语义：
     * <ul>
     *   <li>AUTO：返回值 = 240 × 倍率 (240=1.0倍行距, 360=1.5倍, 480=2倍)</li>
     *   <li>EXACT：返回值 = 精确行距 (twip)</li>
     *   <li>AT_LEAST：返回值 = 最小行距 (twip)</li>
     * </ul>
     */
    private void applyParagraphLineSpacing(WordDocument.WordParagraph wp) throws IOException {
        int sp = wp.getSpacingBetween();
        if (sp <= 0) return;

        int n;
        if (sp >= 200) {
            // ≥200 → AUTO 模式: 240 = 1.0 倍 = 1/6" = 36/216"
            n = sp * 36 / 240;
        } else {
            // <200 → EXACT/AT_LEAST 模式: twip 直接换算
            n = twipsTo216(sp);
        }
        if (n > 0 && n != currentLineSpacingN) {
            printer.send(EscpCommand.setLineSpacing216(n));
            currentLineSpacingN = n;
        }
    }

    /**
     * 左缩进 — ESC l n (设置左边界到第 n 列)。
     * 段落结束后由 {@link #restoreParagraphIndent} 恢复。
     */
    private void applyParagraphIndent(WordDocument.WordParagraph wp) throws IOException {
        int left = wp.getIndentLeft();
        if (left > 0) {
            int cols = twipsToColumns(left, wp.getFontSizePt());
            if (cols > 0 && cols != currentLeftIndentCols) {
                printer.send(EscpCommand.setLeftMargin(cols));
                currentLeftIndentCols = cols;
            }
        }
    }

    /**
     * 恢复段落左缩进。
     */
    private void restoreParagraphIndent(WordDocument.WordParagraph wp) throws IOException {
        if (currentLeftIndentCols > 0 && wp.getIndentLeft() > 0) {
            printer.send(EscpCommand.setLeftMargin(0));
            currentLeftIndentCols = 0;
        }
    }

    /**
     * 首行缩进 + 对齐方式。
     *
     * <p>DLQ-3500K 无原生对齐命令，用前置空格近似实现：
     * <ul>
     *   <li>首行缩进 → 前置 N 个空格 (N = indentFirstLine / twips_per_col)</li>
     *   <li>居中 → 前置 (lineWidth - textLen) / 2 个空格</li>
     *   <li>右对齐 → 前置 (lineWidth - textLen) 个空格</li>
     * </ul>
     */
    private String applyParagraphAlignment(WordDocument.WordParagraph wp, String text) {
        int indentFL = wp.getIndentFirstLine();
        int align = wp.getAlignment();

        // 首行缩进
        String result = text;
        if (indentFL > 0) {
            int spaces = twipsToColumns(indentFL, wp.getFontSizePt());
            if (spaces > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < spaces; i++) sb.append(' ');
                result = sb.append(text).toString();
            }
        }

        // 对齐 (仅 LEFT 以外的)
        if (align == 1 || align == 2) {
            // 估算一行可容纳的等宽字符数 (10CPI 基准, A4 ≈ 82 列)
            int lineWidth = 82 - currentLeftIndentCols;
            // 汉字按 2 字符粗略估算
            int approxLen = 0;
            for (char c : text.toCharArray()) {
                approxLen += (c >= 0x4e00 && c <= 0x9fff) ? 2 : 1;
            }

            int pad;
            if (align == 1) {
                pad = Math.max(0, (lineWidth - approxLen) / 2);
            } else {
                pad = Math.max(0, lineWidth - approxLen);
            }

            if (pad > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < pad; i++) sb.append(' ');
                result = sb.append(text).toString();
            }
        }
        return result;
    }

    // -- 单位换算 --

    /** twip → n/216 英寸 (1 inch = 1440 twip, n/216 = twip * 216/1440 = twip * 3/20) */
    private static int twipsTo216(int twips) {
        return Math.max(1, twips * 3 / 20);
    }

    /** twip → 字符列数 (1 inch = 1440 twip), 根据字号推断 CPI */
    private static int twipsToColumns(int twips, int fontSizePt) {
        int cpi;
        if (fontSizePt <= 10) cpi = 15;
        else if (fontSizePt <= 12) cpi = 12;
        else cpi = 10;
        int twipsPerCol = 1440 / cpi;
        return Math.max(0, twips / twipsPerCol);
    }

    // ================================================================
    // 基于 PrintConfig 的多页拼合打印 (N-up)
    // ================================================================

    /**
     * 使用 PrintConfig 打印 Word 文档，自动处理 N-up 拼合布局。
     *
     * <p>典型场景 — 2 页 A5 横版打到 1 张 A4 纸：
     * <pre>
     *   PrintConfig config = PrintConfig.a4TwoA5Landscape();
     *   WordDocument doc = WordDocument.load("test.docx");
     *   PrintSelector selector = new PrintSelector().pageRange("1-2");
     *
     *   WordPrinter wp = new WordPrinter(printer);
     *   wp.printWithConfig(doc, selector, config);
     * </pre>
     *
     * <p>工作原理：
     * <ol>
     *   <li>应用 PrintConfig 设置（CPI、行间距、边距等）</li>
     *   <li>由 selector 筛选要打印的段落</li>
     *   <li>按 effectivePageLines() 将段落切成逻辑页</li>
     *   <li>每 pagesPerSheet 个逻辑页组成一张物理纸</li>
     *   <li>自动调用 beginPhysicalSheet / beginLogicalPage / endLogicalPage</li>
     * </ol>
     *
     * @param doc      已加载的 Word 文档
     * @param selector 行选择规则（为 null 时打印全部，页码过滤按 config 页长重新计算）
     * @param config   打印配置（含页面布局、纸张、边距等）
     */
    public void printWithConfig(WordDocument doc, PrintSelector selector, PrintConfig config) throws IOException {
        long startTime = System.currentTimeMillis();

        // 1. 获取有效行/页参数
        int logicalPageLines = config.effectivePageLines();
        int pagesPerSheet = config.getPageLayout().pagesPerSheet();
        boolean isMultiUp = config.getPageLayout().isMultiUp();

        // 更新 selector 的每页行数（用于页码范围计算）
        if (selector != null) {
            selector.linesPerPage(logicalPageLines);
            log("文档: " + doc.getFilePath());
            log("布局: " + config.getPageLayout());
            log("每逻辑页: " + logicalPageLines + " 行 | 每物理纸: " + pagesPerSheet + " 逻辑页");
            log("规则: " + selector);
        } else {
            log("文档: " + doc.getFilePath() + " (打印全部)");
            log("布局: " + config.getPageLayout());
        }

        // 2. 应用配置
        printer.applyConfig(config);
        resetFormatState();

        // 3. 收集需打印的段落
        List<WordDocument.WordParagraph> paragraphs = doc.getAllParagraphs();
        List<WordDocument.WordParagraph> selected = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            int lineNumber = i + 1;
            WordDocument.WordParagraph wp = paragraphs.get(i);
            if (selector == null || selector.accept(lineNumber, wp)) {
                selected.add(wp);
            } else if (selector != null && selector.isLeaveBlank() && !wp.isEmpty()) {
                // leaveBlank: 跳过的行插入空段落占位，保持逻辑页行数对齐
                selected.add(new WordDocument.WordParagraph(""));
            }
        }

        if (selected.isEmpty()) {
            log("没有可打印的内容（选择器过滤后为空）");
            return;
        }

        // 4. 按逻辑页行数切分
        int totalLogicalPages = (int) Math.ceil((double) selected.size() / logicalPageLines);
        log(String.format("共 %d 逻辑页 / %d 张物理纸",
                totalLogicalPages,
                (int) Math.ceil((double) totalLogicalPages / pagesPerSheet)));

        int physicalSheetCount = 0;

        for (int logicalPageIdx = 0; logicalPageIdx < totalLogicalPages; logicalPageIdx++) {
            int posInSheet = logicalPageIdx % pagesPerSheet;

            // 开始一张新物理纸
            if (posInSheet == 0) {
                if (logicalPageIdx > 0) {
                    // 上一张物理纸收尾
                    endPhysicalSheetForConfig(config, pagesPerSheet - 1, true);
                    printer.feedLines(1);
                }
                physicalSheetCount++;
                log(String.format("--- 物理纸 #%d ---", physicalSheetCount));
                printer.beginPhysicalSheet(config);
            }

            // 开始逻辑页
            printer.beginLogicalPage(config, posInSheet);
            log(String.format("  逻辑页 %d/%d (纸上位置 %d/%d)",
                    logicalPageIdx + 1, totalLogicalPages,
                    posInSheet + 1, pagesPerSheet));

            // 打印该逻辑页的段落
            int startLine = logicalPageIdx * logicalPageLines;
            int endLine = Math.min(startLine + logicalPageLines, selected.size());

            for (int j = startLine; j < endLine; j++) {
                WordDocument.WordParagraph wp = selected.get(j);
                printParagraph(wp, selector);
            }

            // 逻辑页内容不够填满时，补空行
            int printed = endLine - startLine;
            if (printed < logicalPageLines) {
                int fill = logicalPageLines - printed;
                for (int f = 0; f < Math.min(fill, 3); f++) {
                    printer.feedLines(1);
                }
            }

            // 结束逻辑页
            boolean isLastOnSheet = (posInSheet == pagesPerSheet - 1);
            boolean isLastOverall = (logicalPageIdx == totalLogicalPages - 1);

            if (isLastOverall) {
                endPhysicalSheetForConfig(config, posInSheet, true);
            } else if (isLastOnSheet) {
                endPhysicalSheetForConfig(config, posInSheet, true);
            } else {
                printer.endLogicalPage(config, posInSheet, false);
            }
        }

        // 5. 收尾
        printer.feedLines(2);
        log("------------------------------");
        log(String.format("完成: %d 段落 → %d 逻辑页 → %d 张物理纸",
                selected.size(), totalLogicalPages, physicalSheetCount));
        log(String.format("耗时: %dms", System.currentTimeMillis() - startTime));
    }

    /**
     * 结束一张物理纸（含最后一个逻辑页的收尾）。
     */
    private void endPhysicalSheetForConfig(PrintConfig config, int lastPageIndex, boolean isLast) throws IOException {
        printer.endLogicalPage(config, lastPageIndex, isLast);
        printer.endPhysicalSheet();
    }

    /** 日志输出 */
    private void log(String msg) {
        if (verbose) {
            System.out.println("  " + msg);
        }
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
