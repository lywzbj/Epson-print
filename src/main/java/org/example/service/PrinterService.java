package org.example.service;

import org.example.command.EscpCommand;
import org.example.config.PageLayout;
import org.example.config.PrintConfig;
import org.example.connection.PrinterConnection;

import java.io.IOException;

/**
 * 打印机高层服务 — 组合连接与命令，提供语义化 API。
 *
 * 使用方式：
 * <pre>
 *   PrinterConnection conn = new SocketConnection("192.168.1.100");
 *   PrinterService printer = new PrinterService(conn);
 *   printer.open();
 *   printer.printText("Hello, Printer!");
 *   printer.close();
 * </pre>
 */
public class PrinterService {

    private final PrinterConnection connection;
    private boolean autoFlush;

    public PrinterService(PrinterConnection connection) {
        this(connection, true);
    }

    public PrinterService(PrinterConnection connection, boolean autoFlush) {
        this.connection = connection;
        this.autoFlush = autoFlush;
    }

    // ---- 生命周期 ----

    /** 连接打印机 */
    public void open() throws IOException {
        connection.connect();
        send(EscpCommand.initializePrinter());
    }

    /** 断开连接 */
    public void close() throws IOException {
        send(EscpCommand.lineFeed());
        send(EscpCommand.formFeed());
        connection.disconnect();
    }

    // ---- 底层发送 ----

    /** 发送原始字节 */
    public void sendRaw(byte[] data) throws IOException {
        connection.write(data);
        if (autoFlush) {
            connection.flush();
        }
    }

    /** 发送命令 */
    public void send(byte[] data) throws IOException {
        sendRaw(data);
    }

    // ---- 初始化 ----

    /** 初始化打印机到默认状态 */
    public void init() throws IOException {
        send(EscpCommand.initializePrinter());
    }

    // ---- 走纸 / 换页 ----

    /** 走纸 n 行 */
    public void feedLines(int n) throws IOException {
        for (int i = 0; i < n; i++) {
            send(EscpCommand.lineFeed());
        }
    }

    /** 换页 */
    public void feedPage() throws IOException {
        send(EscpCommand.formFeed());
    }

    // ---- 页格式 ----

    /** 设置页长（行数） */
    public void setPageLength(int lines) throws IOException {
        send(EscpCommand.setPageLength(lines));
    }

    /** 设置页长（英寸） */
    public void setPageLengthInInches(int inches) throws IOException {
        send(EscpCommand.setPageLengthInInches(inches));
    }

    /** 设置底部空白（页缝跳过） */
    public void setBottomMargin(int lines) throws IOException {
        send(EscpCommand.setBottomMargin(lines));
    }

    // ---- 字符间距 ----

    public void select10CPI() throws IOException {
        send(EscpCommand.select10CPI());
    }

    public void select12CPI() throws IOException {
        send(EscpCommand.select12CPI());
    }

    public void select15CPI() throws IOException {
        send(EscpCommand.select15CPI());
    }

    // ---- 字体样式 ----

    public void bold(boolean on) throws IOException {
        send(on ? EscpCommand.boldOn() : EscpCommand.boldOff());
    }

    public void italic(boolean on) throws IOException {
        send(on ? EscpCommand.italicOn() : EscpCommand.italicOff());
    }

    public void underline(int mode) throws IOException {
        send(EscpCommand.underline(mode));
    }

    public void underlineOff() throws IOException {
        send(EscpCommand.underlineOff());
    }

    public void doubleStrike(boolean on) throws IOException {
        send(on ? EscpCommand.doubleStrikeOn() : EscpCommand.doubleStrikeOff());
    }

    public void doubleHeight(boolean on) throws IOException {
        send(EscpCommand.doubleHeight(on ? 1 : 0));
    }

    public void doubleWidth(boolean on) throws IOException {
        send(EscpCommand.doubleWidth(on ? 1 : 0));
    }

    // ---- 英文文本 ----

    /** 打印一行英文文本（自动添加 CR+LF） */
    public void printText(String text) throws IOException {
        send(EscpCommand.buildTextLine(text));
    }

    /** 打印英文文本不自动换行 */
    public void printTextNoLF(String text) throws IOException {
        try {
            sendRaw(text.getBytes("ASCII"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- 汉字 ----

    /** 进入汉字模式 */
    public void enterChineseMode() throws IOException {
        send(EscpCommand.enterChineseMode());
    }

    /** 退出汉字模式 */
    public void exitChineseMode() throws IOException {
        send(EscpCommand.exitChineseMode());
    }

    /** 选择汉字字体 (0=宋体, 1=黑体) */
    public void selectChineseFont(int font) throws IOException {
        send(EscpCommand.selectChineseFont(font));
    }

    /** 设置汉字大小 */
    public void setChineseCharSize(int width, int height) throws IOException {
        send(EscpCommand.setChineseCharacterSize(width, height));
    }

    /** 打印汉字文本 */
    public void printChinese(String text) throws IOException {
        send(EscpCommand.enterChineseMode());
        sendRaw(EscpCommand.encodeChinese(text));
        send(EscpCommand.carriageReturn());
        send(EscpCommand.lineFeed());
        send(EscpCommand.exitChineseMode());
    }

    /** 打印汉字文本（不退出汉字模式，方便连续使用） */
    public void printChineseRaw(String text) throws IOException {
        sendRaw(EscpCommand.encodeChinese(text));
        send(EscpCommand.carriageReturn());
        send(EscpCommand.lineFeed());
    }

    /** 纵向打印汉字 */
    public void printChineseVertical(String text) throws IOException {
        send(EscpCommand.enterChineseMode());
        send(EscpCommand.setVerticalPrint(true));
        sendRaw(EscpCommand.encodeChinese(text));
        send(EscpCommand.carriageReturn());
        send(EscpCommand.lineFeed());
        send(EscpCommand.setVerticalPrint(false));
        send(EscpCommand.exitChineseMode());
    }

    // ---- 位图 ----

    /** 打印位图 */
    public void printBitImage(int mode, byte[] data) throws IOException {
        send(EscpCommand.selectBitImage(mode, data));
    }

    /** 8针单密度位图 */
    public void printBitImage8Single(int nL, int nH, byte[] data) throws IOException {
        send(EscpCommand.bitImage8PinSingle(nL, nH, data));
    }

    // ---- 位置/边距 ----

    /** 设置绝对打印位置 */
    public void setAbsolutePosition(int pos) throws IOException {
        send(EscpCommand.setAbsolutePosition(pos));
    }

    /** 设置左边界 */
    public void setLeftMargin(int col) throws IOException {
        send(EscpCommand.setLeftMargin(col));
    }

    /** 设置右边界 */
    public void setRightMargin(int col) throws IOException {
        send(EscpCommand.setRightMargin(col));
    }

    // ---- 行间距 ----

    public void setLineSpacing1_8() throws IOException {
        send(EscpCommand.lineSpacing1_8());
    }

    public void setLineSpacing1_6() throws IOException {
        send(EscpCommand.lineSpacing1_6());
    }

    public void setLineSpacing216(int n) throws IOException {
        send(EscpCommand.setLineSpacing216(n));
    }

    // ================================================================
    // 基于 PrintConfig 的配置化打印
    // ================================================================

    /**
     * 应用 PrintConfig 中的所有设置到打印机。
     * 调用后打印机即处于配置所描述的状态。
     *
     * @param config 打印配置
     */
    public void applyConfig(PrintConfig config) throws IOException {
        // 1. 初始化 (可选)
        if (config.isAutoInit()) {
            send(EscpCommand.initializePrinter());
        }

        // 2. 单向/双向
        send(EscpCommand.unidirectionalPrint(config.isUnidirectional() ? 1 : 0));

        // 3. 行间距
        switch (config.getLineSpacing()) {
            case ONE_EIGHTH:
                send(EscpCommand.lineSpacing1_8());
                break;
            case ONE_SIXTH:
                send(EscpCommand.lineSpacing1_6());
                break;
            case N_216TH:
                send(EscpCommand.setLineSpacing216(config.getLineSpacingValue()));
                break;
            case N_72ND:
                send(EscpCommand.setLineSpacing72(config.getLineSpacingValue()));
                break;
        }

        // 4. 页长 — 根据配置计算
        int pageLines = config.effectivePageLines();
        send(EscpCommand.setPageLength(pageLines));

        // 5. 边距
        if (config.getBottomMargin() > 0) {
            send(EscpCommand.setBottomMargin(config.getBottomMargin()));
        }
        if (config.getLeftMargin() > 0) {
            send(EscpCommand.setLeftMargin(config.getLeftMargin()));
        }
        if (config.getRightMargin() > 0) {
            send(EscpCommand.setRightMargin(config.getRightMargin()));
        }

        // 6. CPI
        switch (config.getCpi()) {
            case 10: send(EscpCommand.select10CPI()); break;
            case 12: send(EscpCommand.select12CPI()); break;
            case 15: send(EscpCommand.select15CPI()); break;
        }

        // 7. 英文字体
        if (config.getFontIndex() > 0) {
            send(EscpCommand.selectFont(config.getFontIndex()));
        }

        // 8. 字体样式
        send(config.isBold() ? EscpCommand.boldOn() : EscpCommand.boldOff());
        send(config.isItalic() ? EscpCommand.italicOn() : EscpCommand.italicOff());
        if (config.getUnderlineMode() > 0) {
            send(EscpCommand.underline(config.getUnderlineMode()));
        } else {
            send(EscpCommand.underlineOff());
        }
        send(config.isDoubleStrike() ? EscpCommand.doubleStrikeOn() : EscpCommand.doubleStrikeOff());
        send(EscpCommand.doubleWidth(config.isDoubleWidth() ? 1 : 0));
        send(EscpCommand.doubleHeight(config.isDoubleHeight() ? 1 : 0));

        // 9. 汉字预设 (不进入模式，仅预先发送字体和大小；汉字模式在 printChinese 时进入)
        //    如果配置了汉字设置，这里不做操作，由 printChinese 内部按需使用。
        //    需要汉字设置时，通过 enterChineseModeAndApply(config) 辅助方法。
    }

    /**
     * 进入汉字模式并应用配置中的汉字设置。
     * 适用于需要连续打印多行汉字的场景。
     */
    public void enterChineseModeAndApply(PrintConfig config) throws IOException {
        send(EscpCommand.enterChineseMode());
        if (config.getChineseFont() != null) {
            send(EscpCommand.selectChineseFont(config.getChineseFont().getCode()));
        }
        if (config.getChineseCharWidth() > 1 || config.getChineseCharHeight() > 1) {
            send(EscpCommand.setChineseCharacterSize(
                    config.getChineseCharWidth(), config.getChineseCharHeight()));
        }
        if (config.isVerticalPrint()) {
            send(EscpCommand.setVerticalPrint(true));
        }
    }

    // ================================================================
    // 多页拼合打印 (N-up) — 物理页 / 逻辑页管理
    // ================================================================

    /**
     * 开始一张物理纸。
     * 多页拼合模式下，一张物理纸上可容纳多个逻辑页。
     *
     * @param config 打印配置 (含页面布局信息)
     */
    public void beginPhysicalSheet(PrintConfig config) throws IOException {
        // 设置页长为物理纸总长 (多页拼合时整个物理纸为一个"页")
        if (config.getPageLayout().isMultiUp()) {
            send(EscpCommand.setPageLength(config.getPhysicalPaper().pageLengthInLines()));
        }
    }

    /**
     * 结束一张物理纸 — 发送换页命令，将纸推出。
     */
    public void endPhysicalSheet() throws IOException {
        send(EscpCommand.formFeed());
    }

    /**
     * 开始物理纸上的第 N 个逻辑页 (0-based)。
     * 对于水平排列，第 0 页在左/上，第 1 页在右/下。
     *
     * <p>此方法处理多页拼合时的定位：
     * <ul>
     *   <li>垂直排列 + 2-up：第 0 页从顶部开始，第 1 页定位到纸张中间</li>
     *   <li>水平排列 + 2-up：第 0 页在左侧，第 1 页通过绝对水平定位移到右侧</li>
     * </ul>
     *
     * @param config     打印配置
     * @param pageIndex  逻辑页序号 (0-based, 0 ~ pagesPerSheet-1)
     */
    public void beginLogicalPage(PrintConfig config, int pageIndex) throws IOException {
        PageLayout layout = config.getPageLayout();

        if (!layout.isMultiUp() || pageIndex == 0) {
            // 单页模式或第一个逻辑页：无需特殊定位
            return;
        }

        switch (layout.getDirection()) {
            case VERTICAL:
                // 垂直排列：定位到下方逻辑页的起始行
                // 走纸到第 pageIndex 个逻辑页的顶部
                int logicalPageLines = layout.getLogicalPageLines();
                // 从当前位置走纸到下一个逻辑页起始位置
                // 注：针式打印机无法反向走纸，因此 VERTICAL 模式依赖顺序打印
                // 上一个逻辑页结束时已在正确位置，此处发送少量空行作为页间分隔
                send(EscpCommand.buildEmptyLines(2)); // 逻辑页之间 2 空行分隔
                break;

            case HORIZONTAL:
                // 水平排列：使用绝对水平定位将打印头移到右半部分
                // 计算右半部分的起始列位置
                int leftColumns = layout.getLogicalPageColumns();
                // ESC $ 使用 n/60 英寸单位；10 CPI 下 1 列 = 1/10 英寸 = 6/60 英寸
                int posUnits = leftColumns * 6;
                send(EscpCommand.setAbsolutePosition(posUnits));
                break;
        }
    }

    /**
     * 结束当前逻辑页。
     * 最后一个逻辑页结束后不换页，等待 beginPhysicalSheet 调用。
     *
     * @param config     打印配置
     * @param pageIndex  逻辑页序号 (0-based)
     * @param isLast     是否为该物理纸上的最后一个逻辑页
     */
    public void endLogicalPage(PrintConfig config, int pageIndex, boolean isLast) throws IOException {
        if (isLast) {
            // 最后一个逻辑页：由 endPhysicalSheet 统一换页
            return;
        }

        PageLayout layout = config.getPageLayout();
        if (!layout.isMultiUp()) {
            return;
        }

        switch (layout.getDirection()) {
            case VERTICAL:
                // 垂直排列：走纸到下一逻辑页起始位置
                // 上一个是 beginLogicalPage 时走纸的 2 行 + 这里补足剩余
                // 实际定位由 beginLogicalPage 负责
                send(EscpCommand.carriageReturn());
                break;

            case HORIZONTAL:
                // 水平排列：回车，准备下一行
                send(EscpCommand.carriageReturn());
                break;
        }
    }

    /**
     * 使用指定配置打印多页文本内容 (自动处理 N-up 布局)。
     *
     * <p>这是最便捷的多页拼合打印入口。调用者只需准备每逻辑页的文本内容，
     * 布局（物理页/逻辑页管理、定位、换页）由本方法全权处理。
     *
     * <p>示例 — A4 纸打 2 页 A5 横版：
     * <pre>
     *   PrintConfig config = PrintConfig.a4TwoA5Landscape();
     *   printer.applyConfig(config);
     *   printer.printWithConfig(config, page1Content, page2Content);
     * </pre>
     *
     * @param config 打印配置
     * @param pages  各逻辑页的文本内容 (英文)，数量应与 pagesPerSheet 对齐
     */
    public void printWithConfig(PrintConfig config, String... pages) throws IOException {
        int pagesPerSheet = config.getPageLayout().pagesPerSheet();

        beginPhysicalSheet(config);

        for (int i = 0; i < pages.length; i++) {
            beginLogicalPage(config, i % pagesPerSheet);

            // 打印本逻辑页内容
            send(EscpCommand.buildTextLine("--- Page " + (i + 1) + " ---"));
            printText(pages[i]);

            boolean isLastOnSheet = (i % pagesPerSheet == pagesPerSheet - 1)
                    || (i == pages.length - 1);
            endLogicalPage(config, i % pagesPerSheet, isLastOnSheet);

            // 如果当前逻辑页是该物理纸的最后一页，且还有更多内容，则换纸
            if (isLastOnSheet && i < pages.length - 1) {
                endPhysicalSheet();
                beginPhysicalSheet(config);
            }
        }

        // 最后一张物理纸收尾
        endPhysicalSheet();
    }

    // ---- 获取底层连接 ----

    public PrinterConnection getConnection() {
        return connection;
    }

    public boolean isAutoFlush() {
        return autoFlush;
    }

    public void setAutoFlush(boolean autoFlush) {
        this.autoFlush = autoFlush;
    }
}
