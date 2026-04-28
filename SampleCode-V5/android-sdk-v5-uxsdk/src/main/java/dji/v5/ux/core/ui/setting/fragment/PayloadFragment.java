package dji.v5.ux.core.ui.setting.fragment;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import dji.v5.manager.aircraft.payload.PayloadIndexType;
import dji.v5.utils.common.ContextUtil;
import dji.v5.utils.common.LogUtils;
import dji.v5.utils.common.StringUtils;
import dji.v5.ux.R;
import dji.v5.ux.core.ui.setting.ui.MenuFragment;
import dji.v5.ux.payload.psdk.PayloadForwardingManager;

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
    private static final String DEFAULT_SERVER_HOST = "192.168.3.31";
    private static final String DEFAULT_SERVER_PORT = "8888";

    private RadioGroup rgPsdkSelection;
    private RadioButton rbPsdkLeft;
    private RadioButton rbPsdkRight;
    private RadioButton rbPsdkUp;
    private RadioButton rbPsdkExternal;
    private RadioButton rbPsdkPort1;
    private RadioButton rbPsdkPort2;
    private RadioButton rbPsdkPort3;
    private RadioButton rbPsdkPort4;
    private RadioButton rbPsdkPort5;
    private RadioButton rbPsdkPort6;
    private RadioButton rbPsdkPort7;
    private TextView tvPayloadInfo;
    private TextView tvConnectionStatus;
    private TextView tvPsdkMessage;
    private EditText etServerIp;
    private EditText etServerPort;
    private Button btnConnectServer;
    private Button btnDisconnectServer;
    private Button btnClear;

    private PayloadIndexType selectedPayloadIndex = PayloadIndexType.PORT_1;
    private PayloadForwardingManager forwardingManager;
    private boolean isApplyingUiState = false;
    private PayloadIndexType lastAppliedPayloadIndex;
    private String lastAppliedServerHost;
    private int lastAppliedServerPort = -1;
    private Boolean lastConnectedState;
    private final PayloadForwardingManager.Listener stateListener = new PayloadForwardingManager.Listener() {
        @Override
        public void onStateChanged(PayloadForwardingManager.StateSnapshot snapshot) {
            applyStateSnapshot(snapshot);
        }
    };

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
        if (mFragmentRoot == null) {
            return;
        }

        initViews(mFragmentRoot);
        forwardingManager = PayloadForwardingManager.getInstance();
        setupPayloadSelection();
        setupButtons();
        forwardingManager.bindListener(stateListener);
        applyStateSnapshot(forwardingManager.getStateSnapshot());
    }

    private void initViews(View view) {
        rgPsdkSelection = view.findViewById(R.id.rg_psdk_selection);
        rbPsdkLeft = view.findViewById(R.id.rb_psdk_left);
        rbPsdkRight = view.findViewById(R.id.rb_psdk_right);
        rbPsdkUp = view.findViewById(R.id.rb_psdk_up);
        rbPsdkExternal = view.findViewById(R.id.rb_psdk_external);
        rbPsdkPort1 = view.findViewById(R.id.rb_psdk_port1);
        rbPsdkPort2 = view.findViewById(R.id.rb_psdk_port2);
        rbPsdkPort3 = view.findViewById(R.id.rb_psdk_port3);
        rbPsdkPort4 = view.findViewById(R.id.rb_psdk_port4);
        rbPsdkPort5 = view.findViewById(R.id.rb_psdk_port5);
        rbPsdkPort6 = view.findViewById(R.id.rb_psdk_port6);
        rbPsdkPort7 = view.findViewById(R.id.rb_psdk_port7);
        tvPayloadInfo = view.findViewById(R.id.tv_payload_info);
        tvConnectionStatus = view.findViewById(R.id.tv_connection_status);
        tvPsdkMessage = view.findViewById(R.id.tv_psdk_message);
        etServerIp = view.findViewById(R.id.et_server_ip);
        etServerPort = view.findViewById(R.id.et_server_port);
        btnConnectServer = view.findViewById(R.id.btn_connect_server);
        btnDisconnectServer = view.findViewById(R.id.btn_disconnect_server);
        btnClear = view.findViewById(R.id.btn_clear);
        lastAppliedPayloadIndex = null;
        lastAppliedServerHost = null;
        lastAppliedServerPort = -1;
        lastConnectedState = null;

        if (etServerIp != null && etServerIp.length() == 0) {
            etServerIp.setText(DEFAULT_SERVER_HOST);
        }
        if (etServerPort != null && etServerPort.length() == 0) {
            etServerPort.setText(DEFAULT_SERVER_PORT);
        }
    }

    private void setupPayloadSelection() {
        if (rgPsdkSelection == null) {
            return;
        }

        rgPsdkSelection.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (isApplyingUiState) {
                    return;
                }

                clearPortSelections();

                PayloadIndexType newIndex = selectedPayloadIndex;
                if (checkedId == R.id.rb_psdk_left) {
                    newIndex = PayloadIndexType.LEFT_OR_MAIN;
                } else if (checkedId == R.id.rb_psdk_right) {
                    newIndex = PayloadIndexType.RIGHT;
                } else if (checkedId == R.id.rb_psdk_up) {
                    newIndex = PayloadIndexType.UP;
                } else if (checkedId == R.id.rb_psdk_external) {
                    newIndex = PayloadIndexType.EXTERNAL;
                }

                requestPayloadSelection(newIndex);
            }
        });

        View.OnClickListener portClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isApplyingUiState) {
                    return;
                }

                if (rgPsdkSelection != null) {
                    rgPsdkSelection.clearCheck();
                }
                clearPortSelections();
                ((RadioButton) v).setChecked(true);

                PayloadIndexType newIndex = selectedPayloadIndex;
                if (v.getId() == R.id.rb_psdk_port1) {
                    newIndex = PayloadIndexType.PORT_1;
                } else if (v.getId() == R.id.rb_psdk_port2) {
                    newIndex = PayloadIndexType.PORT_2;
                } else if (v.getId() == R.id.rb_psdk_port3) {
                    newIndex = PayloadIndexType.PORT_3;
                } else if (v.getId() == R.id.rb_psdk_port4) {
                    newIndex = PayloadIndexType.PORT_4;
                } else if (v.getId() == R.id.rb_psdk_port5) {
                    newIndex = PayloadIndexType.PORT_5;
                } else if (v.getId() == R.id.rb_psdk_port6) {
                    newIndex = PayloadIndexType.PORT_6;
                } else if (v.getId() == R.id.rb_psdk_port7) {
                    newIndex = PayloadIndexType.PORT_7;
                }

                requestPayloadSelection(newIndex);
            }
        };

        if (rbPsdkPort1 != null) rbPsdkPort1.setOnClickListener(portClickListener);
        if (rbPsdkPort2 != null) rbPsdkPort2.setOnClickListener(portClickListener);
        if (rbPsdkPort3 != null) rbPsdkPort3.setOnClickListener(portClickListener);
        if (rbPsdkPort4 != null) rbPsdkPort4.setOnClickListener(portClickListener);
        if (rbPsdkPort5 != null) rbPsdkPort5.setOnClickListener(portClickListener);
        if (rbPsdkPort6 != null) rbPsdkPort6.setOnClickListener(portClickListener);
        if (rbPsdkPort7 != null) rbPsdkPort7.setOnClickListener(portClickListener);
    }

    private void requestPayloadSelection(PayloadIndexType newIndex) {
        if (newIndex == null || newIndex == selectedPayloadIndex || forwardingManager == null) {
            return;
        }
        forwardingManager.selectPayload(newIndex);
    }

    private void clearPortSelections() {
        if (rbPsdkPort1 != null) rbPsdkPort1.setChecked(false);
        if (rbPsdkPort2 != null) rbPsdkPort2.setChecked(false);
        if (rbPsdkPort3 != null) rbPsdkPort3.setChecked(false);
        if (rbPsdkPort4 != null) rbPsdkPort4.setChecked(false);
        if (rbPsdkPort5 != null) rbPsdkPort5.setChecked(false);
        if (rbPsdkPort6 != null) rbPsdkPort6.setChecked(false);
        if (rbPsdkPort7 != null) rbPsdkPort7.setChecked(false);
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

    private void connectToServer() {
        if (etServerIp == null || etServerPort == null || forwardingManager == null) {
            return;
        }

        String serverHost = etServerIp.getText().toString().trim();
        String portText = etServerPort.getText().toString().trim();
        if (serverHost.isEmpty() || portText.isEmpty()) {
            updateNetworkStatus("网络状态: 参数不完整");
            return;
        }

        try {
            int serverPort = Integer.parseInt(portText);
            forwardingManager.connect(serverHost, serverPort);
        } catch (NumberFormatException e) {
            updateNetworkStatus("网络状态: 端口格式错误");
        }
    }

    private void disconnectFromServer() {
        if (forwardingManager != null) {
            forwardingManager.disconnect();
        }
    }

    private void clearMessages() {
        if (forwardingManager != null) {
            forwardingManager.clearMessages();
        }
    }

    private void applyStateSnapshot(PayloadForwardingManager.StateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        selectedPayloadIndex = snapshot.getSelectedPayloadIndex();
        isApplyingUiState = true;
        try {
            applyServerConfig(snapshot);
            applyPayloadSelection(snapshot.getSelectedPayloadIndex());
        } finally {
            isApplyingUiState = false;
        }

        if (tvPayloadInfo != null) {
            String payloadInfoText = snapshot.getPayloadInfoText();
            setTextIfChanged(tvPayloadInfo, payloadInfoText == null || payloadInfoText.isEmpty()
                    ? getString(R.string.uxsdk_payload_no_device)
                    : payloadInfoText);
        }

        updateNetworkStatus(snapshot.getConnectionStatus());
        updateButtonStates(snapshot.isConnected());

        if (tvPsdkMessage != null) {
            String displayLog = snapshot.getDisplayLog();
            setTextIfChanged(tvPsdkMessage, displayLog == null || displayLog.isEmpty()
                    ? getString(R.string.uxsdk_payload_no_data)
                    : displayLog);
        }
    }

    private void applyServerConfig(PayloadForwardingManager.StateSnapshot snapshot) {
        String host = snapshot.getServerHost();
        if (host != null && !host.equals(lastAppliedServerHost)) {
            updateEditTextFromState(etServerIp, host);
            lastAppliedServerHost = host;
        }

        int serverPort = snapshot.getServerPort();
        if (serverPort != lastAppliedServerPort) {
            updateEditTextFromState(etServerPort, String.valueOf(serverPort));
            lastAppliedServerPort = serverPort;
        }
    }

    private void applyPayloadSelection(PayloadIndexType payloadIndexType) {
        if (payloadIndexType == null) {
            return;
        }
        if (payloadIndexType == lastAppliedPayloadIndex) {
            return;
        }

        lastAppliedPayloadIndex = payloadIndexType;
        if (rgPsdkSelection != null) {
            rgPsdkSelection.clearCheck();
        }
        clearPortSelections();

        switch (payloadIndexType) {
            case LEFT_OR_MAIN:
                if (rbPsdkLeft != null) rbPsdkLeft.setChecked(true);
                break;
            case RIGHT:
                if (rbPsdkRight != null) rbPsdkRight.setChecked(true);
                break;
            case UP:
                if (rbPsdkUp != null) rbPsdkUp.setChecked(true);
                break;
            case EXTERNAL:
                if (rbPsdkExternal != null) rbPsdkExternal.setChecked(true);
                break;
            case PORT_2:
                if (rbPsdkPort2 != null) rbPsdkPort2.setChecked(true);
                break;
            case PORT_3:
                if (rbPsdkPort3 != null) rbPsdkPort3.setChecked(true);
                break;
            case PORT_4:
                if (rbPsdkPort4 != null) rbPsdkPort4.setChecked(true);
                break;
            case PORT_5:
                if (rbPsdkPort5 != null) rbPsdkPort5.setChecked(true);
                break;
            case PORT_6:
                if (rbPsdkPort6 != null) rbPsdkPort6.setChecked(true);
                break;
            case PORT_7:
                if (rbPsdkPort7 != null) rbPsdkPort7.setChecked(true);
                break;
            case PORT_1:
            default:
                if (rbPsdkPort1 != null) rbPsdkPort1.setChecked(true);
                break;
        }
    }

    private void updateNetworkStatus(String status) {
        if (tvConnectionStatus != null) {
            setTextIfChanged(tvConnectionStatus, status);
        }
    }

    private void updateButtonStates(boolean connected) {
        if (lastConnectedState != null && lastConnectedState.booleanValue() == connected) {
            return;
        }
        lastConnectedState = connected;

        if (btnConnectServer != null) {
            btnConnectServer.setEnabled(!connected);
        }
        if (btnDisconnectServer != null) {
            btnDisconnectServer.setEnabled(connected);
        }
    }

    private void updateEditTextFromState(EditText editText, String value) {
        if (editText == null || value == null || editText.hasFocus()) {
            return;
        }
        String currentValue = editText.getText().toString();
        if (!value.equals(currentValue)) {
            editText.setText(value);
            editText.setSelection(editText.length());
        }
    }

    private void setTextIfChanged(TextView textView, String value) {
        if (textView == null) {
            return;
        }

        String safeValue = value == null ? "" : value;
        CharSequence currentText = textView.getText();
        if (currentText == null || !safeValue.contentEquals(currentText)) {
            textView.setText(safeValue);
        }
    }

    @Override
    public void onDestroyView() {
        if (forwardingManager != null) {
            forwardingManager.unbindListener(stateListener);
        }
        super.onDestroyView();
        LogUtils.d(TAG, "PayloadFragment view destroyed; payload forwarding remains active in manager.");
    }
}
