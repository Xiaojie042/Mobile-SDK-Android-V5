package dji.v5.ux.core.ui.setting.fragment;

import android.os.Bundle;
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
import java.util.Map;

import dji.v5.manager.aircraft.payload.PayloadCenter;
import dji.v5.manager.aircraft.payload.PayloadIndexType;
import dji.v5.manager.aircraft.payload.data.PayloadBasicInfo;
import dji.v5.manager.aircraft.payload.listener.PayloadBasicInfoListener;
import dji.v5.manager.aircraft.payload.listener.PayloadDataListener;
import dji.v5.manager.interfaces.IPayloadManager;
import dji.v5.utils.common.ContextUtil;
import dji.v5.utils.common.LogUtils;
import dji.v5.utils.common.StringUtils;
import dji.v5.ux.R;
import dji.v5.ux.core.ui.setting.ui.MenuFragment;
import dji.v5.ux.payload.network.NetworkClient;

/**
 * Description : Payload Settings Fragment
 *
 * @author: Byte.Cai
 * date : 2022/11/21
 * <p>
 * Copyright (c) 2022, DJI All Rights Reserved.
 */
public class PayloadFragment extends MenuFragment {
    
    private static final String TAG = "PayloadFragment";
    
    private RadioGroup rgPsdkSelection;
    private RadioButton rbPsdkUp;
    private RadioButton rbPsdkLeft;
    private RadioButton rbPsdkRight;
    private RadioButton rbPsdkExtension1;
    private RadioButton rbPsdkExtension2;
    private RadioButton rbPsdkExtension3;
    private TextView tvPayloadInfo;
    private TextView tvConnectionStatus;
    private TextView tvPsdkMessage;
    private EditText etServerIp;
    private EditText etServerPort;
    private Button btnConnectServer;
    private Button btnDisconnectServer;
    private Button btnClear;
    
    private PayloadIndexType selectedPayloadIndex = PayloadIndexType.UP;
    private StringBuilder messageBuffer = new StringBuilder();
    private StringBuilder psdkDataBuffer = new StringBuilder();
    
    // 网络客户端
    private NetworkClient networkClient;
    
    // Payload监听器
    private PayloadBasicInfoListener payloadBasicInfoListener;
    private PayloadDataListener payloadDataListener;
    
    @Override
    protected String getPreferencesTitle() {
        return StringUtils.getResStr(ContextUtil.getContext(), R.string.uxsdk_payload_setting_title);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.uxsdk_fragment_setting_menu_payload_layout;
    }
    
    @Override
    protected void onPrepareView() {
        super.onPrepareView();
        if (mFragmentRoot != null) {
            initViews(mFragmentRoot);
            initNetworkClient();
            setupPayloadSelection();
            setupButtons();
            initPayloadListeners();
        }
    }
    
    private void initViews(View view) {
        if (view == null) {
            return;
        }
        
        rgPsdkSelection = view.findViewById(R.id.rg_psdk_selection);
        rbPsdkUp = view.findViewById(R.id.rb_psdk_up);
        rbPsdkLeft = view.findViewById(R.id.rb_psdk_left);
        rbPsdkRight = view.findViewById(R.id.rb_psdk_right);
        rbPsdkExtension1 = view.findViewById(R.id.rb_psdk_extension_1);
        rbPsdkExtension2 = view.findViewById(R.id.rb_psdk_extension_2);
        rbPsdkExtension3 = view.findViewById(R.id.rb_psdk_extension_3);
        tvPayloadInfo = view.findViewById(R.id.tv_payload_info);
        tvConnectionStatus = view.findViewById(R.id.tv_connection_status);
        tvPsdkMessage = view.findViewById(R.id.tv_psdk_message);
        etServerIp = view.findViewById(R.id.et_server_ip);
        etServerPort = view.findViewById(R.id.et_server_port);
        btnConnectServer = view.findViewById(R.id.btn_connect_server);
        btnDisconnectServer = view.findViewById(R.id.btn_disconnect_server);
        btnClear = view.findViewById(R.id.btn_clear);
        
        // 设置默认服务器地址
        if (etServerIp != null) {
            etServerIp.setText("192.168.3.31");
        }
        if (etServerPort != null) {
            etServerPort.setText("8888");
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
                            updateButtonStates(true);
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
                            updateButtonStates(false);
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
                            updateButtonStates(false);
                        }
                    });
                }
            }
            
            @Override
            public void onDataSent(int bytes) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            appendMessage("📤 数据已转发: " + bytes + " 字节");
                        }
                    });
                }
            }
        });
    }
    
    private void setupPayloadSelection() {
        if (rgPsdkSelection == null) {
            return;
        }
        
        // 设置RadioGroup监听器（第一排）
        rgPsdkSelection.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // 取消第二排的选择
                if (rbPsdkExtension1 != null) rbPsdkExtension1.setChecked(false);
                if (rbPsdkExtension2 != null) rbPsdkExtension2.setChecked(false);
                if (rbPsdkExtension3 != null) rbPsdkExtension3.setChecked(false);
                
                PayloadIndexType oldIndex = selectedPayloadIndex;
                
                if (checkedId == R.id.rb_psdk_up) {
                    selectedPayloadIndex = PayloadIndexType.UP;
                } else if (checkedId == R.id.rb_psdk_left) {
                    selectedPayloadIndex = PayloadIndexType.LEFT_OR_MAIN;
                } else if (checkedId == R.id.rb_psdk_right) {
                    selectedPayloadIndex = PayloadIndexType.RIGHT;
                }
                
                if (oldIndex != selectedPayloadIndex) {
                    appendMessage("选择 PSDK: " + getPayloadDisplayName(selectedPayloadIndex));
                    updatePayloadInfo();
                    reinitPayloadListeners();
                }
            }
        });
        
        // 设置第二排RadioButton的点击监听器
        View.OnClickListener extensionClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 取消RadioGroup中的选择
                if (rgPsdkSelection != null) {
                    rgPsdkSelection.clearCheck();
                }
                
                // 取消其他扩展点的选择
                if (v.getId() != R.id.rb_psdk_extension_1 && rbPsdkExtension1 != null) {
                    rbPsdkExtension1.setChecked(false);
                }
                if (v.getId() != R.id.rb_psdk_extension_2 && rbPsdkExtension2 != null) {
                    rbPsdkExtension2.setChecked(false);
                }
                if (v.getId() != R.id.rb_psdk_extension_3 && rbPsdkExtension3 != null) {
                    rbPsdkExtension3.setChecked(false);
                }
                
                // 设置当前点击的为选中状态
                ((RadioButton) v).setChecked(true);
                
                PayloadIndexType oldIndex = selectedPayloadIndex;
                
                // 根据点击的扩展点设置对应的PayloadIndexType
                // 需要查看所有可用的PayloadIndexType值来确定扩展点对应的类型
                PayloadIndexType[] allValues = PayloadIndexType.values();
                
                if (v.getId() == R.id.rb_psdk_extension_1) {
                    // 扩展点1 - 尝试使用索引3的值（如果存在）
                    if (allValues.length > 3) {
                        selectedPayloadIndex = allValues[3];
                    } else {
                        selectedPayloadIndex = PayloadIndexType.UP;
                    }
                    appendMessage("选择 PSDK: 扩展点1 (" + selectedPayloadIndex.name() + ")");
                } else if (v.getId() == R.id.rb_psdk_extension_2) {
                    // 扩展点2 - 尝试使用索引4的值（如果存在）
                    if (allValues.length > 4) {
                        selectedPayloadIndex = allValues[4];
                    } else {
                        selectedPayloadIndex = PayloadIndexType.UP;
                    }
                    appendMessage("选择 PSDK: 扩展点2 (" + selectedPayloadIndex.name() + ")");
                } else if (v.getId() == R.id.rb_psdk_extension_3) {
                    // 扩展点3 - 尝试使用索引5的值（如果存在）
                    if (allValues.length > 5) {
                        selectedPayloadIndex = allValues[5];
                    } else {
                        selectedPayloadIndex = PayloadIndexType.UP;
                    }
                    appendMessage("选择 PSDK: 扩展点3 (" + selectedPayloadIndex.name() + ")");
                }
                
                // 总是触发更新，即使索引相同
                updatePayloadInfo();
                reinitPayloadListeners();
            }
        };
        
        if (rbPsdkExtension1 != null) rbPsdkExtension1.setOnClickListener(extensionClickListener);
        if (rbPsdkExtension2 != null) rbPsdkExtension2.setOnClickListener(extensionClickListener);
        if (rbPsdkExtension3 != null) rbPsdkExtension3.setOnClickListener(extensionClickListener);
        
        // 默认选择UP挂载点
        if (rbPsdkUp != null) {
            rbPsdkUp.setChecked(true);
        }
        
        // 打印所有可用的PayloadIndexType值，用于调试
        PayloadIndexType[] allValues = PayloadIndexType.values();
        StringBuilder debugMsg = new StringBuilder("可用的 PayloadIndexType: ");
        for (int i = 0; i < allValues.length; i++) {
            debugMsg.append(i).append("=").append(allValues[i].name());
            if (i < allValues.length - 1) {
                debugMsg.append(", ");
            }
        }
        LogUtils.d(TAG, debugMsg.toString());
        appendMessage(debugMsg.toString());
    }
    
    private String getPayloadDisplayName(PayloadIndexType indexType) {
        switch (indexType) {
            case UP:
                return getString(R.string.uxsdk_payload_psdk_up);
            case LEFT_OR_MAIN:
                return getString(R.string.uxsdk_payload_psdk_left);
            case RIGHT:
                return getString(R.string.uxsdk_payload_psdk_right);
            case UNKNOWN:
            default:
                return indexType.name() + " 挂载点";
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
        
        if (btnClear != null) {
            btnClear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearMessages();
                }
            });
        }
    }
    
    private void initPayloadListeners() {
        payloadBasicInfoListener = new PayloadBasicInfoListener() {
            @Override
            public void onPayloadBasicInfoUpdate(PayloadBasicInfo payloadBasicInfo) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updatePayloadInfoUI(payloadBasicInfo);
                            if (payloadBasicInfo != null && payloadBasicInfo.isConnected()) {
                                appendMessage("📡 PSDK 设备已连接: " + payloadBasicInfo.getPayloadProductName());
                            }
                        }
                    });
                }
            }
        };
        
        payloadDataListener = new PayloadDataListener() {
            @Override
            public void onDataFromPayloadUpdate(byte[] data) {
                if (data != null && data.length > 0) {
                    String dataString = new String(data);
                    appendPsdkData(dataString);
                    
                    // 如果网络已连接，自动转发数据
                    if (networkClient != null && networkClient.isConnected()) {
                        String jsonData = constructJsonData(dataString);
                        networkClient.sendData(jsonData);
                    }
                }
            }
        };
        
        // 注册监听器
        registerPayloadListeners();
        
        // 更新初始信息
        updatePayloadInfo();
    }
    
    private void registerPayloadListeners() {
        Map<PayloadIndexType, IPayloadManager> payloadManagerMap = PayloadCenter.getInstance().getPayloadManager();
        if (payloadManagerMap != null) {
            IPayloadManager payloadManager = payloadManagerMap.get(selectedPayloadIndex);
            if (payloadManager != null) {
                try {
                    // 使用反射调用监听器方法
                    payloadManager.getClass().getMethod("addPayloadBasicInfoListener", PayloadBasicInfoListener.class)
                            .invoke(payloadManager, payloadBasicInfoListener);
                    payloadManager.getClass().getMethod("addPayloadDataListener", PayloadDataListener.class)
                            .invoke(payloadManager, payloadDataListener);
                    LogUtils.d(TAG, "注册 Payload 监听器: " + selectedPayloadIndex.name());
                } catch (Exception e) {
                    LogUtils.e(TAG, "注册监听器失败: " + e.getMessage());
                }
            }
        }
    }
    
    private void unregisterPayloadListeners() {
        if (payloadBasicInfoListener != null && payloadDataListener != null) {
            Map<PayloadIndexType, IPayloadManager> payloadManagerMap = PayloadCenter.getInstance().getPayloadManager();
            if (payloadManagerMap != null) {
                for (PayloadIndexType indexType : PayloadIndexType.values()) {
                    IPayloadManager payloadManager = payloadManagerMap.get(indexType);
                    if (payloadManager != null) {
                        try {
                            // 使用反射调用监听器移除方法
                            payloadManager.getClass().getMethod("removePayloadBasicInfoListener", PayloadBasicInfoListener.class)
                                    .invoke(payloadManager, payloadBasicInfoListener);
                            payloadManager.getClass().getMethod("removePayloadDataListener", PayloadDataListener.class)
                                    .invoke(payloadManager, payloadDataListener);
                        } catch (Exception e) {
                            LogUtils.e(TAG, "移除监听器失败: " + e.getMessage());
                        }
                    }
                }
                LogUtils.d(TAG, "移除所有 Payload 监听器");
            }
        }
    }
    
    private void reinitPayloadListeners() {
        // 移除旧的监听器
        unregisterPayloadListeners();
        
        // 注册新的监听器
        registerPayloadListeners();
    }
    
    private void updatePayloadInfo() {
        Map<PayloadIndexType, IPayloadManager> payloadManagerMap = PayloadCenter.getInstance().getPayloadManager();
        if (payloadManagerMap != null) {
            IPayloadManager payloadManager = payloadManagerMap.get(selectedPayloadIndex);
            if (payloadManager != null) {
                try {
                    // 使用反射调用getPayloadBasicInfo方法
                    Object info = payloadManager.getClass().getMethod("getPayloadBasicInfo").invoke(payloadManager);
                    if (info instanceof PayloadBasicInfo) {
                        updatePayloadInfoUI((PayloadBasicInfo) info);
                    }
                } catch (Exception e) {
                    LogUtils.e(TAG, "获取 Payload 信息失败: " + e.getMessage());
                }
            }
        }
    }
    
    private void updatePayloadInfoUI(PayloadBasicInfo info) {
        if (tvPayloadInfo == null) {
            return;
        }
        
        if (info == null) {
            tvPayloadInfo.setText(getString(R.string.uxsdk_payload_no_device));
            return;
        }
        
        StringBuilder infoText = new StringBuilder();
        infoText.append("设备名称: ").append(info.getPayloadProductName() != null ? info.getPayloadProductName() : "未知").append("\n");
        infoText.append("设备类型: ").append(info.getPayloadType() != null ? info.getPayloadType().name() : "未知").append("\n");
        infoText.append("序列号: ").append(info.getSerialNumber() != null ? info.getSerialNumber() : "未知").append("\n");
        infoText.append("固件版本: ").append(info.getFirmwareVersion() != null ? info.getFirmwareVersion() : "未知").append("\n");
        infoText.append("连接状态: ").append(info.isConnected() ? "已连接" : "未连接").append("\n");
        infoText.append("上传带宽: ").append(info.getUploadBandwidth()).append(" KB/s");
        
        tvPayloadInfo.setText(infoText.toString());
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
            networkClient.disconnect();
        }
    }
    
    private String constructJsonData(String psdkData) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = sdf.format(new Date());
        
        // 获取当前 Payload 信息
        Map<PayloadIndexType, IPayloadManager> payloadManagerMap = PayloadCenter.getInstance().getPayloadManager();
        PayloadBasicInfo info = null;
        if (payloadManagerMap != null) {
            IPayloadManager payloadManager = payloadManagerMap.get(selectedPayloadIndex);
            if (payloadManager != null) {
                try {
                    // 使用反射调用getPayloadBasicInfo方法
                    Object basicInfo = payloadManager.getClass().getMethod("getPayloadBasicInfo").invoke(payloadManager);
                    if (basicInfo instanceof PayloadBasicInfo) {
                        info = (PayloadBasicInfo) basicInfo;
                    }
                } catch (Exception e) {
                    LogUtils.e(TAG, "获取 Payload 信息失败: " + e.getMessage());
                }
            }
        }
        
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"type\":\"psdk_data\",");
        json.append("\"timestamp\":\"").append(timestamp).append("\",");
        json.append("\"payload_index\":\"").append(selectedPayloadIndex.name()).append("\",");
        
        if (info != null) {
            json.append("\"device_name\":\"").append(escapeJson(info.getPayloadProductName())).append("\",");
            json.append("\"device_type\":\"").append(info.getPayloadType() != null ? info.getPayloadType().name() : "UNKNOWN").append("\",");
            json.append("\"serial_number\":\"").append(escapeJson(info.getSerialNumber())).append("\",");
            json.append("\"firmware_version\":\"").append(escapeJson(info.getFirmwareVersion())).append("\",");
            json.append("\"is_connected\":").append(info.isConnected()).append(",");
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
    
    private void updateButtonStates(boolean connected) {
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
                if (messageBuffer.length() > 5000) {
                    messageBuffer.setLength(5000);
                }
                
                updateMessageDisplay();
            }
        });
    }
    
    private void appendPsdkData(String data) {
        if (getActivity() == null) {
            return;
        }
        
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                String timestamp = sdf.format(new Date());
                psdkDataBuffer.insert(0, "[" + timestamp + "] " + data + "\n");
                
                // 限制数据缓冲区大小
                if (psdkDataBuffer.length() > 5000) {
                    psdkDataBuffer.setLength(5000);
                }
                
                updateMessageDisplay();
            }
        });
    }
    
    private void updateMessageDisplay() {
        if (tvPsdkMessage != null) {
            StringBuilder display = new StringBuilder();
            display.append("=== 系统日志 ===\n");
            display.append(messageBuffer.toString());
            display.append("\n=== PSDK 数据 ===\n");
            display.append(psdkDataBuffer.toString());
            
            tvPsdkMessage.setText(display.toString());
        }
    }
    
    private void clearMessages() {
        messageBuffer.setLength(0);
        psdkDataBuffer.setLength(0);
        if (tvPsdkMessage != null) {
            tvPsdkMessage.setText(getString(R.string.uxsdk_payload_no_data));
        }
        appendMessage("消息已清空");
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // 移除监听器
        unregisterPayloadListeners();
        
        // 断开网络连接
        if (networkClient != null) {
            networkClient.shutdown();
            networkClient = null;
        }
        
        LogUtils.d(TAG, "PayloadFragment 已销毁");
    }
}