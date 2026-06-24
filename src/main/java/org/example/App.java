package org.example;

import org.example.demo.PrinterDemo;

/**
 * EPSON DLQ-3500K 打印机控制程序入口。
 *
 * 用法：
 *   java -jar Epson-print.jar                              → 默认演示
 *   java -jar Epson-print.jar word report.docx              → 打印 Word 文件
 *   java -jar Epson-print.jar word report.docx --pages=1-3  → 选择页打印
 *   java -jar Epson-print.jar word report.docx --lines=10-50 --skip-empty → 行范围+跳过空行
 *   java -jar Epson-print.jar word report.docx --skip-marker='###SKIP'     → 跳过标记行
 *
 * Word 打印选项:
 *   --pages=P1-P2,P4    页码范围 (1-based)
 *   --lines=L1-L2       行范围 (1-based)
 *   --skip-empty        跳过空行
 *   --skip-marker=MARK  跳过含标记文本的行
 *   --skip-line=1,3,5   跳过指定行号
 *   --lpp=66            每页行数 (默认 66)
 *
 * 连接方式（放在 word 前）:
 *   socket host [port]    网络连接
 *   file devPath          文件流连接
 *   javax "printerName"   javax.print API
 */
public class App {
    public static void main(String[] args) {
        PrinterDemo.main(args);
    }
}
