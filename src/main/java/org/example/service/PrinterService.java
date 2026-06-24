package org.example.service;

import org.example.command.EscpCommand;
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
