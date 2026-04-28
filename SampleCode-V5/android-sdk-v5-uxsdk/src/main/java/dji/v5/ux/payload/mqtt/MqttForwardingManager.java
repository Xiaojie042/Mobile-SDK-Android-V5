package dji.v5.ux.payload.mqtt;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import dji.v5.manager.aircraft.payload.PayloadCenter;
import dji.v5.manager.aircraft.payload.PayloadIndexType;
import dji.v5.manager.aircraft.payload.data.PayloadBasicInfo;
import dji.v5.manager.aircraft.payload.listener.PayloadBasicInfoListener;
import dji.v5.manager.aircraft.payload.listener.PayloadDataListener;
import dji.v5.manager.interfaces.IPayloadManager;
import dji.v5.utils.common.LogUtils;
import dji.v5.ux.payload.flightdata.FlightDataJsonBuilder;

public final class MqttForwardingManager {

    private static final String TAG = "MqttForwardingMgr";
    private static final String DEFAULT_HOST = "117.149.181.48";
    private static final int DEFAULT_PORT = 10101;
    private static final String DEFAULT_CLIENT_ID = "dji-msdk-client";
    private static final String DEFAULT_USERNAME = "subscribe";
    private static final String DEFAULT_FLIGHT_TOPIC = "dji/msdk/flight_data";
    private static final String DEFAULT_PSDK_TOPIC = "dji/msdk/psdk_data";
    private static final int DEFAULT_FREQUENCY = 1;
    private static final int MAX_LOG_LENGTH = 4000;
    private static final String STATUS_CONNECTED = "MQTT状态: 已连接";
    private static final String STATUS_CONNECTING = "MQTT状态: 连接中...";
    private static final String STATUS_DISCONNECTED = "MQTT状态: 未连接";
    private static final String STATUS_ERROR = "MQTT状态: 连接错误";

    private static final MqttForwardingManager INSTANCE = new MqttForwardingManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleMqttClient mqttClient = new SimpleMqttClient();
    private final StringBuilder messageBuffer = new StringBuilder();
    private final Map<PayloadIndexType, PayloadBasicInfo> payloadInfoMap = new HashMap<>();
    private final Map<PayloadIndexType, PayloadBasicInfoListener> payloadBasicInfoListeners = new HashMap<>();
    private final Map<PayloadIndexType, PayloadDataListener> payloadDataListeners = new HashMap<>();
    private final Runnable flightDataRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isForwarding || !flightDataEnabled) {
                return;
            }

            collectAndPublishFlightData();

            if (isForwarding && flightDataEnabled) {
                mainHandler.postDelayed(this, getUpdateIntervalMs());
            }
        }
    };

    private Listener listener;
    private String brokerHost = DEFAULT_HOST;
    private int brokerPort = DEFAULT_PORT;
    private String clientId = DEFAULT_CLIENT_ID;
    private String username = DEFAULT_USERNAME;
    private String password = "";
    private String flightTopic = DEFAULT_FLIGHT_TOPIC;
    private String psdkTopic = DEFAULT_PSDK_TOPIC;
    private int updateFrequency = DEFAULT_FREQUENCY;
    private boolean flightDataEnabled = true;
    private boolean psdkDataEnabled = true;
    private boolean isForwarding = false;
    private String connectionStatus = STATUS_DISCONNECTED;
    private String lastFlightPreviewJson = "";
    private String lastPsdkPreviewJson = "";
    private int publishedCount = 0;

    public interface Listener {
        void onStateChanged(StateSnapshot snapshot);
    }

    public static final class StateSnapshot {
        private final String brokerHost;
        private final int brokerPort;
        private final String clientId;
        private final String username;
        private final String password;
        private final String flightTopic;
        private final String psdkTopic;
        private final int updateFrequency;
        private final boolean flightDataEnabled;
        private final boolean psdkDataEnabled;
        private final boolean connected;
        private final boolean forwarding;
        private final String connectionStatus;
        private final String previewText;
        private final String messageLog;
        private final int publishedCount;

        private StateSnapshot(String brokerHost,
                              int brokerPort,
                              String clientId,
                              String username,
                              String password,
                              String flightTopic,
                              String psdkTopic,
                              int updateFrequency,
                              boolean flightDataEnabled,
                              boolean psdkDataEnabled,
                              boolean connected,
                              boolean forwarding,
                              String connectionStatus,
                              String previewText,
                              String messageLog,
                              int publishedCount) {
            this.brokerHost = brokerHost;
            this.brokerPort = brokerPort;
            this.clientId = clientId;
            this.username = username;
            this.password = password;
            this.flightTopic = flightTopic;
            this.psdkTopic = psdkTopic;
            this.updateFrequency = updateFrequency;
            this.flightDataEnabled = flightDataEnabled;
            this.psdkDataEnabled = psdkDataEnabled;
            this.connected = connected;
            this.forwarding = forwarding;
            this.connectionStatus = connectionStatus;
            this.previewText = previewText;
            this.messageLog = messageLog;
            this.publishedCount = publishedCount;
        }

        public String getBrokerHost() {
            return brokerHost;
        }

        public int getBrokerPort() {
            return brokerPort;
        }

        public String getClientId() {
            return clientId;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getFlightTopic() {
            return flightTopic;
        }

        public String getPsdkTopic() {
            return psdkTopic;
        }

        public int getUpdateFrequency() {
            return updateFrequency;
        }

        public boolean isFlightDataEnabled() {
            return flightDataEnabled;
        }

        public boolean isPsdkDataEnabled() {
            return psdkDataEnabled;
        }

        public boolean isConnected() {
            return connected;
        }

        public boolean isForwarding() {
            return forwarding;
        }

        public String getConnectionStatus() {
            return connectionStatus;
        }

        public String getPreviewText() {
            return previewText;
        }

        public String getMessageLog() {
            return messageLog;
        }

        public int getPublishedCount() {
            return publishedCount;
        }
    }

    private MqttForwardingManager() {
        mqttClient.setBroker(brokerHost, brokerPort, clientId, username, password);
        mqttClient.setCallback(new SimpleMqttClient.Callback() {
            @Override
            public void onConnected() {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        connectionStatus = STATUS_CONNECTED;
                        appendMessage("MQTT服务器连接成功。");
                        dispatchState();
                    }
                });
            }

            @Override
            public void onDisconnected() {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        connectionStatus = STATUS_DISCONNECTED;
                        stopForwardingInternal(true);
                        appendMessage("MQTT服务器连接已断开。");
                        dispatchState();
                    }
                });
            }

            @Override
            public void onError(final String error) {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        connectionStatus = STATUS_ERROR;
                        stopForwardingInternal(true);
                        appendMessage(error);
                        dispatchState();
                    }
                });
            }

            @Override
            public void onDataPublished(String topic, int bytes) {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        publishedCount++;
                        dispatchState();
                    }
                });
            }
        });
    }

    public static MqttForwardingManager getInstance() {
        return INSTANCE;
    }

    public void bindListener(final Listener listener) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                MqttForwardingManager.this.listener = listener;
                dispatchState();
            }
        });
    }

    public void unbindListener(final Listener listener) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (MqttForwardingManager.this.listener == listener) {
                    MqttForwardingManager.this.listener = null;
                }
            }
        });
    }

    public StateSnapshot getStateSnapshot() {
        return buildSnapshot();
    }

    public void connect(final String host,
                        final int port,
                        final String clientId,
                        final String username,
                        final String password,
                        final String flightTopic,
                        final String psdkTopic,
                        final int frequency,
                        final boolean flightDataEnabled,
                        final boolean psdkDataEnabled) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                updateConfigurationInternal(host, port, clientId, username, password, flightTopic, psdkTopic, frequency,
                        flightDataEnabled, psdkDataEnabled);
                mqttClient.setBroker(brokerHost, brokerPort, MqttForwardingManager.this.clientId,
                        MqttForwardingManager.this.username, MqttForwardingManager.this.password);
                connectionStatus = STATUS_CONNECTING;
                appendMessage("正在连接MQTT服务器: " + brokerHost + ":" + brokerPort);
                dispatchState();
                mqttClient.connect();
            }
        });
    }

    public void disconnect() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                stopForwardingInternal(true);
                mqttClient.disconnect();
            }
        });
    }

    public void startForwarding() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (isForwarding) {
                    return;
                }
                if (!mqttClient.isConnected()) {
                    appendMessage("请先连接MQTT服务器。");
                    dispatchState();
                    return;
                }
                if (!flightDataEnabled && !psdkDataEnabled) {
                    appendMessage("至少启用一种数据源。");
                    dispatchState();
                    return;
                }

                isForwarding = true;
                appendMessage("开始上传数据到MQTT服务器。");
                if (flightDataEnabled) {
                    startFlightDataTimer();
                }
                if (psdkDataEnabled) {
                    registerPayloadListeners();
                }
                dispatchState();
            }
        });
    }

    public void stopForwarding() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                stopForwardingInternal(true);
                dispatchState();
            }
        });
    }

    public void updateFrequency(final int frequency) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                int normalizedFrequency = normalizeFrequency(frequency);
                if (updateFrequency == normalizedFrequency) {
                    return;
                }

                updateFrequency = normalizedFrequency;
                appendMessage("飞控数据发布频率设置为 " + updateFrequency + " Hz。");
                if (isForwarding && flightDataEnabled) {
                    startFlightDataTimer();
                }
                dispatchState();
            }
        });
    }

    public void updateSourceEnabled(final boolean flightEnabled, final boolean psdkEnabled) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (flightDataEnabled == flightEnabled && psdkDataEnabled == psdkEnabled) {
                    return;
                }

                boolean wasPsdkEnabled = psdkDataEnabled;
                boolean wasFlightEnabled = flightDataEnabled;
                flightDataEnabled = flightEnabled;
                psdkDataEnabled = psdkEnabled;

                if (isForwarding) {
                    if (wasFlightEnabled != flightDataEnabled) {
                        if (flightDataEnabled) {
                            startFlightDataTimer();
                        } else {
                            mainHandler.removeCallbacks(flightDataRunnable);
                        }
                    }
                    if (wasPsdkEnabled != psdkDataEnabled) {
                        if (psdkDataEnabled) {
                            registerPayloadListeners();
                        } else {
                            unregisterPayloadListeners();
                        }
                    }
                }
                dispatchState();
            }
        });
    }

    public void clearMessages() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                messageBuffer.setLength(0);
                appendMessage("日志已清空。");
                dispatchState();
            }
        });
    }

    private void updateConfigurationInternal(String host,
                                             int port,
                                             String clientId,
                                             String username,
                                             String password,
                                             String flightTopic,
                                             String psdkTopic,
                                             int frequency,
                                             boolean flightDataEnabled,
                                             boolean psdkDataEnabled) {
        this.brokerHost = host == null ? "" : host.trim();
        this.brokerPort = port;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.flightTopic = flightTopic == null ? "" : flightTopic.trim();
        this.psdkTopic = psdkTopic == null ? "" : psdkTopic.trim();
        this.updateFrequency = normalizeFrequency(frequency);
        this.flightDataEnabled = flightDataEnabled;
        this.psdkDataEnabled = psdkDataEnabled;
    }

    private void collectAndPublishFlightData() {
        try {
            lastFlightPreviewJson = FlightDataJsonBuilder.buildFlightDataJson();
            mqttClient.publish(flightTopic, lastFlightPreviewJson);
            dispatchState();
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to publish flight data: " + e.getMessage());
            appendMessage("飞控数据发布失败: " + safeMessage(e));
            dispatchState();
        }
    }

    private void publishPsdkData(final PayloadIndexType indexType, final byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }

        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (!isForwarding || !psdkDataEnabled || !mqttClient.isConnected()) {
                    return;
                }

                String dataString = new String(data, StandardCharsets.UTF_8);
                lastPsdkPreviewJson = buildPsdkDataJson(indexType, dataString);
                mqttClient.publish(psdkTopic, lastPsdkPreviewJson);
                dispatchState();
            }
        });
    }

    private void startFlightDataTimer() {
        mainHandler.removeCallbacks(flightDataRunnable);
        mainHandler.post(flightDataRunnable);
    }

    private void registerPayloadListeners() {
        unregisterPayloadListeners();

        Map<PayloadIndexType, IPayloadManager> payloadManagerMap = PayloadCenter.getInstance().getPayloadManager();
        if (payloadManagerMap == null || payloadManagerMap.isEmpty()) {
            appendMessage("未发现可用的PSDK管理器。");
            return;
        }

        for (Map.Entry<PayloadIndexType, IPayloadManager> entry : payloadManagerMap.entrySet()) {
            final PayloadIndexType indexType = entry.getKey();
            IPayloadManager payloadManager = entry.getValue();
            if (indexType == null || payloadManager == null) {
                continue;
            }

            PayloadBasicInfoListener basicInfoListener = new PayloadBasicInfoListener() {
                @Override
                public void onPayloadBasicInfoUpdate(final PayloadBasicInfo payloadBasicInfo) {
                    runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            if (payloadBasicInfo != null) {
                                payloadInfoMap.put(indexType, payloadBasicInfo);
                            } else {
                                payloadInfoMap.remove(indexType);
                            }
                        }
                    });
                }
            };

            PayloadDataListener dataListener = new PayloadDataListener() {
                @Override
                public void onDataFromPayloadUpdate(byte[] data) {
                    publishPsdkData(indexType, data);
                }
            };

            try {
                payloadManager.getClass().getMethod("addPayloadBasicInfoListener", PayloadBasicInfoListener.class)
                        .invoke(payloadManager, basicInfoListener);
                payloadManager.getClass().getMethod("addPayloadDataListener", PayloadDataListener.class)
                        .invoke(payloadManager, dataListener);
                payloadBasicInfoListeners.put(indexType, basicInfoListener);
                payloadDataListeners.put(indexType, dataListener);
            } catch (Exception e) {
                LogUtils.e(TAG, "注册PSDK MQTT监听器失败: " + e.getMessage());
                appendMessage("注册PSDK监听器失败(" + indexType.name() + "): " + safeMessage(e));
            }
        }

        appendMessage("PSDK数据监听已启动。");
    }

    private void unregisterPayloadListeners() {
        if (payloadBasicInfoListeners.isEmpty() && payloadDataListeners.isEmpty()) {
            return;
        }

        Map<PayloadIndexType, IPayloadManager> payloadManagerMap = PayloadCenter.getInstance().getPayloadManager();
        if (payloadManagerMap != null) {
            for (Map.Entry<PayloadIndexType, PayloadBasicInfoListener> entry : payloadBasicInfoListeners.entrySet()) {
                IPayloadManager payloadManager = payloadManagerMap.get(entry.getKey());
                if (payloadManager == null) {
                    continue;
                }
                try {
                    payloadManager.getClass().getMethod("removePayloadBasicInfoListener", PayloadBasicInfoListener.class)
                            .invoke(payloadManager, entry.getValue());
                } catch (Exception e) {
                    LogUtils.e(TAG, "移除PSDK基础信息监听器失败: " + e.getMessage());
                }
            }

            for (Map.Entry<PayloadIndexType, PayloadDataListener> entry : payloadDataListeners.entrySet()) {
                IPayloadManager payloadManager = payloadManagerMap.get(entry.getKey());
                if (payloadManager == null) {
                    continue;
                }
                try {
                    payloadManager.getClass().getMethod("removePayloadDataListener", PayloadDataListener.class)
                            .invoke(payloadManager, entry.getValue());
                } catch (Exception e) {
                    LogUtils.e(TAG, "移除PSDK数据监听器失败: " + e.getMessage());
                }
            }
        }

        payloadBasicInfoListeners.clear();
        payloadDataListeners.clear();
    }

    private void stopForwardingInternal(boolean appendLog) {
        if (!isForwarding) {
            return;
        }

        isForwarding = false;
        mainHandler.removeCallbacks(flightDataRunnable);
        unregisterPayloadListeners();
        if (appendLog) {
            appendMessage("停止上传数据到MQTT服务器。");
        }
    }

    private String buildPsdkDataJson(PayloadIndexType indexType, String psdkData) {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "psdk_data");
            json.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date()));
            json.put("payload_index", indexType == null ? "UNKNOWN" : indexType.name());

            PayloadBasicInfo payloadInfo = payloadInfoMap.get(indexType);
            if (payloadInfo != null) {
                json.put("device_name", payloadInfo.getPayloadProductName());
                json.put("device_type", payloadInfo.getPayloadType() == null ? "UNKNOWN" : payloadInfo.getPayloadType().name());
                json.put("serial_number", payloadInfo.getSerialNumber());
                json.put("firmware_version", payloadInfo.getFirmwareVersion());
                json.put("is_connected", payloadInfo.isConnected());
            }

            json.put("data", psdkData == null ? "" : psdkData);
        } catch (JSONException e) {
            LogUtils.e(TAG, "构建PSDK MQTT JSON失败: " + e.getMessage());
        }
        return json.toString();
    }

    private long getUpdateIntervalMs() {
        return Math.max(100, 1000L / normalizeFrequency(updateFrequency));
    }

    private int normalizeFrequency(int frequency) {
        if (frequency <= 1) {
            return 1;
        }
        if (frequency <= 5) {
            return 5;
        }
        return 10;
    }

    private StateSnapshot buildSnapshot() {
        return new StateSnapshot(
                brokerHost,
                brokerPort,
                clientId,
                username,
                password,
                flightTopic,
                psdkTopic,
                updateFrequency,
                flightDataEnabled,
                psdkDataEnabled,
                mqttClient.isConnected(),
                isForwarding,
                connectionStatus,
                buildPreviewText(),
                messageBuffer.toString(),
                publishedCount);
    }

    private String buildPreviewText() {
        if (lastFlightPreviewJson.isEmpty() && lastPsdkPreviewJson.isEmpty()) {
            return "";
        }

        StringBuilder preview = new StringBuilder();
        if (!lastFlightPreviewJson.isEmpty()) {
            preview.append("=== Flight Data ===\n");
            preview.append(lastFlightPreviewJson);
        }
        if (!lastPsdkPreviewJson.isEmpty()) {
            if (preview.length() > 0) {
                preview.append("\n\n");
            }
            preview.append("=== PSDK Data ===\n");
            preview.append(lastPsdkPreviewJson);
        }
        return preview.toString();
    }

    private void appendMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        messageBuffer.insert(0, "[" + timestamp + "] " + message + "\n");
        if (messageBuffer.length() > MAX_LOG_LENGTH) {
            messageBuffer.setLength(MAX_LOG_LENGTH);
        }
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
