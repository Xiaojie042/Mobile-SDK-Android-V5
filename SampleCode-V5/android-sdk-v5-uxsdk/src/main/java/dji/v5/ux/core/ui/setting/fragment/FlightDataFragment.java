package dji.v5.ux.core.ui.setting.fragment;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.BatteryKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.common.Attitude;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
import dji.sdk.keyvalue.value.common.LocationCoordinate3D;
import dji.sdk.keyvalue.value.common.Velocity3D;
import dji.sdk.keyvalue.value.flightcontroller.FlightMode;
import dji.v5.manager.KeyManager;
import dji.v5.utils.common.ContextUtil;
import dji.v5.utils.common.LogUtils;
import dji.v5.utils.common.StringUtils;
import dji.v5.ux.R;
import dji.v5.ux.core.ui.setting.ui.MenuFragment;
import dji.v5.ux.payload.network.NetworkClient;

/**
 * 飞控数据转发设置界面
 * 用于将无人机飞控数据通过网络转发到PC端
 * 
 * @author DJI
 * @date 2024
 */
public class FlightDataFragment extends MenuFragment {
    
    private static final String TAG = "FlightDataFragment";
    
    private RadioGroup rgFrequency;
    private RadioButton rb1Hz;
    private RadioButton rb5Hz;
    private RadioButton rb10Hz;
    private TextView tvFlightDataPreview;
    private TextView tvConnectionStatus;
    private TextView tvMessageLog;
    private EditText etServerIp;
    private EditText etServerPort;
    private Button btnConnectServer;
    private Button btnDisconnectServer;
    private Button btnStartForward;
    private Button btnStopForward;
    private Button btnClear;
    
    private int updateFrequency = 1; // Hz
    private boolean isForwarding = false;
    private StringBuilder messageBuffer = new StringBuilder();
    
    // 网络客户端
    private NetworkClient networkClient;
    
    // 数据更新Handler
    private Handler dataHandler;
    private Runnable dataUpdateRunnable;
    
    @Override
    protected String getPreferencesTitle() {
        return StringUtils.getResStr(ContextUtil.getContext(), R.string.uxsdk_flight_data_setting_title);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.uxsdk_fragment_setting_menu_flight_data_layout;
    }
    
    @Override
    protected void onPrepareView() {
        super.onPrepareView();
        if (mFragmentRoot != null) {
            initViews(mFragmentRoot);
            initNetworkClient();
            setupFrequencySelection();
            setupButtons();
            initDataHandler();
        }
    }
    
    private void initViews(View view) {
        if (view == null) {
            return;
        }
        
        rgFrequency = view.findViewById(R.id.rg_frequency);
        rb1Hz = view.findViewById(R.id.rb_1hz);
        rb5Hz = view.findViewById(R.id.rb_5hz);
        rb10Hz = view.findViewById(R.id.rb_10hz);
        tvFlightDataPreview = view.findViewById(R.id.tv_flight_data_preview);
        tvConnectionStatus = view.findViewById(R.id.tv_connection_status);
        tvMessageLog = view.findViewById(R.id.tv_message_log);
        etServerIp = view.findViewById(R.id.et_server_ip);
        etServerPort = view.findViewById(R.id.et_server_port);
        btnConnectServer = view.findViewById(R.id.btn_connect_server);
        btnDisconnectServer = view.findViewById(R.id.btn_disconnect_server);
        btnStartForward = view.findViewById(R.id.btn_start_forward);
        btnStopForward = view.findViewById(R.id.btn_stop_forward);
        btnClear = view.findViewById(R.id.btn_clear);
        
        // 设置默认服务器地址
        if (etServerIp != null) {
            etServerIp.setText("192.168.3.31");
        }
        if (etServerPort != null) {
            etServerPort.setText("9999");
        }
    }
    
    private void initNetworkClient() {
        networkClient = new NetworkClient();
        networkClient.setCallback(new NetworkClient.NetworkCallback() {
            @Override
            public void onConnected() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            appendMessage("✅ 服务器连接成功");
                            updateNetworkStatus("网络状态: 已连接");
                            updateButtonStates(true, isForwarding);
                        }
                    });
                }
            }
            
            @Override
            public void onDisconnected() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            appendMessage("🔌 服务器连接已断开");
                            updateNetworkStatus("网络状态: 未连接");
                            stopDataForwarding();
                            updateButtonStates(false, false);
                        }
                    });
                }
            }
            
            @Override
            public void onError(String error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            appendMessage("❌ " + error);
                            updateNetworkStatus("网络状态: 连接错误");
                            stopDataForwarding();
                            updateButtonStates(false, false);
                        }
                    });
                }
            }
            
            @Override
            public void onDataSent(int bytes) {
                // 数据发送成功，不需要每次都记录日志
            }
        });
    }
    
    private void setupFrequencySelection() {
        if (rgFrequency == null) {
            return;
        }
        
        rgFrequency.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rb_1hz) {
                    updateFrequency = 1;
                } else if (checkedId == R.id.rb_5hz) {
                    updateFrequency = 5;
                } else if (checkedId == R.id.rb_10hz) {
                    updateFrequency = 10;
                }
                
                appendMessage("更新频率设置为: " + updateFrequency + " Hz");
                
                // 如果正在转发，重启转发以应用新频率
                if (isForwarding) {
                    stopDataForwarding();
                    startDataForwarding();
                }
            }
        });
        
        // 默认选择1Hz
        if (rb1Hz != null) {
            rb1Hz.setChecked(true);
        }
    }
    
    private void setupButtons() {
        if (btnConnectServer != null) {
            btnConnectServer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    connectToServer();
                }
            });
        }
        
        if (btnDisconnectServer != null) {
            btnDisconnectServer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    disconnectFromServer();
                }
            });
        }
        
        if (btnStartForward != null) {
            btnStartForward.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startDataForwarding();
                }
            });
        }
        
        if (btnStopForward != null) {
            btnStopForward.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopDataForwarding();
                }
            });
        }
        
        if (btnClear != null) {
            btnClear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearMessages();
                }
            });
        }
    }
    
    private void initDataHandler() {
        dataHandler = new Handler(Looper.getMainLooper());
        dataUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isForwarding) {
                    collectAndSendFlightData();
                    dataHandler.postDelayed(this, 1000 / updateFrequency);
                }
            }
        };
    }
    
    private void collectAndSendFlightData() {
        try {
            // 收集飞控数据
            String jsonData = constructFlightDataJson();
            
            // 更新预览
            updateFlightDataPreview(jsonData);
            
            // 如果网络已连接，发送数据
            if (networkClient != null && networkClient.isConnected()) {
                networkClient.sendData(jsonData);
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "收集飞控数据失败: " + e.getMessage());
        }
    }
    
    private String constructFlightDataJson() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"flight_data\",");
        json.append("\"timestamp\":\"").append(timestamp).append("\",");
        
        // 姿态数据
        Attitude attitude = KeyManager.getInstance().getValue(KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude));
        if (attitude != null) {
            json.append("\"attitude\":{");
            json.append("\"pitch\":").append(attitude.getPitch()).append(",");
            json.append("\"roll\":").append(attitude.getRoll()).append(",");
            json.append("\"yaw\":").append(attitude.getYaw());
            json.append("},");
        }
        
        // GPS位置
        LocationCoordinate3D location = KeyManager.getInstance().getValue(KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D));
        if (location != null) {
            json.append("\"location\":{");
            json.append("\"latitude\":").append(location.getLatitude()).append(",");
            json.append("\"longitude\":").append(location.getLongitude()).append(",");
            json.append("\"altitude\":").append(location.getAltitude());
            json.append("},");
        }
        
        // 速度
        Velocity3D velocity = KeyManager.getInstance().getValue(KeyTools.createKey(FlightControllerKey.KeyAircraftVelocity));
        if (velocity != null) {
            json.append("\"velocity\":{");
            json.append("\"x\":").append(velocity.getX()).append(",");
            json.append("\"y\":").append(velocity.getY()).append(",");
            json.append("\"z\":").append(velocity.getZ());
            json.append("},");
        }
        
        // 飞行模式
        FlightMode flightMode = KeyManager.getInstance().getValue(KeyTools.createKey(FlightControllerKey.KeyFlightMode));
        if (flightMode != null) {
            json.append("\"flight_mode\":\"").append(flightMode.name()).append("\",");
        }
        
        // 返航点
        LocationCoordinate2D homeLocation = KeyManager.getInstance().getValue(KeyTools.createKey(FlightControllerKey.KeyHomeLocation));
        if (homeLocation != null) {
            json.append("\"home_location\":{");
            json.append("\"latitude\":").append(homeLocation.getLatitude()).append(",");
            json.append("\"longitude\":").append(homeLocation.getLongitude());
            json.append("},");
        }
        
        // 电池电量 - 使用BatteryKey
        Integer batteryPercent = KeyManager.getInstance().getValue(KeyTools.createKey(BatteryKey.KeyChargeRemainingInPercent, ComponentIndexType.AGGREGATION));
        if (batteryPercent != null) {
            json.append("\"battery_percentage\":").append(batteryPercent).append(",");
        }
        
        // 卫星数量 - 暂时注释掉，等待确认正确的Key名称
        // Integer satelliteCount = KeyManager.getInstance().getValue(KeyTools.createKey(FlightControllerKey.KeyGPSSatelliteCount));
        // if (satelliteCount != null) {
        //     json.append("\"satellite_count\":").append(satelliteCount).append(",");
        // }
        
        // 是否在飞行中
        Boolean isFlying = KeyManager.getInstance().getValue(KeyTools.createKey(FlightControllerKey.KeyIsFlying));
        if (isFlying != null) {
            json.append("\"is_flying\":").append(isFlying).append(",");
        }
        
        // 移除最后的逗号
        if (json.charAt(json.length() - 1) == ',') {
            json.setLength(json.length() - 1);
        }
        
        json.append("}");
        
        return json.toString();
    }
    
    private void updateFlightDataPreview(String jsonData) {
        if (tvFlightDataPreview != null && getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 格式化JSON显示
                    String formatted = formatJsonForDisplay(jsonData);
                    tvFlightDataPreview.setText(formatted);
                }
            });
        }
    }
    
    private String formatJsonForDisplay(String json) {
        // 简单的JSON格式化，用于显示
        return json.replace(",\"", ",\n\"")
                   .replace("{", "{\n  ")
                   .replace("}", "\n}")
                   .replace(":{", ":\n  {")
                   .replace("},", "\n  },");
    }
    
    private void connectToServer() {
        if (etServerIp == null || etServerPort == null || networkClient == null) {
            return;
        }
        
        String serverHost = etServerIp.getText().toString().trim();
        String portText = etServerPort.getText().toString().trim();
        
        if (serverHost.isEmpty() || portText.isEmpty()) {
            appendMessage("请输入服务器地址和端口");
            return;
        }
        
        try {
            int serverPort = Integer.parseInt(portText);
            networkClient.setServerAddress(serverHost, serverPort);
            
            appendMessage("正在连接到服务器: " + serverHost + ":" + serverPort);
            updateNetworkStatus("网络状态: 连接中...");
            
            networkClient.connect();
        } catch (NumberFormatException e) {
            appendMessage("端口号格式错误");
        }
    }
    
    private void disconnectFromServer() {
        if (networkClient != null) {
            stopDataForwarding();
            networkClient.disconnect();
        }
    }
    
    private void startDataForwarding() {
        if (!isForwarding) {
            isForwarding = true;
            appendMessage("▶️ 开始转发飞控数据 (" + updateFrequency + " Hz)");
            dataHandler.post(dataUpdateRunnable);
            updateButtonStates(networkClient != null && networkClient.isConnected(), true);
        }
    }
    
    private void stopDataForwarding() {
        if (isForwarding) {
            isForwarding = false;
            appendMessage("⏸️ 停止转发飞控数据");
            dataHandler.removeCallbacks(dataUpdateRunnable);
            updateButtonStates(networkClient != null && networkClient.isConnected(), false);
        }
    }
    
    private void updateNetworkStatus(String status) {
        if (tvConnectionStatus != null && getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tvConnectionStatus.setText(status);
                }
            });
        }
    }
    
    private void updateButtonStates(boolean connected, boolean forwarding) {
        if (getActivity() == null) {
            return;
        }
        
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (btnConnectServer != null) {
                    btnConnectServer.setEnabled(!connected);
                }
                if (btnDisconnectServer != null) {
                    btnDisconnectServer.setEnabled(connected);
                }
                if (btnStartForward != null) {
                    btnStartForward.setEnabled(connected && !forwarding);
                }
                if (btnStopForward != null) {
                    btnStopForward.setEnabled(forwarding);
                }
            }
        });
    }
    
    private void appendMessage(String message) {
        if (getActivity() == null) {
            return;
        }
        
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                String timestamp = sdf.format(new Date());
                messageBuffer.insert(0, "[" + timestamp + "] " + message + "\n");
                
                // 限制消息缓冲区大小
                if (messageBuffer.length() > 3000) {
                    messageBuffer.setLength(3000);
                }
                
                if (tvMessageLog != null) {
                    tvMessageLog.setText(messageBuffer.toString());
                }
            }
        });
    }
    
    private void clearMessages() {
        messageBuffer.setLength(0);
        if (tvMessageLog != null) {
            tvMessageLog.setText("日志已清空");
        }
        appendMessage("消息已清空");
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // 停止数据转发
        stopDataForwarding();
        
        // 断开网络连接
        if (networkClient != null) {
            networkClient.shutdown();
            networkClient = null;
        }
        
        // 清理Handler
        if (dataHandler != null) {
            dataHandler.removeCallbacksAndMessages(null);
            dataHandler = null;
        }
        
        LogUtils.d(TAG, "FlightDataFragment 已销毁");
    }
}
