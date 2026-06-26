package org.example.word;

import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 从已有 .docx 文件中提取指定页面范围，输出为新的 .docx 文件。
 *
 * <p>采用 ZIP 层直裁方案：打开 .docx（ZIP 格式），裁剪 {@code word/document.xml}
 * 中的 body 元素范围，保留所有其他部件（样式、图片、编号定义等）原样输出。
 * 这确保新文档的格式与原始文档 100% 一致。
 *
 * <h3>使用</h3>
 * <pre>
 *   WordDocument doc = WordDocument.load("report.docx");
 *   WordPageExporter.exportPage(doc, 1, "E:\\tmp\\page1.docx");
 * </pre>
 */
public class WordPageExporter {

    /**
     * 导出文档的指定页到新 .docx 文件。
     *
     * @param doc        已加载的 WordDocument（含页面边界信息）
     * @param page       目标页码 (1-based)
     * @param outputPath 输出 .docx 路径
     */
    public static void exportPage(WordDocument doc, int page, String outputPath) throws IOException {
        exportPages(doc, Collections.singletonList(page), outputPath);
    }

    /**
     * 导出文档的指定页码范围到新 .docx 文件。
     *
     * @param doc        已加载的 WordDocument
     * @param pages      目标页码列表 (1-based)
     * @param outputPath 输出 .docx 路径
     */
    public static void exportPages(WordDocument doc, List<Integer> pages, String outputPath) throws IOException {
        String sourcePath = doc.getFilePath();
        if (sourcePath == null || sourcePath.isEmpty() || sourcePath.equals("(stream)")) {
            throw new IOException("无法从 InputStream 加载的文档导出页面");
        }

        // 收集目标页的段落范围（全局段落索引，0-based）
        int paraStart = Integer.MAX_VALUE;
        int paraEnd = 0;
        for (int page : pages) {
            int start = doc.getPageStartLine(page) - 1;  // 1-based → 0-based
            int end   = doc.getPageEndLine(page);         // 1-based, exclusive after conversion
            if (start < 0) continue;
            paraStart = Math.min(paraStart, start);
            paraEnd = Math.max(paraEnd, end);
        }
        if (paraStart >= paraEnd) {
            throw new IOException("目标页无内容");
        }

        // ZIP 直裁：读原始 .docx → 裁剪 document.xml → 写新 .docx
        try (ZipFile zipIn = new ZipFile(sourcePath)) {
            // 读取原始 document.xml
            ZipEntry docEntry = zipIn.getEntry("word/document.xml");
            if (docEntry == null) throw new IOException("document.xml 未找到");
            byte[] docBytes = zipIn.getInputStream(docEntry).readAllBytes();
            String docXml = new String(docBytes, StandardCharsets.UTF_8);

            // 裁剪 body 元素
            String croppedXml = cropBodyElements(docXml, paraStart, paraEnd);

            // 写出新 .docx
            writeNewDocx(zipIn, croppedXml, outputPath);
        }
    }

    /**
     * 裁剪 document.xml 中的 body 段落/表格。
     *
     * <p>遍历 {@code <w:body>} 的直接子元素（{@code <w:p>} 和 {@code <w:tbl>}），
     * 按传入的段落索引范围选择需要的元素。表格行已被展开为多个段落，
     * 此处按段落索引区间选取对应的 body 元素。
     *
     * @param xml        原始 document.xml 文本
     * @param pdfSt    目标起始段落索引 (0-based, inclusive)
     * @param paraEnd   目标结束段落索引 (0-based, exclusive)
     */
    private static String cropBodyElements(String xml, int pdfSt, int paraEnd)
            throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document dom = factory.newDocumentBuilder()
                    .parse(new org.xml.sax.InputSource(new StringReader(xml)));

            // 找到 <w:body> 元素
            Node body = findBodyElement(dom);
            if (body == null) throw new IOException("document.xml 中未找到 <w:body>");

            // 收集 body 的直接子元素 (w:p, w:tbl, w:sectPr)
            List<Node> children = new ArrayList<>();
            Node child = body.getFirstChild();
            while (child != null) {
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    children.add(child);
                }
                child = child.getNextSibling();
            }

            // 按段落索引范围选择 body 元素。
            // 关键：一个 <w:tbl> 可能展开为多个段落行，需确保包含表格的所有段落。
            // 策略：计算每个 body 元素贡献的段落数，按累计索引选择。
            int accumulated = 0;
            List<Node> selected = new ArrayList<>();
            boolean inRange = false;

            // 预计算每个 body 元素的段落贡献数
            int[] paraCounts = new int[children.size()];
            for (int i = 0; i < children.size(); i++) {
                paraCounts[i] = countParagraphs(children.get(i));
            }

            for (int i = 0; i < children.size(); i++) {
                Node n = children.get(i);
                int count = paraCounts[i];

                // 判断该 body 元素是否与目标段落范围有交集
                boolean intersects = (accumulated + count > pdfSt) && (accumulated < paraEnd);

                if (intersects) {
                    selected.add(n);
                    inRange = true;
                } else if (inRange) {
                    // 已超出范围，停止
                    // 但需要保留后续的 <w:sectPr>
                    if (isSectPr(n)) {
                        selected.add(n);
                    }
                    break;
                }

                accumulated += count;
            }

            // 确保 <w:sectPr> 被保留（页面布局）
            if (selected.stream().noneMatch(n -> isSectPr(n))) {
                for (int i = children.size() - 1; i >= 0; i--) {
                    if (isSectPr(children.get(i))) {
                        selected.add(children.get(i));
                        break;
                    }
                }
            }

            // 重建 body 内容
            while (body.getFirstChild() != null) {
                body.removeChild(body.getFirstChild());
            }
            for (Node n : selected) {
                body.appendChild(n.cloneNode(true));
            }

            // 序列化回 XML 字符串
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty("omit-xml-declaration", "yes");
            StringWriter sw = new StringWriter();
            transformer.transform(new DOMSource(dom), new StreamResult(sw));
            return sw.toString();

        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("解析 document.xml 失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IOException("XML 处理失败: " + e.getMessage(), e);
        }
    }

    /** 统计元素包含的段落数：w:p → 1, w:tbl → 每行 1 */
    private static int countParagraphs(Node element) {
        String tag = element.getLocalName();
        if (tag == null) return 0;
        if ("p".equals(tag)) return 1;
        if ("tbl".equals(tag)) {
            int rows = 0;
            NodeList rows_list = ((Element) element).getElementsByTagNameNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "tr");
            if (rows_list.getLength() > 0) return rows_list.getLength();
            // fallback: count w:tr without namespace
            for (Node c = element.getFirstChild(); c != null; c = c.getNextSibling()) {
                if (c.getNodeType() == Node.ELEMENT_NODE && "tr".equals(c.getLocalName())) rows++;
            }
            return Math.max(1, rows);
        }
        return 0;
    }

    private static boolean isSectPr(Node n) {
        return n.getNodeType() == Node.ELEMENT_NODE && "sectPr".equals(n.getLocalName());
    }

    private static Node findBodyElement(Document dom) {
        NodeList bodies = dom.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "body");
        if (bodies.getLength() > 0) return bodies.item(0);

        // fallback: search w:body without namespace awareness
        NodeList all = dom.getElementsByTagName("body");
        if (all.getLength() > 0) return all.item(0);

        return null;
    }

    /**
     * 将原始 .docx ZIP 中的所有条目复制到新 ZIP，仅替换 word/document.xml。
     */
    private static void writeNewDocx(ZipFile source, String newDocumentXml,
                                     String outputPath) throws IOException {
        Path out = Path.of(outputPath);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(out))) {
            Enumeration<? extends ZipEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if ("word/document.xml".equals(name)) {
                    // 使用裁剪后的 XML
                    zos.putNextEntry(new ZipEntry(name));
                    zos.write(newDocumentXml.getBytes(StandardCharsets.UTF_8));
                } else {
                    // 原样复制
                    zos.putNextEntry(new ZipEntry(name));
                    InputStream is = source.getInputStream(entry);
                    is.transferTo(zos);
                    is.close();
                }
                zos.closeEntry();
            }
        }
    }
}
