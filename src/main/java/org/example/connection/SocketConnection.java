package org.example.connection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 通过 TCP/IP 网络端口连接打印机（标准 RAW 打印，端口 9100）。
 */
public class SocketConnection implements PrinterConnection {

    private final String host;
    private final int port;
    private final int timeout;

    private Socket socket;
    private OutputStream outputStream;

    /**
     * 创建网络连接，默认端口 9100。
     * @param host 打印机 IP 地址或主机名
     */
    public SocketConnection(String host) {
        this(host, 9100, 5000);
    }

    /**
     * 创建网络连接。
     * @param host    打印机 IP 地址或主机名
     * @param port    端口号（通常为 9100）
     * @param timeout 连接超时（毫秒）
     */
    public SocketConnection(String host, int port, int timeout) {
        this.host = host;
        this.port = port;
        this.timeout = timeout;
    }

    public SocketConnection(String host, int port) {
        this(host,port,5000);
    }

    @Override
    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeout);
        socket.setSoTimeout(timeout);
        outputStream = socket.getOutputStream();
        System.out.println("[SocketConnection] 已连接: " + host + ":" + port);
    }

    @Override
    public void disconnect() throws IOException {
        if (outputStream != null) {
            outputStream.close();
        }
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        System.out.println("[SocketConnection] 已断开");
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
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
}
