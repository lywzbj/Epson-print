package org.example.command;

import java.io.UnsupportedEncodingException;

/**
 * ESC/P-K 命令构建器。
 * 所有方法返回 byte[]，不依赖任何连接实现，保持纯粹的协议层。
 *
 * 控制码定义：
 *   ESC = 0x1B, FS = 0x1C, GS = 0x1D
 *   LF  = 0x0A, FF = 0x0C, CR  = 0x0D
 *
 * 参考：EPSON ESC/P-K 编程手册 (DLQ-3500K)
 */
public final class EscpCommand {

    // ===== 控制码常量 =====
    public static final byte ESC = 0x1B;
    public static final byte FS  = 0x1C;
    public static final byte GS  = 0x1D;
    public static final byte LF  = 0x0A;
    public static final byte FF  = 0x0C;
    public static final byte CR  = 0x0D;
    public static final byte NUL = 0x00;
    public static final byte SP  = 0x20;

    private EscpCommand() {
        // 工具类，禁止实例化
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /**
     * 将 int 值（0~255）安全转换为 byte。
     */
    public static byte b(int value) {
        return (byte) (value & 0xFF);
    }

    /**
     * 将多个 int 值合并为 byte[]。
     */
    public static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = b(values[i]);
        }
        return result;
    }

    /**
     * 拼接多个 byte[]。
     */
    public static byte[] concat(byte[]... arrays) {
        int totalLen = 0;
        for (byte[] arr : arrays) {
            totalLen += arr.length;
        }
        byte[] result = new byte[totalLen];
        int offset = 0;
        for (byte[] arr : arrays) {
            System.arraycopy(arr, 0, result, offset, arr.length);
            offset += arr.length;
        }
        return result;
    }

    // ================================================================
    // 打印机初始化与控制
    // ================================================================

    /** ESC @ — 初始化打印机，恢复默认设置 */
    public static byte[] initializePrinter() {
        return bytes(ESC, '@');
    }

    /** LF — 换行 */
    public static byte[] lineFeed() {
        return bytes(LF);
    }

    /** FF — 换页 */
    public static byte[] formFeed() {
        return bytes(FF);
    }

    /** CR — 回车 */
    public static byte[] carriageReturn() {
        return bytes(CR);
    }

    /** ESC C n — 设置页长（行数） */
    public static byte[] setPageLength(int lines) {
        return bytes(ESC, 'C', lines);
    }

    /** ESC C NUL n — 设置页长（英寸） */
    public static byte[] setPageLengthInInches(int inches) {
        return bytes(ESC, 'C', 0, inches);
    }

    /** ESC N n — 设置页缝跳过（底部空白行数） */
    public static byte[] setBottomMargin(int lines) {
        return bytes(ESC, 'N', lines);
    }

    /** ESC O — 取消页缝跳过 */
    public static byte[] cancelBottomMargin() {
        return bytes(ESC, 'O');
    }

    /** ESC EM n — 进纸 n/180 英寸 */
    public static byte[] feedPaper180(int n) {
        return bytes(ESC, 0x19, n);
    }

    /** ESC J n — 进纸 n/216 英寸 */
    public static byte[] feedPaper216(int n) {
        return bytes(ESC, 'J', n);
    }

    // ================================================================
    // 字符间距 / CPI 控制
    // ================================================================

    /** ESC P — 选择 10 CPI 字体 */
    public static byte[] select10CPI() {
        return bytes(ESC, 'P');
    }

    /** ESC M — 选择 12 CPI 字体 */
    public static byte[] select12CPI() {
        return bytes(ESC, 'M');
    }

    /** ESC g — 选择 15 CPI 字体 */
    public static byte[] select15CPI() {
        return bytes(ESC, 'g');
    }

    /** ESC p n — 选择比例打印开/关 (n=1 开, 0 关) */
    public static byte[] setProportional(int on) {
        return bytes(ESC, 'p', on);
    }

    /** ESC SP n — 设置字符间距 (n=0~127 点) */
    public static byte[] setCharacterSpacing(int n) {
        return bytes(ESC, SP, n);
    }

    // ================================================================
    // 字体样式
    // ================================================================

    /** ESC E — 加粗开 */
    public static byte[] boldOn() {
        return bytes(ESC, 'E');
    }

    /** ESC F — 加粗关 */
    public static byte[] boldOff() {
        return bytes(ESC, 'F');
    }

    /** ESC 4 — 斜体开 */
    public static byte[] italicOn() {
        return bytes(ESC, '4');
    }

    /** ESC 5 — 斜体关 */
    public static byte[] italicOff() {
        return bytes(ESC, '5');
    }

    /** ESC - n — 下划线 (n=0 关, 1 单线, 2 双线) */
    public static byte[] underline(int mode) {
        return bytes(ESC, '-', mode);
    }

    /** ESC - 0 — 下划线关 */
    public static byte[] underlineOff() {
        return bytes(ESC, '-', 0);
    }

    /** ESC G — 双重打印开 */
    public static byte[] doubleStrikeOn() {
        return bytes(ESC, 'G');
    }

    /** ESC H — 双重打印关 */
    public static byte[] doubleStrikeOff() {
        return bytes(ESC, 'H');
    }

    /** ESC S n — 上标/下标 (n=0 上标, 1 下标) */
    public static byte[] superscript(int on) {
        return bytes(ESC, 'S', on);
    }

    /** ESC T — 取消上标/下标 */
    public static byte[] cancelSuperscript() {
        return bytes(ESC, 'T');
    }

    /** ESC k n — 选择字体 (n=0 ROMAN, 1 SANS_SERIF, 2 COURIER, ...) */
    public static byte[] selectFont(int n) {
        return bytes(ESC, 'k', n);
    }

    /**
     * ESC ! n — Master Select，一个字节组合控制多种字体属性。
     *
     * <pre>
     *   bit 0 = 12 CPI (否则 10 CPI)
     *   bit 1 = 比例打印
     *   bit 2 = 压缩 (15 CPI)
     *   bit 3 = 加粗
     *   bit 4 = 倍高
     *   bit 5 = 倍宽
     *   bit 6 = 斜体
     *   bit 7 = 下划线
     * </pre>
     *
     * <p>示例：
     * <pre>
     *   masterSelect(0x00)  正常 10 CPI
     *   masterSelect(0x01)  12 CPI
     *   masterSelect(0x08)  加粗
     *   masterSelect(0x10)  倍高
     *   masterSelect(0x20)  倍宽
     *   masterSelect(0x30)  倍宽 + 倍高
     *   masterSelect(0x38)  倍宽 + 倍高 + 加粗
     * </pre>
     */
    public static byte[] masterSelect(int n) {
        return bytes(ESC, '!', n);
    }

    /** ESC w n — 倍高开/关 (n=1 开, 0 关) */
    public static byte[] doubleHeight(int on) {
        return bytes(ESC, 'w', on);
    }

    /** ESC W n — 倍宽开/关 (n=1 开, 0 关) */
    public static byte[] doubleWidth(int on) {
        return bytes(ESC, 'W', on);
    }

    // ================================================================
    // 汉字模式
    // ================================================================

    /** FS & — 进入汉字模式 */
    public static byte[] enterChineseMode() {
        return bytes(FS, '&');
    }

    /** FS . — 退出汉字模式 */
    public static byte[] exitChineseMode() {
        return bytes(FS, '.');
    }

    /** FS K n — 选择汉字字体 (n=0 宋体, 1 黑体) */
    public static byte[] selectChineseFont(int n) {
        return bytes(FS, 'K', n);
    }

    /** FS S n1 n2 — 设置汉字字符大小
     *  n1=1~4 (宽度倍数), n2=1~4 (高度倍数) */
    public static byte[] setChineseCharacterSize(int width, int height) {
        return bytes(FS, 'S', width, height);
    }

    /** FS V n — 纵向打印 (n=1 开, 0 关) */
    public static byte[] setVerticalPrint(boolean on) {
        return bytes(FS, 'V', on ? 1 : 0);
    }

    /** FS W n — 汉字倍宽/倍高 (n: bit0=倍宽, bit1=倍高) */
    public static byte[] chineseDoubleSize(int mode) {
        return bytes(FS, 'W', mode);
    }

    /** FS SI — 半角汉字 */
    public static byte[] chineseHalfWidth() {
        return bytes(FS, 0x0F);
    }

    /** FS DC2 — 取消半角汉字 */
    public static byte[] chineseCancelHalfWidth() {
        return bytes(FS, 0x12);
    }

    /** FS r n — 加印汉字上/下标 (n=0 取消, 1 上标, 2 下标) */
    public static byte[] chineseSuperSubscript(int n) {
        return bytes(FS, 'r', n);
    }

    /** FS e n — 设置/取消纵横倍数打印 (n: bit0-3 纵, bit4-7 横) */
    public static byte[] chineseEnlarge(int vertical, int horizontal) {
        return bytes(FS, 'e', vertical, horizontal);
    }

    /**
     * 将中文字符串转换为 GBK 字节数组。
     */
    public static byte[] encodeChinese(String text) {
        try {
            return text.getBytes("GBK");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("GBK 编码不可用", e);
        }
    }

    // ================================================================
    // 行间距
    // ================================================================

    /** ESC 0 — 设置 1/8 英寸行间距 */
    public static byte[] lineSpacing1_8() {
        return bytes(ESC, '0');
    }

    /** ESC 2 — 设置 1/6 英寸行间距 */
    public static byte[] lineSpacing1_6() {
        return bytes(ESC, '2');
    }

    /** ESC 3 n — 设置 n/216 英寸行间距 */
    public static byte[] setLineSpacing216(int n) {
        return bytes(ESC, '3', n);
    }

    /** ESC A n — 设置 n/72 英寸行间距 */
    public static byte[] setLineSpacing72(int n) {
        return bytes(ESC, 'A', n);
    }

    /** ESC + n — 设置 n/4320 英寸行间距 */
    public static byte[] setLineSpacing4320(int n) {
        return bytes(ESC, '+', n);
    }

    // ================================================================
    // 图形 / 位图
    // ================================================================

    /** ESC * m nL nH data — 位图打印
     *  m: 位图模式 (0=8针单密度, 1=8针双密度, 32=24针单密度, 33=24针双密度 ...)
     *  nL, nH: 图像数据列数 = nL + nH * 256
     *  @param m    位图模式
     *  @param nL   列数低字节
     *  @param nH   列数高字节
     *  @param data 位图数据
     */
    public static byte[] selectBitImage(int m, int nL, int nH, byte[] data) {
        byte[] header = bytes(ESC, '*', m, nL, nH);
        byte[] result = new byte[header.length + data.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(data, 0, result, header.length, data.length);
        return result;
    }

    /**
     * 位图打印便捷方法 — 自动计算 nL/nH。
     * @param m    位图模式
     * @param data 位图数据
     */
    public static byte[] selectBitImage(int m, byte[] data) {
        int columns = data.length;  // 简化为数据长度 = 列数
        int nL = columns % 256;
        int nH = columns / 256;
        return selectBitImage(m, nL, nH, data);
    }

    /** ESC K nL nH data — 8针单密度位图 */
    public static byte[] bitImage8PinSingle(int nL, int nH, byte[] data) {
        byte[] header = bytes(ESC, 'K', nL, nH);
        return concat(header, data);
    }

    /** ESC L nL nH data — 8针双密度位图 */
    public static byte[] bitImage8PinDouble(int nL, int nH, byte[] data) {
        byte[] header = bytes(ESC, 'L', nL, nH);
        return concat(header, data);
    }

    /** ESC Y nL nH data — 8针双密度双速位图 */
    public static byte[] bitImage8PinDoubleSpeed(int nL, int nH, byte[] data) {
        byte[] header = bytes(ESC, 'Y', nL, nH);
        return concat(header, data);
    }

    /** ESC Z nL nH data — 8针四密度位图 */
    public static byte[] bitImage8PinQuad(int nL, int nH, byte[] data) {
        byte[] header = bytes(ESC, 'Z', nL, nH);
        return concat(header, data);
    }

    // ================================================================
    // 制表 / 水平/垂直定位
    // ================================================================

    /** ESC $ nL nH — 设置绝对打印位置 (n/60 英寸从左边距起) */
    public static byte[] setAbsolutePosition(int position) {
        int nL = position % 256;
        int nH = position / 256;
        return bytes(ESC, '$', nL, nH);
    }

    /** ESC \ nL nH — 设置相对打印位置 (n/120 英寸) */
    public static byte[] setRelativePosition(int position) {
        int nL = position % 256;
        int nH = position / 256;
        return bytes(ESC, '\\', nL, nH);
    }

    /** ESC D n1 ... nk NUL — 设置水平制表位 */
    public static byte[] setHorizontalTabs(int... positions) {
        byte[] result = new byte[2 + positions.length + 1];
        result[0] = ESC;
        result[1] = 'D';
        for (int i = 0; i < positions.length; i++) {
            result[2 + i] = b(positions[i]);
        }
        result[result.length - 1] = NUL;
        return result;
    }

    /** HT — 水平制表 */
    public static byte[] horizontalTab() {
        return bytes(0x09);
    }

    /** ESC B n1 ... nk NUL — 设置垂直制表位 */
    public static byte[] setVerticalTabs(int... positions) {
        byte[] result = new byte[2 + positions.length + 1];
        result[0] = ESC;
        result[1] = 'B';
        for (int i = 0; i < positions.length; i++) {
            result[2 + i] = b(positions[i]);
        }
        result[result.length - 1] = NUL;
        return result;
    }

    /** VT — 垂直制表 */
    public static byte[] verticalTab() {
        return bytes(0x0B);
    }

    // ================================================================
    // 页边距
    // ================================================================

    /** ESC l n — 设置左边界 (起始列) */
    public static byte[] setLeftMargin(int column) {
        return bytes(ESC, 'l', column);
    }

    /** ESC Q n — 设置右边界 (结束列) */
    public static byte[] setRightMargin(int column) {
        return bytes(ESC, 'Q', column);
    }

    // ================================================================
    // 打印方向
    // ================================================================

    /** ESC U n — 单向打印开/关 (n=1 单向, 0 双向) */
    public static byte[] unidirectionalPrint(int on) {
        return bytes(ESC, 'U', on);
    }

    // ================================================================
    // 便捷：构建文本打印命令
    // ================================================================

    /**
     * 将普通文本转为带 CR+LF 的打印命令。
     * @param text 要打印的英文文本
     * @return 完整的打印字节序列
     */
    public static byte[] buildTextLine(String text) {
        try {
            byte[] textBytes = text.getBytes("ASCII");
            return concat(textBytes, bytes(CR, LF));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("ASCII 编码不可用", e);
        }
    }

    /**
     * 构建汉字文本打印命令（含进入/退出汉字模式）。
     * @param text 要打印的汉字文本
     * @return 完整的打印字节序列
     */
    public static byte[] buildChineseLine(String text) {
        return concat(
                enterChineseMode(),
                encodeChinese(text),
                bytes(CR, LF),
                exitChineseMode()
        );
    }

    /**
     * 构建空行走纸命令。
     * @param lines 空行数
     * @return 走纸字节序列
     */
    public static byte[] buildEmptyLines(int lines) {
        byte[] result = new byte[lines];
        for (int i = 0; i < lines; i++) {
            result[i] = LF;
        }
        return result;
    }
}
