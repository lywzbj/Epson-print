package org.example.word;

import org.example.config.PaperSize;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档页面布局信息 — 从 DOCX 的 section properties 提取。
 *
 * <p>所有内部尺寸以 twip (1/1440 inch) 存储，对外提供 mm、行数、列数等换算。
 * 与已有的 {@link org.example.config.PrintConfig} 配合使用，实现文档页面属性
 * 自动映射到打印机配置。
 *
 * <h3>使用</h3>
 * <pre>
 *   DocPageLayout pl = DocPageLayout.fromSectPrXml(xml);
 *   PaperSize paper = pl.toPaperSize();
 *   int topLines = pl.topMarginLines(6);
 * </pre>
 */
public class DocPageLayout {

    // ---- 纸张尺寸 (twip) ----
    private final int pageWidthTwips;
    private final int pageHeightTwips;

    // ---- 页边距 (twip) ----
    private final int marginTopTwips;
    private final int marginBottomTwips;
    private final int marginLeftTwips;
    private final int marginRightTwips;

    // ---- 页眉/页脚距 (twip) ----
    private final int marginHeaderTwips;
    private final int marginFooterTwips;

    /** Word 默认边距 (twip): 上下 1440 = 1 inch = 25.4mm, 左右 1800 = 1.25 inch = 31.75mm */
    public static final int DEFAULT_TOP_BOTTOM = 1440;
    public static final int DEFAULT_LEFT_RIGHT = 1800;
    /** A4 默认尺寸 (twip) */
    public static final int A4_WIDTH = 11906;
    public static final int A4_HEIGHT = 16838;

    public DocPageLayout(int pageWidthTwips, int pageHeightTwips,
                         int marginTopTwips, int marginBottomTwips,
                         int marginLeftTwips, int marginRightTwips,
                         int marginHeaderTwips, int marginFooterTwips) {
        this.pageWidthTwips = pageWidthTwips;
        this.pageHeightTwips = pageHeightTwips;
        this.marginTopTwips = marginTopTwips;
        this.marginBottomTwips = marginBottomTwips;
        this.marginLeftTwips = marginLeftTwips;
        this.marginRightTwips = marginRightTwips;
        this.marginHeaderTwips = marginHeaderTwips;
        this.marginFooterTwips = marginFooterTwips;
    }

    // ================================================================
    // 工厂方法
    // ================================================================

    /**
     * 从 word/document.xml 的 {@code <w:sectPr>} XML 片段解析页面布局。
     *
     * <p>通过正则提取 {@code <w:pgMar>} (页边距) 和 {@code <w:pgSz>} (纸张尺寸)。
     * 单位：twip (1/1440 inch)。
     *
     * @param sectPrXml 含 {@code <w:sectPr>} 元素的 XML 字符串
     * @return 解析结果；解析失败则返回 A4 默认
     */
    public static DocPageLayout fromSectPrXml(String sectPrXml) {
        if (sectPrXml == null || sectPrXml.isEmpty()) return defaults();

        // 提取 <w:pgMar ... />
        String pgMarXml = extractElement(sectPrXml, "w:pgMar");
        String pgSzXml  = extractElement(sectPrXml, "w:pgSz");

        int top    = parseAttrInt(pgMarXml, "w:top",    DEFAULT_TOP_BOTTOM);
        int bottom = parseAttrInt(pgMarXml, "w:bottom", DEFAULT_TOP_BOTTOM);
        int left   = parseAttrInt(pgMarXml, "w:left",   DEFAULT_LEFT_RIGHT);
        int right  = parseAttrInt(pgMarXml, "w:right",  DEFAULT_LEFT_RIGHT);
        int header = parseAttrInt(pgMarXml, "w:header", 720);  // 12.7mm default
        int footer = parseAttrInt(pgMarXml, "w:footer", 720);

        int w = parseAttrInt(pgSzXml, "w:w", A4_WIDTH);
        int h = parseAttrInt(pgSzXml, "w:h", A4_HEIGHT);

        return new DocPageLayout(w, h, top, bottom, left, right, header, footer);
    }

    /** A4 + Word 默认边距 */
    public static DocPageLayout defaults() {
        return new DocPageLayout(A4_WIDTH, A4_HEIGHT,
                DEFAULT_TOP_BOTTOM, DEFAULT_TOP_BOTTOM,
                DEFAULT_LEFT_RIGHT, DEFAULT_LEFT_RIGHT, 720, 720);
    }

    // ================================================================
    // 单位换算
    // ================================================================

    /** twip → mm */
    public static int twipsToMm(int twips) {
        return Math.round(twips / 56.7f);
    }

    /** mm → twip */
    public static int mmToTwips(int mm) {
        return Math.round(mm * 56.7f);
    }

    // ================================================================
    // 纸张尺寸 (mm)
    // ================================================================

    public int pageWidthMm()  { return twipsToMm(pageWidthTwips); }
    public int pageHeightMm() { return twipsToMm(pageHeightTwips); }
    public int pageWidthTwips()  { return pageWidthTwips; }
    public int pageHeightTwips() { return pageHeightTwips; }

    // ================================================================
    // 边距
    // ================================================================

    public int marginTopTwips()    { return marginTopTwips; }
    public int marginBottomTwips() { return marginBottomTwips; }
    public int marginLeftTwips()   { return marginLeftTwips; }
    public int marginRightTwips()  { return marginRightTwips; }
    public int marginHeaderTwips() { return marginHeaderTwips; }
    public int marginFooterTwips() { return marginFooterTwips; }

    public int marginTopMm()    { return twipsToMm(marginTopTwips); }
    public int marginBottomMm() { return twipsToMm(marginBottomTwips); }
    public int marginLeftMm()   { return twipsToMm(marginLeftTwips); }
    public int marginRightMm()  { return twipsToMm(marginRightTwips); }
    public int marginHeaderMm() { return twipsToMm(marginHeaderTwips); }
    public int marginFooterMm() { return twipsToMm(marginFooterTwips); }

    // ================================================================
    // 可打印区域
    // ================================================================

    /** 有效打印宽度 (twip) */
    public int printableWidthTwips() {
        return Math.max(1, pageWidthTwips - marginLeftTwips - marginRightTwips);
    }

    /** 有效打印高度 (twip) */
    public int printableHeightTwips() {
        return Math.max(1, pageHeightTwips - marginTopTwips - marginBottomTwips);
    }

    // ================================================================
    // PaperSize 匹配
    // ================================================================

    /**
     * 匹配已知 PaperSize（±2mm 容差），不匹配则返回 CUSTOM。
     */
    public PaperSize toPaperSize() {
        int wMm = pageWidthMm(), hMm = pageHeightMm();
        PaperSize best = PaperSize.CUSTOM;
        int bestDiff = Integer.MAX_VALUE;

        for (PaperSize ps : PaperSize.values()) {
            if (ps == PaperSize.CUSTOM) continue;
            int diff = Math.abs(ps.getWidthMM() - wMm) + Math.abs(ps.getHeightMM() - hMm);
            if (diff < bestDiff) { bestDiff = diff; best = ps; }
        }
        // ±2mm 综合容差
        if (best != PaperSize.CUSTOM && bestDiff <= 4) return best;

        return PaperSize.custom(wMm, hMm);
    }

    // ================================================================
    // 行/列换算
    // ================================================================

    /**
     * 上边距占用行数。
     * @param lpi 每英寸行数 (6 = 1/6")
     */
    public int topMarginLines(int lpi) {
        return Math.round((float) marginTopTwips * lpi / 1440);
    }

    /** 下边距占用行数 */
    public int bottomMarginLines(int lpi) {
        return Math.round((float) marginBottomTwips * lpi / 1440);
    }

    /**
     * 左边距占用列数。
     * @param cpi 每英寸字符数
     */
    public int leftMarginCols(int cpi) {
        return Math.round((float) marginLeftTwips * cpi / 1440);
    }

    /** 可打印区域行数 */
    public int printableLines(int lpi) {
        return Math.max(1, Math.round((float) printableHeightTwips() * lpi / 1440));
    }

    /** 可打印区域列数 */
    public int printableCols(int cpi) {
        return Math.max(1, Math.round((float) printableWidthTwips() * cpi / 1440));
    }

    // ================================================================
    // 辅助
    // ================================================================

    /** 从 XML 中提取指定元素的字符串（含起止标签和属性） */
    private static String extractElement(String xml, String tagName) {
        Pattern p = Pattern.compile("<" + tagName + "\\s([^>]*)/?>", Pattern.DOTALL);
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(0) : "";
    }

    /** 从 XML 元素字符串中提取整数属性 */
    private static int parseAttrInt(String elementXml, String attrName, int defaultValue) {
        if (elementXml == null || elementXml.isEmpty()) return defaultValue;
        Matcher m = Pattern.compile(attrName + "=\"(\\d+)\"").matcher(elementXml);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    @Override
    public String toString() {
        return String.format("DocPageLayout{%d×%dmm, margins T%d/B%d/L%d/R%dmm, header=%dmm, footer=%dmm}",
                pageWidthMm(), pageHeightMm(),
                marginTopMm(), marginBottomMm(), marginLeftMm(), marginRightMm(),
                marginHeaderMm(), marginFooterMm());
    }
}
