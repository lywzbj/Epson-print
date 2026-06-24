package org.example;

import org.example.config.PrintConfig;
import org.example.connection.JavaxPrintConnection;
import org.example.connection.PrinterConnection;
import org.example.service.PrinterService;
import org.example.word.PrintSelector;
import org.example.word.WordDocument;
import org.example.word.WordPrinter;

import java.io.IOException;

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
        // ---- 创建连接 ----
        PrinterConnection connection = new JavaxPrintConnection(PRINTER_NAME);
        PrinterService printer = new PrinterService(connection);

        try {
            printer.open();

            // 打印文档第 1 页的第 5 行
            printLineOfPage(printer, DEFAULT_FILE, 1, 5);

        } catch (IOException e) {
            System.err.println("打印出错: " + e.getMessage());
            System.err.println("提示：如果打印机未连接，请检查网络或 USB 连接。");
        } finally {
            try {
                printer.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ================================================================
    // 打印功能（静态方法）
    // ================================================================

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
