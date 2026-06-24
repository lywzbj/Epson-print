package org.example;

import org.example.config.PrintConfig;
import org.example.connection.JavaxPrintConnection;
import org.example.connection.PrinterConnection;
import org.example.preview.PrintPreview;
import org.example.service.PrinterService;
import org.example.word.PrintJob;
import org.example.word.PrintSelector;
import org.example.word.WordDocument;
import org.example.word.WordPrinter;

import java.io.IOException;
import java.util.List;

/**
 * EPSON DLQ-3500K 打印机控制程序入口。
 *
 * <p>每个打印功能封装为独立静态方法，main 方法按需调用。
 */
public class App {

    /** 默认打印机名称（javax.print 查找时使用） */
    private static final String PRINTER_NAME = "DLQ-3500";

    /** 默认测试文件 */
    private static final String DEFAULT_FILE = "E:\\tmp\\test.docx";

    // ================================================================
    // main 入口
    // ================================================================

    public static void main(String[] args) {
        PrinterConnection connection = new JavaxPrintConnection(PRINTER_NAME);
        PrinterService printer = new PrinterService(connection);
        try {
            printer.open();
            // 【示例】打印第 1 页，跳过前 2 行并留白进纸
            printPageSkipWithBlank(printer, DEFAULT_FILE, 1, 2);

        } catch (IOException e) {
            System.err.println("打印出错: " + e.getMessage());
        } finally {
            try {
                printer.close();  // ★ 关键：close() 才真正提交打印任务
            } catch (IOException ignored) {
            }
        }
    }

    // ================================================================
    // 打印功能（静态方法）
    // ================================================================

    /**
     * 打印指定页，跳过该页的前 N 行（页内行号）。
     *
     * <p>典型场景：Word 文档第 1 页前 3 行是标题/副标题/日期，只想从第 4 行正文开始打。
     */
    public static void printPageSkipFirst(PrinterService printer, String filePath,
                                           int page, int skipCount) throws IOException {
        System.out.println("==========================================");
        System.out.println("  打印第 " + page + " 页，跳过前 " + skipCount + " 行");
        System.out.println("  文件: " + filePath);
        System.out.println("==========================================");

        PrintConfig config = PrintConfig.defaultConfig();
        printer.applyConfig(config);

        WordDocument doc = WordDocument.load(filePath);

        // 将页内行号 → 全局行号
        int pageBase = (page - 1) * doc.getLinesPerPage();
        int pageStart = pageBase + 1;
        int pageEnd   = Math.min(pageBase + doc.getLinesPerPage(), doc.getParagraphCount());

        // 跳过前 skipCount 行 → 全局行号
        int[] globalSkips = new int[skipCount];
        for (int i = 0; i < skipCount; i++) {
            globalSkips[i] = pageStart + i;
        }

        System.out.println("  页范围: 全局行 " + pageStart + " ~ " + pageEnd);
        System.out.println("  跳过: 全局行 " + pageStart + " ~ " + (pageStart + skipCount - 1));

        PrintSelector selector = new PrintSelector()
                .lineRange(pageStart, pageEnd)
                .skipLines(globalSkips);

        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.print(doc, selector);

        System.out.println("\n  打印完成! (预计 " + (pageEnd - pageStart + 1 - skipCount) + " 行)");
    }

    /**
     * 打印指定页，跳过前 N 行并留白进纸。
     *
     * <p>与 {@link #printPageSkipFirst} 的区别：跳过的行发送 LF 让纸继续走，
     * 在纸上留下相应的空白行，后续内容从正确的位置开始打印。
     *
     * <p>适用场景：模板打印 — 文档前几行是 Logo/标题占位区域，
     * 需要跳过但保留纸张空间供预印内容使用。
     */
    public static void printPageSkipWithBlank(PrinterService printer, String filePath,
                                               int page, int skipCount) throws IOException {
        System.out.println("==========================================");
        System.out.println("  打印第 " + page + " 页，跳过前 " + skipCount + " 行（留白进纸）");
        System.out.println("  文件: " + filePath);
        System.out.println("==========================================");

        PrintConfig config = PrintConfig.defaultConfig();
        printer.applyConfig(config);

        WordDocument doc = WordDocument.load(filePath);

        int pageBase = (page - 1) * doc.getLinesPerPage();
        int pageStart = pageBase + 1;
        int pageEnd   = Math.min(pageBase + doc.getLinesPerPage(), doc.getParagraphCount());

        int[] skips = new int[skipCount];
        for (int i = 0; i < skipCount; i++) {
            skips[i] = pageStart + i;
        }

        System.out.println("  页范围: 全局行 " + pageStart + " ~ " + pageEnd);
        System.out.println("  跳过留白: 全局行 " + pageStart + " ~ " + (pageStart + skipCount - 1));

        PrintSelector selector = new PrintSelector()
                .lineRange(pageStart, pageEnd)
                .skipLines(skips)
                .leaveBlank(true);          // ← 关键：跳过时发送 LF 进纸留白

        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.print(doc, selector);

        System.out.println("\n  打印完成! 前 " + skipCount + " 行已留白进纸");
    }

    /**
     * 演示 PrintSelector 各种跳过模式的完整用法（生成预览文件，不连接打印机）。
     */
    public static void printSkipDemo(PrinterService printer, String filePath)
            throws IOException {
        System.out.println("==========================================");
        System.out.println("  跳过模式演示");
        System.out.println("  文件: " + filePath);
        System.out.println("==========================================");

        WordDocument doc = WordDocument.load(filePath);
        int totalLines = doc.getParagraphCount();

        // ──── 模式 1：跳过前 N 行 ────
        System.out.println("\n--- 模式 1: 跳过前 2 行 ---");
        PrintSelector s1 = new PrintSelector().skipFirst(2);
        WordPrinter wp = new WordPrinter(printer);
        wp.print(doc, s1);

        // ──── 模式 2：跳过指定几行（黑名单）───
        System.out.println("\n--- 模式 2: 跳过第 3,5,7 行 ---");
        PrintSelector s2 = new PrintSelector().skipLines(3, 5, 7);
        wp.print(doc, s2);

        // ──── 模式 3：范围内跳过一段连续行 ────
        System.out.println("\n--- 模式 3: 1~20 行中跳过 5~10 行 ---");
        PrintSelector s3 = new PrintSelector()
                .lineRange(1, 20)
                .skipRange(5, 6);   // 跳过第 5~10 行
        wp.print(doc, s3);

        // ──── 模式 4：跳过空行 ────
        System.out.println("\n--- 模式 4: 跳过空行 ---");
        PrintSelector s4 = new PrintSelector().skipEmptyLines(true);
        wp.print(doc, s4);

        // ──── 模式 5：跳过含标记的行 ────
        System.out.println("\n--- 模式 5: 跳过含\"名词\"的行 ---");
        PrintSelector s5 = new PrintSelector().skipByMarker("名词");
        wp.print(doc, s5);

        // ──── 模式 6：组合：跳过前 2 行 + 空行 ────
        System.out.println("\n--- 模式 6: 跳过前 2 行 + 跳过空行 ---");
        PrintSelector s6 = new PrintSelector().skipFirst(2).skipEmptyLines(true);
        wp.print(doc, s6);

        // ──── 模式 7：白名单反向 — 跳过所有不在白名单中的行 ────
        System.out.println("\n--- 模式 7: 只打印第 1,3,5 行 (其余全跳过) ---");
        PrintSelector s7 = new PrintSelector().selectLines(1, 3, 5);
        wp.print(doc, s7);

        System.out.println("\n  全部 7 种跳过模式演示完成!");
    }

    /**
     * 生成打印预览 HTML 文件 — 不需要打印机连接，纯粹本地生成。
     *
     * <p>预览 HTML 包含：
     * <ul>
     *   <li>A4 纸尺寸页面 (210×297mm) 分页显示</li>
     *   <li>每行标注全局行号 + 格式标签 (B/I/U/字号/表格/缩进/对齐/间距)</li>
     *   <li>鼠标悬停高亮</li>
     *   <li>浏览器打开即可预览，支持打印</li>
     * </ul>
     *
     * @param filePath   Word 文件路径
     * @param outputPath HTML 输出路径 (如 E:\tmp\preview.html)
     */
    public static void generatePreviewFile(String filePath, String outputPath)
            throws IOException {
        System.out.println("==========================================");
        System.out.println("  生成打印预览");
        System.out.println("  文件: " + filePath);
        System.out.println("  输出: " + outputPath);
        System.out.println("==========================================");

        WordDocument doc = WordDocument.load(filePath);
        System.out.println("  文档已加载: " + doc.getParagraphCount() + " 段落, "
                + doc.getTableCount() + " 个表格");

        // 创建预览记录器
        PrintPreview preview = new PrintPreview(filePath);
        WordPrinter wordPrinter = new WordPrinter(null, false); // verbose=false
        wordPrinter.generatePreview(doc, null, preview);  // 全部内容
        // 或带选择器: wordPrinter.generatePreview(doc, selector, preview);

        // 写出 HTML
        preview.writeHtml(outputPath);
        System.out.println("\n  预览文件已生成: " + preview.getHtmlPath());
        System.out.println("  共 " + preview.lineCount() + " 行");
        System.out.println("  请用浏览器打开查看");
    }

    /**
     * 混合打印 — 使用 PrintJob 一次性描述多段不同的打印需求。
     *
     * <p>典型场景：一次打印中既有整页，又有某页的几行，还有某页表格的几行。
     * 所有选择按页级语义描述（页内行号/表格内行号），PrintJob 内部自动转为全局索引。
     *
     * <p>完整版用法示例（当文档足够大时）：
     * <pre>
     *   PrintJob job = PrintJob.builder()
     *       .select(1).all("封面页")                          // 第 1 页全部
     *       .and()
     *       .select(2).lines("第 2 页 第 3,5,7 行", 3, 5, 7)  // 第 2 页的几行
     *       .and()
     *       .select(3).table(0).rows("第 3 页表格 #0 第 1~3 行", 1, 2, 3).noBorder()
     *       .build();
     *   job.execute(printer, doc);
     * </pre>
     */
    public static void printMixedJob(PrinterService printer, String filePath)
            throws IOException {
        System.out.println("==========================================");
        System.out.println("  混合打印 (PrintJob)");
        System.out.println("  文件: " + filePath);
        System.out.println("==========================================");

        PrintConfig config = PrintConfig.defaultConfig();
        printer.applyConfig(config);

        WordDocument doc = WordDocument.load(filePath);
        int totalPages = doc.getTotalPages();
        int tableCount = doc.getTableCount();
        System.out.println("  文档: " + doc.getParagraphCount() + " 段, "
                + totalPages + " 页, " + tableCount + " 个表格");

        // 混合选择：页1正文 + 同页表格第1行（不带边框）
        PrintJob job = PrintJob.builder()
                .select(1).all("第 1 页 全部正文内容")
                .build();

        job.execute(printer, doc);
    }

    /**
     * 打印 Word 文档中指定页的指定行。
     *
     * <p>页号按默认 66 行/页计算，传递到底层 PrintSelector 进行行号过滤。
     *
     * @param printer  已打开的 PrinterService
     * @param filePath Word 文件路径
     * @param page     页号 (1-based)
     * @param lineOnPage 该页内的行号 (1-based，相对于该页起始)
     */
    public static void printLineOfPage(PrinterService printer, String filePath,
                                        int page, int lineOnPage) throws IOException {
        System.out.println("==========================================");
        System.out.println("  打印指定行");
        System.out.println("  文件: " + filePath);
        System.out.println("  目标: 第 " + page + " 页, 第 " + lineOnPage + " 行");
        System.out.println("==========================================");

        // 1. 配置
        PrintConfig config = PrintConfig.defaultConfig();
        printer.applyConfig(config);

        // 2. 加载文档
        WordDocument doc = WordDocument.load(filePath);
        System.out.println("  文档已加载: " + doc.getParagraphCount() + " 段落");

        // 3. 计算目标行的全局行号
        int globalLine = (page - 1) * doc.getLinesPerPage() + lineOnPage;
        System.out.println("  全局行号: " + globalLine);

        // 4. 通过 PrintSelector 精确选中该行
        PrintSelector selector = new PrintSelector()
                .lineRange(globalLine, globalLine);

        // 5. 打印
        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.print(doc, selector);

        System.out.println("\n  打印完成!");
    }

    /**
     * 打印 Word 文档的指定页，跳过若干行和空行。
     *
     * <p>PrintSelector 支持的跳过方式：
     * <ul>
     *   <li>{@code .skipLines(n1, n2, ...)}    — 跳过指定全局行号</li>
     *   <li>{@code .skipEmptyLines(true)}      — 跳过空行</li>
     *   <li>{@code .skipByMarker("###SKIP")}   — 跳过含标记文本的行</li>
     * </ul>
     * 以上三种可任意链式组合。
     *
     * @param printer    已打开的 PrinterService
     * @param filePath   Word 文件路径
     * @param page       页号 (1-based)
     * @param skipLines  该页内要跳过的行号 (相对于页起始，1-based)
     */
    public static void printPageSkipLines(PrinterService printer, String filePath,
                                           int page, int... skipLines) throws IOException {
        System.out.println("==========================================");
        System.out.println("  打印单页并跳过指定行");
        System.out.println("  文件: " + filePath);
        System.out.println("  页码: " + page);
        System.out.println("==========================================");

        PrintConfig config = PrintConfig.defaultConfig();
        printer.applyConfig(config);

        WordDocument doc = WordDocument.load(filePath);

        // 计算该页的起止行号 (全局)
        int pageStart = (page - 1) * doc.getLinesPerPage() + 1;
        int pageEnd   = Math.min(page * doc.getLinesPerPage(), doc.getParagraphCount());
        System.out.println("  页范围: 全局行 " + pageStart + " ~ " + pageEnd
                + " (共 " + (pageEnd - pageStart + 1) + " 行)");

        // 将页内行号转为全局行号
        StringBuilder skipInfo = new StringBuilder();
        int[] globalSkips = new int[skipLines.length];
        for (int i = 0; i < skipLines.length; i++) {
            globalSkips[i] = pageStart + skipLines[i] - 1;
            if (i > 0) skipInfo.append(", ");
            skipInfo.append("页内第").append(skipLines[i]).append("行→全局行").append(globalSkips[i]);
        }
        System.out.println("  跳过: " + skipInfo);

        // 构建选择器：打印整页，但跳过指定行 + 空行
        PrintSelector selector = new PrintSelector()
                .lineRange(pageStart, pageEnd)
                .skipLines(globalSkips)
                .skipEmptyLines(true);

        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.print(doc, selector);

        System.out.println("\n  打印完成!");
    }

    /**
     * 打印 Word 文档的指定页（整页内容）。
     *
     * @param printer  已打开的 PrinterService
     * @param filePath Word 文件路径
     * @param page     页号 (1-based)
     */
    public static void printPage(PrinterService printer, String filePath,
                                  int page) throws IOException {
        System.out.println("==========================================");
        System.out.println("  打印单页");
        System.out.println("  文件: " + filePath);
        System.out.println("  目标: 第 " + page + " 页");
        System.out.println("==========================================");

        PrintConfig config = PrintConfig.defaultConfig();
        printer.applyConfig(config);

        WordDocument doc = WordDocument.load(filePath);

        int startLine = (page - 1) * doc.getLinesPerPage() + 1;
        int endLine   = Math.min(page * doc.getLinesPerPage(), doc.getParagraphCount());

        System.out.println("  段落范围: " + startLine + " ~ " + endLine);

        PrintSelector selector = new PrintSelector()
                .lineRange(startLine, endLine);

        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.print(doc, selector);

        System.out.println("\n  打印完成!");
    }

    /**
     * 打印 Word 文档的指定行（全局行号）。
     *
     * @param printer  已打开的 PrinterService
     * @param filePath Word 文件路径
     * @param line     全局行号 (1-based)
     */
    public static void printLine(PrinterService printer, String filePath,
                                  int line) throws IOException {
        System.out.println("==========================================");
        System.out.println("  打印指定行");
        System.out.println("  文件: " + filePath);
        System.out.println("  行号: " + line);
        System.out.println("==========================================");

        PrintConfig config = PrintConfig.defaultConfig();
        printer.applyConfig(config);

        WordDocument doc = WordDocument.load(filePath);

        PrintSelector selector = new PrintSelector()
                .lineRange(line, line);

        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.print(doc, selector);

        System.out.println("\n  打印完成!");
    }

    /**
     * 打印 Word 文档中指定表格的指定行数据。
     *
     * <p>使用 PrintSelector 的链式 API 精确过滤：
     * <ol>
     *   <li>{@code .skipNonTable(true)} — 跳过所有非表格段落</li>
     *   <li>{@code .tableRow(tableIndex, rowInTable)} — 精确匹配表格 + 行号</li>
     * </ol>
     *
     * <p>调用方式：
     * <pre>
     *   printTableRow(printer, "test.docx", 0, 1);  // 第 1 个表格的第 1 行
     *   printTableRow(printer, "test.docx", 1, 3);  // 第 2 个表格的第 3 行
     * </pre>
     *
     * @param printer    已打开的 PrinterService
     * @param filePath   Word 文件路径
     * @param tableIndex 表格序号 (0-based，Word 文档中第几个表格)
     * @param rowInTable 表格内行号 (1-based，含表头)
     */
    public static void printTableRow(PrinterService printer, String filePath,
                                      int tableIndex, int rowInTable) throws IOException {
        System.out.println("==========================================");
        System.out.println("  打印表格指定行");
        System.out.println("  文件: " + filePath);
        System.out.println("  表格序号: " + tableIndex + " (第 " + (tableIndex + 1) + " 个表格)");
        System.out.println("  行号: " + rowInTable);
        System.out.println("==========================================");

        // 1. 配置
        PrintConfig config = PrintConfig.defaultConfig();
        printer.applyConfig(config);

        // 2. 加载文档
        WordDocument doc = WordDocument.load(filePath);
        System.out.println("  文档已加载: " + doc.getParagraphCount() + " 段落");

        // 3. 预览表格信息
        int tableCount = doc.getTableCount();
        System.out.println("  文档中共 " + tableCount + " 个表格");

        if (tableIndex >= tableCount) {
            System.out.println("  错误: 表格序号 " + tableIndex + " 超出范围 (0~" + (tableCount - 1) + ")");
            return;
        }

        List<WordDocument.WordParagraph> tableRows = doc.getTableParagraphs(tableIndex);
        System.out.println("  表格 #" + tableIndex + " 共 " + tableRows.size() + " 行:");

        // 打印表格内容预览
        for (WordDocument.WordParagraph row : tableRows) {
            String text = row.getText();
            System.out.println("    第 " + row.getRowInTable() + " 行: "
                    + (text.length() > 70 ? text.substring(0, 67) + "..." : text));
        }

        // 4. 检查目标行是否存在
        WordDocument.WordParagraph targetRow = doc.getTableRow(tableIndex, rowInTable);
        if (targetRow == null) {
            System.out.println("  错误: 表格 #" + tableIndex + " 中不存在第 " + rowInTable + " 行");
            return;
        }

        // 5. 构建选择器 — 只打印目标表格行，去掉边框
        PrintSelector selector = new PrintSelector()
                .tableRow(tableIndex, rowInTable)   // 精确匹配：第几个表格的第几行
                .tableBorder(false);                 // 去掉 " | " 分隔符

        // 6. 打印
        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.print(doc, selector);

        System.out.println("\n  打印完成!");
        System.out.println("  已打印: 表格 #" + tableIndex + " 第 " + rowInTable + " 行");
    }

    /**
     * 打印 Word 文档中不连续的多行（白名单模式）。
     *
     * <p>内部使用 {@code PrintSelector.selectLines()} 白名单机制，
     * 精确选中指定行号，未列出的行全部跳过。
     *
     * <p>调用示例：
     * <pre>
     *   printSelectedLines(printer, "test.docx", 1, 3, 5, 7);  // 只打第 1,3,5,7 行
     *   printSelectedLines(printer, "test.docx", 2, 4);        // 只打第 2,4 行
     * </pre>
     */
    public static void printSelectedLines(PrinterService printer, String filePath,
                                           int... lineNumbers) throws IOException {
        System.out.println("==========================================");
        System.out.println("  打印指定多行（不连续）");
        System.out.println("  文件: " + filePath);
        System.out.print("  行号: ");
        for (int i = 0; i < lineNumbers.length; i++) {
            if (i > 0) System.out.print(", ");
            System.out.print(lineNumbers[i]);
        }
        System.out.println("\n==========================================");

        PrintConfig config = PrintConfig.defaultConfig();
        printer.applyConfig(config);

        WordDocument doc = WordDocument.load(filePath);
        System.out.println("  文档已加载: " + doc.getParagraphCount() + " 段落");

        // 使用白名单
        PrintSelector selector = new PrintSelector()
                .selectLines(lineNumbers);

        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.print(doc, selector);

        System.out.println("\n  打印完成!");
        System.out.println("  已打印: " + lineNumbers.length + " 行 (行号: " + join(lineNumbers) + ")");
    }

    /**
     * 打印 Word 文档中不连续的多行（黑名单模式）。
     *
     * <p>内部使用 {@code lineRange() + skipLines()} 组合：
     * 先划定一个连续范围，再从其中排除指定行。
     *
     * <p>适用场景：范围中大部分行要打，只排除少数几行。
     */
    public static void printRangeExcluding(PrinterService printer, String filePath,
                                            int from, int to, int... exclude) throws IOException {
        System.out.println("==========================================");
        System.out.println("  打印范围并排除指定行");
        System.out.println("  文件: " + filePath);
        System.out.println("  范围: " + from + " ~ " + to);
        System.out.print("  排除: ");
        for (int i = 0; i < exclude.length; i++) {
            if (i > 0) System.out.print(", ");
            System.out.print(exclude[i]);
        }
        System.out.println("\n==========================================");

        PrintConfig config = PrintConfig.defaultConfig();
        printer.applyConfig(config);

        WordDocument doc = WordDocument.load(filePath);

        PrintSelector selector = new PrintSelector()
                .lineRange(from, to)
                .skipLines(exclude)
                .skipEmptyLines(true);

        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.print(doc, selector);

        int expected = (to - from + 1) - exclude.length;
        System.out.println("\n  打印完成! 预计 " + expected + " 行");
    }

    /** 简单的 int 数组拼接，避免 Arrays.toString() 的方括号 */
    private static String join(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /**
     * 打印 Word 文档中指定表格的多行数据。
     *
     * <p>PrintSelector 的表格多行选择方式：
     * <ul>
     *   <li>{@code .tableRows(0, 1, 5)} — 表格 #0 的第 1~5 行（范围）</li>
     *   <li>{@code .tableRows(0, 2, 2)} — 表格 #0 的第 2 行（等同于 tableRow(0,2)）</li>
     *   <li>{@code .skipNonTable(true)} — 只打印表格行，跳过正文</li>
     *   <li>{@code .tableBorder(false)} — 去掉单元格分隔符</li>
     * </ul>
     *
     * @param printer    已打开的 PrinterService
     * @param filePath   Word 文件路径
     * @param tableIndex 表格序号 (0-based)
     * @param fromRow    起始行号 (1-based, 含)
     * @param toRow      结束行号 (1-based, 含)
     * @param border     是否保留表格边框分隔符
     */
    public static void printTableRows(PrinterService printer, String filePath,
                                       int tableIndex, int fromRow, int toRow,
                                       boolean border) throws IOException {
        System.out.println("==========================================");
        System.out.println("  打印表格多行");
        System.out.println("  文件: " + filePath);
        System.out.println("  表格: #" + tableIndex + " (第 " + (tableIndex + 1) + " 个表格)");
        System.out.println("  行范围: " + fromRow + " ~ " + toRow);
        System.out.println("  边框: " + (border ? "保留" : "去掉"));
        System.out.println("==========================================");

        PrintConfig config = PrintConfig.defaultConfig();
        printer.applyConfig(config);

        WordDocument doc = WordDocument.load(filePath);
        System.out.println("  文档已加载: " + doc.getParagraphCount() + " 段落, "
                + doc.getTableCount() + " 个表格");

        // 校验
        if (tableIndex >= doc.getTableCount()) {
            System.out.println("  错误: 表格序号 " + tableIndex + " 超出范围");
            return;
        }

        // 预览表格
        List<WordDocument.WordParagraph> allRows = doc.getTableParagraphs(tableIndex);
        System.out.println("  表格 #" + tableIndex + " 共 " + allRows.size() + " 行:");
        for (WordDocument.WordParagraph row : allRows) {
            int rn = row.getRowInTable();
            boolean willPrint = (rn >= fromRow && rn <= toRow);
            String marker = willPrint ? "  ← 打印" : "";
            String text = row.getText();
            System.out.println("    第 " + rn + " 行: "
                    + (text.length() > 60 ? text.substring(0, 57) + "..." : text)
                    + marker);
        }

        // 构建选择器
        PrintSelector selector = new PrintSelector()
                .tableRows(tableIndex, fromRow, toRow)
                .tableBorder(border);

        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.print(doc, selector);

        int count = toRow - fromRow + 1;
        System.out.println("\n  打印完成!");
        System.out.println("  已打印: 表格 #" + tableIndex + " 第 " + fromRow + "~" + toRow + " 行 (共 " + count + " 行)");
    }

    /**
     * A5 横版 2-up 拼合打印：2 页 A5 横向内容 → 1 张 A4 纸。
     *
     * @param printer   已打开的 PrinterService
     * @param filePath  Word 文件路径
     * @param pageRange 页码范围 (如 "1-2", "3-4"), null 表示打印全部
     */
    public static void printA5TwoUp(PrinterService printer, String filePath,
                                     String pageRange) throws IOException {
        System.out.println("==========================================");
        System.out.println("  A5 横版 2-up 拼合打印");
        System.out.println("  文件: " + filePath);
        System.out.println("  布局: 2 页 A5 横版 → 1 张 A4 纸");
        System.out.println("  页码范围: " + (pageRange != null ? pageRange : "全部"));
        System.out.println("==========================================");

        PrintConfig config = PrintConfig.a4TwoA5Landscape();
        System.out.println("  配置: " + config);

        WordDocument doc = WordDocument.load(filePath);
        System.out.println("  文档已加载: " + doc.getParagraphCount() + " 段落, 约 " + doc.getTotalPages() + " 页");

        PrintSelector selector = new PrintSelector();
        if (pageRange != null && !pageRange.trim().isEmpty()) {
            selector.pageRange(pageRange);
        }

        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.printWithConfig(doc, selector, config);

        System.out.println("\n  打印完成!");
    }

    /**
     * 打印全部 Word 文档内容（普通模式，不做多页拼合）。
     *
     * @param printer  已打开的 PrinterService
     * @param filePath Word 文件路径
     */
    public static void printAll(PrinterService printer, String filePath) throws IOException {
        System.out.println("==========================================");
        System.out.println("  打印全部文档");
        System.out.println("  文件: " + filePath);
        System.out.println("==========================================");

        PrintConfig config = PrintConfig.defaultConfig();
        printer.applyConfig(config);

        WordDocument doc = WordDocument.load(filePath);
        System.out.println("  文档已加载: " + doc.getParagraphCount() + " 段落");

        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.print(doc);

        System.out.println("\n  打印完成!");
    }
}
