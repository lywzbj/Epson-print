package org.example;

import org.example.command.EscpCommand;
import org.example.connection.JavaxPrintConnection;
import org.example.connection.PrinterConnection;
import org.example.service.PrinterService;

import java.io.IOException;

public class Client {

    private static final String PRINTER_NAME = "DLQ-3500";

    public static void main(String[] args) {

        PrinterConnection connection = new JavaxPrintConnection(PRINTER_NAME);
        PrinterService printer = new PrinterService(connection);
        try {
            printer.open();
            printer.feedLines(50);
            printer.enterChineseMode();
            printer.send(EscpCommand.doubleWidth(1)); // FS S 2 2
            printer.send(EscpCommand.doubleHeight(1)); // FS S 2 2
            printer.send(EscpCommand.setChineseCharacterSize(8,8));
            printer.send(EscpCommand.encodeChinese("你好"));
            printer.exitChineseMode();


        }catch (Exception e) {
            e.printStackTrace();
        }finally {
            try {
                printer.close();  // ★ 关键：close() 才真正提交打印任务
            } catch (IOException ignored) {
            }
        }










    }










}
