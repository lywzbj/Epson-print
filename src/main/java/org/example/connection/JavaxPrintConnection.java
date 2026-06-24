package org.example.connection;

import javax.print.*;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 通过 javax.print 标准 API 连接打印机。
 * 操作系统可自动发现已安装的打印机驱动，适用于 USB 打印机。
 *
 * 采用内部缓冲机制：所有 write() 的数据先缓存到内存，
 * 在 disconnect() 时一次性作为 RAW 打印任务提交，保证
 * ESC/P-K 命令序列的完整性。
 */
public class JavaxPrintConnection implements PrinterConnection {

    private final String printerName;
    private PrintService printService;
    private final ByteArrayOutputStream buffer;

    /**
     * 通过打印机名称（部分匹配）查找。
     * @param printerName 打印机名称关键词（如 "DLQ-3500"）
     */
    public JavaxPrintConnection(String printerName) {
        this.printerName = printerName;
        this.buffer = new ByteArrayOutputStream();
    }

    @Override
    public void connect() throws IOException {
        buffer.reset();

        PrintService[] services = PrintServiceLookup.lookupPrintServices(
                DocFlavor.BYTE_ARRAY.AUTOSENSE, null);

        for (PrintService service : services) {
            if (service.getName().contains(printerName)) {
                printService = service;
                System.out.println("[JavaxPrintConnection] 找到打印机: " + service.getName());
                return;
            }
        }

        // 没有精确匹配，尝试默认打印机
        printService = PrintServiceLookup.lookupDefaultPrintService();
        if (printService == null) {
            throw new IOException("未找到任何打印机，请确认打印机已安装并开机");
        }
        System.out.println("[JavaxPrintConnection] 使用默认打印机: " + printService.getName());
    }

    @Override
    public void disconnect() throws IOException {
        if (printService == null) {
            return;
        }

        // 将缓冲区中的所有数据一次性提交打印
        byte[] allData = buffer.toByteArray();
        if (allData.length > 0) {
            System.out.println("[JavaxPrintConnection] 提交打印任务 (" + allData.length + " bytes)...");

            DocPrintJob job = printService.createPrintJob();
            Doc doc = new SimpleDoc(allData,
                    DocFlavor.BYTE_ARRAY.AUTOSENSE, null);

            HashPrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
            attrs.add(new Copies(1));

            try {
                job.print(doc, attrs);
                System.out.println("[JavaxPrintConnection] 打印任务已提交");
            } catch (PrintException e) {
                throw new IOException("打印失败: " + e.getMessage(), e);
            }
        }

        buffer.reset();
        printService = null;
        System.out.println("[JavaxPrintConnection] 已断开");
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (printService == null) {
            throw new IOException("未连接打印机，请先调用 connect()");
        }
        buffer.write(data);
    }

    @Override
    public void flush() {
        // 数据已在缓冲区中，不需要立即刷新
    }

    @Override
    public boolean isConnected() {
        return printService != null;
    }
}
