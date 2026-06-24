package org.example.connection;

import java.io.*;

/**
 * 通过文件流连接打印机（LPT 并口 / USB 虚拟端口）。
 *
 * Windows 示例路径： "LPT1", "\\\\.\\USB001"
 * Linux 示例路径：   "/dev/usb/lp0", "/dev/parallel0"
 */
public class FileStreamConnection implements PrinterConnection {

    private final String devicePath;
    private OutputStream outputStream;

    /**
     * 创建文件流连接。
     * @param devicePath 设备文件路径
     */
    public FileStreamConnection(String devicePath) {
        this.devicePath = devicePath;
    }

    @Override
    public void connect() throws IOException {
        outputStream = new FileOutputStream(devicePath);
        System.out.println("[FileStreamConnection] 已连接: " + devicePath);
    }

    @Override
    public void disconnect() throws IOException {
        if (outputStream != null) {
            outputStream.close();
            outputStream = null;
        }
        System.out.println("[FileStreamConnection] 已断开");
    }

    @Override
    public void write(byte[] data) throws IOException {
        if (outputStream == null) {
            throw new IOException("未连接打印机，请先调用 connect()");
        }
        outputStream.write(data);
    }

    @Override
    public void flush() throws IOException {
        if (outputStream != null) {
            outputStream.flush();
        }
    }

    @Override
    public boolean isConnected() {
        return outputStream != null;
    }
}
