package org.example.config;

/**
 * 页面布局配置 — 控制 N-up 打印（多页拼合一页）以及排列方向。
 *
 * <h3>典型场景</h3>
 * <pre>
 *   // 场景 1：普通打印 (1 页 / 张)
 *   PageLayout normal = PageLayout.normal();
 *
 *   // 场景 2：A4 纸打 2 页 A5 横版 (上下排列)
 *   PageLayout twoUp = PageLayout.twoUp(Direction.VERTICAL, PaperSize.A5_LANDSCAPE);
 *
 *   // 场景 3：A4 纸打 2 页 A5 竖版 (左右排列)
 *   PageLayout twoUpH = PageLayout.twoUp(Direction.HORIZONTAL, PaperSize.A5);
 *
 *   // 场景 4：A4 纸打 4 页 A5
 *   PageLayout fourUp = PageLayout.fourUp(PaperSize.A5);
 * </pre>
 */
public class PageLayout {

    /** N-up 模式 */
    public enum NUpMode {
        /** 普通：每张纸 1 个逻辑页 */
        NORMAL,
        /** 每张纸 2 个逻辑页 */
        TWO_UP,
        /** 每张纸 4 个逻辑页 */
        FOUR_UP
    }

    /** 多页排列方向 */
    public enum Direction {
        /**
         * 垂直排列 — 逻辑页从上到下依次排列。
         * 适用于针式打印机从上方进纸的自然打印顺序。
         */
        VERTICAL,
        /**
         * 水平排列 — 逻辑页从左到右依次排列。
         * 需要配合绝对水平定位命令使用。
         */
        HORIZONTAL
    }

    // ================================================================
    // 字段
    // ================================================================

    private final NUpMode nUpMode;
    private final Direction direction;
    private final PaperSize logicalPageSize;

    // 内部计算的辅助值
    private final int logicalPageLines;   // 每个逻辑页的最大行数
    private final int logicalPageColumns; // 每个逻辑页的最大列数

    // ================================================================
    // 构造器
    // ================================================================

    private PageLayout(NUpMode nUpMode, Direction direction, PaperSize logicalPageSize) {
        this.nUpMode = nUpMode;
        this.direction = direction;
        this.logicalPageSize = logicalPageSize;
        this.logicalPageLines = logicalPageSize.linesAt6LPI();
        this.logicalPageColumns = logicalPageSize.defaultColumns();
    }

    // ================================================================
    // 静态工厂方法
    // ================================================================

    /** 普通单页打印 (1-up) */
    public static PageLayout normal() {
        return new PageLayout(NUpMode.NORMAL, Direction.VERTICAL, PaperSize.A4);
    }

    /** 双页拼合打印 (2-up)，指定排列方向和逻辑页尺寸 */
    public static PageLayout twoUp(Direction direction, PaperSize logicalPageSize) {
        return new PageLayout(NUpMode.TWO_UP, direction, logicalPageSize);
    }

    /** 四页拼合打印 (4-up) */
    public static PageLayout fourUp(PaperSize logicalPageSize) {
        return new PageLayout(NUpMode.FOUR_UP, Direction.VERTICAL, logicalPageSize);
    }

    // ================================================================
    // 便捷工厂：常见预设
    // ================================================================

    /**
     * A4 纸纵向打印 2 页 A5 横版 — 上下各一页。
     * 适用于：A4 (210×297mm) 上，上下排列两个 A5 横版 (210×148mm)。
     * 这是用户最常见的 "两页 A5 横版打到一张 A4" 场景。
     */
    public static PageLayout a4_twoA5LandscapeVertical() {
        return twoUp(Direction.VERTICAL, PaperSize.A5_LANDSCAPE);
    }

    /**
     * A4 纸横向打印 2 页 A5 竖版 — 左右各一页。
     * 适用于：A4 横放时，左右排列两个 A5 竖版 (148×210mm)。
     */
    public static PageLayout a4_twoA5PortraitHorizontal() {
        return twoUp(Direction.HORIZONTAL, PaperSize.A5);
    }

    // ================================================================
    // getter
    // ================================================================

    public NUpMode getNUpMode() {
        return nUpMode;
    }

    public Direction getDirection() {
        return direction;
    }

    public PaperSize getLogicalPageSize() {
        return logicalPageSize;
    }

    /** 每个逻辑页的最大行数 (基于 1/6" 行间距) */
    public int getLogicalPageLines() {
        return logicalPageLines;
    }

    /** 每个逻辑页的最大列数 (基于 10 CPI) */
    public int getLogicalPageColumns() {
        return logicalPageColumns;
    }

    /** 每张物理纸上的逻辑页数量 */
    public int pagesPerSheet() {
        switch (nUpMode) {
            case TWO_UP:  return 2;
            case FOUR_UP: return 4;
            default:      return 1;
        }
    }

    /** 是否为多页拼合模式 */
    public boolean isMultiUp() {
        return nUpMode != NUpMode.NORMAL;
    }

    @Override
    public String toString() {
        if (nUpMode == NUpMode.NORMAL) {
            return "PageLayout[NORMAL]";
        }
        return String.format("PageLayout[%s, %s, logical=%s, %d lines/pg]",
                nUpMode, direction, logicalPageSize, logicalPageLines);
    }
}
