package org.example.config;

/**
 * 纸张尺寸定义 — 提供标准纸张的物理尺寸及在针式打印机上的逻辑换算。
 *
 * <p>尺寸换算基于以下默认值：
 * <ul>
 *   <li>水平方向：10 CPI (每英寸 10 字符)，1 英寸 ≈ 25.4 mm</li>
 *   <li>垂直方向：1/6 英寸行间距 (每英寸 6 行)</li>
 *   <li>上下留白合计默认约 19mm (约 4.5 行)</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 *   PaperSize a4 = PaperSize.A4;
 *   int maxCols = a4.columnsAt(10);   // 10 CPI 下最大列数
 *   int maxLines = a4.linesAt6LPI();  // 1/6" 下最大行数
 * </pre>
 */
public enum PaperSize {

    /** A4: 210 × 297 mm */
    A4(210, 297),

    /** A5 竖版: 148 × 210 mm */
    A5(148, 210),

    /** A5 横版: 210 × 148 mm (A5 旋转 90°) */
    A5_LANDSCAPE(210, 148),

    /** Letter: 215.9 × 279.4 mm */
    LETTER(216, 279),

    /** Legal: 215.9 × 355.6 mm */
    LEGAL(216, 356),

    /** 自定义尺寸 — 通过 {@link #custom(int, int)} 创建 */
    CUSTOM(0, 0);

    /** 纸张宽度 (mm) */
    private final int widthMM;

    /** 纸张高度 (mm) */
    private final int heightMM;

    /** 最近一次 custom() 设置的宽度 (mm) */
    private static int customWidthMM;
    /** 最近一次 custom() 设置的高度 (mm) */
    private static int customHeightMM;

    PaperSize(int widthMM, int heightMM) {
        this.widthMM = widthMM;
        this.heightMM = heightMM;
    }

    // ================================================================
    // 基本 getter
    // ================================================================

    public int getWidthMM() {
        if (this == CUSTOM) return customWidthMM;
        return widthMM;
    }

    public int getHeightMM() {
        if (this == CUSTOM) return customHeightMM;
        return heightMM;
    }

    /** 宽度 (英寸) */
    public double getWidthInches() {
        return widthMM / 25.4;
    }

    /** 高度 (英寸) */
    public double getHeightInches() {
        return heightMM / 25.4;
    }

    // ================================================================
    // 列数 / 行数换算
    // ================================================================

    /**
     * 计算指定 CPI 下的最大列数（不含边距）。
     * @param cpi 每英寸字符数 (如 10, 12, 15)
     * @return 可打印列数
     */
    public int columnsAt(int cpi) {
        return (int) Math.floor(getWidthInches() * cpi);
    }

    /**
     * 默认 10 CPI 下的最大列数。
     */
    public int defaultColumns() {
        return columnsAt(10);
    }

    /**
     * 计算 1/6 英寸行间距下的最大行数。
     * 默认预留 19mm (约 0.75") 上下边距。
     * @return 可打印行数
     */
    public int linesAt6LPI() {
        return linesAtLPI(6);
    }

    /**
     * 计算指定 LPI 下的最大行数。
     * @param lpi 每英寸行数 (如 6, 8)
     * @return 可打印行数
     */
    public int linesAtLPI(int lpi) {
        // 预留约 19mm 上下边距
        double printableHeightInches = getHeightInches() - 0.75;
        return Math.max(1, (int) Math.floor(printableHeightInches * lpi));
    }

    // ================================================================
    // 自定义
    // ================================================================

    /**
     * 创建自定义纸张尺寸。
     * @param widthMM  宽度 (mm)
     * @param heightMM 高度 (mm)
     * @return 设置了尺寸的 CUSTOM 枚举值
     */
    public static PaperSize custom(int widthMM, int heightMM) {
        customWidthMM = widthMM;
        customHeightMM = heightMM;
        return CUSTOM;
    }

    // ================================================================
    // 描述
    // ================================================================

    /** 页长 (行数) — 用于 ESC C n 命令，基于 1/6" 行间距 */
    public int pageLengthInLines() {
        return linesAt6LPI();
    }

    @Override
    public String toString() {
        return name() + "(" + widthMM + "×" + heightMM + "mm)";
    }
}
