package org.example.word;

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

    /** 跳过指定行号 */
    public PrintSelector skipLines(int... lineNumbers) {
        for (int n : lineNumbers) {
            this.skipLineNumbers.add(n);
        }
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

        // 行范围过滤
        if (lineStart > 0 && lineNumber < lineStart) return false;
        if (lineEnd > 0 && lineNumber > lineEnd) return false;

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
                || !skipMarkers.isEmpty() || !skipLineNumbers.isEmpty();
    }

    public Set<Integer> selectedPages() { return Collections.unmodifiableSet(selectedPages); }
    public int lineStart() { return lineStart; }
    public int lineEnd() { return lineEnd; }
    public int linesPerPage() { return linesPerPage; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PrintSelector[");
        if (!selectedPages.isEmpty()) sb.append("pages=").append(selectedPages).append(", ");
        if (lineStart > 0 || lineEnd > 0) sb.append("lines=").append(lineStart).append("-").append(lineEnd).append(", ");
        if (skipEmptyLines) sb.append("skipEmpty, ");
        if (!skipMarkers.isEmpty()) sb.append("skipMarkers=").append(skipMarkers).append(", ");
        if (!skipLineNumbers.isEmpty()) sb.append("skipLines=").append(skipLineNumbers).append(", ");
        if (sb.charAt(sb.length() - 2) == ',') sb.setLength(sb.length() - 2);
        sb.append("]");
        return sb.toString();
    }
}
