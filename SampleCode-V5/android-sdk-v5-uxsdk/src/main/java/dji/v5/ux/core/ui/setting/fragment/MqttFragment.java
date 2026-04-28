package dji.v5.ux.core.ui.setting.fragment;

import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import dji.v5.utils.common.ContextUtil;
import dji.v5.utils.common.LogUtils;
import dji.v5.utils.common.StringUtils;
import dji.v5.ux.R;
import dji.v5.ux.core.ui.setting.ui.MenuFragment;
import dji.v5.ux.payload.mqtt.MqttForwardingManager;

public class MqttFragment extends MenuFragment {

    private static final String TAG = "MqttFragment";

    private EditText etBrokerHost;
    private EditText etBrokerPort;
    private EditText etClientId;
    private EditText etUsername;
    private EditText etPassword;
    private EditText etFlightTopic;
    private EditText etPsdkTopic;
    private RadioGroup rgFrequency;
    private RadioButton rb1Hz;
    private RadioButton rb5Hz;
    private RadioButton rb10Hz;
    private CheckBox cbFlightData;
    private CheckBox cbPsdkData;
    private TextView tvConnectionStatus;
    private TextView tvPreview;
    private TextView tvMessageLog;
    private Button btnConnect;
    private Button btnDisconnect;
    private Button btnStartForward;
    private Button btnStopForward;
    private Button btnClear;

    private boolean isApplyingUiState = false;
    private Boolean lastConnectedState;
    private Boolean lastForwardingState;
    private Boolean lastHasDataSource;
    private MqttForwardingManager forwardingManager;

    private final MqttForwardingManager.Listener stateListener = new MqttForwardingManager.Listener() {
        @Override
        public void onStateChanged(MqttForwardingManager.StateSnapshot snapshot) {
            applyStateToView(snapshot);
        }
    };

    private final RadioGroup.OnCheckedChangeListener frequencyChangeListener = new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            if (isApplyingUiState || forwardingManager == null) {
                return;
            }

            if (checkedId == R.id.rb_mqtt_5hz) {
                forwardingManager.updateFrequency(5);
            } else if (checkedId == R.id.rb_mqtt_10hz) {
                forwardingManager.updateFrequency(10);
            } else {
                forwardingManager.updateFrequency(1);
            }
        }
    };

    private final View.OnClickListener sourceClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (isApplyingUiState || forwardingManager == null || cbFlightData == null || cbPsdkData == null) {
                return;
            }
            forwardingManager.updateSourceEnabled(cbFlightData.isChecked(), cbPsdkData.isChecked());
        }
    };

    @Override
    protected String getPreferencesTitle() {
        return StringUtils.getResStr(ContextUtil.getContext(), R.string.uxsdk_mqtt_setting_title);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.uxsdk_fragment_setting_menu_mqtt_layout;
    }

    @Override
    protected void onPrepareView() {
        super.onPrepareView();
        if (mFragmentRoot == null) {
            return;
        }

        initViews(mFragmentRoot);
        forwardingManager = MqttForwardingManager.getInstance();
        setupFrequencySelection();
        setupSourceSelection();
        setupButtons();

        MqttForwardingManager.StateSnapshot snapshot = forwardingManager.getStateSnapshot();
        populateInputFields(snapshot);
        applyStateToView(snapshot);
        forwardingManager.bindListener(stateListener);
    }

    private void initViews(View view) {
        etBrokerHost = view.findViewById(R.id.et_mqtt_host);
        etBrokerPort = view.findViewById(R.id.et_mqtt_port);
        etClientId = view.findViewById(R.id.et_mqtt_client_id);
        etUsername = view.findViewById(R.id.et_mqtt_username);
        etPassword = view.findViewById(R.id.et_mqtt_password);
        etFlightTopic = view.findViewById(R.id.et_mqtt_flight_topic);
        etPsdkTopic = view.findViewById(R.id.et_mqtt_psdk_topic);
        rgFrequency = view.findViewById(R.id.rg_mqtt_frequency);
        rb1Hz = view.findViewById(R.id.rb_mqtt_1hz);
        rb5Hz = view.findViewById(R.id.rb_mqtt_5hz);
        rb10Hz = view.findViewById(R.id.rb_mqtt_10hz);
        cbFlightData = view.findViewById(R.id.cb_mqtt_flight_data);
        cbPsdkData = view.findViewById(R.id.cb_mqtt_psdk_data);
        tvConnectionStatus = view.findViewById(R.id.tv_mqtt_connection_status);
        tvPreview = view.findViewById(R.id.tv_mqtt_preview);
        tvMessageLog = view.findViewById(R.id.tv_mqtt_message_log);
        btnConnect = view.findViewById(R.id.btn_mqtt_connect);
        btnDisconnect = view.findViewById(R.id.btn_mqtt_disconnect);
        btnStartForward = view.findViewById(R.id.btn_mqtt_start_forward);
        btnStopForward = view.findViewById(R.id.btn_mqtt_stop_forward);
        btnClear = view.findViewById(R.id.btn_mqtt_clear);
        lastConnectedState = null;
        lastForwardingState = null;
        lastHasDataSource = null;
    }

    private void setupFrequencySelection() {
        if (rgFrequency != null) {
            rgFrequency.setOnCheckedChangeListener(null);
            rgFrequency.setOnCheckedChangeListener(frequencyChangeListener);
        }
    }

    private void setupSourceSelection() {
        if (cbFlightData != null) {
            cbFlightData.setOnClickListener(sourceClickListener);
        }
        if (cbPsdkData != null) {
            cbPsdkData.setOnClickListener(sourceClickListener);
        }
    }

    private void setupButtons() {
        if (btnConnect != null) {
            btnConnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    connectToBroker();
                }
            });
        }
        if (btnDisconnect != null) {
            btnDisconnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    disconnectFromBroker();
                }
            });
        }
        if (btnStartForward != null) {
            btnStartForward.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startForwarding();
                }
            });
        }
        if (btnStopForward != null) {
            btnStopForward.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopForwarding();
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

    private void populateInputFields(MqttForwardingManager.StateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        setEditText(etBrokerHost, snapshot.getBrokerHost());
        setEditText(etBrokerPort, String.valueOf(snapshot.getBrokerPort()));
        setEditText(etClientId, snapshot.getClientId());
        setEditText(etUsername, snapshot.getUsername());
        setEditText(etPassword, snapshot.getPassword());
        setEditText(etFlightTopic, snapshot.getFlightTopic());
        setEditText(etPsdkTopic, snapshot.getPsdkTopic());
    }

    private void applyStateToView(MqttForwardingManager.StateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        applyFrequencySelection(snapshot.getUpdateFrequency());
        applySourceSelection(snapshot.isFlightDataEnabled(), snapshot.isPsdkDataEnabled());
        setTextIfChanged(tvConnectionStatus, snapshot.getConnectionStatus()
                + " | 已发布: " + snapshot.getPublishedCount());
        setTextIfChanged(tvPreview, snapshot.getPreviewText() == null || snapshot.getPreviewText().trim().isEmpty()
                ? getString(R.string.uxsdk_mqtt_no_data)
                : snapshot.getPreviewText());
        setTextIfChanged(tvMessageLog, snapshot.getMessageLog() == null || snapshot.getMessageLog().trim().isEmpty()
                ? getString(R.string.uxsdk_mqtt_no_messages)
                : snapshot.getMessageLog());
        updateButtonStates(snapshot.isConnected(), snapshot.isForwarding(),
                snapshot.isFlightDataEnabled() || snapshot.isPsdkDataEnabled());
    }

    private void applyFrequencySelection(int frequency) {
        if (rgFrequency == null || rb1Hz == null || rb5Hz == null || rb10Hz == null) {
            return;
        }

        int targetId;
        if (frequency >= 10) {
            targetId = R.id.rb_mqtt_10hz;
        } else if (frequency >= 5) {
            targetId = R.id.rb_mqtt_5hz;
        } else {
            targetId = R.id.rb_mqtt_1hz;
        }

        if (rgFrequency.getCheckedRadioButtonId() == targetId) {
            return;
        }

        isApplyingUiState = true;
        try {
            if (targetId == R.id.rb_mqtt_10hz) {
                rb10Hz.setChecked(true);
            } else if (targetId == R.id.rb_mqtt_5hz) {
                rb5Hz.setChecked(true);
            } else {
                rb1Hz.setChecked(true);
            }
        } finally {
            isApplyingUiState = false;
        }
    }

    private void applySourceSelection(boolean flightEnabled, boolean psdkEnabled) {
        isApplyingUiState = true;
        try {
            if (cbFlightData != null && cbFlightData.isChecked() != flightEnabled) {
                cbFlightData.setChecked(flightEnabled);
            }
            if (cbPsdkData != null && cbPsdkData.isChecked() != psdkEnabled) {
                cbPsdkData.setChecked(psdkEnabled);
            }
        } finally {
            isApplyingUiState = false;
        }
    }

    private void connectToBroker() {
        if (forwardingManager == null
                || etBrokerHost == null
                || etBrokerPort == null
                || etClientId == null
                || etFlightTopic == null
                || etPsdkTopic == null
                || cbFlightData == null
                || cbPsdkData == null) {
            return;
        }

        String host = etBrokerHost.getText().toString().trim();
        String portText = etBrokerPort.getText().toString().trim();
        String clientId = etClientId.getText().toString().trim();
        String flightTopic = etFlightTopic.getText().toString().trim();
        String psdkTopic = etPsdkTopic.getText().toString().trim();
        String username = etUsername == null ? "" : etUsername.getText().toString().trim();
        String password = etPassword == null ? "" : etPassword.getText().toString();
        boolean uploadFlightData = cbFlightData.isChecked();
        boolean uploadPsdkData = cbPsdkData.isChecked();

        clearInputErrors();

        if (host.isEmpty()) {
            etBrokerHost.setError(getString(R.string.uxsdk_mqtt_error_required));
            return;
        }
        if (clientId.isEmpty()) {
            etClientId.setError(getString(R.string.uxsdk_mqtt_error_required));
            return;
        }
        if (!uploadFlightData && !uploadPsdkData) {
            setTextIfChanged(tvConnectionStatus, getString(R.string.uxsdk_mqtt_error_source_required));
            return;
        }
        if (uploadFlightData && flightTopic.isEmpty()) {
            etFlightTopic.setError(getString(R.string.uxsdk_mqtt_error_required));
            return;
        }
        if (uploadPsdkData && psdkTopic.isEmpty()) {
            etPsdkTopic.setError(getString(R.string.uxsdk_mqtt_error_required));
            return;
        }

        try {
            int port = Integer.parseInt(portText);
            if (port <= 0 || port > 65535) {
                etBrokerPort.setError(getString(R.string.uxsdk_mqtt_error_invalid_port));
                return;
            }
            forwardingManager.connect(host, port, clientId, username, password, flightTopic, psdkTopic,
                    getSelectedFrequency(), uploadFlightData, uploadPsdkData);
        } catch (NumberFormatException e) {
            etBrokerPort.setError(getString(R.string.uxsdk_mqtt_error_invalid_port));
        }
    }

    private void disconnectFromBroker() {
        if (forwardingManager != null) {
            forwardingManager.disconnect();
        }
    }

    private void startForwarding() {
        if (forwardingManager != null) {
            forwardingManager.startForwarding();
        }
    }

    private void stopForwarding() {
        if (forwardingManager != null) {
            forwardingManager.stopForwarding();
        }
    }

    private void clearMessages() {
        if (forwardingManager != null) {
            forwardingManager.clearMessages();
        }
    }

    private int getSelectedFrequency() {
        if (rgFrequency == null) {
            return 1;
        }
        int checkedId = rgFrequency.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_mqtt_10hz) {
            return 10;
        }
        if (checkedId == R.id.rb_mqtt_5hz) {
            return 5;
        }
        return 1;
    }

    private void clearInputErrors() {
        if (etBrokerHost != null) etBrokerHost.setError(null);
        if (etBrokerPort != null) etBrokerPort.setError(null);
        if (etClientId != null) etClientId.setError(null);
        if (etFlightTopic != null) etFlightTopic.setError(null);
        if (etPsdkTopic != null) etPsdkTopic.setError(null);
    }

    private void updateButtonStates(boolean connected, boolean forwarding, boolean hasDataSource) {
        if (lastConnectedState != null
                && lastConnectedState.booleanValue() == connected
                && lastForwardingState != null
                && lastForwardingState.booleanValue() == forwarding
                && lastHasDataSource != null
                && lastHasDataSource.booleanValue() == hasDataSource) {
            return;
        }
        lastConnectedState = connected;
        lastForwardingState = forwarding;
        lastHasDataSource = hasDataSource;

        if (btnConnect != null) {
            btnConnect.setEnabled(!connected);
        }
        if (btnDisconnect != null) {
            btnDisconnect.setEnabled(connected);
        }
        if (btnStartForward != null) {
            btnStartForward.setEnabled(connected && !forwarding && hasDataSource);
        }
        if (btnStopForward != null) {
            btnStopForward.setEnabled(forwarding);
        }
    }

    private void setEditText(EditText editText, String value) {
        if (editText == null || value == null) {
            return;
        }
        if (!value.equals(editText.getText().toString())) {
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

        etBrokerHost = null;
        etBrokerPort = null;
        etClientId = null;
        etUsername = null;
        etPassword = null;
        etFlightTopic = null;
        etPsdkTopic = null;
        rgFrequency = null;
        rb1Hz = null;
        rb5Hz = null;
        rb10Hz = null;
        cbFlightData = null;
        cbPsdkData = null;
        tvConnectionStatus = null;
        tvPreview = null;
        tvMessageLog = null;
        btnConnect = null;
        btnDisconnect = null;
        btnStartForward = null;
        btnStopForward = null;
        btnClear = null;

        super.onDestroyView();
        LogUtils.d(TAG, "MqttFragment view destroyed; MQTT forwarding remains active in manager.");
    }
}
