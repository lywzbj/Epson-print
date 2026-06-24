package org.example.config;

/**
 * 打印配置类 — 集中管理所有打印参数，通过 Builder 模式构建后传递给 {@code PrinterService}。
 *
 * <h3>典型用法</h3>
 * <pre>
 *   // 最简配置
 *   PrintConfig config = PrintConfig.builder().build();
 *   printer.applyConfig(config);
 *
 *   // A4 纸打 2 页 A5 横版 (上下排列)
 *   PrintConfig twoUp = PrintConfig.builder()
 *           .pageLayout(PageLayout.a4_twoA5LandscapeVertical())
 *           .cpi(10)
 *           .lineSpacing(LineSpacing.ONE_SIXTH)
 *           .build();
 *
 *   // 自定义完整配置
 *   PrintConfig custom = PrintConfig.builder()
 *           .physicalPaper(PaperSize.A4)
 *           .pageLayout(PageLayout.twoUp(Direction.VERTICAL, PaperSize.A5_LANDSCAPE))
 *           .topMargin(2)
 *           .bottomMargin(2)
 *           .leftMargin(3)
 *           .cpi(12)
 *           .lineSpacing(LineSpacing.ONE_SIXTH)
 *           .bold(true)
 *           .chineseFont(ChineseFont.SONG_TI)
 *           .chineseCharWidth(2)
 *           .build();
 *
 *   printer.applyConfig(custom);
 * </pre>
 *
 * @see PageLayout
 * @see PaperSize
 */
public class PrintConfig {

    // ================================================================
    // 嵌套枚举 / 常量
    // ================================================================

    /** 行间距预设 */
    public enum LineSpacing {
        /** 1/8 英寸 (ESC 0) */
        ONE_EIGHTH,
        /** 1/6 英寸 (ESC 2) — 默认 */
        ONE_SIXTH,
        /** n/216 英寸 (ESC 3 n) */
        N_216TH,
        /** n/72 英寸 (ESC A n) */
        N_72ND
    }

    /** 汉字字体 */
    public enum ChineseFont {
        /** 宋体 */
        SONG_TI(0),
        /** 黑体 */
        HEI_TI(1);

        private final int code;
        ChineseFont(int code) { this.code = code; }
        public int getCode() { return code; }
    }

    // ================================================================
    // 字段
    // ================================================================

    // — 纸张与布局 —
    private final PaperSize physicalPaper;       // 物理纸张尺寸
    private final PageLayout pageLayout;         // 页面布局 (含 N-up 设置)

    // — 边距 (行数/列数) —
    private final int topMargin;                 // 顶部空白 (行)
    private final int bottomMargin;              // 底部空白 (行), 映射到 ESC N
    private final int leftMargin;                // 左边界 (列), 映射到 ESC l
    private final int rightMargin;               // 右边界 (列), 映射到 ESC Q

    // — 字符间距 —
    private final int cpi;                       // 每英寸字符数: 10, 12, 15

    // — 行间距 —
    private final LineSpacing lineSpacing;       // 行间距模式
    private final int lineSpacingValue;          // 行间距参数 (用于 N_216TH / N_72ND)

    // — 字体样式 —
    private final boolean bold;
    private final boolean italic;
    private final int underlineMode;             // 0=关, 1=单下划线, 2=双下划线
    private final boolean doubleStrike;
    private final boolean doubleWidth;
    private final boolean doubleHeight;
    private final int fontIndex;                 // 英文字体编号 (0=Roman, 1=SansSerif, ...)

    // — 汉字 —
    private final ChineseFont chineseFont;
    private final int chineseCharWidth;          // 汉字宽度倍数 (1~4)
    private final int chineseCharHeight;         // 汉字高度倍数 (1~4)
    private final boolean verticalPrint;         // 纵向打印

    // — 控制 —
    private final boolean unidirectional;        // 单向打印 (更精确, 速度较慢)
    private final boolean autoInit;              // 打印前是否自动初始化

    // ================================================================
    // 私有构造器 (由 Builder 调用)
    // ================================================================

    private PrintConfig(Builder builder) {
        this.physicalPaper = builder.physicalPaper;
        this.pageLayout = builder.pageLayout;
        this.topMargin = builder.topMargin;
        this.bottomMargin = builder.bottomMargin;
        this.leftMargin = builder.leftMargin;
        this.rightMargin = builder.rightMargin;
        this.cpi = builder.cpi;
        this.lineSpacing = builder.lineSpacing;
        this.lineSpacingValue = builder.lineSpacingValue;
        this.bold = builder.bold;
        this.italic = builder.italic;
        this.underlineMode = builder.underlineMode;
        this.doubleStrike = builder.doubleStrike;
        this.doubleWidth = builder.doubleWidth;
        this.doubleHeight = builder.doubleHeight;
        this.fontIndex = builder.fontIndex;
        this.chineseFont = builder.chineseFont;
        this.chineseCharWidth = builder.chineseCharWidth;
        this.chineseCharHeight = builder.chineseCharHeight;
        this.verticalPrint = builder.verticalPrint;
        this.unidirectional = builder.unidirectional;
        this.autoInit = builder.autoInit;
    }

    // ================================================================
    // 静态工厂：快速开始
    // ================================================================

    /** 创建默认 Builder */
    public static Builder builder() {
        return new Builder();
    }

    /** 默认配置 (A4, 1-up, 10 CPI, 1/6") */
    public static PrintConfig defaultConfig() {
        return builder().build();
    }

    /**
     * 便捷：A4 纸打 2 页 A5 横版 (上下排列)。
     * 这是 "两页 A5 横向打到一张 A4 纸" 的最常用配置。
     */
    public static PrintConfig a4TwoA5Landscape() {
        return builder()
                .physicalPaper(PaperSize.A4)
                .pageLayout(PageLayout.a4_twoA5LandscapeVertical())
                .cpi(10)
                .lineSpacing(LineSpacing.ONE_SIXTH)
                .build();
    }

    // ================================================================
    // getter
    // ================================================================

    public PaperSize getPhysicalPaper() {
        return physicalPaper;
    }

    public PageLayout getPageLayout() {
        return pageLayout;
    }

    public int getTopMargin() {
        return topMargin;
    }

    public int getBottomMargin() {
        return bottomMargin;
    }

    public int getLeftMargin() {
        return leftMargin;
    }

    public int getRightMargin() {
        return rightMargin;
    }

    public int getCpi() {
        return cpi;
    }

    public LineSpacing getLineSpacing() {
        return lineSpacing;
    }

    public int getLineSpacingValue() {
        return lineSpacingValue;
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public int getUnderlineMode() {
        return underlineMode;
    }

    public boolean isDoubleStrike() {
        return doubleStrike;
    }

    public boolean isDoubleWidth() {
        return doubleWidth;
    }

    public boolean isDoubleHeight() {
        return doubleHeight;
    }

    public int getFontIndex() {
        return fontIndex;
    }

    public ChineseFont getChineseFont() {
        return chineseFont;
    }

    public int getChineseCharWidth() {
        return chineseCharWidth;
    }

    public int getChineseCharHeight() {
        return chineseCharHeight;
    }

    public boolean isVerticalPrint() {
        return verticalPrint;
    }

    public boolean isUnidirectional() {
        return unidirectional;
    }

    public boolean isAutoInit() {
        return autoInit;
    }

    /**
     * 根据物理纸张和页面布局，计算每逻辑页的行数。
     * 多页拼合时，返回逻辑页行数；普通模式返回物理纸张行数 - 边距。
     */
    public int effectivePageLines() {
        if (pageLayout.isMultiUp()) {
            return pageLayout.getLogicalPageLines();
        }
        return physicalPaper.linesAt6LPI() - topMargin - bottomMargin;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PrintConfig{");
        sb.append("paper=").append(physicalPaper);
        sb.append(", layout=").append(pageLayout);
        sb.append(", cpi=").append(cpi);
        sb.append(", lineSpacing=").append(lineSpacing);
        if (bold) sb.append(", bold");
        if (italic) sb.append(", italic");
        if (underlineMode > 0) sb.append(", underline=").append(underlineMode);
        if (chineseFont != null) sb.append(", chineseFont=").append(chineseFont);
        sb.append('}');
        return sb.toString();
    }

    // ================================================================
    // Builder
    // ================================================================

    /**
     * PrintConfig 的建造者。
     *
     * <p>所有字段均有合理默认值，只需设置需要覆盖的项即可。
     * 默认值：
     * <ul>
     *   <li>物理纸张: A4</li>
     *   <li>页面布局: 普通单页 (1-up)</li>
     *   <li>CPI: 10</li>
     *   <li>行间距: 1/6 英寸</li>
     *   <li>边距: 均为 0</li>
     *   <li>样式: 全部关闭</li>
     *   <li>汉字字体: 宋体, 1×1</li>
     *   <li>自动初始化: true</li>
     * </ul>
     */
    public static class Builder {
        private PaperSize physicalPaper = PaperSize.A4;
        private PageLayout pageLayout = PageLayout.normal();
        private int topMargin = 0;
        private int bottomMargin = 0;
        private int leftMargin = 0;
        private int rightMargin = 0;
        private int cpi = 10;
        private LineSpacing lineSpacing = LineSpacing.ONE_SIXTH;
        private int lineSpacingValue = 0;
        private boolean bold = false;
        private boolean italic = false;
        private int underlineMode = 0;
        private boolean doubleStrike = false;
        private boolean doubleWidth = false;
        private boolean doubleHeight = false;
        private int fontIndex = 0;
        private ChineseFont chineseFont = ChineseFont.SONG_TI;
        private int chineseCharWidth = 1;
        private int chineseCharHeight = 1;
        private boolean verticalPrint = false;
        private boolean unidirectional = false;
        private boolean autoInit = true;

        // -- 纸张与布局 --

        /** 设置物理纸张尺寸 */
        public Builder physicalPaper(PaperSize paper) {
            this.physicalPaper = paper;
            return this;
        }

        /** 设置页面布局 (含 N-up 设置) */
        public Builder pageLayout(PageLayout layout) {
            this.pageLayout = layout;
            return this;
        }

        // -- 边距 --

        /** 顶部空白行数 */
        public Builder topMargin(int lines) {
            this.topMargin = Math.max(0, lines);
            return this;
        }

        /** 底部空白行数 (页缝跳过) */
        public Builder bottomMargin(int lines) {
            this.bottomMargin = Math.max(0, lines);
            return this;
        }

        /** 左边界列数 */
        public Builder leftMargin(int cols) {
            this.leftMargin = Math.max(0, cols);
            return this;
        }

        /** 右边界列数 */
        public Builder rightMargin(int cols) {
            this.rightMargin = Math.max(0, cols);
            return this;
        }

        // -- 字符间距 --

        /** 设置 CPI (10 / 12 / 15) */
        public Builder cpi(int cpi) {
            if (cpi != 10 && cpi != 12 && cpi != 15) {
                throw new IllegalArgumentException("CPI 仅支持 10, 12, 15");
            }
            this.cpi = cpi;
            return this;
        }

        // -- 行间距 --

        /** 设置行间距模式 */
        public Builder lineSpacing(LineSpacing mode) {
            this.lineSpacing = mode;
            return this;
        }

        /** 设置行间距参数值 (用于 N_216TH / N_72ND 模式) */
        public Builder lineSpacingValue(int value) {
            this.lineSpacingValue = Math.max(0, value);
            return this;
        }

        // -- 字体样式 --

        public Builder bold(boolean on) { this.bold = on; return this; }
        public Builder italic(boolean on) { this.italic = on; return this; }

        /** 设置下划线模式 (0=关, 1=单线, 2=双线) */
        public Builder underline(int mode) {
            if (mode < 0 || mode > 2) {
                throw new IllegalArgumentException("下划线模式仅支持 0, 1, 2");
            }
            this.underlineMode = mode;
            return this;
        }

        public Builder doubleStrike(boolean on) { this.doubleStrike = on; return this; }
        public Builder doubleWidth(boolean on) { this.doubleWidth = on; return this; }
        public Builder doubleHeight(boolean on) { this.doubleHeight = on; return this; }

        /** 选择英文字体 (0=Roman, 1=SansSerif, 2=Courier, ...) */
        public Builder fontIndex(int index) {
            this.fontIndex = Math.max(0, index);
            return this;
        }

        // -- 汉字 --

        /** 设置汉字字体 */
        public Builder chineseFont(ChineseFont font) {
            this.chineseFont = font;
            return this;
        }

        /** 设置汉字字符大小 (1~4 倍) */
        public Builder chineseCharSize(int width, int height) {
            this.chineseCharWidth = clamp(width, 1, 4);
            this.chineseCharHeight = clamp(height, 1, 4);
            return this;
        }

        /** 仅设置汉字宽度 */
        public Builder chineseCharWidth(int w) {
            this.chineseCharWidth = clamp(w, 1, 4);
            return this;
        }

        /** 仅设置汉字高度 */
        public Builder chineseCharHeight(int h) {
            this.chineseCharHeight = clamp(h, 1, 4);
            return this;
        }

        /** 是否纵向打印汉字 */
        public Builder verticalPrint(boolean on) { this.verticalPrint = on; return this; }

        // -- 控制 --

        /** 是否单向打印 (更精确, 较慢) */
        public Builder unidirectional(boolean on) { this.unidirectional = on; return this; }

        /** 打印前是否自动发送初始化命令 */
        public Builder autoInit(boolean on) { this.autoInit = on; return this; }

        // -- 构建 --

        public PrintConfig build() {
            return new PrintConfig(this);
        }

        private static int clamp(int val, int min, int max) {
            return Math.max(min, Math.min(max, val));
        }
    }
}
