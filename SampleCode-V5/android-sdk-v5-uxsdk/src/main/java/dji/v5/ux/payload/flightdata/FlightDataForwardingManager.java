package dji.v5.ux.payload.flightdata;

import android.os.Handler;
import android.os.Looper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dji.v5.utils.common.LogUtils;
import dji.v5.ux.payload.network.NetworkClient;

public final class FlightDataForwardingManager {

    private static final String TAG = "FlightDataForwardMgr";
    private static final String DEFAULT_HOST = "192.168.3.31";
    private static final int DEFAULT_PORT = 9999;
    private static final int DEFAULT_FREQUENCY = 1;
    private static final int MAX_LOG_LENGTH = 3000;
    private static final String STATUS_CONNECTED = "Network Status: Connected";
    private static final String STATUS_CONNECTING = "Network Status: Connecting...";
    private static final String STATUS_DISCONNECTED = "Network Status: Disconnected";
    private static final String STATUS_ERROR = "Network Status: Error";

    private static final FlightDataForwardingManager INSTANCE = new FlightDataForwardingManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final NetworkClient networkClient = new NetworkClient();
    private final StringBuilder messageBuffer = new StringBuilder();
    private final Runnable dataUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isForwarding) {
                return;
            }

            collectAndSendFlightData();

            if (isForwarding) {
                mainHandler.postDelayed(this, getUpdateIntervalMs());
            }
        }
    };

    private Listener listener;
    private String serverHost = DEFAULT_HOST;
    private int serverPort = DEFAULT_PORT;
    private int updateFrequency = DEFAULT_FREQUENCY;
    private boolean isForwarding = false;
    private String connectionStatus = STATUS_DISCONNECTED;
    private String lastPreviewJson = "";

    public interface Listener {
        void onStateChanged(StateSnapshot snapshot);
    }

    public static final class StateSnapshot {
        private final String serverHost;
        private final int serverPort;
        private final int updateFrequency;
        private final boolean connected;
        private final boolean forwarding;
        private final String connectionStatus;
        private final String previewJson;
        private final String messageLog;

        private StateSnapshot(String serverHost,
                              int serverPort,
                              int updateFrequency,
                              boolean connected,
                              boolean forwarding,
                              String connectionStatus,
                              String previewJson,
                              String messageLog) {
            this.serverHost = serverHost;
            this.serverPort = serverPort;
            this.updateFrequency = updateFrequency;
            this.connected = connected;
            this.forwarding = forwarding;
            this.connectionStatus = connectionStatus;
            this.previewJson = previewJson;
            this.messageLog = messageLog;
        }

        public String getServerHost() {
            return serverHost;
        }

        public int getServerPort() {
            return serverPort;
        }

        public int getUpdateFrequency() {
            return updateFrequency;
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

        public String getPreviewJson() {
            return previewJson;
        }

        public String getMessageLog() {
            return messageLog;
        }
    }

    private FlightDataForwardingManager() {
        networkClient.setServerAddress(serverHost, serverPort);
        networkClient.setCallback(new NetworkClient.NetworkCallback() {
            @Override
            public void onConnected() {
                runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        connectionStatus = STATUS_CONNECTED;
                        appendMessage("Server connected.");
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
                        appendMessage("Server disconnected.");
                        stopForwardingInternal(true);
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
                        appendMessage("Network error: " + error);
                        stopForwardingInternal(true);
                        dispatchState();
                    }
                });
            }

            @Override
            public void onDataSent(int bytes) {
                // Keep the data path quiet to avoid log spam.
            }
        });
    }

    public static FlightDataForwardingManager getInstance() {
        return INSTANCE;
    }

    public void bindListener(final Listener listener) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                FlightDataForwardingManager.this.listener = listener;
                dispatchState();
            }
        });
    }

    public void unbindListener(final Listener listener) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (FlightDataForwardingManager.this.listener == listener) {
                    FlightDataForwardingManager.this.listener = null;
                }
            }
        });
    }

    public StateSnapshot getStateSnapshot() {
        return buildSnapshot();
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
                appendMessage("Update frequency set to " + updateFrequency + " Hz.");

                if (isForwarding) {
                    mainHandler.removeCallbacks(dataUpdateRunnable);
                    mainHandler.post(dataUpdateRunnable);
                }

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

                appendMessage("Connecting to server: " + serverHost + ":" + serverPort);
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
                stopForwardingInternal(true);
                networkClient.disconnect();
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

                if (!networkClient.isConnected()) {
                    appendMessage("Connect to the server before starting forwarding.");
                    dispatchState();
                    return;
                }

                isForwarding = true;
                appendMessage("Start forwarding flight data (" + updateFrequency + " Hz).");
                mainHandler.removeCallbacks(dataUpdateRunnable);
                mainHandler.post(dataUpdateRunnable);
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

    public void clearMessages() {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                messageBuffer.setLength(0);
                appendMessage("Message log cleared.");
                dispatchState();
            }
        });
    }

    private void collectAndSendFlightData() {
        try {
            lastPreviewJson = FlightDataJsonBuilder.buildFlightDataJson();
            dispatchState();

            if (networkClient.isConnected()) {
                networkClient.sendData(lastPreviewJson);
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "Failed to collect flight data: " + e.getMessage());
            appendMessage("Failed to collect flight data: " + safeMessage(e));
            dispatchState();
        }
    }

    private void stopForwardingInternal(boolean appendLog) {
        if (!isForwarding) {
            return;
        }

        isForwarding = false;
        mainHandler.removeCallbacks(dataUpdateRunnable);

        if (appendLog) {
            appendMessage("Stop forwarding flight data.");
        }
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
                serverHost,
                serverPort,
                updateFrequency,
                networkClient.isConnected(),
                isForwarding,
                connectionStatus,
                lastPreviewJson,
                messageBuffer.toString());
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
