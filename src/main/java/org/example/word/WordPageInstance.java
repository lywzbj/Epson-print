package org.example.word;

import org.example.config.PrintConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Word 文档中的一页 — 包含打印该页所需的全部信息。
 *
 * <p>由 {@link WordDocument} 在解析 DOCX 时根据真实分页符构建，
 * 每个实例对应文档中的一个逻辑页。调用方可直接通过此实例获取
 * 本页的段落列表、页面布局和页眉/页脚，无需再关心全局行号等细节。
 *
 * <h3>使用</h3>
 * <pre>
 *   WordDocument doc = WordDocument.load("report.docx");
 *   WordPageInstance page1 = doc.getPage(1);
 *   page1.getParagraphs().forEach(p -> System.out.println(p.getText()));
 *   PrintConfig config = page1.derivePrintConfig();
 * </pre>
 */
public class WordPageInstance {

    private final int pageNumber;
    private final List<WordDocument.WordParagraph> paragraphs;
    private final DocPageLayout pageLayout;
    private final String headerText;
    private final String footerText;

    public WordPageInstance(int pageNumber,
                            List<WordDocument.WordParagraph> paragraphs,
                            DocPageLayout pageLayout,
                            String headerText,
                            String footerText) {
        this.pageNumber = pageNumber;
        this.paragraphs = new ArrayList<>(paragraphs);
        this.pageLayout = pageLayout;
        this.headerText = headerText;
        this.footerText = footerText;
    }

    // ======== getters ========

    /** 页码 (1-based) */
    public int getPageNumber() {
        return pageNumber;
    }

    /** 本页的所有段落（不可变副本） */
    public List<WordDocument.WordParagraph> getParagraphs() {
        return Collections.unmodifiableList(paragraphs);
    }

    /** 本页的段落数 */
    public int getParagraphCount() {
        return paragraphs.size();
    }

    /** 本页的页面布局（纸张尺寸、边距等）。多节文档中每页可能不同 */
    public DocPageLayout getPageLayout() {
        return pageLayout;
    }

    /** 本页页眉文本（可能为 null） */
    public String getHeaderText() {
        return headerText;
    }

    /** 本页页脚文本（可能为 null） */
    public String getFooterText() {
        return footerText;
    }

    // ======== 衍生方法 ========

    /**
     * 从本页的页面属性自动生成打印配置。
     * 边距转换为 ESC/P 可用的行数和列数。
     */
    public PrintConfig derivePrintConfig() {
        DocPageLayout pl = (pageLayout != null) ? pageLayout : DocPageLayout.defaults();
        return PrintConfig.builder()
                .physicalPaper(pl.toPaperSize())
                .topMargin(pl.topMarginLines(6))
                .bottomMargin(Math.max(1, pl.bottomMarginLines(6)))
                .leftMargin(pl.leftMarginCols(10))
                .cpi(10)
                .lineSpacing(PrintConfig.LineSpacing.ONE_SIXTH)
                .build();
    }

    @Override
    public String toString() {
        return String.format("WordPageInstance{page=%d, paragraphs=%d, layout=%s}",
                pageNumber, paragraphs.size(),
                pageLayout != null ? pageLayout.toString() : "null");
    }
}
