package org.example.word;

import java.util.*;

/**
 * 打印任务描述 — 支持在单次打印中混合选择不同页的不同内容。
 *
 * <h3>核心思路</h3>
 * 用户按"页级"思维描述要打什么，PrintJob 内部将页内行号/表格行号
 * 解析为全局行号，交给 {@link PrintSelector} 和 {@link WordPrinter} 执行。
 *
 * <h3>典型用法</h3>
 * <pre>
 *   PrintJob job = PrintJob.builder()
 *       .select(1).all("第 1 页全部")
 *       .select(2).lines("第 2 页第 3,5 行", 3, 5)
 *       .select(3).table(0).rows("第 3 页表格 #0 第 1~3 行", 1, 2, 3).noBorder()
 *       .build();
 *
 *   job.execute(printer, doc);
 * </pre>
 */
public class PrintJob {

    /** 每页行数（与 WordDocument 默认一致） */
    private final int linesPerPage;
    private final List<JobRule> rules;

    private PrintJob(int linesPerPage, List<JobRule> rules) {
        this.linesPerPage = linesPerPage;
        this.rules = new ArrayList<>(rules);
    }

    // ================================================================
    // 执行
    // ================================================================

    /**
     * 在指定打印机上执行本打印任务。
     *
     * @param printer 已打开的 PrinterService
     * @param doc     已加载的 WordDocument
     */
    public void execute(org.example.service.PrinterService printer, WordDocument doc)
            throws java.io.IOException {
        int totalPages = doc.getTotalPages();
        System.out.println("  文档: " + doc.getParagraphCount() + " 段, "
                + totalPages + " 页, 共 " + rules.size() + " 条规则");

        WordPrinter wordPrinter = new WordPrinter(printer);
        int seg = 0;

        for (JobRule rule : rules) {
            seg++;
            rule.validate(totalPages);
            System.out.println("\n--- 第 " + seg + "/" + rules.size()
                    + " 段: " + rule.getLabel() + " ---");

            PrintSelector selector = rule.toSelector(linesPerPage);
            System.out.println("  选择器: " + selector);

            wordPrinter.print(doc, selector);

            if (seg < rules.size()) {
                printer.feedLines(3);  // 段间分隔
            }
        }
        System.out.println("\n  全部 " + seg + " 段打印完成!");
    }

    /**
     * 用 PrintConfig + printWithConfig 执行（支持 A5 2-up 等布局）。
     */
    public void executeWithConfig(org.example.service.PrinterService printer,
                                   WordDocument doc,
                                   org.example.config.PrintConfig config)
            throws java.io.IOException {
        int totalPages = doc.getTotalPages();
        System.out.println("  文档: " + doc.getParagraphCount() + " 段, "
                + totalPages + " 页, 共 " + rules.size() + " 条规则");

        WordPrinter wordPrinter = new WordPrinter(printer);
        int seg = 0;

        for (JobRule rule : rules) {
            seg++;
            rule.validate(totalPages);
            System.out.println("\n--- 第 " + seg + "/" + rules.size()
                    + " 段: " + rule.getLabel() + " ---");

            PrintSelector selector = rule.toSelector(linesPerPage);
            System.out.println("  选择器: " + selector);

            wordPrinter.printWithConfig(doc, selector, config);

            if (seg < rules.size()) {
                printer.feedLines(3);
            }
        }
        System.out.println("\n  全部 " + seg + " 段打印完成!");
    }

    // ================================================================
    // Builder 入口
    // ================================================================

    public static PageSelector builder() {
        return new Builder();
    }

    // ================================================================
    // 内部类：一条选择规则
    // ================================================================

    public static class JobRule {
        private final String label;
        private final int page;             // 页码 (1-based)
        private final boolean all;          // 整页
        private final int[] lines;          // 页内行号 (1-based, 相对于该页起始)
        private final Integer tableIndex;   // 表格序号 (null 表示不限表格)
        private final int[] tableRows;      // 表格内行号 (1-based)
        private final boolean border;       // 表格边框

        JobRule(String label, int page, boolean all, int[] lines,
                Integer tableIndex, int[] tableRows, boolean border) {
            this.label = (label != null) ? label : buildDefaultLabel(page, all, lines, tableIndex, tableRows);
            this.page = page;
            this.all = all;
            this.lines = lines;
            this.tableIndex = tableIndex;
            this.tableRows = tableRows;
            this.border = border;
        }

        public String getLabel() { return label; }

        private static String buildDefaultLabel(int page, boolean all, int[] lines,
                                                 Integer tableIndex, int[] tableRows) {
            if (all) return "第 " + page + " 页(全部)";
            if (tableIndex != null) {
                return "第 " + page + " 页/表格#" + tableIndex + " 行" + Arrays.toString(tableRows);
            }
            return "第 " + page + " 页 行" + Arrays.toString(lines);
        }

        void validate(int totalPages) {
            if (page < 1 || page > totalPages) {
                throw new IllegalArgumentException(
                        "页码 " + page + " 超出范围 (1~" + totalPages + ")");
            }
        }

        /**
         * 将页级规则转换为全局 PrintSelector。
         */
        PrintSelector toSelector(int linesPerPage) {
            int pageBase = (page - 1) * linesPerPage;  // 该页起始前已跳过的行数

            if (all) {
                // 整页：lineRange
                int start = pageBase + 1;
                int end = pageBase + linesPerPage;
                return new PrintSelector().lineRange(start, end);
            }

            if (tableIndex != null && tableRows.length > 0) {
                // 指定表格的某些行
                int[] globalLines = new int[tableRows.length];
                // 需要将 "表格内行号" 映射为 "全局行号"。
                // 表格行也是段落的一部分，在 pageBase 之后顺序排列。
                // 这里先假设表格行号在页内顺序就是其段落序号（简化处理）。
                // 实际由 PrintSelector.tableRow/tableRows 按表格元数据精确过滤。
                PrintSelector s = new PrintSelector()
                        .lineRange(pageBase + 1, pageBase + linesPerPage)
                        .skipNonTable(true)
                        .tableBorder(border);

                if (tableRows.length == 1) {
                    s.tableRow(tableIndex, tableRows[0]);
                } else {
                    int min = tableRows[0], max = tableRows[0];
                    for (int r : tableRows) { min = Math.min(min, r); max = Math.max(max, r); }
                    s.tableRows(tableIndex, min, max);
                }
                return s;
            }

            if (lines.length > 0) {
                // 页内特定行 → 全局行号
                int[] globalLines = new int[lines.length];
                for (int i = 0; i < lines.length; i++) {
                    globalLines[i] = pageBase + lines[i];
                }
                return new PrintSelector()
                        .lineRange(pageBase + 1, pageBase + linesPerPage)
                        .selectLines(globalLines);
            }

            // fallback: 整页
            return new PrintSelector().lineRange(pageBase + 1, pageBase + linesPerPage);
        }
    }

    // ================================================================
    // Builder 链式接口
    // ================================================================

    /** 顶层 Builder：选择页码 */
    public interface PageSelector {
        /** 选择指定页 (1-based) */
        PageAction select(int page);
    }

    /** 选中某一页后的操作 */
    public interface PageAction {
        /** 打印该页全部内容 */
        RowSelector all();
        /** 打印该页全部内容，带标签 */
        RowSelector all(String label);
        /** 打印该页中的指定行（页内行号） */
        RowSelector lines(int... lineNumbers);
        /** 打印该页中的指定行，带标签 */
        RowSelector lines(String label, int... lineNumbers);
        /** 选择该页中的表格 */
        TableAction table(int tableIndex);
    }

    /** 完成当前规则后，可继续选择下一页，或构建 */
    public interface RowSelector {
        /** 继续选择下一页 */
        PageSelector and();
        /** 完成构建 */
        PrintJob build();
    }

    /** 表格子选择器 */
    public interface TableAction {
        /** 打印表格中的指定行 */
        TableRowSelector rows(int... rowNumbers);
        /** 打印表格中的指定行，带标签 */
        TableRowSelector rows(String label, int... rowNumbers);
    }

    /** 表格行选择 + 选项 */
    public interface TableRowSelector {
        /** 去掉表格边框分隔符 */
        TableRowSelector noBorder();
        /** 保留边框（默认） */
        TableRowSelector withBorder();
        /** 继续选择下一页 */
        PageSelector and();
        /** 完成构建 */
        PrintJob build();
    }

    // ================================================================
    // Builder 实现
    // ================================================================

    static class Builder implements PageSelector, PageAction, RowSelector,
            TableAction, TableRowSelector {

        private final List<JobRule> rules = new ArrayList<>();
        private int currentPage = 0;
        private String currentLabel;
        // 当前规则参数
        private boolean currentAll;
        private int[] currentLines = new int[0];
        private Integer currentTableIndex;
        private int[] currentTableRows = new int[0];
        private boolean currentBorder = true;

        @Override
        public PageAction select(int page) {
            flushRule();
            currentPage = page;
            currentLabel = null;
            currentAll = false;
            currentLines = new int[0];
            currentTableIndex = null;
            currentTableRows = new int[0];
            currentBorder = true;
            return this;
        }

        @Override
        public RowSelector all() { currentAll = true; return this; }

        @Override
        public RowSelector all(String label) { currentLabel = label; currentAll = true; return this; }

        @Override
        public RowSelector lines(int... lineNumbers) {
            currentLines = lineNumbers;
            return this;
        }

        @Override
        public RowSelector lines(String label, int... lineNumbers) {
            currentLabel = label;
            currentLines = lineNumbers;
            return this;
        }

        @Override
        public TableAction table(int tableIndex) {
            currentTableIndex = tableIndex;
            return this;
        }

        @Override
        public TableRowSelector rows(int... rowNumbers) {
            currentTableRows = rowNumbers;
            return this;
        }

        @Override
        public TableRowSelector rows(String label, int... rowNumbers) {
            currentLabel = label;
            currentTableRows = rowNumbers;
            return this;
        }

        @Override
        public TableRowSelector noBorder() { currentBorder = false; return this; }

        @Override
        public TableRowSelector withBorder() { currentBorder = true; return this; }

        @Override
        public PageSelector and() {
            flushRule();
            return this;
        }

        @Override
        public PrintJob build() {
            flushRule();
            return new PrintJob(66, rules);
        }

        private void flushRule() {
            if (currentPage > 0) {
                rules.add(new JobRule(currentLabel, currentPage, currentAll,
                        currentLines, currentTableIndex, currentTableRows, currentBorder));
                currentPage = 0;
            }
        }
    }
}
