package dji.v5.ux.payload.psdk;

import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import dji.v5.manager.aircraft.payload.PayloadCenter;
import dji.v5.manager.aircraft.payload.PayloadIndexType;
import dji.v5.manager.aircraft.payload.data.PayloadBasicInfo;
import dji.v5.manager.aircraft.payload.listener.PayloadBasicInfoListener;
import dji.v5.manager.aircraft.payload.listener.PayloadDataListener;
import dji.v5.manager.interfaces.IPayloadManager;
import dji.v5.utils.common.LogUtils;
import dji.v5.ux.payload.network.NetworkClient;

public final class PayloadForwardingManager {

    private static final String TAG = "PayloadForwardMgr";
    private static final String DEFAULT_HOST = "192.168.3.31";
    private static final int DEFAULT_PORT = 8888;
    private static final int MAX_LOG_LENGTH = 5000;
    private static final String STATUS_CONNECTED = "网络状态: 已连接";
    private static final String STATUS_CONNECTING = "网络状态: 连接中...";
    private static final String STATUS_DISCONNECTED = "网络状态: 未连接";
    private static final String STATUS_ERROR = "网络状态: 连接错误";

    private static final PayloadForwardingManager INSTANCE = new PayloadForwardingManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final NetworkClient networkClient = new NetworkClient();
    private final StringBuilder messageBuffer = new StringBuilder();
    private final StringBuilder psdkDataBuffer = new StringBuilder();

    private Listener listener;
    private PayloadIndexType selectedPayloadIndex = PayloadIndexType.PORT_1;
    private String serverHost = DEFAULT_HOST;
    private int serverPort = DEFAULT_PORT;
    private String connectionStatus = STATUS_DISCONNECTED;
    private PayloadBasicInfo currentPayloadInfo;
    private String payloadInfoText = "";
    private PayloadBasicInfoListener payloadBasicInfoListener;
    private PayloadDataListener payloadDataListener;

    public interface Listener {
        void onStateChanged(StateSnapshot snapshot);
    }

    public static final class StateSnapshot {
        private final String serverHost;
        private final int serverPort;
        private final PayloadIndexType selectedPayloadIndex;
        private final boolean connected;
        private final String connectionStatus;
        private final String payloadInfoText;
        private final String displayLog;

        private StateSnapshot(String serverHost,
                              int serverPort,
                              PayloadIndexType selectedPayloadIndex,
                              boolean connected,
                              String connectionStatus,
                              String payloadInfoText,
                              String displayLog) {
            this.serverHost = serverHost;
            this.serverPort = serverPort;
            this.selectedPayloadIndex = selectedPayloadIndex;
            this.connected = connected;
            this.connectionStatus = connectionStatus;
            this.payloadInfoText = payloadInfoText;
            this.displayLog = displayLog;
        }

        public String getServerHost() {
            return serverHost;
        }

        public int getServerPort() {
            return serverPort;
        }

        public PayloadIndexType getSelectedPayloadIndex() {
            return selectedPayloadIndex;
        }

        public boolean isConnected() {
            return connected;
        }

        public String getConnectionStatus() {
            return connectionStatus;
        }

        public String getPayloadInfoText() {
            return payloadInfoText;
        }

        public String getDisplayLog() {
            return displayLog;
        }
    }

    private PayloadForwardingManager() {
        networkClient.setServerAddress(serverHost, serverPort);
        networkClient.setCallback(new NetworkClient.NetworkCallback() {
            @Override
            public void onConnected() {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        appendMessage("✓ 服务器连接成功");
                        connectionStatus = STATUS_CONNECTED;
                        dispatchState();
                    }
                });
            }

            @Override
            public void onDisconnected() {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        appendMessage("📲 服务器连接已断开");
                        connectionStatus = STATUS_DISCONNECTED;
                        dispatchState();
                    }
                });
            }

            @Override
            public void onError(final String error) {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        appendMessage("✖ " + error);
                        connectionStatus = STATUS_ERROR;
                        dispatchState();
                    }
                });
            }

            @Override
            public void onDataSent(final int bytes) {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        appendMessage("📤 数据已转发: " + bytes + " 字节");
                        dispatchState();
                    }
                });
            }
        });

        initPayloadListeners();
        registerPayloadListeners();
        updatePayloadInfo();
    }

    public static PayloadForwardingManager getInstance() {
        return INSTANCE;
    }

    public void bindListener(final Listener listener) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                PayloadForwardingManager.this.listener = listener;
                dispatchState();
            }
        });
    }

    public void unbindListener(final Listener listener) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (PayloadForwardingManager.this.listener == listener) {
                    PayloadForwardingManager.this.listener = null;
                }
            }
        });
    }

    public StateSnapshot getStateSnapshot() {
        return buildSnapshot();
    }

    public void selectPayload(final PayloadIndexType payloadIndexType) {
        if (payloadIndexType == null) {
            return;
        }

        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (selectedPayloadIndex == payloadIndexType) {
                    return;
                }

                selectedPayloadIndex = payloadIndexType;
                appendMessage("选择 PSDK: " + payloadIndexType.name());
                LogUtils.d(TAG, "切换到挂载点: " + payloadIndexType.name());
                reRegisterPayloadListeners();
                updatePayloadInfo();
                dispatchState();
            }
        });
    }

    public void connect(final String host, final int port) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                serverHost = host == null ? "" : host.trim();
                serverPort = port;
                networkClient.setServerAddress(serverHost, serverPort);
                appendMessage("正在连接到服务器: " + serverHost + ":" + serverPort);
                connectionStatus = STATUS_CONNECTING;
                dispatchState();
                networkClient.connect();
            }
        });
    }

    public void disconnect() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                networkClient.disconnect();
            }
        });
    }

    public void clearMessages() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                messageBuffer.setLength(0);
                psdkDataBuffer.setLength(0);
                appendMessage("消息已清空");
                dispatchState();
            }
        });
    }

    private void initPayloadListeners() {
        payloadBasicInfoListener = new PayloadBasicInfoListener() {
            @Override
            public void onPayloadBasicInfoUpdate(final PayloadBasicInfo payloadBasicInfo) {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        currentPayloadInfo = payloadBasicInfo;
                        payloadInfoText = buildPayloadInfoText(payloadBasicInfo);
                        if (payloadBasicInfo != null && payloadBasicInfo.isConnected()) {
                            appendMessage("📡 PSDK 设备已连接: " + payloadBasicInfo.getPayloadProductName());
                        }
                        dispatchState();
                    }
                });
            }
        };

        payloadDataListener = new PayloadDataListener() {
            @Override
            public void onDataFromPayloadUpdate(byte[] data) {
                if (data == null || data.length == 0 || containsOnlyLineBreaks(data)) {
                    return;
                }

                final String dataString = new String(data);
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        appendPsdkData(dataString);
                        dispatchState();

                        if (networkClient.isConnected()) {
                            networkClient.sendData(constructJsonData(dataString));
                        }
                    }
                });
            }
        };
    }

    private void registerPayloadListeners() {
        Map<PayloadIndexType, IPayloadManager> payloadManagerMap = PayloadCenter.getInstance().getPayloadManager();
        if (payloadManagerMap == null) {
            return;
        }

        IPayloadManager payloadManager = payloadManagerMap.get(selectedPayloadIndex);
        if (payloadManager == null) {
            return;
        }

        try {
            payloadManager.getClass().getMethod("addPayloadBasicInfoListener", PayloadBasicInfoListener.class)
                    .invoke(payloadManager, payloadBasicInfoListener);
            payloadManager.getClass().getMethod("addPayloadDataListener", PayloadDataListener.class)
                    .invoke(payloadManager, payloadDataListener);
            LogUtils.d(TAG, "注册 Payload 监听器: " + selectedPayloadIndex.name());
        } catch (Exception e) {
            LogUtils.e(TAG, "注册监听器失败: " + e.getMessage());
        }
    }

    private void unregisterPayloadListeners() {
        if (payloadBasicInfoListener == null || payloadDataListener == null) {
            return;
        }

        Map<PayloadIndexType, IPayloadManager> payloadManagerMap = PayloadCenter.getInstance().getPayloadManager();
        if (payloadManagerMap == null) {
            return;
        }

        for (PayloadIndexType indexType : PayloadIndexType.values()) {
            IPayloadManager payloadManager = payloadManagerMap.get(indexType);
            if (payloadManager == null) {
                continue;
            }

            try {
                payloadManager.getClass().getMethod("removePayloadBasicInfoListener", PayloadBasicInfoListener.class)
                        .invoke(payloadManager, payloadBasicInfoListener);
                payloadManager.getClass().getMethod("removePayloadDataListener", PayloadDataListener.class)
                        .invoke(payloadManager, payloadDataListener);
            } catch (Exception e) {
                LogUtils.e(TAG, "移除监听器失败: " + e.getMessage());
            }
        }
    }

    private void reRegisterPayloadListeners() {
        unregisterPayloadListeners();
        registerPayloadListeners();
    }

    private void updatePayloadInfo() {
        Map<PayloadIndexType, IPayloadManager> payloadManagerMap = PayloadCenter.getInstance().getPayloadManager();
        if (payloadManagerMap == null) {
            currentPayloadInfo = null;
            payloadInfoText = "";
            return;
        }

        IPayloadManager payloadManager = payloadManagerMap.get(selectedPayloadIndex);
        if (payloadManager == null) {
            currentPayloadInfo = null;
            payloadInfoText = "";
            return;
        }

        try {
            Object info = payloadManager.getClass().getMethod("getPayloadBasicInfo").invoke(payloadManager);
            if (info instanceof PayloadBasicInfo) {
                currentPayloadInfo = (PayloadBasicInfo) info;
                payloadInfoText = buildPayloadInfoText(currentPayloadInfo);
            } else {
                currentPayloadInfo = null;
                payloadInfoText = "";
            }
        } catch (Exception e) {
            currentPayloadInfo = null;
            payloadInfoText = "";
            LogUtils.e(TAG, "获取 Payload 信息失败: " + e.getMessage());
        }
    }

    private String buildPayloadInfoText(PayloadBasicInfo info) {
        if (info == null) {
            return "";
        }

        StringBuilder infoText = new StringBuilder();
        infoText.append("设备名称: ").append(info.getPayloadProductName() != null ? info.getPayloadProductName() : "未知").append("\n");
        infoText.append("设备类型: ").append(info.getPayloadType() != null ? info.getPayloadType().name() : "未知").append("\n");
        infoText.append("序列号: ").append(info.getSerialNumber() != null ? info.getSerialNumber() : "未知").append("\n");
        infoText.append("固件版本: ").append(info.getFirmwareVersion() != null ? info.getFirmwareVersion() : "未知").append("\n");
        infoText.append("连接状态: ").append(info.isConnected() ? "已连接" : "未连接").append("\n");
        infoText.append("上传带宽: ").append(info.getUploadBandwidth()).append(" KB/s");
        return infoText.toString();
    }

    private boolean containsOnlyLineBreaks(byte[] data) {
        if (data == null || data.length == 0) {
            return true;
        }
        for (byte value : data) {
            if (value != '\r' && value != '\n') {
                return false;
            }
        }
        return true;
    }

    private String constructJsonData(String psdkData) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"psdk_data\",");
        json.append("\"timestamp\":\"").append(timestamp).append("\",");
        json.append("\"payload_index\":\"").append(selectedPayloadIndex.name()).append("\",");

        if (currentPayloadInfo != null) {
            json.append("\"device_name\":\"").append(escapeJson(currentPayloadInfo.getPayloadProductName())).append("\",");
            json.append("\"device_type\":\"")
                    .append(currentPayloadInfo.getPayloadType() != null ? currentPayloadInfo.getPayloadType().name() : "UNKNOWN")
                    .append("\",");
            json.append("\"serial_number\":\"").append(escapeJson(currentPayloadInfo.getSerialNumber())).append("\",");
            json.append("\"firmware_version\":\"").append(escapeJson(currentPayloadInfo.getFirmwareVersion())).append("\",");
            json.append("\"is_connected\":").append(currentPayloadInfo.isConnected()).append(",");
        }

        json.append("\"data\":\"").append(escapeJson(psdkData)).append("\"");
        json.append("}");
        return json.toString();
    }

    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void appendMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        messageBuffer.insert(0, "[" + timestamp + "] " + message + "\n");
        if (messageBuffer.length() > MAX_LOG_LENGTH) {
            messageBuffer.setLength(MAX_LOG_LENGTH);
        }
    }

    private void appendPsdkData(String data) {
        if (data == null || data.trim().isEmpty()) {
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        psdkDataBuffer.insert(0, "[" + timestamp + "] " + data + "\n");
        if (psdkDataBuffer.length() > MAX_LOG_LENGTH) {
            psdkDataBuffer.setLength(MAX_LOG_LENGTH);
        }
    }

    private String buildDisplayLog() {
        if (messageBuffer.length() == 0 && psdkDataBuffer.length() == 0) {
            return "";
        }

        StringBuilder display = new StringBuilder();
        display.append("=== 系统日志 ===\n");
        display.append(messageBuffer);
        display.append("\n=== PSDK 数据 ===\n");
        display.append(psdkDataBuffer);
        return display.toString();
    }

    private StateSnapshot buildSnapshot() {
        return new StateSnapshot(
                serverHost,
                serverPort,
                selectedPayloadIndex,
                networkClient.isConnected(),
                connectionStatus,
                payloadInfoText,
                buildDisplayLog());
    }

    private void dispatchState() {
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onStateChanged(buildSnapshot());
        }
    }

    private void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }
}
