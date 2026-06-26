package org.example.word;

import org.example.word.WordDocument.WordParagraph;

import java.util.*;

/**
 * 打印选择器 — DSL 风格构建打印规则。
 *
 * 支持的选择规则：
 *   - 页码范围："1-3,5,7-9"
 *   - 行范围：10 ~ 50
 *   - 跳过空行
 *   - 跳过含指定标记的行
 *   - 跳过指定行号
 *
 * 使用示例：
 * <pre>
 *   PrintSelector selector = new PrintSelector()
 *       .pageRange("1-3,5")
 *       .lineRange(10, 80)
 *       .skipEmptyLines(true)
 *       .skipByMarker("###SKIP");
 *
 *   if (selector.shouldPrint(lineNum, text)) { ... }
 * </pre>
 */
public class PrintSelector {

    /** 页码范围：每页行数（针式打印机通常 66 行/页） */
    private static final int DEFAULT_LINES_PER_PAGE = 66;

    private int linesPerPage = DEFAULT_LINES_PER_PAGE;
    private final Set<Integer> selectedPages = new LinkedHashSet<>();
    private int lineStart = -1;
    private int lineEnd = -1;
    private boolean skipEmptyLines = false;
    private final Set<String> skipMarkers = new LinkedHashSet<>();
    private final Set<Integer> skipLineNumbers = new LinkedHashSet<>();
    private final Set<Integer> selectLineNumbers = new LinkedHashSet<>();  // 白名单：只打印这些行

    // 表格过滤
    private boolean skipNonTable = false;       // 只打印表格行
    private int targetTableIndex = -1;          // 目标表格序号 (-1=不限)
    private int targetTableRow = 0;             // 目标表格行号 (1-based, 0=不限)
    private int targetTableRowEnd = 0;          // 目标表格行结束号 (>=targetTableRow 时表示范围)
    private boolean tableBorder = true;         // 是否保留表格边框 (false=去掉 " | " 分隔符)
    private boolean leaveBlank = false;         // 跳过行时是否留白进纸 (false=跳过不占位, true=发LF留空行)

    // ======== 构建方法 (链式) ========

    /** 设置每页行数（用于页码范围计算） */
    public PrintSelector linesPerPage(int n) {
        this.linesPerPage = n;
        return this;
    }

    /**
     * 选择打印的页码范围。如 "1-3,5,7-9"。
     * 页号从 1 开始。
     */
    public PrintSelector pageRange(String range) {
        selectedPages.clear();
        if (range == null || range.trim().isEmpty()) {
            return this;
        }
        String[] parts = range.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.contains("-")) {
                String[] se = part.split("-");
                int s = Integer.parseInt(se[0].trim());
                int e = Integer.parseInt(se[1].trim());
                for (int i = s; i <= e; i++) {
                    selectedPages.add(i);
                }
            } else {
                selectedPages.add(Integer.parseInt(part));
            }
        }
        return this;
    }

    /**
     * 选择打印的页码范围。
     * @param start 起始页（含，1-based）
     * @param end   结束页（含）
     */
    public PrintSelector pageRange(int start, int end) {
        for (int i = start; i <= end; i++) {
            selectedPages.add(i);
        }
        return this;
    }

    /**
     * 选择打印的行范围。
     * @param start 起始行号（含，1-based）
     * @param end   结束行号（含）
     */
    public PrintSelector lineRange(int start, int end) {
        this.lineStart = start;
        this.lineEnd = end;
        return this;
    }

    /** 是否跳过空行 */
    public PrintSelector skipEmptyLines(boolean skip) {
        this.skipEmptyLines = skip;
        return this;
    }

    /** 跳过包含指定标记文本的行（如 "###SKIP"） */
    public PrintSelector skipByMarker(String marker) {
        if (marker != null && !marker.isEmpty()) {
            this.skipMarkers.add(marker);
        }
        return this;
    }

    /** 跳过包含任一指定标记的行 */
    public PrintSelector skipByMarkers(String... markers) {
        for (String m : markers) {
            skipByMarker(m);
        }
        return this;
    }

    /** 跳过指定行号（全局，黑名单） */
    public PrintSelector skipLines(int... lineNumbers) {
        for (int n : lineNumbers) {
            this.skipLineNumbers.add(n);
        }
        return this;
    }

    /**
     * 跳过从第 1 行开始的连续 N 行。
     * 组合 lineRange 时，从范围起始处开始跳过。
     *
     * <p>示例：跳过前 3 行（常用于去掉封面/标题行）
     * <pre>
     *   new PrintSelector().lineRange(1, 100).skipFirst(3);
     * </pre>
     */
    public PrintSelector skipFirst(int count) {
        for (int i = 1; i <= count; i++) {
            this.skipLineNumbers.add(i);
        }
        return this;
    }

    /**
     * 跳过从指定偏移开始的连续 N 行。
     *
     * <p>示例：从第 10 行开始跳过 5 行 (10~14)
     * <pre>
     *   new PrintSelector().skipRange(10, 5);
     * </pre>
     */
    public PrintSelector skipRange(int from, int count) {
        for (int i = 0; i < count; i++) {
            this.skipLineNumbers.add(from + i);
        }
        return this;
    }

    /**
     * 只打印指定行号（全局，白名单）。
     * 设置后 lineRange 失效，只打印白名单中的行。
     * 可与 skipLines 叠加：白名单中的行如果也在 skipLines 中，仍被跳过。
     *
     * <p>示例：
     * <pre>
     *   // 只打印第 1, 3, 5, 7 行
     *   new PrintSelector().selectLines(1, 3, 5, 7);
     *
     *   // 打印第 1~100 行中除 2,4,6 的所有行（白名单+黑名单组合）
     *   new PrintSelector().selectLines(1, 2, 3, 4, 5, 6).skipLines(2, 4, 6);
     * </pre>
     */
    public PrintSelector selectLines(int... lineNumbers) {
        for (int n : lineNumbers) {
            this.selectLineNumbers.add(n);
        }
        return this;
    }

    // -- 表格过滤 --

    /**
     * 跳过所有非表格行，只打印表格内容。
     */
    public PrintSelector skipNonTable(boolean skip) {
        this.skipNonTable = skip;
        return this;
    }

    /**
     * 只打印指定表格的指定行。
     * @param tableIndex 表格序号 (0-based，Word 文档中第几个表格)
     * @param rowInTable 表格内行号 (1-based)
     */
    public PrintSelector tableRow(int tableIndex, int rowInTable) {
        this.targetTableIndex = tableIndex;
        this.targetTableRow = rowInTable;
        this.targetTableRowEnd = 0;
        return this;
    }

    /**
     * 只打印指定表格的行范围（多行）。
     * @param tableIndex 表格序号 (0-based)
     * @param fromRow    起始行号 (1-based, 含)
     * @param toRow      结束行号 (1-based, 含)
     */
    public PrintSelector tableRows(int tableIndex, int fromRow, int toRow) {
        this.targetTableIndex = tableIndex;
        this.targetTableRow = fromRow;
        this.targetTableRowEnd = toRow;
        return this;
    }

    /**
     * 是否保留表格单元格之间的 " | " 分隔符。
     * <ul>
     *   <li>{@code tableBorder(true)}  — 保留边框（默认），如 "张三 | 25 | 研发部"</li>
     *   <li>{@code tableBorder(false)} — 去掉边框，如 "张三 25 研发部"</li>
     * </ul>
     */
    public PrintSelector tableBorder(boolean on) {
        this.tableBorder = on;
        return this;
    }

    /**
     * 跳过行时是否在纸上留白（发送 LF 进纸空行）。
     * <ul>
     *   <li>{@code leaveBlank(false)} — 默认：跳过的行不占纸面，后续内容紧密排列</li>
     *   <li>{@code leaveBlank(true)}  — 跳过时发 LF，纸继续走，保留空白位置</li>
     * </ul>
     */
    public PrintSelector leaveBlank(boolean on) {
        this.leaveBlank = on;
        return this;
    }

    // ======== 判断方法 ========

    /**
     * 判断某一行是否应该被打印。
     * @param lineNumber 当前行号（1-based，全局计数）
     * @param text       该行文本内容
     * @return true = 应该打印
     */
    public boolean shouldPrint(int lineNumber, String text) {
        // 页码过滤
        if (!selectedPages.isEmpty()) {
            int page = pageOf(lineNumber);
            if (!selectedPages.contains(page)) return false;
        }

        // 行范围过滤（白名单优先，有白名单时行范围无效）
        if (!selectLineNumbers.isEmpty()) {
            if (!selectLineNumbers.contains(lineNumber)) return false;
        } else {
            if (lineStart > 0 && lineNumber < lineStart) return false;
            if (lineEnd > 0 && lineNumber > lineEnd) return false;
        }

        // 跳过空行
        if (skipEmptyLines && (text == null || text.trim().isEmpty())) return false;

        // 跳过含标记的行
        if (!skipMarkers.isEmpty() && text != null) {
            for (String marker : skipMarkers) {
                if (text.contains(marker)) return false;
            }
        }

        // 跳过指定行号
        if (skipLineNumbers.contains(lineNumber)) return false;

        return true;
    }

    /**
     * 判断某一行是否应该被打印（含表格过滤）。
     * WordPrinter 调用此方法，可获取 WordParagraph 的表格来源信息。
     */
    public boolean accept(int lineNumber, WordParagraph wp) {
        // 先走基础文本过滤
        if (!shouldPrint(lineNumber, wp.getText())) return false;

        // 只打印表格行
        if (skipNonTable && !wp.isTableRow()) return false;

        // 指定表格 + 行号 (单行或范围)
        if (targetTableIndex >= 0 && targetTableRow > 0) {
            if (wp.getTableIndex() != targetTableIndex) return false;
            int row = wp.getRowInTable();
            if (targetTableRowEnd >= targetTableRow) {
                // 范围匹配
                if (row < targetTableRow || row > targetTableRowEnd) return false;
            } else {
                // 单行匹配
                if (row != targetTableRow) return false;
            }
        }

        return true;
    }

    /**
     * 对表格行文本进行格式化转换。
     * 当 tableBorder=false 时，去掉 " | " 分隔符，各单元格用空格分隔。
     *
     * @param wp  段落对象
     * @return 格式化后的文本（非表格行原样返回）
     */
    public String formatTableText(WordParagraph wp) {
        if (!tableBorder && wp.isTableRow()) {
            return wp.getText().replace(" | ", "  ");
        }
        return wp.getText();
    }

    /**
     * 计算行号所在的页（1-based）。
     */
    public int pageOf(int lineNumber) {
        return (lineNumber - 1) / linesPerPage + 1;
    }

    /** 估计总页数 */
    public int totalPages(int totalLines) {
        return (int) Math.ceil((double) totalLines / linesPerPage);
    }

    // ======== 状态查询 ========

    public boolean hasPageFilter() { return !selectedPages.isEmpty(); }
    public boolean hasLineFilter() { return lineStart > 0 || lineEnd > 0; }
    public boolean hasAnyFilter() {
        return hasPageFilter() || hasLineFilter() || skipEmptyLines
                || skipNonTable || targetTableIndex >= 0 || targetTableRow > 0
                || !skipMarkers.isEmpty() || !skipLineNumbers.isEmpty()
                || !selectLineNumbers.isEmpty();
    }

    public Set<Integer> selectedPages() { return Collections.unmodifiableSet(selectedPages); }
    /** 清除页面过滤，使 pageOf 不再过滤任何行 */
    public PrintSelector clearPageFilter() { selectedPages.clear(); return this; }
    /** 批量添加白名单行号 */
    public PrintSelector addSelectLines(Collection<Integer> lineNumbers) {
        selectLineNumbers.addAll(lineNumbers); return this;
    }
    public int lineStart() { return lineStart; }
    public int lineEnd() { return lineEnd; }
    public int linesPerPage() { return linesPerPage; }
    public boolean isTableBorder() { return tableBorder; }
    public boolean isLeaveBlank()  { return leaveBlank; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PrintSelector[");
        if (!selectedPages.isEmpty()) sb.append("pages=").append(selectedPages).append(", ");
        if (!selectLineNumbers.isEmpty()) sb.append("selectLines=").append(selectLineNumbers).append(", ");
        else if (lineStart > 0 || lineEnd > 0) sb.append("lines=").append(lineStart).append("-").append(lineEnd).append(", ");
        if (skipEmptyLines) sb.append("skipEmpty, ");
        if (skipNonTable) sb.append("skipNonTable, ");
        if (targetTableIndex >= 0) sb.append("table=#").append(targetTableIndex).append(", ");
        if (targetTableRow > 0) {
            if (targetTableRowEnd >= targetTableRow) {
                sb.append("rowsInTable=").append(targetTableRow).append("-").append(targetTableRowEnd).append(", ");
            } else {
                sb.append("rowInTable=").append(targetTableRow).append(", ");
            }
        }
        if (!tableBorder) sb.append("tableBorder=off, ");
        if (leaveBlank) sb.append("leaveBlank, ");
        if (!skipMarkers.isEmpty()) sb.append("skipMarkers=").append(skipMarkers).append(", ");
        if (!skipLineNumbers.isEmpty()) sb.append("skipLines=").append(skipLineNumbers).append(", ");
        if (sb.length() >= 2 && sb.charAt(sb.length() - 2) == ',') sb.setLength(sb.length() - 2);
        sb.append("]");
        return sb.toString();
    }
}
