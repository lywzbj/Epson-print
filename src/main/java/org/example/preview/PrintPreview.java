package org.example.preview;

import org.example.word.WordDocument.WordParagraph;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 打印预览 — 将 Word 文档的段落及格式信息渲染为独立 HTML 预览文件。
 *
 * <p>不连接真实打印机，纯粹生成可视化预览用于人工核对。
 *
 * <h3>使用方式</h3>
 * <pre>
 *   PrintPreview preview = new PrintPreview("E:\\tmp\\test.docx");
 *   wordPrinter.generatePreview(doc, selector, preview);
 *   preview.writeHtml("E:\\tmp\\preview.html");
 *   System.out.println("预览文件: " + preview.getHtmlPath());
 * </pre>
 */
public class PrintPreview {

    private final String sourceFile;
    private final List<RecordedLine> lines;
    private String htmlPath;

    public PrintPreview(String sourceFile) {
        this.sourceFile = sourceFile;
        this.lines = new ArrayList<>();
    }

    // ================================================================
    // 记录
    // ================================================================

    /** 记录一个段落（传入 Word 解析结果，提取全部格式信息） */
    public void addLine(WordParagraph wp, String formattedText, int globalLine) {
        lines.add(new RecordedLine(wp, formattedText, globalLine));
    }

    /** 记录一个段落分隔（空行） */
    public void addSeparator() {
        lines.add(RecordedLine.SEPARATOR);
    }

    /** 已记录的行数 */
    public int lineCount() {
        return lines.size();
    }

    public String getSourceFile() {
        return sourceFile;
    }

    // ================================================================
    // 渲染
    // ================================================================

    /**
     * 生成独立 HTML 预览文件。
     * @param outputPath 输出路径 (如 E:\tmp\preview.html)
     */
    public void writeHtml(String outputPath) throws IOException {
        StringBuilder html = new StringBuilder(16 * 1024);

        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n")
            .append("<meta charset=\"UTF-8\">\n")
            .append("<title>打印预览 — ").append(escapeHtml(sourceFile)).append("</title>\n")
            .append("<style>\n")
            .append("  * { margin:0; padding:0; box-sizing:border-box; }\n")
            .append("  body { font-family:'Microsoft YaHei','SimSun',sans-serif; ")
            .append("background:#f5f5f5; padding:20px; }\n")
            .append("  .page { width:210mm; min-height:297mm; margin:0 auto 30px; ")
            .append("background:#fff; box-shadow:0 0 8px rgba(0,0,0,.15); ")
            .append("padding:15mm 20mm 15mm 25mm; position:relative; }\n")
            .append("  .page-header { border-bottom:1px solid #ccc; margin-bottom:12px; ")
            .append("padding-bottom:8px; color:#666; font-size:11px; }\n")
            .append("  .line { white-space:pre-wrap; word-break:break-all; ")
            .append("position:relative; min-height:1.2em; }\n")
            .append("  .line:hover { background:#fffde7; }\n")
            .append("  .line-num { display:inline-block; width:36px; color:#bbb; ")
            .append("font-size:9px; text-align:right; margin-right:8px; ")
            .append("user-select:none; }\n")
            .append("  .line .tags { float:right; font-size:8px; color:#999; ")
            .append("margin-left:10px; white-space:nowrap; }\n")
            .append("  .line .tag { display:inline-block; background:#e8e8e8; ")
            .append("border-radius:2px; padding:0 3px; margin-left:2px; }\n")
            .append("  .line .tag.bold { background:#ffcc80; color:#333; }\n")
            .append("  .line .tag.italic { background:#b3e5fc; color:#333; }\n")
            .append("  .line .tag.uline { background:#c8e6c9; color:#333; }\n")
            .append("  .line .tag.size { background:#e1bee7; color:#333; }\n")
            .append("  .line .tag.table { background:#ffcc02; color:#333; font-weight:bold; }\n")
            .append("  .line .tag.spacing { background:#ffe0b2; color:#333; }\n")
            .append("  .line .tag.indent { background:#b2dfdb; color:#333; }\n")
            .append("  .line .tag.align { background:#d1c4e9; color:#333; }\n")
            .append("  .separator { border-top:1px dashed #ddd; margin:4px 0; }\n")
            .append("  .summary { max-width:210mm; margin:0 auto; color:#666; ")
            .append("font-size:12px; }\n")
            .append("</style>\n</head>\n<body>\n");

        // 分页 (A4 每页约 66 行，但 HTML 预览按约 55 行/页来分以适配边距)
        int linesPerPage = 55;
        int pageNum = 1;
        int lineOnPage = 0;

        html.append("<div class=\"page\">\n");
        html.append("<div class=\"page-header\">")
            .append("文件: ").append(escapeHtml(sourceFile))
            .append(" &nbsp;|&nbsp; 第 ").append(pageNum).append(" 页")
            .append(" &nbsp;|&nbsp; 共 ").append(lines.size()).append(" 行")
            .append("</div>\n");

        for (int i = 0; i < lines.size(); i++) {
            RecordedLine rl = lines.get(i);

            if (rl.isSeparator) {
                html.append("<div class=\"separator\"></div>\n");
                continue;
            }

            lineOnPage++;
            if (lineOnPage > linesPerPage) {
                html.append("</div>\n<div class=\"page\">\n");
                pageNum++;
                lineOnPage = 1;
                html.append("<div class=\"page-header\">")
                    .append("文件: ").append(escapeHtml(sourceFile))
                    .append(" &nbsp;|&nbsp; 第 ").append(pageNum).append(" 页")
                    .append("</div>\n");
            }

            html.append("<div class=\"line\"");

            // inline style: font-size, font-weight, font-style
            int pt = rl.fontSizePt > 0 ? rl.fontSizePt : 12;
            html.append(" style=\"font-size:").append(pt).append("pt;");

            if (rl.bold) html.append(" font-weight:bold;");
            if (rl.italic) html.append(" font-style:italic;");
            if (rl.underline) html.append(" text-decoration:underline;");
            if (rl.doubleWidth) html.append(" letter-spacing:0.3em;");
            if (rl.doubleHeight) html.append(" line-height:2.2;");

            // 段前/段后间距
            if (rl.spacingBefore > 0) {
                html.append(" margin-top:").append(rl.spacingBefore / 20).append("pt;");
            }
            if (rl.spacingAfter > 0) {
                html.append(" margin-bottom:").append(rl.spacingAfter / 20).append("pt;");
            }

            // 左缩进
            if (rl.indentLeft > 0) {
                html.append(" margin-left:").append(rl.indentLeft / 20).append("pt;");
            }

            // 首行缩进
            if (rl.indentFirstLine > 0) {
                html.append(" text-indent:").append(rl.indentFirstLine / 20).append("pt;");
            }

            // 对齐
            if (rl.alignment == 1) html.append(" text-align:center;");
            else if (rl.alignment == 2) html.append(" text-align:right;");
            else if (rl.alignment == 3) html.append(" text-align:justify;");

            html.append("\">");

            // 行号
            html.append("<span class=\"line-num\">").append(rl.globalLine).append("</span>");

            // 格式化标签
            html.append("<span class=\"tags\">");
            if (rl.isTableRow) html.append("<span class=\"tag table\">表#")
                .append(rl.tableIndex).append("行").append(rl.rowInTable).append("</span>");
            if (rl.bold) html.append("<span class=\"tag bold\">B</span>");
            if (rl.italic) html.append("<span class=\"tag italic\">I</span>");
            if (rl.underline) html.append("<span class=\"tag uline\">U</span>");
            if (rl.fontSizePt > 0) html.append("<span class=\"tag size\">")
                .append(rl.fontSizePt).append("pt</span>");
            if (rl.spacingBefore > 0 || rl.spacingAfter > 0) {
                html.append("<span class=\"tag spacing\">")
                    .append("↑").append(rl.spacingBefore / 20)
                    .append("/↓").append(rl.spacingAfter / 20).append("</span>");
            }
            if (rl.indentLeft > 0) html.append("<span class=\"tag indent\">←")
                .append(rl.indentLeft / 20).append("</span>");
            if (rl.indentFirstLine > 0) html.append("<span class=\"tag indent\">¶")
                .append(rl.indentFirstLine / 20).append("</span>");
            if (rl.alignment == 1) html.append("<span class=\"tag align\">居中</span>");
            else if (rl.alignment == 2) html.append("<span class=\"tag align\">右齐</span>");
            html.append("</span>");

            // 文本
            html.append(escapeHtml(rl.text));

            html.append("</div>\n");
        }

        html.append("</div>\n");  // close last page

        // 底部摘要
        html.append("<div class=\"summary\">\n");
        html.append("  <p>文件: ").append(escapeHtml(sourceFile)).append("<br>\n");
        html.append("  总行数: ").append(lines.size()).append(" &nbsp;|&nbsp; ")
            .append("页数: ").append(pageNum).append("<br>\n");
        html.append("  生成时间: ").append(java.time.LocalDateTime.now()).append("</p>\n");
        html.append("</div>\n");

        html.append("</body>\n</html>");

        // 写入
        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(outputPath, StandardCharsets.UTF_8))) {
            writer.write(html.toString());
        }

        this.htmlPath = outputPath;
    }

    public String getHtmlPath() {
        return htmlPath;
    }

    // ================================================================
    // 内部类
    // ================================================================

    /** 记录的一行 */
    public static class RecordedLine {
        static final RecordedLine SEPARATOR = new RecordedLine();

        final boolean isSeparator;
        final String text;
        final int globalLine;

        // 字符格式
        final boolean bold;
        final boolean italic;
        final boolean underline;
        final int fontSizePt;
        final String fontName;
        final boolean doubleWidth;
        final boolean doubleHeight;

        // 表格来源
        final boolean isTableRow;
        final int tableIndex;
        final int rowInTable;

        // 段落间距 (twip)
        final int spacingBefore;
        final int spacingAfter;
        final int spacingBetween;

        // 缩进 (twip)
        final int indentLeft;
        final int indentFirstLine;

        // 对齐: 0=left, 1=center, 2=right, 3=both
        final int alignment;

        private RecordedLine() {
            this.isSeparator = true;
            this.text = "";
            this.globalLine = 0;
            this.bold = false; this.italic = false; this.underline = false;
            this.fontSizePt = 0; this.fontName = null;
            this.doubleWidth = false; this.doubleHeight = false;
            this.isTableRow = false; this.tableIndex = -1; this.rowInTable = 0;
            this.spacingBefore = 0; this.spacingAfter = 0; this.spacingBetween = 0;
            this.indentLeft = 0; this.indentFirstLine = 0;
            this.alignment = 0;
        }

        RecordedLine(WordParagraph wp, String formattedText, int globalLine) {
            this.isSeparator = false;
            this.text = formattedText != null ? formattedText : wp.getText();
            this.globalLine = globalLine;

            // 字符格式
            this.bold = wp.isBold();
            this.italic = wp.isItalic();
            this.underline = wp.isUnderline();
            this.fontSizePt = wp.getFontSizePt();
            this.fontName = wp.getFontName();
            // 根据字号估算倍宽倍高（与 WordPrinter.applyFontSize 一致）
            if (fontSizePt > 0) {
                this.doubleWidth = fontSizePt >= 21;
                this.doubleHeight = fontSizePt >= 18;
            } else {
                this.doubleWidth = false;
                this.doubleHeight = false;
            }

            // 表格
            this.isTableRow = wp.isTableRow();
            this.tableIndex = wp.getTableIndex();
            this.rowInTable = wp.getRowInTable();

            // 段落格式
            this.spacingBefore = wp.getSpacingBefore();
            this.spacingAfter = wp.getSpacingAfter();
            this.spacingBetween = wp.getSpacingBetween();
            this.indentLeft = wp.getIndentLeft();
            this.indentFirstLine = wp.getIndentFirstLine();
            this.alignment = wp.getAlignment();
        }
    }

    // ================================================================
    // 辅助
    // ================================================================

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
