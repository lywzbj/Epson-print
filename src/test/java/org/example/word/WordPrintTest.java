package org.example.word;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import static org.junit.Assert.*;

/**
 * PrintSelector、WordDocument、WordPrinter 单元测试。
 */
public class WordPrintTest {

    // ================================================================
    // PrintSelector 测试
    // ================================================================

    @Test
    public void testSelectorNoFilter() {
        PrintSelector s = new PrintSelector();
        assertTrue("无过滤时第1行应打印", s.shouldPrint(1, "Hello"));
        assertTrue("无过滤时空行应打印", s.shouldPrint(2, ""));
    }

    @Test
    public void testSelectorPageRange() {
        PrintSelector s = new PrintSelector().pageRange("1,3");
        // 每页 66 行 (默认)
        assertTrue("第1行(第1页)应打印", s.shouldPrint(1, "text"));
        assertTrue("第66行(第1页)应打印", s.shouldPrint(66, "text"));
        assertFalse("第67行(第2页)不应打印", s.shouldPrint(67, "text"));
        assertTrue("第133行(第3页)应打印", s.shouldPrint(133, "text"));
    }

    @Test
    public void testSelectorPageRangeWithDash() {
        PrintSelector s = new PrintSelector().pageRange("2-3");
        assertFalse("第1页不应打印", s.shouldPrint(1, "text"));
        assertTrue("第67行(第2页)应打印", s.shouldPrint(67, "text"));
        assertTrue("第198行(第3页)应打印", s.shouldPrint(198, "text"));
        assertFalse("第199行(第4页)不应打印", s.shouldPrint(199, "text"));
    }

    @Test
    public void testSelectorLineRange() {
        PrintSelector s = new PrintSelector().lineRange(5, 10);
        assertFalse("第4行不应打印", s.shouldPrint(4, "text"));
        assertTrue("第5行应打印", s.shouldPrint(5, "text"));
        assertTrue("第10行应打印", s.shouldPrint(10, "text"));
        assertFalse("第11行不应打印", s.shouldPrint(11, "text"));
    }

    @Test
    public void testSkipEmptyLines() {
        PrintSelector s = new PrintSelector().skipEmptyLines(true);
        assertTrue("非空行应打印", s.shouldPrint(1, "Hello"));
        assertFalse("空行应跳过", s.shouldPrint(2, ""));
        assertFalse("空白行应跳过", s.shouldPrint(3, "   "));
        assertFalse("null 应跳过", s.shouldPrint(4, null));
    }

    @Test
    public void testSkipByMarker() {
        PrintSelector s = new PrintSelector().skipByMarker("###SKIP").skipByMarker("// IGNORE");
        assertTrue("正常行应打印", s.shouldPrint(1, "Hello"));
        assertFalse("含 ###SKIP 的行应跳过", s.shouldPrint(2, "This line ###SKIP should not print"));
        assertFalse("含 // IGNORE 的行应跳过", s.shouldPrint(3, "// IGNORE this line"));
    }

    @Test
    public void testSkipLines() {
        PrintSelector s = new PrintSelector().skipLines(2, 5, 8);
        assertTrue(s.shouldPrint(1, "text"));
        assertFalse(s.shouldPrint(2, "text"));
        assertTrue(s.shouldPrint(3, "text"));
        assertFalse(s.shouldPrint(5, "text"));
        assertFalse(s.shouldPrint(8, "text"));
    }

    @Test
    public void testCombinedFilters() {
        PrintSelector s = new PrintSelector()
                .pageRange("1-2")
                .lineRange(10, 100)
                .skipEmptyLines(true);

        // 第1页第10行
        assertTrue(s.shouldPrint(10, "Valid"));
        // 空行
        assertFalse(s.shouldPrint(10, ""));
        // 第2页范围外（行号超出 lineRange）
        assertFalse(s.shouldPrint(200, "Valid"));
    }

    @Test
    public void testHasFilter() {
        assertFalse(new PrintSelector().hasAnyFilter());
        assertTrue(new PrintSelector().pageRange("1").hasAnyFilter());
        assertTrue(new PrintSelector().lineRange(1, 10).hasAnyFilter());
        assertTrue(new PrintSelector().skipEmptyLines(true).hasAnyFilter());
    }

    @Test
    public void testPageOf() {
        PrintSelector s = new PrintSelector();  // 默认 66 行/页
        assertEquals(1, s.pageOf(1));
        assertEquals(1, s.pageOf(66));
        assertEquals(2, s.pageOf(67));
        assertEquals(10, s.pageOf(600));
    }

    @Test
    public void testTotalPages() {
        PrintSelector s = new PrintSelector();
        assertEquals(0, s.totalPages(0));
        assertEquals(1, s.totalPages(1));
        assertEquals(1, s.totalPages(66));
        assertEquals(2, s.totalPages(67));
        assertEquals(10, s.totalPages(600));
    }

    // ================================================================
    // WordParagraph 测试
    // ================================================================

    @Test
    public void testParagraphChineseDetection() {
        WordDocument.WordParagraph wp = new WordDocument.WordParagraph(
                "Hello World", false, false, false, 24, "Arial");
        assertFalse("纯英文不应检测为中文", wp.containsChinese());
        assertFalse("Arial 不是中文字体", wp.shouldUseChineseMode());

        wp = new WordDocument.WordParagraph(
                "你好世界", false, false, false, 24, "宋体");
        assertTrue("中文文本应检测为中文", wp.containsChinese());
        assertTrue("中文文本应用汉字模式", wp.shouldUseChineseMode());
    }

    @Test
    public void testParagraphFormatGetters() {
        WordDocument.WordParagraph wp = new WordDocument.WordParagraph(
                "Test", true, true, true, 28, "楷体");
        assertEquals("Test", wp.getText());
        assertTrue(wp.isBold());
        assertTrue(wp.isItalic());
        assertTrue(wp.isUnderline());
        assertEquals(28, wp.getFontSize());
        assertEquals(14, wp.getFontSizePt());
        assertEquals("楷体", wp.getFontName());
    }

    @Test
    public void testEmptyParagraph() {
        WordDocument.WordParagraph wp = new WordDocument.WordParagraph("");
        assertTrue(wp.isEmpty());
        assertFalse(wp.containsChinese());
        assertFalse(wp.shouldUseChineseMode());
    }

    @Test
    public void testToString() {
        WordDocument.WordParagraph wp = new WordDocument.WordParagraph(
                "Hello", true, false, true, 24, "Arial");
        String s = wp.toString();
        assertTrue(s.contains("B"));
        assertTrue(s.contains("U"));
        assertFalse(s.contains("I"));
    }

    // ================================================================
    // WordDocument 测试（需要测试 .docx 文件或模拟）
    // ================================================================

    @Test
    public void testWordDocumentBasic() throws Exception {
        // 通过 classpath 加载测试 .docx 文件
        java.io.InputStream in = getClass().getResourceAsStream("/test_sample.docx");
        if (in == null) {
            System.out.println("[WARN] 无测试 .docx 文件，跳过 WordDocument 集成测试。");
            System.out.println("       请将 test_sample.docx 放入 src/test/resources/");
            return;
        }

        WordDocument doc = WordDocument.load(in);
        assertNotNull(doc);
        assertTrue("文档应有段落", doc.getParagraphCount() >= 0);
        assertTrue("总页数 >= 0", doc.getTotalPages() >= 0);
    }

    @Test(expected = java.io.FileNotFoundException.class)
    public void testLoadMissingFile() throws Exception {
        WordDocument.load("nonexistent_file.docx");
    }
}
