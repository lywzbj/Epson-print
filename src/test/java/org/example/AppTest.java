package org.example;

import org.example.command.EscpCommand;
import org.junit.Test;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.*;

/**
 * ESC/P-K 命令构建器单元测试。
 * 验证每个命令方法输出的 byte[] 序列是否与 ESC/P 手册一致。
 */
public class AppTest {

    // ================================================================
    // 辅助方法
    // ================================================================

    private String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02X ", b & 0xFF));
        }
        return sb.toString().trim();
    }

    /** 每个 byte[] 测试后打印 hex 方便人工核对 */
    private void printHex(String label, byte[] data) {
        System.out.printf("  %-40s → %s%n", label, hex(data));
    }

    // ================================================================
    // 初始化与控制
    // ================================================================

    @Test
    public void testInitializePrinter() {
        byte[] cmd = EscpCommand.initializePrinter();
        printHex("initializePrinter (ESC @)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x40}, cmd);
    }

    @Test
    public void testLineFeed() {
        byte[] cmd = EscpCommand.lineFeed();
        assertEquals(1, cmd.length);
        assertEquals((byte) 0x0A, cmd[0]);
    }

    @Test
    public void testFormFeed() {
        byte[] cmd = EscpCommand.formFeed();
        assertEquals(1, cmd.length);
        assertEquals((byte) 0x0C, cmd[0]);
    }

    @Test
    public void testCarriageReturn() {
        byte[] cmd = EscpCommand.carriageReturn();
        assertEquals(1, cmd.length);
        assertEquals((byte) 0x0D, cmd[0]);
    }

    @Test
    public void testSetPageLength() {
        byte[] cmd = EscpCommand.setPageLength(66);
        printHex("setPageLength(66) (ESC C 66)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x43, 66}, cmd);
    }

    @Test
    public void testSetPageLengthInInches() {
        byte[] cmd = EscpCommand.setPageLengthInInches(11);
        printHex("setPageLengthInInches(11)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x43, 0, 11}, cmd);
    }

    @Test
    public void testSetBottomMargin() {
        byte[] cmd = EscpCommand.setBottomMargin(3);
        printHex("setBottomMargin(3) (ESC N 3)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x4E, 3}, cmd);
    }

    @Test
    public void testCancelBottomMargin() {
        byte[] cmd = EscpCommand.cancelBottomMargin();
        printHex("cancelBottomMargin (ESC O)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x4F}, cmd);
    }

    // ================================================================
    // CPI 控制
    // ================================================================

    @Test
    public void testSelect10CPI() {
        byte[] cmd = EscpCommand.select10CPI();
        printHex("select10CPI (ESC P)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x50}, cmd);
    }

    @Test
    public void testSelect12CPI() {
        byte[] cmd = EscpCommand.select12CPI();
        printHex("select12CPI (ESC M)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x4D}, cmd);
    }

    @Test
    public void testSelect15CPI() {
        byte[] cmd = EscpCommand.select15CPI();
        printHex("select15CPI (ESC g)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x67}, cmd);
    }

    // ================================================================
    // 字体样式
    // ================================================================

    @Test
    public void testBoldOn() {
        byte[] cmd = EscpCommand.boldOn();
        printHex("boldOn (ESC E)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x45}, cmd);
    }

    @Test
    public void testBoldOff() {
        byte[] cmd = EscpCommand.boldOff();
        printHex("boldOff (ESC F)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x46}, cmd);
    }

    @Test
    public void testItalicOn() {
        byte[] cmd = EscpCommand.italicOn();
        printHex("italicOn (ESC 4)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x34}, cmd);
    }

    @Test
    public void testItalicOff() {
        byte[] cmd = EscpCommand.italicOff();
        printHex("italicOff (ESC 5)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x35}, cmd);
    }

    @Test
    public void testUnderline() {
        byte[] cmd1 = EscpCommand.underline(1);
        printHex("underline(1) (ESC - 1)", cmd1);
        assertArrayEquals(new byte[]{0x1B, 0x2D, 1}, cmd1);

        byte[] cmd2 = EscpCommand.underline(2);
        printHex("underline(2) (ESC - 2)", cmd2);
        assertArrayEquals(new byte[]{0x1B, 0x2D, 2}, cmd2);

        byte[] cmd0 = EscpCommand.underlineOff();
        printHex("underlineOff (ESC - 0)", cmd0);
        assertArrayEquals(new byte[]{0x1B, 0x2D, 0}, cmd0);
    }

    @Test
    public void testDoubleStrike() {
        assertArrayEquals(new byte[]{0x1B, 0x47}, EscpCommand.doubleStrikeOn());
        assertArrayEquals(new byte[]{0x1B, 0x48}, EscpCommand.doubleStrikeOff());
    }

    @Test
    public void testDoubleHeight() {
        assertArrayEquals(new byte[]{0x1B, 0x77, 1}, EscpCommand.doubleHeight(1));
        assertArrayEquals(new byte[]{0x1B, 0x77, 0}, EscpCommand.doubleHeight(0));
    }

    @Test
    public void testDoubleWidth() {
        assertArrayEquals(new byte[]{0x1B, 0x57, 1}, EscpCommand.doubleWidth(1));
        assertArrayEquals(new byte[]{0x1B, 0x57, 0}, EscpCommand.doubleWidth(0));
    }

    // ================================================================
    // 汉字模式
    // ================================================================

    @Test
    public void testEnterChineseMode() {
        byte[] cmd = EscpCommand.enterChineseMode();
        printHex("enterChineseMode (FS &)", cmd);
        assertArrayEquals(new byte[]{0x1C, 0x26}, cmd);
    }

    @Test
    public void testExitChineseMode() {
        byte[] cmd = EscpCommand.exitChineseMode();
        printHex("exitChineseMode (FS .)", cmd);
        assertArrayEquals(new byte[]{0x1C, 0x2E}, cmd);
    }

    @Test
    public void testSelectChineseFont() {
        byte[] cmd0 = EscpCommand.selectChineseFont(0);
        printHex("selectChineseFont(0) 宋体 (FS K 0)", cmd0);
        assertArrayEquals(new byte[]{0x1C, 0x4B, 0}, cmd0);

        byte[] cmd1 = EscpCommand.selectChineseFont(1);
        printHex("selectChineseFont(1) 黑体 (FS K 1)", cmd1);
        assertArrayEquals(new byte[]{0x1C, 0x4B, 1}, cmd1);
    }

    @Test
    public void testSetChineseCharSize() {
        byte[] cmd = EscpCommand.setChineseCharacterSize(2, 2);
        printHex("setChineseCharSize(2,2) (FS S 2 2)", cmd);
        assertArrayEquals(new byte[]{0x1C, 0x53, 2, 2}, cmd);
    }

    @Test
    public void testVerticalPrint() {
        assertArrayEquals(new byte[]{0x1C, 0x56, 1}, EscpCommand.setVerticalPrint(true));
        assertArrayEquals(new byte[]{0x1C, 0x56, 0}, EscpCommand.setVerticalPrint(false));
    }

    @Test
    public void testEncodeChinese() throws UnsupportedEncodingException {
        // 验证 "打印机" 的 GBK 编码
        byte[] encoded = EscpCommand.encodeChinese("打印机");
        byte[] expected = "打印机".getBytes("GBK");
        printHex("encodeChinese(\"打印机\") → GBK", encoded);
        assertArrayEquals(expected, encoded);
    }

    // ================================================================
    // 行间距
    // ================================================================

    @Test
    public void testLineSpacing() {
        assertArrayEquals(new byte[]{0x1B, 0x30}, EscpCommand.lineSpacing1_8());
        assertArrayEquals(new byte[]{0x1B, 0x32}, EscpCommand.lineSpacing1_6());
        assertArrayEquals(new byte[]{0x1B, 0x33, 24}, EscpCommand.setLineSpacing216(24));
        assertArrayEquals(new byte[]{0x1B, 0x41, 12}, EscpCommand.setLineSpacing72(12));
    }

    // ================================================================
    // 位图
    // ================================================================

    @Test
    public void testSelectBitImage() {
        byte[] data = {(byte) 0xAA, (byte) 0x55};
        byte[] cmd = EscpCommand.selectBitImage(0, 2, 0, data);
        printHex("selectBitImage(0, 2, 0, [AA,55])", cmd);
        // ESC * 0 2 0 AA 55
        assertArrayEquals(new byte[]{0x1B, 0x2A, 0, 2, 0, (byte) 0xAA, (byte) 0x55}, cmd);
    }

    @Test
    public void testBitImage8PinSingle() {
        byte[] data = {(byte) 0xFF};
        byte[] cmd = EscpCommand.bitImage8PinSingle(1, 0, data);
        printHex("bitImage8PinSingle(1, 0, [FF]) (ESC K)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x4B, 1, 0, (byte) 0xFF}, cmd);
    }

    // ================================================================
    // 辅助方法测试
    // ================================================================

    @Test
    public void testByteConversion() {
        // b() 方法应正确处理 0~255 范围与 Java 有符号 byte 的转换
        assertEquals((byte) 0x00, EscpCommand.b(0));
        assertEquals((byte) 0x7F, EscpCommand.b(127));
        assertEquals((byte) 0x80, EscpCommand.b(128));
        assertEquals((byte) 0xFF, EscpCommand.b(255));
    }

    @Test
    public void testConcat() {
        byte[] a = {0x1B, 0x40};
        byte[] b = {0x0A};
        byte[] result = EscpCommand.concat(a, b);
        assertArrayEquals(new byte[]{0x1B, 0x40, 0x0A}, result);
    }

    @Test
    public void testBuildTextLine() {
        byte[] cmd = EscpCommand.buildTextLine("Hi");
        printHex("buildTextLine(\"Hi\")", cmd);
        assertArrayEquals(new byte[]{'H', 'i', 0x0D, 0x0A}, cmd);
    }

    @Test
    public void testBuildEmptyLines() {
        byte[] cmd = EscpCommand.buildEmptyLines(3);
        printHex("buildEmptyLines(3)", cmd);
        assertArrayEquals(new byte[]{0x0A, 0x0A, 0x0A}, cmd);
    }

    // ================================================================
    // 页边距与定位
    // ================================================================

    @Test
    public void testMargins() {
        assertArrayEquals(new byte[]{0x1B, 0x6C, 5}, EscpCommand.setLeftMargin(5));
        assertArrayEquals(new byte[]{0x1B, 0x51, 70}, EscpCommand.setRightMargin(70));
    }

    @Test
    public void testAbsolutePosition() {
        byte[] cmd = EscpCommand.setAbsolutePosition(180);
        // 180 = 0xB4; nL = 180 % 256 = 180, nH = 180 / 256 = 0
        printHex("setAbsolutePosition(180)", cmd);
        assertArrayEquals(new byte[]{0x1B, 0x24, (byte) 180, 0}, cmd);
    }
}
