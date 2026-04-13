package dji.v5.ux.core.ui.setting.fragment;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import dji.v5.utils.common.ContextUtil;
import dji.v5.utils.common.LogUtils;
import dji.v5.utils.common.StringUtils;
import dji.v5.ux.R;
import dji.v5.ux.core.ui.setting.ui.MenuFragment;
import dji.v5.ux.payload.flightdata.FlightDataForwardingManager;
import dji.v5.ux.payload.flightdata.FlightDataJsonBuilder;

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

    private boolean isApplyingUiState = false;
    private FlightDataForwardingManager forwardingManager;

    private final RadioGroup.OnCheckedChangeListener frequencyChangeListener = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (isApplyingUiState || forwardingManager == null) {
                return;
            }

            if (checkedId == R.id.rb_5hz) {
                forwardingManager.updateFrequency(5);
            } else if (checkedId == R.id.rb_10hz) {
                forwardingManager.updateFrequency(10);
            } else {
                forwardingManager.updateFrequency(1);
            }
        }
    };

    private final FlightDataForwardingManager.Listener stateListener = new FlightDataForwardingManager.Listener() {
        @Override
        public void onStateChanged(FlightDataForwardingManager.StateSnapshot snapshot) {
            applyStateToView(snapshot);
        }
    };

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
        if (mFragmentRoot == null) {
            return;
        }

        initViews(mFragmentRoot);
        forwardingManager = FlightDataForwardingManager.getInstance();
        setupFrequencySelection();
        setupButtons();

        FlightDataForwardingManager.StateSnapshot snapshot = forwardingManager.getStateSnapshot();
        populateInputFields(snapshot);
        applyFrequencySelection(snapshot.getUpdateFrequency());
        applyStateToView(snapshot);
        forwardingManager.bindListener(stateListener);
    }

    private void initViews(View view) {
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
    }

    private void setupFrequencySelection() {
        if (rgFrequency != null) {
            rgFrequency.setOnCheckedChangeListener(null);
            rgFrequency.setOnCheckedChangeListener(frequencyChangeListener);
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

    private void populateInputFields(FlightDataForwardingManager.StateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        if (etServerIp != null) {
            etServerIp.setText(snapshot.getServerHost());
        }
        if (etServerPort != null) {
            etServerPort.setText(String.valueOf(snapshot.getServerPort()));
        }
    }

    private void applyStateToView(FlightDataForwardingManager.StateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        applyFrequencySelection(snapshot.getUpdateFrequency());
        updateNetworkStatus(snapshot.getConnectionStatus());
        updatePreview(snapshot.getPreviewJson());
        updateMessageLog(snapshot.getMessageLog());
        updateButtonStates(snapshot.isConnected(), snapshot.isForwarding());
    }

    private void applyFrequencySelection(int frequency) {
        if (rgFrequency == null || rb1Hz == null || rb5Hz == null || rb10Hz == null) {
            return;
        }

        int targetId;
        if (frequency >= 10) {
            targetId = R.id.rb_10hz;
        } else if (frequency >= 5) {
            targetId = R.id.rb_5hz;
        } else {
            targetId = R.id.rb_1hz;
        }

        if (rgFrequency.getCheckedRadioButtonId() == targetId) {
            return;
        }

        isApplyingUiState = true;
        if (targetId == R.id.rb_10hz) {
            rb10Hz.setChecked(true);
        } else if (targetId == R.id.rb_5hz) {
            rb5Hz.setChecked(true);
        } else {
            rb1Hz.setChecked(true);
        }
        isApplyingUiState = false;
    }

    private void updatePreview(String previewJson) {
        if (tvFlightDataPreview == null) {
            return;
        }

        if (previewJson == null || previewJson.trim().isEmpty()) {
            tvFlightDataPreview.setText(R.string.uxsdk_flight_data_no_data);
            return;
        }

        tvFlightDataPreview.setText(FlightDataJsonBuilder.formatJsonForDisplay(previewJson));
    }

    private void updateMessageLog(String messageLog) {
        if (tvMessageLog == null) {
            return;
        }

        if (messageLog == null || messageLog.trim().isEmpty()) {
            tvMessageLog.setText(R.string.uxsdk_flight_data_no_messages);
            return;
        }

        tvMessageLog.setText(messageLog);
    }

    private void connectToServer() {
        if (forwardingManager == null || etServerIp == null || etServerPort == null) {
            return;
        }

        String serverHost = etServerIp.getText().toString().trim();
        String portText = etServerPort.getText().toString().trim();

        etServerIp.setError(null);
        etServerPort.setError(null);

        if (serverHost.isEmpty()) {
            etServerIp.setError("Server IP required");
            return;
        }

        if (portText.isEmpty()) {
            etServerPort.setError("Port required");
            return;
        }

        try {
            int serverPort = Integer.parseInt(portText);
            if (serverPort <= 0 || serverPort > 65535) {
                etServerPort.setError("Invalid port");
                return;
            }

            forwardingManager.connect(serverHost, serverPort);
        } catch (NumberFormatException e) {
            etServerPort.setError("Invalid port");
        }
    }

    private void disconnectFromServer() {
        if (forwardingManager != null) {
            forwardingManager.disconnect();
        }
    }

    private void startDataForwarding() {
        if (forwardingManager != null) {
            forwardingManager.startForwarding();
        }
    }

    private void stopDataForwarding() {
        if (forwardingManager != null) {
            forwardingManager.stopForwarding();
        }
    }

    private void clearMessages() {
        if (forwardingManager != null) {
            forwardingManager.clearMessages();
        }
    }

    private void updateNetworkStatus(String status) {
        if (tvConnectionStatus != null) {
            tvConnectionStatus.setText(status);
        }
    }

    private void updateButtonStates(boolean connected, boolean forwarding) {
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

    @Override
    public void onDestroyView() {
        if (forwardingManager != null) {
            forwardingManager.unbindListener(stateListener);
        }

        rgFrequency = null;
        rb1Hz = null;
        rb5Hz = null;
        rb10Hz = null;
        tvFlightDataPreview = null;
        tvConnectionStatus = null;
        tvMessageLog = null;
        etServerIp = null;
        etServerPort = null;
        btnConnectServer = null;
        btnDisconnectServer = null;
        btnStartForward = null;
        btnStopForward = null;
        btnClear = null;

        super.onDestroyView();
        LogUtils.d(TAG, "FlightDataFragment view destroyed; forwarding remains active in manager.");
    }
}
