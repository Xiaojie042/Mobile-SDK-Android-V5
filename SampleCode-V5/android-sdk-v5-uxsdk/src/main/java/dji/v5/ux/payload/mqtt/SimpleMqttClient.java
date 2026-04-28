package dji.v5.ux.payload.mqtt;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class SimpleMqttClient {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 60;

    private final Object stateLock = new Object();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private volatile String host = "";
    private volatile int port = 1883;
    private volatile String clientId = "";
    private volatile String username = "";
    private volatile String password = "";
    private volatile Socket socket;
    private volatile InputStream inputStream;
    private volatile OutputStream outputStream;
    private volatile boolean connected = false;
    private volatile Callback callback;

    public interface Callback {
        void onConnected();

        void onDisconnected();

        void onError(String error);

        void onDataPublished(String topic, int bytes);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setBroker(String host, int port, String clientId, String username, String password) {
        this.host = host == null ? "" : host.trim();
        this.port = port;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
    }

    public void connect() {
        executeSafely(new Runnable() {
            @Override
            public void run() {
                connectInternal();
            }
        });
    }

    public void disconnect() {
        executeSafely(new Runnable() {
            @Override
            public void run() {
                disconnectInternal(true, true);
            }
        });
    }

    public void publish(final String topic, final String payload) {
        executeSafely(new Runnable() {
            @Override
            public void run() {
                publishInternal(topic, payload);
            }
        });
    }

    public boolean isConnected() {
        Socket currentSocket = socket;
        return connected
                && currentSocket != null
                && currentSocket.isConnected()
                && !currentSocket.isClosed()
                && !currentSocket.isOutputShutdown();
    }

    public void shutdown() {
        disconnect();
        executorService.shutdown();
    }

    private void connectInternal() {
        String targetHost = host == null ? "" : host.trim();
        int targetPort = port;
        String targetClientId = clientId == null ? "" : clientId.trim();

        if (targetHost.isEmpty()) {
            notifyError("MQTT连接失败: 服务器地址为空");
            return;
        }
        if (targetPort <= 0 || targetPort > 65535) {
            notifyError("MQTT连接失败: 端口无效(" + targetPort + ")");
            return;
        }
        if (targetClientId.isEmpty()) {
            notifyError("MQTT连接失败: Client ID为空");
            return;
        }

        disconnectInternal(false, true);

        Socket newSocket = new Socket();
        InputStream newInputStream = null;
        OutputStream newOutputStream = null;
        try {
            InetAddress targetAddress = InetAddress.getByName(targetHost);
            newSocket.setKeepAlive(true);
            newSocket.setTcpNoDelay(true);
            newSocket.connect(new InetSocketAddress(targetAddress, targetPort), CONNECT_TIMEOUT_MS);
            newSocket.setSoTimeout(READ_TIMEOUT_MS);
            newInputStream = newSocket.getInputStream();
            newOutputStream = newSocket.getOutputStream();

            byte[] connectPacket = buildConnectPacket(targetClientId);
            newOutputStream.write(connectPacket);
            newOutputStream.flush();
            readConnAck(newInputStream);

            synchronized (stateLock) {
                socket = newSocket;
                inputStream = newInputStream;
                outputStream = newOutputStream;
                connected = true;
            }
            notifyConnected();
        } catch (IOException e) {
            closeQuietly(newInputStream);
            closeQuietly(newOutputStream);
            closeQuietly(newSocket);
            clearConnectionState();
            notifyError("MQTT连接失败: " + safeMessage(e));
        }
    }

    private void publishInternal(String topic, String payload) {
        String targetTopic = topic == null ? "" : topic.trim();
        if (targetTopic.isEmpty()) {
            notifyError("MQTT发布失败: Topic为空");
            return;
        }

        Socket currentSocket = socket;
        OutputStream currentOutputStream = outputStream;
        if (!isSocketReady(currentSocket, currentOutputStream)) {
            notifyError("MQTT发布失败: 未连接服务器");
            return;
        }

        try {
            byte[] payloadBytes = (payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8);
            byte[] topicBytes = targetTopic.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream variableHeaderAndPayload = new ByteArrayOutputStream();
            writeUtf(variableHeaderAndPayload, topicBytes);
            variableHeaderAndPayload.write(payloadBytes);

            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            packet.write(0x30);
            writeRemainingLength(packet, variableHeaderAndPayload.size());
            packet.write(variableHeaderAndPayload.toByteArray());

            currentOutputStream.write(packet.toByteArray());
            currentOutputStream.flush();
            notifyDataPublished(targetTopic, payloadBytes.length);
        } catch (IOException e) {
            disconnectBrokenConnection();
            notifyError("MQTT发布失败: " + safeMessage(e));
        }
    }

    private byte[] buildConnectPacket(String targetClientId) throws IOException {
        ByteArrayOutputStream variableHeader = new ByteArrayOutputStream();
        writeUtf(variableHeader, "MQTT".getBytes(StandardCharsets.UTF_8));
        variableHeader.write(0x04);

        int connectFlags = 0x02;
        boolean hasUsername = username != null && !username.trim().isEmpty();
        boolean hasPassword = password != null && !password.isEmpty();
        if (hasUsername) {
            connectFlags |= 0x80;
        }
        if (hasPassword) {
            connectFlags |= 0x40;
        }
        variableHeader.write(connectFlags);
        variableHeader.write((DEFAULT_KEEP_ALIVE_SECONDS >> 8) & 0xFF);
        variableHeader.write(DEFAULT_KEEP_ALIVE_SECONDS & 0xFF);

        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeUtf(payload, targetClientId.getBytes(StandardCharsets.UTF_8));
        if (hasUsername) {
            writeUtf(payload, username.trim().getBytes(StandardCharsets.UTF_8));
        }
        if (hasPassword) {
            writeUtf(payload, password.getBytes(StandardCharsets.UTF_8));
        }

        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(0x10);
        writeRemainingLength(packet, variableHeader.size() + payload.size());
        packet.write(variableHeader.toByteArray());
        packet.write(payload.toByteArray());
        return packet.toByteArray();
    }

    private void readConnAck(InputStream stream) throws IOException {
        int header = stream.read();
        if (header != 0x20) {
            throw new IOException("服务器未返回CONNACK");
        }

        int remainingLength = readRemainingLength(stream);
        if (remainingLength != 2) {
            throw new IOException("CONNACK长度异常");
        }

        int ackFlags = stream.read();
        int returnCode = stream.read();
        if (ackFlags < 0 || returnCode < 0) {
            throw new IOException("CONNACK读取失败");
        }
        if (returnCode != 0) {
            throw new IOException("服务器拒绝连接，返回码: " + returnCode);
        }
    }

    private int readRemainingLength(InputStream stream) throws IOException {
        int multiplier = 1;
        int value = 0;
        int encodedByte;
        int loops = 0;
        do {
            encodedByte = stream.read();
            if (encodedByte < 0) {
                throw new IOException("读取剩余长度失败");
            }
            value += (encodedByte & 127) * multiplier;
            multiplier *= 128;
            loops++;
            if (loops > 4) {
                throw new IOException("剩余长度编码异常");
            }
        } while ((encodedByte & 128) != 0);
        return value;
    }

    private void writeRemainingLength(ByteArrayOutputStream outputStream, int length) {
        int value = length;
        do {
            int encodedByte = value % 128;
            value = value / 128;
            if (value > 0) {
                encodedByte = encodedByte | 128;
            }
            outputStream.write(encodedByte);
        } while (value > 0);
    }

    private void writeUtf(ByteArrayOutputStream outputStream, byte[] bytes) throws IOException {
        int length = bytes == null ? 0 : bytes.length;
        outputStream.write((length >> 8) & 0xFF);
        outputStream.write(length & 0xFF);
        if (length > 0) {
            outputStream.write(bytes);
        }
    }

    private void disconnectInternal(boolean notifyDisconnected, boolean sendDisconnectPacket) {
        Socket socketToClose;
        InputStream streamToClose;
        OutputStream outputToClose;

        synchronized (stateLock) {
            socketToClose = socket;
            streamToClose = inputStream;
            outputToClose = outputStream;
            socket = null;
            inputStream = null;
            outputStream = null;
            connected = false;
        }

        if (sendDisconnectPacket && outputToClose != null) {
            try {
                outputToClose.write(0xE0);
                outputToClose.write(0x00);
                outputToClose.flush();
            } catch (IOException ignored) {
                // Socket is being closed; the disconnect packet is best effort.
            }
        }

        closeQuietly(outputToClose);
        closeQuietly(streamToClose);
        closeQuietly(socketToClose);

        if (notifyDisconnected) {
            notifyDisconnected();
        }
    }

    private void disconnectBrokenConnection() {
        disconnectInternal(false, false);
    }

    private boolean isSocketReady(Socket currentSocket, OutputStream currentOutputStream) {
        return connected
                && currentSocket != null
                && currentOutputStream != null
                && currentSocket.isConnected()
                && !currentSocket.isClosed()
                && !currentSocket.isOutputShutdown();
    }

    private void clearConnectionState() {
        synchronized (stateLock) {
            socket = null;
            inputStream = null;
            outputStream = null;
            connected = false;
        }
    }

    private void executeSafely(Runnable task) {
        try {
            executorService.execute(task);
        } catch (RejectedExecutionException e) {
            notifyError("MQTT线程已关闭，无法继续执行操作");
        }
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Ignore close errors.
        }
    }

    private void notifyConnected() {
        Callback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onConnected();
        }
    }

    private void notifyDisconnected() {
        Callback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onDisconnected();
        }
    }

    private void notifyError(String error) {
        Callback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onError(error == null ? "Unknown MQTT error" : error);
        }
    }

    private void notifyDataPublished(String topic, int bytes) {
        Callback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onDataPublished(topic, bytes);
        }
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty()
                ? throwable.getClass().getSimpleName()
                : message;
    }
}
