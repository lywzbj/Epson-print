package org.example.service;

import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * PDF 打印服务 — 将 .docx/.pdf 文件通过 LibreOffice 转换后发送到打印机。
 *
 * <p>利用操作系统的排版引擎（LibreOffice + 打印机驱动），
 * 彻底解决手动 ESC/P 命令拼接待的排版偏差问题。
 *
 * <h3>使用</h3>
 * <pre>
 *   PdfPrinter.print("E:\\tmp\\page1.docx", "DLQ-3500K");
 * </pre>
 */
public class PdfPrinter {

    private static final int CONVERT_TIMEOUT_SEC = 60;

    /**
     * 将 .docx 文件转为 PDF 并通过 Java Print Service 发送到指定打印机。
     *
     * @param filePath     .docx 或 .pdf 文件路径
     * @param printerName  打印机名称（部分匹配），为 null 时使用默认打印机
     */
    public static void print(String filePath, String printerName) throws IOException, PrintException {
        // Step 1: 如果是 .docx，用 LibreOffice 转 PDF
        String pdfPath = filePath;
        if (filePath.toLowerCase().endsWith(".docx")) {
            pdfPath = convertToPdf(filePath);
        }

        // Step 2: Java Print Service 打印 PDF
        printPdf(pdfPath, printerName);
    }

    /**
     * 调用 LibreOffice 将 .docx 转为 PDF。
     *
     * @param docxPath .docx 文件路径
     * @return 生成的 PDF 路径（与源文件同目录同名 .pdf）
     */
    public static String convertToPdf(String docxPath) throws IOException {
        File docxFile = new File(docxPath);
        if (!docxFile.exists()) {
            throw new IOException("文件不存在: " + docxPath);
        }

        String outDir = docxFile.getParent();
        String pdfName = docxFile.getName().replaceAll("\\.docx$", ".pdf");
        String pdfPath = outDir + File.separator + pdfName;

        // 删除旧的 PDF（如果存在）
        Files.deleteIfExists(Path.of(pdfPath));

        ProcessBuilder pb = new ProcessBuilder(
                "soffice",
                "--headless",
                "--norestore",
                "--convert-to", "pdf",
                "--outdir", outDir,
                docxPath
        );

        pb.redirectErrorStream(true);
        Process process = pb.start();

        try {
            boolean finished = process.waitFor(CONVERT_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("LibreOffice 转换超时 (" + CONVERT_TIMEOUT_SEC + "秒)");
            }
            if (process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new IOException("LibreOffice 转换失败 (exit=" + process.exitValue() + "): " + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("LibreOffice 转换被中断", e);
        }

        if (!new File(pdfPath).exists()) {
            throw new IOException("PDF 未生成: " + pdfPath);
        }

        return pdfPath;
    }

    /**
     * 通过 Java Print Service 打印 PDF 文件。
     *
     * @param pdfPath     PDF 文件路径
     * @param printerName 打印机名称（部分匹配），为 null 时使用默认打印机
     */
    public static void printPdf(String pdfPath, String printerName)
            throws PrintException, IOException {
        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists()) {
            throw new IOException("PDF 文件不存在: " + pdfPath);
        }

        // 查找打印机
        PrintService ps = findPrinter(printerName);
        if (ps == null) {
            throw new PrintException("未找到打印机: " +
                    (printerName != null ? printerName : "(默认)"));
        }

        System.out.println("  打印机: " + ps.getName());
        System.out.println("  文件: " + pdfPath);

        // 提交打印任务
        try (FileInputStream fis = new FileInputStream(pdfFile)) {
            Doc pdfDoc = new SimpleDoc(fis, DocFlavor.INPUT_STREAM.PDF, null);
            DocPrintJob job = ps.createPrintJob();

            PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
            attrs.add(new Copies(1));

            job.print(pdfDoc, attrs);
            System.out.println("  打印任务已提交");
        }
    }

    /**
     * 查找打印机。支持部分名称匹配。
     */
    private static PrintService findPrinter(String printerName) {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(
                DocFlavor.INPUT_STREAM.PDF, null);

        if (services.length == 0) {
            // 回退：查找任何接受 AUTOSENSE 的打印机
            services = PrintServiceLookup.lookupPrintServices(
                    DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        }

        if (printerName == null || printerName.isEmpty()) {
            PrintService def = PrintServiceLookup.lookupDefaultPrintService();
            if (def != null) return def;
            return (services.length > 0) ? services[0] : null;
        }

        String lower = printerName.toLowerCase();
        for (PrintService ps : services) {
            if (ps.getName().toLowerCase().contains(lower)) {
                return ps;
            }
        }
        return null;
    }
}
