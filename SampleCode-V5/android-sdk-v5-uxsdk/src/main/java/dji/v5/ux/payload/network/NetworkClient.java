package dji.v5.ux.payload.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import dji.v5.utils.common.ContextUtil;

/**
 * 网络客户端类，用于连接到PC服务器并发送数据
 */
public class NetworkClient {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final String DEFAULT_HOST = "192.168.3.31";

    private final Object stateLock = new Object();
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private volatile String host = DEFAULT_HOST;
    private volatile int port = 8888;
    private volatile Socket socket;
    private volatile OutputStream outputStream;
    private volatile boolean connected = false;
    private volatile NetworkCallback callback;

    public interface NetworkCallback {
        void onConnected();
        void onDisconnected();
        void onError(String error);
        void onDataSent(int bytes);
    }

    public NetworkClient() {
    }

    public void setCallback(NetworkCallback callback) {
        this.callback = callback;
    }

    public void setServerAddress(String host, int port) {
        this.host = host == null ? "" : host.trim();
        this.port = port;
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
                disconnectInternal(true);
            }
        });
    }

    public void sendData(final String data) {
        executeSafely(new Runnable() {
            @Override
            public void run() {
                sendDataInternal(data == null ? "" : data);
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

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public void shutdown() {
        disconnect();
        executorService.shutdown();
    }

    private void connectInternal() {
        String targetHost = host == null ? "" : host.trim();
        int targetPort = port;

        if (targetHost.isEmpty()) {
            notifyError("连接失败: 服务器地址为空");
            return;
        }
        if (targetPort <= 0 || targetPort > 65535) {
            notifyError("连接失败: 端口号无效(" + targetPort + ")");
            return;
        }

        disconnectInternal(false);

        Socket newSocket = new Socket();
        OutputStream newOutputStream = null;
        try {
            InetAddress targetAddress = InetAddress.getByName(targetHost);
            InetSocketAddress socketAddress = new InetSocketAddress(targetAddress, targetPort);

            newSocket.setKeepAlive(true);
            newSocket.setTcpNoDelay(true);
            newSocket.connect(socketAddress, CONNECT_TIMEOUT_MS);
            newOutputStream = newSocket.getOutputStream();

            synchronized (stateLock) {
                socket = newSocket;
                outputStream = newOutputStream;
                connected = true;
            }

            notifyConnected();
        } catch (IOException e) {
            closeQuietly(newOutputStream);
            closeQuietly(newSocket);
            clearConnectionState();
            notifyError(buildConnectErrorMessage(targetHost, targetPort, e));
        }
    }

    private void disconnectInternal(boolean notifyDisconnected) {
        Socket socketToClose;
        OutputStream streamToClose;

        synchronized (stateLock) {
            socketToClose = socket;
            streamToClose = outputStream;
            socket = null;
            outputStream = null;
            connected = false;
        }

        IOException closeError = null;

        try {
            if (streamToClose != null) {
                streamToClose.close();
            }
        } catch (IOException e) {
            closeError = e;
        }

        try {
            if (socketToClose != null) {
                socketToClose.close();
            }
        } catch (IOException e) {
            if (closeError == null) {
                closeError = e;
            }
        }

        if (closeError != null) {
            notifyError("断开连接异常: " + safeMessage(closeError));
            return;
        }

        if (notifyDisconnected) {
            notifyDisconnected();
        }
    }

    private void sendDataInternal(String data) {
        Socket currentSocket = socket;
        OutputStream currentOutputStream = outputStream;
        if (!isSocketReady(currentSocket, currentOutputStream)) {
            notifyError(buildNotConnectedMessage());
            return;
        }

        try {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            currentOutputStream.write(bytes);
            currentOutputStream.write('\n');
            currentOutputStream.flush();
            notifyDataSent(bytes.length);
        } catch (IOException e) {
            disconnectBrokenConnection();
            notifyError(buildSendErrorMessage(e));
        }
    }

    private boolean isSocketReady(Socket currentSocket, OutputStream currentOutputStream) {
        return connected
                && currentSocket != null
                && currentOutputStream != null
                && currentSocket.isConnected()
                && !currentSocket.isClosed()
                && !currentSocket.isOutputShutdown();
    }

    private void disconnectBrokenConnection() {
        Socket brokenSocket;
        OutputStream brokenStream;

        synchronized (stateLock) {
            brokenSocket = socket;
            brokenStream = outputStream;
            socket = null;
            outputStream = null;
            connected = false;
        }

        closeQuietly(brokenStream);
        closeQuietly(brokenSocket);
    }

    private void clearConnectionState() {
        synchronized (stateLock) {
            socket = null;
            outputStream = null;
            connected = false;
        }
    }

    private void executeSafely(Runnable task) {
        try {
            executorService.execute(task);
        } catch (RejectedExecutionException e) {
            notifyError("网络线程已关闭，无法继续执行操作");
        }
    }

    private String buildConnectErrorMessage(String targetHost, int targetPort, IOException error) {
        StringBuilder builder = new StringBuilder("连接失败: ");
        builder.append(mapConnectError(error));
        builder.append("。目标=").append(targetHost).append(":").append(targetPort);

        String networkSummary = buildNetworkSummary(targetHost);
        if (!networkSummary.isEmpty()) {
            builder.append("，").append(networkSummary);
        }

        return builder.toString();
    }

    private String buildSendErrorMessage(IOException error) {
        StringBuilder builder = new StringBuilder("数据发送失败: ");
        builder.append(mapSendError(error));

        String networkSummary = buildNetworkSummary(host);
        if (!networkSummary.isEmpty()) {
            builder.append("，").append(networkSummary);
        }

        return builder.toString();
    }

    private String buildNotConnectedMessage() {
        StringBuilder builder = new StringBuilder("网络未连接");
        if (host != null && !host.trim().isEmpty() && port > 0) {
            builder.append("，目标=").append(host.trim()).append(":").append(port);
        }

        String networkSummary = buildNetworkSummary(host);
        if (!networkSummary.isEmpty()) {
            builder.append("，").append(networkSummary);
        }

        return builder.toString();
    }

    private String mapConnectError(IOException error) {
        if (error instanceof SocketTimeoutException) {
            return "连接超时，请确认服务器地址可达且端口已开放";
        }
        if (error instanceof UnknownHostException) {
            return "服务器地址无法解析";
        }
        if (error instanceof NoRouteToHostException) {
            return "没有到目标主机的路由，请检查遥控器与PC是否处于同一路由可达网络";
        }
        if (error instanceof ConnectException) {
            String message = safeMessage(error).toLowerCase(Locale.US);
            if (message.contains("refused")) {
                return "服务器拒绝连接，请确认PC已监听该端口且防火墙已放行";
            }
            if (message.contains("unreachable")) {
                return "目标主机不可达，请检查网络连通性和路由配置";
            }
            return "目标主机连接失败，请确认服务是否已启动";
        }
        if (error instanceof SocketException) {
            return "Socket异常: " + safeMessage(error);
        }
        return safeMessage(error);
    }

    private String mapSendError(IOException error) {
        if (error instanceof SocketException) {
            return "Socket连接已断开: " + safeMessage(error);
        }
        return safeMessage(error);
    }

    private String buildNetworkSummary(String targetHost) {
        Context context = ContextUtil.getContext();
        if (context == null) {
            return "";
        }

        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return "";
        }

        try {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) {
                return "当前没有活动网络";
            }

            NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(activeNetwork);
            LinkProperties linkProperties = connectivityManager.getLinkProperties(activeNetwork);

            StringBuilder builder = new StringBuilder();
            builder.append("当前网络=").append(getTransportName(capabilities));

            LinkAddress localIpv4 = findPrimaryIpv4(linkProperties);
            if (localIpv4 != null) {
                builder.append("，本机IP=")
                        .append(localIpv4.getAddress().getHostAddress())
                        .append("/")
                        .append(localIpv4.getPrefixLength());
            }

            String gateway = findGateway(linkProperties);
            if (!gateway.isEmpty()) {
                builder.append("，网关=").append(gateway);
            }

            String subnetHint = buildSubnetHint(localIpv4, targetHost);
            if (!subnetHint.isEmpty()) {
                builder.append("，").append(subnetHint);
            }

            return builder.toString();
        } catch (SecurityException ignored) {
            return "";
        }
    }

    private LinkAddress findPrimaryIpv4(LinkProperties linkProperties) {
        if (linkProperties == null) {
            return null;
        }

        List<LinkAddress> linkAddresses = linkProperties.getLinkAddresses();
        if (linkAddresses == null) {
            return null;
        }

        for (LinkAddress linkAddress : linkAddresses) {
            InetAddress address = linkAddress.getAddress();
            if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                return linkAddress;
            }
        }

        return null;
    }

    private String findGateway(LinkProperties linkProperties) {
        if (linkProperties == null) {
            return "";
        }

        List<RouteInfo> routes = linkProperties.getRoutes();
        if (routes == null) {
            return "";
        }

        for (RouteInfo route : routes) {
            InetAddress gateway = route.getGateway();
            if (gateway instanceof Inet4Address && !gateway.isAnyLocalAddress()) {
                return gateway.getHostAddress();
            }
        }

        return "";
    }

    private String buildSubnetHint(LinkAddress localIpv4, String targetHost) {
        if (localIpv4 == null || targetHost == null || targetHost.trim().isEmpty()) {
            return "";
        }

        try {
            InetAddress targetAddress = InetAddress.getByName(targetHost.trim());
            InetAddress localAddress = localIpv4.getAddress();
            if (!(targetAddress instanceof Inet4Address) || !(localAddress instanceof Inet4Address)) {
                return "";
            }

            if (localAddress.isSiteLocalAddress()
                    && targetAddress.isSiteLocalAddress()
                    && !isSameSubnet((Inet4Address) localAddress,
                    (Inet4Address) targetAddress,
                    localIpv4.getPrefixLength())) {
                return "目标IP=" + targetAddress.getHostAddress()
                        + " 与当前本机IP不在同一网段，通常需要把PC和遥控器接到同一路由可达网络";
            }
        } catch (UnknownHostException ignored) {
            return "";
        }

        return "";
    }

    private boolean isSameSubnet(Inet4Address localAddress, Inet4Address targetAddress, int prefixLength) {
        if (prefixLength < 0 || prefixLength > 32) {
            return false;
        }

        byte[] localBytes = localAddress.getAddress();
        byte[] targetBytes = targetAddress.getAddress();
        int remainingBits = prefixLength;

        for (int i = 0; i < localBytes.length && remainingBits > 0; i++) {
            int bitsToCompare = Math.min(remainingBits, 8);
            int mask = 0xFF << (8 - bitsToCompare);
            if ((localBytes[i] & mask) != (targetBytes[i] & mask)) {
                return false;
            }
            remainingBits -= bitsToCompare;
        }

        return true;
    }

    private String getTransportName(NetworkCapabilities capabilities) {
        if (capabilities == null) {
            return "UNKNOWN";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "WIFI";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return "ETHERNET";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "CELLULAR";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return "VPN";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return "BLUETOOTH";
        }
        return "OTHER";
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    private void closeQuietly(OutputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // Ignore close exception for cleanup path.
        }
    }

    private void closeQuietly(Socket currentSocket) {
        if (currentSocket == null) {
            return;
        }
        try {
            currentSocket.close();
        } catch (IOException ignored) {
            // Ignore close exception for cleanup path.
        }
    }

    private void notifyConnected() {
        NetworkCallback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onConnected();
        }
    }

    private void notifyDisconnected() {
        NetworkCallback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onDisconnected();
        }
    }

    private void notifyError(String error) {
        NetworkCallback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onError(error);
        }
    }

    private void notifyDataSent(int bytes) {
        NetworkCallback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onDataSent(bytes);
        }
    }
}
