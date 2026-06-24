package org.example.demo;

import org.example.config.PageLayout;
import org.example.config.PrintConfig;
import org.example.connection.FileStreamConnection;
import org.example.connection.PrinterConnection;
import org.example.connection.SocketConnection;
import org.example.service.PrinterService;
import org.example.word.PrintSelector;
import org.example.word.WordDocument;
import org.example.word.WordPrinter;

import java.io.IOException;

/**
 * 打印机功能演示 — 支持命令模式打印和 Word 文档打印。
 *
 * 用法：
 *   java ... PrinterDemo                                → 默认演示
 *   java ... PrinterDemo word report.docx                → 打印整个 Word（默认 javax.print）
 *   java ... PrinterDemo word report.docx --pages=1-3     → 指定页码范围
 *   java ... PrinterDemo word report.docx --lines=10-80 --skip-empty  → 行范围+跳过空行
 *   java ... PrinterDemo word report.docx --skip-marker='###SKIP'     → 跳过标记行
 */
public class PrinterDemo {

    private final PrinterService printer;

    public PrinterDemo(PrinterService printer) {
        this.printer = printer;
    }

    // ================================================================
    // 入口
    // ================================================================

    public static void main(String[] args) {
        // ---- Word 文档打印模式 ----
        if (args.length >= 2 && "word".equalsIgnoreCase(args[0])) {
            runWordPrint(args);
            return;
        }

        // ---- A5 横版 2-up 拼合打印演示 ----
        if (args.length >= 1 && "a5layout".equalsIgnoreCase(args[0])) {
            runA5LayoutDemo(args);
            return;
        }

        // ---- 默认演示模式 ----
        System.out.println("==========================================");
        System.out.println("  EPSON DLQ-3500K 打印机功能演示");
        System.out.println("==========================================");

        PrinterConnection connection = createConnection(args);
        PrinterService printer = new PrinterService(connection);
        PrinterDemo demo = new PrinterDemo(printer);

        try {
            printer.open();

            demo.demo_printerInit();
            demo.demo_englishText();
            demo.demo_chineseText();
            demo.demo_bitImage();
            demo.demo_pageControl();
            demo.demo_a5TwoUpOnA4();

            System.out.println("\n全部演示完成！");

        } catch (IOException e) {
            System.err.println("打印出错: " + e.getMessage());
            System.err.println("提示：如果打印机未连接，这是正常的——"
                    + "命令构建功能不受影响，请查看命令日志。");
        } finally {
            try {
                printer.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Word 文档打印模式。
     * 语法：word <file.docx> [--pages=P] [--lines=L1-L2] [--skip-empty] [--skip-marker=M] [--lpp=N] [--a5-2up]
     *
     * --a5-2up: 启用 A5 横版 2-up 拼合模式，将 2 页 Word 内容横向打印到 1 张 A4 纸。
     *            配合 --pages=P1-P2 指定源文档页码范围（默认打印全部，按逻辑页切分）。
     *            配合 --lines=L1-L2 按行范围选取后切分到逻辑页。
     */
    private static void runWordPrint(String[] args) {
        String filePath = args[1];
        PrintSelector selector = new PrintSelector();
        boolean a5TwoUpMode = false;

        // 解析参数
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if ("--a5-2up".equals(arg)) {
                a5TwoUpMode = true;
            } else if (arg.startsWith("--pages=")) {
                selector.pageRange(arg.substring("--pages=".length()));
            } else if (arg.startsWith("--lines=")) {
                String range = arg.substring("--lines=".length());
                if (range.contains("-")) {
                    String[] se = range.split("-");
                    int s = Integer.parseInt(se[0]);
                    int e = Integer.parseInt(se[1]);
                    selector.lineRange(s, e);
                }
            } else if ("--skip-empty".equals(arg)) {
                selector.skipEmptyLines(true);
            } else if (arg.startsWith("--skip-marker=")) {
                String marker = arg.substring("--skip-marker=".length());
                selector.skipByMarker(marker);
            } else if (arg.startsWith("--skip-line=")) {
                String[] nums = arg.substring("--skip-line=".length()).split(",");
                int[] skips = new int[nums.length];
                for (int j = 0; j < nums.length; j++) {
                    skips[j] = Integer.parseInt(nums[j]);
                }
                selector.skipLines(skips);
            } else if (arg.startsWith("--lpp=")) {
                int lpp = Integer.parseInt(arg.substring("--lpp=".length()));
                selector.linesPerPage(lpp);
            }
        }

        // 创建连接
        PrinterConnection connection = createConnection(args);
        PrinterService printer = new PrinterService(connection);

        try {
            printer.open();

            if (a5TwoUpMode) {
                // ---- A5 横版 2-up 拼合模式 ----
                System.out.println("\n=== A5 横版 2-up 拼合打印 ===");
                System.out.println("  源文件: " + filePath);

                PrintConfig config = PrintConfig.a4TwoA5Landscape();
                System.out.println("  配置: " + config);

                WordDocument doc = WordDocument.load(filePath);
                System.out.println("  文档: " + doc.getParagraphCount() + " 段落, 约 " + doc.getTotalPages() + " 页");
                System.out.println("  每逻辑页: " + config.effectivePageLines() + " 行 (A5 横版)");
                System.out.println("  每物理纸: " + config.getPageLayout().pagesPerSheet() + " 逻辑页 (A4)");

                WordPrinter wordPrinter = new WordPrinter(printer);
                wordPrinter.printWithConfig(doc, selector, config);

                System.out.println("=== A5 2-up 打印完成 ===");
            } else {
                // ---- 普通 Word 打印模式 ----
                PrinterDemo demo = new PrinterDemo(printer);
                demo.demo_wordDocument(filePath, selector);
            }
        } catch (IOException e) {
            System.err.println("打印出错: " + e.getMessage());
        } finally {
            try {
                printer.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 根据命令行参数或环境创建连接。
     */
    private static PrinterConnection createConnection(String[] args) {
        if (args.length >= 2 && "socket".equalsIgnoreCase(args[0])) {
            String host = args[1];
            int port = args.length >= 3 ? Integer.parseInt(args[2]) : 9100;
            System.out.println("[连接方式] Socket → " + host + ":" + port);
            return new SocketConnection(host, port);
        }

        if (args.length >= 2 && "file".equalsIgnoreCase(args[0])) {
            System.out.println("[连接方式] 文件流 → " + args[1]);
            return new FileStreamConnection(args[1]);
        }

        if (args.length >= 2 && "javax".equalsIgnoreCase(args[0])) {
            System.out.println("[连接方式] javax.print → " + args[1]);
            return new org.example.connection.JavaxPrintConnection(args[1]);
        }

        // 默认：javax.print 查找 DLQ-3500K
        String printerName = "DLQ-3500";
        System.out.println("[连接方式] javax.print 默认 → 查找 \"" + printerName + "\"");
        return new org.example.connection.JavaxPrintConnection(printerName);
    }

    // ================================================================
    // 演示 1：打印机初始化与基本控制
    // ================================================================

    public void demo_printerInit() throws IOException {
        System.out.println("\n--- 演示 1: 打印机初始化 ---");
        printer.init();
        System.out.println("  ✓ 发送初始化命令 (ESC @)");
        printer.setLineSpacing1_6();
        System.out.println("  ✓ 设置 1/6 英寸行间距");
        printer.feedLines(3);
        System.out.println("  ✓ 走纸 3 行");
    }

    // ================================================================
    // 演示 2：英文文本打印
    // ================================================================

    public void demo_englishText() throws IOException {
        System.out.println("\n--- 演示 2: 英文文本打印 ---");

        printer.bold(true);
        printer.doubleHeight(true);
        printer.printText("=== EPSON DLQ-3500K English Text Test ===");
        printer.bold(false);
        printer.doubleHeight(false);
        printer.feedLines(1);

        printer.select10CPI();
        printer.printText("[10 CPI] The quick brown fox jumps over the lazy dog.");

        printer.select12CPI();
        printer.printText("[12 CPI] The quick brown fox jumps over the lazy dog.");

        printer.select15CPI();
        printer.printText("[15 CPI] The quick brown fox jumps over the lazy dog.");

        printer.feedLines(1);
        printer.select10CPI();

        printer.bold(true);
        printer.printText("[Bold] This text is BOLD.");
        printer.bold(false);

        printer.italic(true);
        printer.printText("[Italic] This text is ITALIC.");
        printer.italic(false);

        printer.underline(1);
        printer.printText("[Underline-Single] This text has UNDERLINE.");
        printer.underlineOff();

        printer.underline(2);
        printer.printText("[Underline-Double] This text has DOUBLE UNDERLINE.");
        printer.underlineOff();

        printer.bold(true);
        printer.italic(true);
        printer.printText("[Bold+Italic] This text is BOLD and ITALIC.");
        printer.bold(false);
        printer.italic(false);

        printer.doubleWidth(true);
        printer.printText("[Double-Width] Wide text.");
        printer.doubleWidth(false);

        printer.doubleHeight(true);
        printer.printText("[Double-Height] Tall text.");
        printer.doubleHeight(false);

        printer.doubleStrike(true);
        printer.printText("[Double-Strike] Double strike text.");
        printer.doubleStrike(false);

        printer.feedLines(2);
    }

    // ================================================================
    // 演示 3：汉字打印
    // ================================================================

    public void demo_chineseText() throws IOException {
        System.out.println("\n--- 演示 3: 汉字打印 ---");

        printer.bold(true);
        printer.doubleHeight(true);
        printer.printChinese("=== EPSON DLQ-3500K 汉字打印测试 ===");
        printer.bold(false);
        printer.doubleHeight(false);
        printer.feedLines(1);

        printer.selectChineseFont(0);
        printer.printChinese("[宋体-正常] 春眠不觉晓，处处闻啼鸟。");

        printer.selectChineseFont(1);
        printer.printChinese("[黑体-正常] 夜来风雨声，花落知多少。");

        printer.enterChineseMode();
        printer.selectChineseFont(0);
        printer.setChineseCharSize(2, 1);
        printer.printChineseRaw("[宋体-倍宽] 白日依山尽");
        printer.exitChineseMode();

        printer.enterChineseMode();
        printer.selectChineseFont(0);
        printer.setChineseCharSize(1, 2);
        printer.printChineseRaw("[宋体-倍高] 黄河入海流");
        printer.exitChineseMode();

        printer.enterChineseMode();
        printer.selectChineseFont(0);
        printer.setChineseCharSize(2, 2);
        printer.printChineseRaw("[宋体-倍宽倍高] 欲穷千里目");
        printer.setChineseCharSize(1, 1);
        printer.exitChineseMode();

        printer.enterChineseMode();
        printer.selectChineseFont(1);
        printer.setChineseCharSize(2, 2);
        printer.printChineseRaw("[黑体-倍宽倍高] 更上一层楼");
        printer.setChineseCharSize(1, 1);
        printer.exitChineseMode();

        printer.feedLines(1);
        printer.printChineseVertical("[纵向打印] 汉字纵向输出效果测试");
        printer.feedLines(2);
    }

    // ================================================================
    // 演示 4：位图打印
    // ================================================================

    public void demo_bitImage() throws IOException {
        System.out.println("\n--- 演示 4: 位图打印 ---");

        printer.bold(true);
        printer.printText("=== Bit Image Test ===");
        printer.bold(false);
        printer.feedLines(1);

        int width = 100;
        byte[] stripePattern = new byte[width];
        for (int i = 0; i < width; i++) {
            stripePattern[i] = (byte) ((i % 16 < 8) ? 0xAA : 0x55);
        }

        printer.setLineSpacing216(24);
        printer.printText("8-pin single-density stripe pattern:");

        int nL = width % 256;
        int nH = width / 256;
        printer.printBitImage8Single(nL, nH, stripePattern);
        printer.feedLines(1);

        printer.printText("Square wave test pattern:");
        byte[] squarePattern = new byte[80];
        for (int i = 0; i < 80; i++) {
            squarePattern[i] = (byte) (i < 40 ? 0xFF : 0x00);
        }
        nL = 80 % 256;
        nH = 80 / 256;
        printer.printBitImage8Single(nL, nH, squarePattern);

        printer.feedLines(2);
        printer.setLineSpacing1_6();
    }

    /**
     * A5 横版 2-up 拼合打印演示入口。
     * 语法：a5layout [连接参数...]
     */
    private static void runA5LayoutDemo(String[] args) {
        System.out.println("==========================================");
        System.out.println("  A5 横版 2-up 拼合打印演示");
        System.out.println("  2 页 A5 横向 → 1 张 A4 纸");
        System.out.println("==========================================");

        // 跳过 "a5layout" 参数，将剩余参数传给 createConnection
        String[] connArgs = new String[args.length - 1];
        System.arraycopy(args, 1, connArgs, 0, connArgs.length);

        PrinterConnection connection = createConnection(connArgs);
        PrinterService printer = new PrinterService(connection);
        PrinterDemo demo = new PrinterDemo(printer);

        try {
            printer.open();
            demo.demo_a5TwoUpOnA4();
            System.out.println("\nA5 2-up 演示完成！");
        } catch (IOException e) {
            System.err.println("打印出错: " + e.getMessage());
        } finally {
            try {
                printer.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ================================================================
    // 演示 5：页格式控制
    // ================================================================

    public void demo_pageControl() throws IOException {
        System.out.println("\n--- 演示 5: 页格式控制 ---");

        printer.init();
        printer.feedLines(1);

        printer.setPageLength(30);
        printer.printText("[Page Length] Set to 30 lines.");

        printer.setBottomMargin(3);
        printer.printText("[Bottom Margin] Set to 3 lines (skip perforation).");

        printer.setLeftMargin(5);
        printer.printText("[Left Margin=5] This line should be indented by 5 columns.");

        printer.setRightMargin(70);
        printer.printText("[Right Margin=70] Right margin set to column 70.");

        printer.setAbsolutePosition(100);
        printer.printTextNoLF("[Abs Pos=100] Absolutely positioned text.");
        printer.feedLines(1);

        printer.send(org.example.command.EscpCommand.cancelBottomMargin());
        printer.printText("[Bottom Margin] Canceled (ESC O).");

        printer.printText("Feeding paper 1/6 inch...");
        printer.feedLines(1);

        printer.printText("--- End of Page Control Demo ---");
        printer.feedLines(2);
    }

    // ================================================================
    // 演示 5b：A5 横版 2-up 拼合打印 (A4 纸)
    // ================================================================

    /**
     * 演示：2 页 A5 横版内容拼合打印到 1 张 A4 纸上。
     *
     * <p>布局说明：
     * <ul>
     *   <li>物理纸：A4 竖版 (210 × 297 mm)</li>
     *   <li>逻辑页：A5 横版 (210 × 148 mm)，每页约 35 行</li>
     *   <li>排列：上下垂直排列，第 1 页在上半部，第 2 页在下半部</li>
     * </ul>
     *
     * <p>打印流程：
     * <ol>
     *   <li>构建 PrintConfig (a4TwoA5Landscape 预设)</li>
     *   <li>应用配置到打印机</li>
     *   <li>使用 printWithConfig() 自动处理布局</li>
     *   <li>或手动控制 beginPhysicalSheet / beginLogicalPage / endLogicalPage</li>
     * </ol>
     */
    public void demo_a5TwoUpOnA4() throws IOException {
        System.out.println("\n--- 演示 5b: A5 横版 2-up 拼合打印 (A4 纸) ---");

        // ----------------------------------------------------------
        // 方式 1：使用便捷方法 printWithConfig() — 推荐
        // ----------------------------------------------------------
        System.out.println("  [方式 1] 使用 printWithConfig() 自动布局");

        PrintConfig config = PrintConfig.a4TwoA5Landscape();
        System.out.println("  配置: " + config);

        // 应用配置
        printer.applyConfig(config);
        System.out.println("  ✓ 已应用打印配置");

        // 准备 2 页 A5 内容，自动拼合到 1 张 A4
        printer.printWithConfig(config,
                "A5 Page 1 (Top): This is the first logical page."
                        + " It occupies the upper half of the A4 sheet."
                        + " A5 landscape = 210 x 148 mm.",
                "A5 Page 2 (Bottom): This is the second logical page."
                        + " It occupies the lower half of the A4 sheet."
                        + " Both pages fit on one A4 sheet."
        );
        System.out.println("  ✓ 已发送 2 页 A5 内容 → 1 张 A4 纸");

        printer.feedLines(2);

        // ----------------------------------------------------------
        // 方式 2：手动控制物理页/逻辑页 — 更灵活
        // ----------------------------------------------------------
        System.out.println("  [方式 2] 手动控制 begin/end 物理页和逻辑页");

        PrintConfig config2 = PrintConfig.builder()
                .physicalPaper(org.example.config.PaperSize.A4)
                .pageLayout(PageLayout.a4_twoA5LandscapeVertical())
                .cpi(10)
                .bold(true)
                .leftMargin(2)
                .build();
        printer.applyConfig(config2);

        // 手动控制
        printer.beginPhysicalSheet(config2);

        // -- 第 1 逻辑页 (上半部分) --
        printer.beginLogicalPage(config2, 0);
        printer.printText("[Manual - Page 1/2] Top half of A4 sheet.");
        printer.printText("This demonstrates manual control over");
        printer.printText("the physical sheet and logical pages.");
        printer.printText("You can print anything here: text, Chinese, bit images...");
        printer.endLogicalPage(config2, 0, false);

        // -- 第 2 逻辑页 (下半部分) --
        printer.beginLogicalPage(config2, 1);
        printer.printText("[Manual - Page 2/2] Bottom half of A4 sheet.");
        printer.printText("The printer automatically positions");
        printer.printText("the second logical page below the first.");
        printer.printText("ESC/P-K commands handle the spacing.");
        printer.endLogicalPage(config2, 1, true);

        printer.endPhysicalSheet();
        System.out.println("  ✓ 手动模式完成");

        // ----------------------------------------------------------
        // 方式 3：汉字内容 + 2-up 布局
        // ----------------------------------------------------------
        printer.feedLines(1);
        System.out.println("  [方式 3] 汉字内容 + 2-up 布局");

        PrintConfig config3 = PrintConfig.builder()
                .physicalPaper(org.example.config.PaperSize.A4)
                .pageLayout(PageLayout.a4_twoA5LandscapeVertical())
                .cpi(10)
                .chineseFont(PrintConfig.ChineseFont.SONG_TI)
                .chineseCharSize(1, 1)
                .build();
        printer.applyConfig(config3);

        printer.beginPhysicalSheet(config3);

        // 第 1 逻辑页 — 汉字
        printer.beginLogicalPage(config3, 0);
        printer.bold(true);
        printer.printChinese("【第 1 页】A5 横版 — 汉字测试");
        printer.bold(false);
        printer.printChinese("春眠不觉晓，处处闻啼鸟。");
        printer.printChinese("夜来风雨声，花落知多少。");
        printer.printChinese("这是 A4 纸的上半部分 (A5 横版第 1 页)。");
        printer.endLogicalPage(config3, 0, false);

        // 第 2 逻辑页 — 汉字
        printer.beginLogicalPage(config3, 1);
        printer.bold(true);
        printer.printChinese("【第 2 页】A5 横版 — 汉字测试");
        printer.bold(false);
        printer.printChinese("白日依山尽，黄河入海流。");
        printer.printChinese("欲穷千里目，更上一层楼。");
        printer.printChinese("这是 A4 纸的下半部分 (A5 横版第 2 页)。");
        printer.endLogicalPage(config3, 1, true);

        printer.endPhysicalSheet();
        System.out.println("  ✓ 汉字 2-up 模式完成");

        printer.feedLines(2);
        System.out.println("  === A5 2-up 拼合打印全部演示完成 ===");
    }

    // ================================================================
    // 演示 6：Word 文档打印
    // ================================================================

    /**
     * 从 Word 文件解析内容并发起打印。
     * @param filePath .docx 文件路径
     * @param selector 选择规则
     */
    public void demo_wordDocument(String filePath, PrintSelector selector) throws IOException {
        System.out.println("\n--- 演示 6: Word 文档打印 ---");

        // 加载文档
        WordDocument doc = WordDocument.load(filePath);
        System.out.println("  文件: " + doc.getFilePath());
        System.out.println("  段落数: " + doc.getParagraphCount() + " | 估计页数: " + doc.getTotalPages());

        // 预览前 5 段
        System.out.println("  前 5 段预览:");
        for (int i = 0; i < Math.min(5, doc.getParagraphCount()); i++) {
            WordDocument.WordParagraph wp = doc.getAllParagraphs().get(i);
            System.out.println("    [" + (i + 1) + "] " + wp);
        }

        // 打印
        WordPrinter wordPrinter = new WordPrinter(printer);
        wordPrinter.print(doc, selector);
    }
}
