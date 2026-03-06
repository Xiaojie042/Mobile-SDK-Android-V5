package dji.v5.ux.payload.network;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 网络客户端类，用于连接到PC服务器并发送数据
 */
public class NetworkClient {
    
    private String host = "192.168.3.31";
    private int port = 8888;
    private Socket socket;
    private OutputStream outputStream;
    private boolean connected = false;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    public interface NetworkCallback {
        void onConnected();
        void onDisconnected();
        void onError(String error);
        void onDataSent(int bytes);
    }
    
    private NetworkCallback callback;
    
    public NetworkClient() {
    }
    
    public void setCallback(NetworkCallback callback) {
        this.callback = callback;
    }
    
    public void setServerAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }
    
    public void connect() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket(host, port);
                    outputStream = socket.getOutputStream();
                    connected = true;
                    
                    if (callback != null) {
                        callback.onConnected();
                    }
                } catch (IOException e) {
                    connected = false;
                    if (callback != null) {
                        callback.onError("连接失败: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    public void disconnect() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (outputStream != null) {
                        outputStream.close();
                        outputStream = null;
                    }
                    if (socket != null) {
                        socket.close();
                        socket = null;
                    }
                    connected = false;
                    
                    if (callback != null) {
                        callback.onDisconnected();
                    }
                } catch (IOException e) {
                    if (callback != null) {
                        callback.onError("断开连接异常: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    public void sendData(final String data) {
        if (!connected || outputStream == null) {
            if (callback != null) {
                callback.onError("网络未连接");
            }
            return;
        }
        
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] bytes = data.getBytes("UTF-8");
                    outputStream.write(bytes);
                    outputStream.write('\n'); // 添加换行符作为分隔符
                    outputStream.flush();
                    
                    if (callback != null) {
                        callback.onDataSent(bytes.length);
                    }
                } catch (IOException e) {
                    connected = false;
                    if (callback != null) {
                        callback.onError("数据发送失败: " + e.getMessage());
                    }
                }
            }
        });
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void shutdown() {
        disconnect();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}