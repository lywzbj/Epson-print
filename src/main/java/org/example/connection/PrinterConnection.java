package org.example.connection;

import java.io.IOException;

/**
 * 打印机连接接口 — 抽象底层通信方式。
 * 支持网络端口、文件流、javax.print 等多种实现。
 */
public interface PrinterConnection {

    /**
     * 建立与打印机的连接。
     */
    void connect() throws IOException;

    /**
     * 断开连接并释放资源。
     */
    void disconnect() throws IOException;

    /**
     * 向打印机发送原始字节数据。
     * @param data 要发送的字节数组
     */
    void write(byte[] data) throws IOException;

    /**
     * 刷新输出缓冲区，确保数据立即发送。
     */
    void flush() throws IOException;

    /**
     * 检查当前是否已连接。
     * @return true 表示已连接
     */
    boolean isConnected();
}
