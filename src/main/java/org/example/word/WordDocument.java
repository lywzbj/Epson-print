package org.example.word;

import org.apache.poi.xwpf.usermodel.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Word (.docx) 文档解析器。
 * 基于 Apache POI，提取段落文本及格式信息。
 *
 * 使用方式：
 * <pre>
 *   WordDocument doc = WordDocument.load("report.docx");
 *   List<WordParagraph> paragraphs = doc.getAllParagraphs();
 *   int pages = doc.getTotalPages();
 * </pre>
 */
public class WordDocument {

    /** 针式打印机默认每页行数 */
    public static final int DEFAULT_LINES_PER_PAGE = 66;

    private final String filePath;
    private final List<WordParagraph> paragraphs;
    private int linesPerPage;

    private WordDocument(String filePath, int linesPerPage) {
        this.filePath = filePath;
        this.linesPerPage = linesPerPage;
        this.paragraphs = new ArrayList<>();
    }

    // ======== 工厂方法 ========

    /**
     * 加载 .docx 文件。
     * @param filePath 文件路径
     */
    public static WordDocument load(String filePath) throws IOException {
        return load(filePath, DEFAULT_LINES_PER_PAGE);
    }

    /**
     * 加载 .docx 文件并指定每页行数。
     * @param filePath      文件路径
     * @param linesPerPage  每页行数（用于页码估算）
     */
    public static WordDocument load(String filePath, int linesPerPage) throws IOException {
        WordDocument doc = new WordDocument(filePath, linesPerPage);
        doc.parse();
        return doc;
    }

    /**
     * 从 InputStream 加载（兼容 classpath 资源）。
     */
    public static WordDocument load(InputStream in, int linesPerPage) throws IOException {
        WordDocument doc = new WordDocument("(stream)", linesPerPage);
        doc.parse(in);
        return doc;
    }

    // ======== 解析逻辑 ========

    private void parse() throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            parse(fis);
        }
    }

    private void parse(InputStream in) throws IOException {
        paragraphs.clear();
        XWPFDocument doc = new XWPFDocument(in);

        int tableCount = 0;  // 当前表格序号 (0-based)

        // 遍历所有 IBODY 元素（包括正文、页眉页脚、表格等）
        for (IBodyElement element : doc.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                XWPFParagraph para = (XWPFParagraph) element;
                WordParagraph wp = parseParagraph(para);
                if (wp != null) {
                    paragraphs.add(wp);
                }
            } else if (element instanceof XWPFTable) {
                XWPFTable table = (XWPFTable) element;
                int rowNum = 0;  // 当前表格内行号 (1-based)
                for (XWPFTableRow row : table.getRows()) {
                    rowNum++;
                    StringBuilder rowText = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph cp : cell.getParagraphs()) {
                            String cellText = extractText(cp);
                            if (!cellText.isEmpty()) {
                                if (rowText.length() > 0) rowText.append(" | ");
                                rowText.append(cellText);
                            }
                        }
                    }
                    if (rowText.length() > 0) {
                        paragraphs.add(new WordParagraph(rowText.toString(),
                                false, false, false, 0, null,
                                true, tableCount, rowNum));
                    }
                }
                tableCount++;
            }
        }

        doc.close();
    }

    /**
     * 解析单个段落。
     */
    private WordParagraph parseParagraph(XWPFParagraph para) {
        List<XWPFRun> runs = para.getRuns();
        if (runs.isEmpty()) {
            // 可能是个空段落（只有换行）
            return new WordParagraph("");
        }

        // 收集文本与格式
        StringBuilder fullText = new StringBuilder();
        boolean isBold = false, isItalic = false, isUnderline = false;
        int maxFontSize = 0;
        String fontName = null;
        String fontNameEastAsia = null;

        for (XWPFRun run : runs) {
            String text = run.text();
            if (text != null && !text.isEmpty()) {
                fullText.append(text);
            }

            if (run.isBold()) isBold = true;
            if (run.isItalic()) isItalic = true;
            if (run.getUnderline() != UnderlinePatterns.NONE) isUnderline = true;

            int fs = run.getFontSize();
            if (fs > maxFontSize) maxFontSize = fs;

            if (fontName == null && run.getFontFamily() != null) {
                fontName = run.getFontFamily();
            }
            if (fontNameEastAsia == null && run.getFontName() != null) {
                fontNameEastAsia = run.getFontName();
            }
        }

        String text = fullText.toString();
        if (text.isEmpty()) {
            return new WordParagraph("");
        }

        // 优先使用东亚字体名（中文环境）
        String effectiveFont = (fontNameEastAsia != null) ? fontNameEastAsia : fontName;

        return new WordParagraph(text, isBold, isItalic, isUnderline,
                maxFontSize, effectiveFont);
    }

    /**
     * 仅提取段落纯文本。
     */
    private String extractText(XWPFParagraph para) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : para.getRuns()) {
            String t = run.text();
            if (t != null) sb.append(t);
        }
        return sb.toString();
    }

    // ======== 查询方法 ========

    /** 所有段落列表 */
    public List<WordParagraph> getAllParagraphs() {
        return new ArrayList<>(paragraphs);
    }

    /** 总段落数 */
    public int getParagraphCount() {
        return paragraphs.size();
    }

    /** 估计总页数 */
    public int getTotalPages() {
        return (int) Math.ceil((double) paragraphs.size() / linesPerPage);
    }

    /** 估计总行数 (≈ 段落数) */
    public int getTotalLines() {
        return paragraphs.size();
    }

    /** 获取指定页的段落 */
    public List<WordParagraph> getParagraphs(int page) {
        int start = (page - 1) * linesPerPage;
        int end = Math.min(start + linesPerPage, paragraphs.size());
        if (start >= paragraphs.size()) return new ArrayList<>();
        return new ArrayList<>(paragraphs.subList(start, end));
    }

    /** 获取页码范围段落 */
    public List<WordParagraph> getParagraphs(int pageStart, int pageEnd) {
        List<WordParagraph> result = new ArrayList<>();
        for (int p = pageStart; p <= pageEnd; p++) {
            result.addAll(getParagraphs(p));
        }
        return result;
    }

    /** 获取行范围段落 */
    public List<WordParagraph> getParagraphsByLines(int lineStart, int lineEnd) {
        int start = Math.max(0, lineStart - 1);
        int end = Math.min(lineEnd, paragraphs.size());
        if (start >= paragraphs.size()) return new ArrayList<>();
        return new ArrayList<>(paragraphs.subList(start, end));
    }

    /** 文件路径 */
    public String getFilePath() {
        return filePath;
    }

    /** 每页行数 */
    public int getLinesPerPage() {
        return linesPerPage;
    }

    public void setLinesPerPage(int linesPerPage) {
        this.linesPerPage = linesPerPage;
    }

    // ======== 表格查询 ========

    /**
     * 统计文档中的表格数量。
     * @return 表格总数
     */
    public int getTableCount() {
        int max = -1;
        for (WordParagraph wp : paragraphs) {
            if (wp.isTableRow && wp.tableIndex > max) {
                max = wp.tableIndex;
            }
        }
        return max + 1;
    }

    /**
     * 获取指定表格的全部行（按行号排序）。
     * @param tableIndex 表格序号 (0-based)
     * @return 该表格所有行的段落列表
     */
    public List<WordParagraph> getTableParagraphs(int tableIndex) {
        List<WordParagraph> result = new ArrayList<>();
        for (WordParagraph wp : paragraphs) {
            if (wp.isTableRow && wp.tableIndex == tableIndex) {
                result.add(wp);
            }
        }
        // 按行号排序
        result.sort((a, b) -> Integer.compare(a.rowInTable, b.rowInTable));
        return result;
    }

    /**
     * 获取指定表格的指定行。
     * @param tableIndex 表格序号 (0-based)
     * @param rowInTable 行号 (1-based)
     * @return 该行段落，找不到返回 null
     */
    public WordParagraph getTableRow(int tableIndex, int rowInTable) {
        for (WordParagraph wp : paragraphs) {
            if (wp.isTableRow && wp.tableIndex == tableIndex && wp.rowInTable == rowInTable) {
                return wp;
            }
        }
        return null;
    }

    /**
     * 获取文档中所有表格行段落（扁平列表）。
     */
    public List<WordParagraph> getAllTableRows() {
        List<WordParagraph> result = new ArrayList<>();
        for (WordParagraph wp : paragraphs) {
            if (wp.isTableRow) {
                result.add(wp);
            }
        }
        return result;
    }

    // ============ 内部类：WordParagraph ============

    /**
     * 代表 Word 文档中的一个段落，包含文本和格式信息。
     */
    public static class WordParagraph {

        private final String text;
        private final boolean bold;
        private final boolean italic;
        private final boolean underline;
        private final int fontSize;        // 单位：半点 (half-point)，如 24 = 12pt
        private final String fontName;

        // 表格来源信息
        private final boolean isTableRow;  // 是否来自表格
        private final int tableIndex;      // 来源表格序号 (0-based), 非表格=-1
        private final int rowInTable;      // 在表格中的行号 (1-based), 非表格=0

        // 中文检测正则
        private static final Pattern CHINESE_PATTERN =
                Pattern.compile("[\\u4e00-\\u9fff\\u3400-\\u4dbf]");

        /** 空段落 */
        public WordParagraph(String text) {
            this(text, false, false, false, 0, null, false, -1, 0);
        }

        public WordParagraph(String text, boolean bold, boolean italic,
                             boolean underline, int fontSize, String fontName) {
            this(text, bold, italic, underline, fontSize, fontName, false, -1, 0);
        }

        /** 完整构造器（含表格来源信息） */
        public WordParagraph(String text, boolean bold, boolean italic,
                             boolean underline, int fontSize, String fontName,
                             boolean isTableRow, int tableIndex, int rowInTable) {
            this.text = (text != null) ? text : "";
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.fontSize = fontSize;
            this.fontName = fontName;
            this.isTableRow = isTableRow;
            this.tableIndex = tableIndex;
            this.rowInTable = rowInTable;
        }

        // ======== Getters ========

        public String getText()             { return text; }
        public boolean isEmpty()            { return text.trim().isEmpty(); }
        public boolean isBold()             { return bold; }
        public boolean isItalic()           { return italic; }
        public boolean isUnderline()        { return underline; }
        public int getFontSize()            { return fontSize; }
        /** 字号转换为磅 (pt) */
        public int getFontSizePt()          { return fontSize / 2; }
        public String getFontName()         { return fontName; }
        public boolean containsChinese()    { return CHINESE_PATTERN.matcher(text).find(); }
        /** 字体是否为中文名称 */
        public boolean isChineseFont() {
            return fontName != null && CHINESE_PATTERN.matcher(fontName).find();
        }

        // ======== 表格来源 ========

        /** 是否来自 Word 表格 */
        public boolean isTableRow()          { return isTableRow; }
        /** 来源表格序号 (0-based)，非表格返回 -1 */
        public int getTableIndex()           { return tableIndex; }
        /** 在表格中的行号 (1-based)，非表格返回 0 */
        public int getRowInTable()           { return rowInTable; }

        // ======== 格式适配 ========

        /**
         * 判断该段是否应以汉字模式打印。
         * 条件：包含中文字符 或 字体名含中文。
         */
        public boolean shouldUseChineseMode() {
            return containsChinese() || isChineseFont();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            if (bold) sb.append("B");
            if (italic) sb.append("I");
            if (underline) sb.append("U");
            if (sb.length() > 1 || bold || italic || underline) {
                // has format
            } else {
                sb.append("-");
            }
            sb.append("] ");
            if (fontSize > 0) sb.append('(').append(getFontSizePt()).append("pt) ");
            if (fontName != null) sb.append('[').append(fontName).append("] ");
            sb.append(text.length() > 60 ? text.substring(0, 57) + "..." : text);
            return sb.toString();
        }
    }
}
