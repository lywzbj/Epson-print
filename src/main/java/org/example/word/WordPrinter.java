package org.example.word;

import org.example.command.EscpCommand;
import org.example.config.PrintConfig;
import org.example.service.PrinterService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Word 文档打印桥接器。
 * 将 WordDocument 的段落内容按 PrintSelector 规则选择，
 * 并将格式映射为 ESC/P-K 命令，通过 PrinterService 发送到打印机。
 *
 * 使用方式：
 * <pre>
 *   WordDocument doc = WordDocument.load("report.docx");
 *   PrintSelector selector = new PrintSelector()
 *       .pageRange("1-3")
 *       .skipEmptyLines(true);
 *
 *   WordPrinter wordPrinter = new WordPrinter(printerService);
 *   wordPrinter.print(doc, selector);
 * </pre>
 */
public class WordPrinter {

    private final PrinterService printer;
    private boolean verbose = true;

    /** 当前打印状态追踪，用于减少冗余命令 */
    private boolean currentBold = false;
    private boolean currentItalic = false;
    private boolean currentUnderline = false;
    private String currentFont = null;
    private int currentFontSize = 0;

    public WordPrinter(PrinterService printer) {
        this.printer = printer;
    }

    public WordPrinter(PrinterService printer, boolean verbose) {
        this.printer = printer;
        this.verbose = verbose;
    }

    // ======== 主打印方法 ========

    /**
     * 打印整个 Word 文档（无过滤）。
     */
    public void print(WordDocument doc) throws IOException {
        print(doc, null);
    }

    /**
     * 按选择器规则打印 Word 文档。
     * @param doc      已加载的 Word 文档
     * @param selector 选择规则（为 null 时打印全部）
     */
    public void print(WordDocument doc, PrintSelector selector) throws IOException {
        long startTime = System.currentTimeMillis();

        if (selector != null) {
            selector.linesPerPage(doc.getLinesPerPage());
            log("文档: " + doc.getFilePath());
            log("规则: " + selector);
        } else {
            log("文档: " + doc.getFilePath() + " (打印全部)");
        }

        // 初始化打印机
        printer.init();
        printer.setLineSpacing1_6();
        resetFormatState();

        // 遍历段落
        List<WordDocument.WordParagraph> paragraphs = doc.getAllParagraphs();
        int totalLines = paragraphs.size();
        int printedLines = 0;
        int skippedLines = 0;

        for (int i = 0; i < totalLines; i++) {
            WordDocument.WordParagraph wp = paragraphs.get(i);
            int lineNumber = i + 1;  // 1-based

            // 选择器过滤
            if (selector != null && !selector.shouldPrint(lineNumber, wp.getText())) {
                skippedLines++;
                continue;
            }

            // 应用格式并打印
            printParagraph(wp);
            printedLines++;
        }

        // 收尾
        printer.feedLines(2);
        log("------------------------------");
        log(String.format("完成: %d 行已打印, %d 行已跳过, 共 %d 行",
                printedLines, skippedLines, totalLines));
        log(String.format("耗时: %dms", System.currentTimeMillis() - startTime));
    }

    // ======== 段落打印 ========

    /**
     * 打印单个段落，自动应用 Word 格式 → ESC/P-K 命令。
     */
    private void printParagraph(WordDocument.WordParagraph wp) throws IOException {
        String text = wp.getText();

        // 空段落：只输出换行
        if (text.trim().isEmpty()) {
            printer.feedLines(1);
            return;
        }

        // 应用字体样式
        applyFormat(wp);

        // 选择打印模式
        if (wp.shouldUseChineseMode()) {
            // 汉字模式
            printer.enterChineseMode();
            if (wp.getFontName() != null) {
                applyChineseFont(wp.getFontName());
            }
            printer.sendRaw(EscpCommand.encodeChinese(text));
            printer.send(EscpCommand.carriageReturn());
            printer.send(EscpCommand.lineFeed());
            printer.exitChineseMode();
        } else {
            // 英文/ASCII 模式
            printer.printText(text);
        }

        // 恢复默认格式
        resetFormat(wp);
    }

    // ======== 格式映射 ========

    /**
     * 将 Word 段落格式应用到打印机。
     * 使用状态追踪，避免重复发送相同命令。
     */
    private void applyFormat(WordDocument.WordParagraph wp) throws IOException {
        // 加粗
        if (wp.isBold() != currentBold) {
            printer.bold(wp.isBold());
            currentBold = wp.isBold();
        }
        // 斜体
        if (wp.isItalic() != currentItalic) {
            printer.italic(wp.isItalic());
            currentItalic = wp.isItalic();
        }
        // 下划线
        boolean needUnderline = wp.isUnderline();
        if (needUnderline != currentUnderline) {
            if (needUnderline) {
                printer.underline(1);
            } else {
                printer.underlineOff();
            }
            currentUnderline = needUnderline;
        }

        // 字号映射（Word 字号 → 打印机控制）
        int fontSizePt = wp.getFontSizePt();
        if (fontSizePt > 0 && fontSizePt != currentFontSize) {
            applyFontSize(fontSizePt);
            currentFontSize = fontSizePt;
        }
    }

    /**
     * 字号映射到打印机命令。
     *
     * Word 字号参考 (pt):
     *   五号=10.5, 小四=12, 四号=14, 小三=15, 三号=16,
     *   小二=18, 二号=22, 小一=24, 一号=26, 初号=42
     *
     * 打印机映射策略:
     *   ≤10pt  → 15CPI (压缩)
     *   10-12pt → 12CPI (正常)
     *   ≥14pt  → 10CPI + 可能倍高
     *   ≥24pt  → 10CPI + 倍宽倍高
     */
    private void applyFontSize(int fontSizePt) throws IOException {
        if (fontSizePt <= 10) {
            printer.select15CPI();
        } else if (fontSizePt <= 12) {
            printer.select12CPI();
        } else {
            printer.select10CPI();
        }

        // 大字：倍高/倍宽
        if (fontSizePt >= 24) {
            printer.doubleWidth(true);
            printer.doubleHeight(true);
        } else if (fontSizePt >= 16) {
            printer.doubleHeight(true);
            printer.doubleWidth(false);
        } else if (fontSizePt >= 14) {
            printer.doubleWidth(true);
            printer.doubleHeight(false);
        } else {
            printer.doubleWidth(false);
            printer.doubleHeight(false);
        }
    }

    /**
     * 汉字字体映射。
     * FontName 含 "宋体"/"SimSun"/"宋" → FS K 0
     * FontName 含 "黑体"/"SimHei"/"黑" → FS K 1
     */
    private void applyChineseFont(String fontName) throws IOException {
        if (fontName == null) return;

        String fn = fontName.toLowerCase();
        if (fn.contains("黑体") || fn.contains("simhei") || fn.contains("hei") || fn.equals("黑")) {
            printer.selectChineseFont(1);  // 黑体
        } else if (fn.contains("宋体") || fn.contains("simsun") || fn.contains("song") || fn.equals("宋")) {
            printer.selectChineseFont(0);  // 宋体
        } else if (fn.contains("楷") || fn.contains("kai")) {
            printer.selectChineseFont(0);  // 无独立楷体命令，用宋体
        } else {
            printer.selectChineseFont(0);  // 默认宋体
        }
        currentFont = fontName;
    }

    /**
     * 段落打印后恢复默认样式。
     * 仅在最后一段或格式变化时才重置，减少冗余命令。
     */
    private void resetFormat(WordDocument.WordParagraph wp) throws IOException {
        if (wp.isBold()) {
            printer.bold(false);
            currentBold = false;
        }
        if (wp.isItalic()) {
            printer.italic(false);
            currentItalic = false;
        }
        if (wp.isUnderline()) {
            printer.underlineOff();
            currentUnderline = false;
        }
        // 字号和倍宽倍高在 applyFontSize 中已按每段设置
    }

    /** 重置所有格式追踪状态 */
    private void resetFormatState() {
        currentBold = false;
        currentItalic = false;
        currentUnderline = false;
        currentFont = null;
        currentFontSize = 0;
    }

    // ================================================================
    // 基于 PrintConfig 的多页拼合打印 (N-up)
    // ================================================================

    /**
     * 使用 PrintConfig 打印 Word 文档，自动处理 N-up 拼合布局。
     *
     * <p>典型场景 — 2 页 A5 横版打到 1 张 A4 纸：
     * <pre>
     *   PrintConfig config = PrintConfig.a4TwoA5Landscape();
     *   WordDocument doc = WordDocument.load("test.docx");
     *   PrintSelector selector = new PrintSelector().pageRange("1-2");
     *
     *   WordPrinter wp = new WordPrinter(printer);
     *   wp.printWithConfig(doc, selector, config);
     * </pre>
     *
     * <p>工作原理：
     * <ol>
     *   <li>应用 PrintConfig 设置（CPI、行间距、边距等）</li>
     *   <li>由 selector 筛选要打印的段落</li>
     *   <li>按 effectivePageLines() 将段落切成逻辑页</li>
     *   <li>每 pagesPerSheet 个逻辑页组成一张物理纸</li>
     *   <li>自动调用 beginPhysicalSheet / beginLogicalPage / endLogicalPage</li>
     * </ol>
     *
     * @param doc      已加载的 Word 文档
     * @param selector 行选择规则（为 null 时打印全部，页码过滤按 config 页长重新计算）
     * @param config   打印配置（含页面布局、纸张、边距等）
     */
    public void printWithConfig(WordDocument doc, PrintSelector selector, PrintConfig config) throws IOException {
        long startTime = System.currentTimeMillis();

        // 1. 获取有效行/页参数
        int logicalPageLines = config.effectivePageLines();
        int pagesPerSheet = config.getPageLayout().pagesPerSheet();
        boolean isMultiUp = config.getPageLayout().isMultiUp();

        // 更新 selector 的每页行数（用于页码范围计算）
        if (selector != null) {
            selector.linesPerPage(logicalPageLines);
            log("文档: " + doc.getFilePath());
            log("布局: " + config.getPageLayout());
            log("每逻辑页: " + logicalPageLines + " 行 | 每物理纸: " + pagesPerSheet + " 逻辑页");
            log("规则: " + selector);
        } else {
            log("文档: " + doc.getFilePath() + " (打印全部)");
            log("布局: " + config.getPageLayout());
        }

        // 2. 应用配置
        printer.applyConfig(config);
        resetFormatState();

        // 3. 收集需打印的段落
        List<WordDocument.WordParagraph> paragraphs = doc.getAllParagraphs();
        List<WordDocument.WordParagraph> selected = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            int lineNumber = i + 1;
            WordDocument.WordParagraph wp = paragraphs.get(i);
            if (selector == null || selector.shouldPrint(lineNumber, wp.getText())) {
                selected.add(wp);
            }
        }

        if (selected.isEmpty()) {
            log("没有可打印的内容（选择器过滤后为空）");
            return;
        }

        // 4. 按逻辑页行数切分
        int totalLogicalPages = (int) Math.ceil((double) selected.size() / logicalPageLines);
        log(String.format("共 %d 逻辑页 / %d 张物理纸",
                totalLogicalPages,
                (int) Math.ceil((double) totalLogicalPages / pagesPerSheet)));

        int physicalSheetCount = 0;

        for (int logicalPageIdx = 0; logicalPageIdx < totalLogicalPages; logicalPageIdx++) {
            int posInSheet = logicalPageIdx % pagesPerSheet;

            // 开始一张新物理纸
            if (posInSheet == 0) {
                if (logicalPageIdx > 0) {
                    // 上一张物理纸收尾
                    endPhysicalSheetForConfig(config, pagesPerSheet - 1, true);
                    printer.feedLines(1);
                }
                physicalSheetCount++;
                log(String.format("--- 物理纸 #%d ---", physicalSheetCount));
                printer.beginPhysicalSheet(config);
            }

            // 开始逻辑页
            printer.beginLogicalPage(config, posInSheet);
            log(String.format("  逻辑页 %d/%d (纸上位置 %d/%d)",
                    logicalPageIdx + 1, totalLogicalPages,
                    posInSheet + 1, pagesPerSheet));

            // 打印该逻辑页的段落
            int startLine = logicalPageIdx * logicalPageLines;
            int endLine = Math.min(startLine + logicalPageLines, selected.size());

            for (int j = startLine; j < endLine; j++) {
                WordDocument.WordParagraph wp = selected.get(j);
                printParagraph(wp);
            }

            // 逻辑页内容不够填满时，补空行
            int printed = endLine - startLine;
            if (printed < logicalPageLines) {
                int fill = logicalPageLines - printed;
                for (int f = 0; f < Math.min(fill, 3); f++) {
                    printer.feedLines(1);
                }
            }

            // 结束逻辑页
            boolean isLastOnSheet = (posInSheet == pagesPerSheet - 1);
            boolean isLastOverall = (logicalPageIdx == totalLogicalPages - 1);

            if (isLastOverall) {
                endPhysicalSheetForConfig(config, posInSheet, true);
            } else if (isLastOnSheet) {
                endPhysicalSheetForConfig(config, posInSheet, true);
            } else {
                printer.endLogicalPage(config, posInSheet, false);
            }
        }

        // 5. 收尾
        printer.feedLines(2);
        log("------------------------------");
        log(String.format("完成: %d 段落 → %d 逻辑页 → %d 张物理纸",
                selected.size(), totalLogicalPages, physicalSheetCount));
        log(String.format("耗时: %dms", System.currentTimeMillis() - startTime));
    }

    /**
     * 结束一张物理纸（含最后一个逻辑页的收尾）。
     */
    private void endPhysicalSheetForConfig(PrintConfig config, int lastPageIndex, boolean isLast) throws IOException {
        printer.endLogicalPage(config, lastPageIndex, isLast);
        printer.endPhysicalSheet();
    }

    /** 日志输出 */
    private void log(String msg) {
        if (verbose) {
            System.out.println("  " + msg);
        }
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
