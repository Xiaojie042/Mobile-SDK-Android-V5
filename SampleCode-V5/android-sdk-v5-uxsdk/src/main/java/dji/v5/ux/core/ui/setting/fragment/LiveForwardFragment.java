package dji.v5.ux.core.ui.setting.fragment;

import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.List;
import java.util.Map;

import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.datacenter.MediaDataCenter;
import dji.v5.manager.datacenter.livestream.LiveStreamSettings;
import dji.v5.manager.datacenter.livestream.LiveStreamStatus;
import dji.v5.manager.datacenter.livestream.LiveStreamStatusListener;
import dji.v5.manager.datacenter.livestream.LiveStreamType;
import dji.v5.manager.datacenter.livestream.StreamQuality;
import dji.v5.manager.datacenter.livestream.settings.GB28181Settings;
import dji.v5.manager.datacenter.livestream.settings.RtmpSettings;
import dji.v5.manager.interfaces.ICameraStreamManager;
import dji.v5.manager.interfaces.ILiveStreamManager;
import dji.v5.utils.common.ContextUtil;
import dji.v5.utils.common.DjiSharedPreferencesManager;
import dji.v5.utils.common.LogUtils;
import dji.v5.utils.common.StringUtils;
import dji.v5.ux.R;
import dji.v5.ux.core.ui.setting.ui.MenuFragment;

public class LiveForwardFragment extends MenuFragment {

    private static final String TAG = "LiveForwardFragment";
    private static final String PREF_RTMP_URL = "uxsdk-live-forward-rtmp-url";
    private static final String PREF_GB28181_CONFIG = "uxsdk-live-forward-gb28181-config";
    private static final String CONFIG_SEPARATOR = "^_^";
    private static final int DEFAULT_GB28181_PORT = 15060;

    private ILiveStreamManager streamManager;
    private ICameraStreamManager cameraStreamManager;
    private RadioGroup rgProtocol;
    private RadioGroup rgCamera;
    private RadioGroup rgQuality;
    private LinearLayout layoutRtmp;
    private LinearLayout layoutGb28181;
    private EditText etRtmpUrl;
    private EditText etGbServerIp;
    private EditText etGbServerPort;
    private EditText etGbServerId;
    private EditText etGbAgentId;
    private EditText etGbChannel;
    private EditText etGbLocalPort;
    private EditText etGbPassword;
    private TextView tvStatus;
    private Button btnStart;
    private Button btnStop;

    private final LiveStreamStatusListener liveStreamStatusListener = new LiveStreamStatusListener() {
        @Override
        public void onLiveStreamStatusUpdate(final LiveStreamStatus status) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateStatus(status);
                }
            });
        }

        @Override
        public void onError(final IDJIError error) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setStatusText(StringUtils.getResStr(ContextUtil.getContext(),
                            R.string.uxsdk_live_forward_error_message, getErrorDescription(error)));
                }
            });
        }
    };

    private final ICameraStreamManager.AvailableCameraUpdatedListener availableCameraUpdatedListener =
            new ICameraStreamManager.AvailableCameraUpdatedListener() {
                @Override
                public void onAvailableCameraUpdated(final List<ComponentIndexType> cameraList) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateAvailableCameraList(cameraList);
                        }
                    });
                }

                @Override
                public void onCameraStreamEnableUpdate(Map<ComponentIndexType, Boolean> cameraStreamEnableMap) {
                    // No-op. This page only needs the available source list.
                }
            };

    @Override
    protected String getPreferencesTitle() {
        return StringUtils.getResStr(ContextUtil.getContext(), R.string.uxsdk_live_forward_title);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.uxsdk_fragment_setting_menu_live_forward_layout;
    }

    @Override
    protected void onPrepareView() {
        super.onPrepareView();
        if (mFragmentRoot == null) {
            return;
        }

        streamManager = MediaDataCenter.getInstance().getLiveStreamManager();
        cameraStreamManager = MediaDataCenter.getInstance().getCameraStreamManager();
        initViews(mFragmentRoot);
        populateInputFields();
        setupSelections();
        setupButtons();

        if (streamManager != null) {
            streamManager.addLiveStreamStatusListener(liveStreamStatusListener);
            updateButtonStates(streamManager.isStreaming());
        }
        if (cameraStreamManager != null) {
            cameraStreamManager.addAvailableCameraUpdatedListener(availableCameraUpdatedListener);
        }
    }

    private void initViews(View view) {
        rgProtocol = view.findViewById(R.id.rg_live_forward_protocol);
        rgCamera = view.findViewById(R.id.rg_live_forward_camera);
        rgQuality = view.findViewById(R.id.rg_live_forward_quality);
        layoutRtmp = view.findViewById(R.id.layout_live_forward_rtmp);
        layoutGb28181 = view.findViewById(R.id.layout_live_forward_gb28181);
        etRtmpUrl = view.findViewById(R.id.et_live_forward_rtmp_url);
        etGbServerIp = view.findViewById(R.id.et_live_forward_gb_server_ip);
        etGbServerPort = view.findViewById(R.id.et_live_forward_gb_server_port);
        etGbServerId = view.findViewById(R.id.et_live_forward_gb_server_id);
        etGbAgentId = view.findViewById(R.id.et_live_forward_gb_agent_id);
        etGbChannel = view.findViewById(R.id.et_live_forward_gb_channel);
        etGbLocalPort = view.findViewById(R.id.et_live_forward_gb_local_port);
        etGbPassword = view.findViewById(R.id.et_live_forward_gb_password);
        tvStatus = view.findViewById(R.id.tv_live_forward_status);
        btnStart = view.findViewById(R.id.btn_live_forward_start);
        btnStop = view.findViewById(R.id.btn_live_forward_stop);
    }

    private void populateInputFields() {
        String rtmpUrl = DjiSharedPreferencesManager.getString(ContextUtil.getContext(),
                PREF_RTMP_URL, "rtmp://192.168.1.100/live/drone");
        setEditText(etRtmpUrl, rtmpUrl);

        String gbConfig = DjiSharedPreferencesManager.getString(ContextUtil.getContext(),
                PREF_GB28181_CONFIG, "");
        if (!TextUtils.isEmpty(gbConfig)) {
            String[] configs = gbConfig.split("\\^_\\^", -1);
            if (configs.length >= 7) {
                setEditText(etGbServerIp, configs[0]);
                setEditText(etGbServerPort, configs[1]);
                setEditText(etGbServerId, configs[2]);
                setEditText(etGbAgentId, configs[3]);
                setEditText(etGbChannel, configs[4]);
                setEditText(etGbLocalPort, configs[5]);
                setEditText(etGbPassword, configs[6]);
                return;
            }
        }
        setEditText(etGbServerPort, String.valueOf(DEFAULT_GB28181_PORT));
        setEditText(etGbLocalPort, String.valueOf(DEFAULT_GB28181_PORT));
    }

    private void setupSelections() {
        if (rgProtocol != null) {
            rgProtocol.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    updateProtocolVisibility();
                }
            });
            rgProtocol.check(R.id.rb_live_forward_gb28181);
            updateProtocolVisibility();
        }

        if (rgCamera != null) {
            rgCamera.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    if (streamManager != null) {
                        streamManager.setCameraIndex(getSelectedCameraIndex());
                    }
                }
            });
            rgCamera.check(R.id.rb_live_forward_camera_left);
        }

        if (rgQuality != null) {
            rgQuality.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    if (streamManager != null) {
                        streamManager.setLiveStreamQuality(getSelectedQuality());
                    }
                }
            });
            rgQuality.check(R.id.rb_live_forward_quality_hd);
        }
    }

    private void setupButtons() {
        if (btnStart != null) {
            btnStart.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startLiveForward();
                }
            });
        }
        if (btnStop != null) {
            btnStop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopLiveForward();
                }
            });
        }
    }

    private void updateProtocolVisibility() {
        boolean isRtmp = rgProtocol != null && rgProtocol.getCheckedRadioButtonId() == R.id.rb_live_forward_rtmp;
        if (layoutRtmp != null) {
            layoutRtmp.setVisibility(isRtmp ? View.VISIBLE : View.GONE);
        }
        if (layoutGb28181 != null) {
            layoutGb28181.setVisibility(isRtmp ? View.GONE : View.VISIBLE);
        }
    }

    private void startLiveForward() {
        if (streamManager == null) {
            return;
        }

        clearInputErrors();
        streamManager.setCameraIndex(getSelectedCameraIndex());
        streamManager.setLiveStreamQuality(getSelectedQuality());
        streamManager.setLiveStreamScaleType(ICameraStreamManager.ScaleType.CENTER_CROP);

        if (rgProtocol != null && rgProtocol.getCheckedRadioButtonId() == R.id.rb_live_forward_rtmp) {
            if (!applyRtmpConfig()) {
                return;
            }
        } else {
            if (!applyGb28181Config()) {
                return;
            }
        }

        setStatusText(StringUtils.getResStr(ContextUtil.getContext(), R.string.uxsdk_live_forward_starting));
        streamManager.startStream(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setStatusText(StringUtils.getResStr(ContextUtil.getContext(),
                                R.string.uxsdk_live_forward_start_success));
                        updateButtonStates(true);
                    }
                });
            }

            @Override
            public void onFailure(final IDJIError error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setStatusText(StringUtils.getResStr(ContextUtil.getContext(),
                                R.string.uxsdk_live_forward_start_failed, getErrorDescription(error)));
                        updateButtonStates(false);
                    }
                });
            }
        });
    }

    private boolean applyRtmpConfig() {
        String rtmpUrl = getInputText(etRtmpUrl);
        if (TextUtils.isEmpty(rtmpUrl)) {
            setInputError(etRtmpUrl, R.string.uxsdk_live_forward_error_required);
            return false;
        }

        LiveStreamSettings settings = new LiveStreamSettings.Builder()
                .setLiveStreamType(LiveStreamType.RTMP)
                .setRtmpSettings(new RtmpSettings.Builder()
                        .setUrl(rtmpUrl)
                        .build())
                .build();
        streamManager.setLiveStreamSettings(settings);
        DjiSharedPreferencesManager.putString(ContextUtil.getContext(), PREF_RTMP_URL, rtmpUrl);
        LogUtils.d(TAG, "Apply RTMP live forward config: " + rtmpUrl);
        return true;
    }

    private boolean applyGb28181Config() {
        String serverIp = getInputText(etGbServerIp);
        String serverPortText = getInputText(etGbServerPort);
        String serverId = getInputText(etGbServerId);
        String agentId = getInputText(etGbAgentId);
        String channel = getInputText(etGbChannel);
        String localPortText = getInputText(etGbLocalPort);
        String password = getInputText(etGbPassword);

        if (TextUtils.isEmpty(serverIp)) {
            setInputError(etGbServerIp, R.string.uxsdk_live_forward_error_required);
            return false;
        }
        if (TextUtils.isEmpty(serverId)) {
            setInputError(etGbServerId, R.string.uxsdk_live_forward_error_required);
            return false;
        }
        if (TextUtils.isEmpty(agentId)) {
            setInputError(etGbAgentId, R.string.uxsdk_live_forward_error_required);
            return false;
        }
        if (TextUtils.isEmpty(channel)) {
            setInputError(etGbChannel, R.string.uxsdk_live_forward_error_required);
            return false;
        }
        if (TextUtils.isEmpty(password)) {
            setInputError(etGbPassword, R.string.uxsdk_live_forward_error_required);
            return false;
        }

        int serverPort = parsePort(etGbServerPort, serverPortText);
        if (serverPort <= 0) {
            return false;
        }
        int localPort = parsePort(etGbLocalPort, localPortText);
        if (localPort <= 0) {
            return false;
        }

        GB28181Settings gb28181Settings = new GB28181Settings.Builder()
                .setServerIP(serverIp)
                .setServerPort(serverPort)
                .setServerID(serverId)
                .setAgentID(agentId)
                .setChannel(channel)
                .setLocalPort(localPort)
                .setPassword(password)
                .build();
        LiveStreamSettings settings = new LiveStreamSettings.Builder()
                .setLiveStreamType(LiveStreamType.GB28181)
                .setGB28181Settings(gb28181Settings)
                .build();
        streamManager.setLiveStreamSettings(settings);

        String gbConfig = serverIp + CONFIG_SEPARATOR + serverPort + CONFIG_SEPARATOR
                + serverId + CONFIG_SEPARATOR + agentId + CONFIG_SEPARATOR
                + channel + CONFIG_SEPARATOR + localPort + CONFIG_SEPARATOR + password;
        DjiSharedPreferencesManager.putString(ContextUtil.getContext(), PREF_GB28181_CONFIG, gbConfig);
        LogUtils.d(TAG, "Apply GB28181 live forward config: " + serverIp + ":" + serverPort);
        return true;
    }

    private void stopLiveForward() {
        if (streamManager == null) {
            return;
        }
        streamManager.stopStream(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setStatusText(StringUtils.getResStr(ContextUtil.getContext(),
                                R.string.uxsdk_live_forward_stop_success));
                        updateButtonStates(false);
                    }
                });
            }

            @Override
            public void onFailure(final IDJIError error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setStatusText(StringUtils.getResStr(ContextUtil.getContext(),
                                R.string.uxsdk_live_forward_stop_failed, getErrorDescription(error)));
                    }
                });
            }
        });
    }

    private ComponentIndexType getSelectedCameraIndex() {
        if (rgCamera == null) {
            return ComponentIndexType.LEFT_OR_MAIN;
        }
        RadioButton selectedButton = mFragmentRoot.findViewById(rgCamera.getCheckedRadioButtonId());
        if (selectedButton == null || selectedButton.getTag() == null) {
            return ComponentIndexType.LEFT_OR_MAIN;
        }
        try {
            return ComponentIndexType.find(Integer.parseInt(String.valueOf(selectedButton.getTag())));
        } catch (NumberFormatException e) {
            return ComponentIndexType.LEFT_OR_MAIN;
        }
    }

    private StreamQuality getSelectedQuality() {
        if (rgQuality == null) {
            return StreamQuality.HD;
        }
        int checkedId = rgQuality.getCheckedRadioButtonId();
        if (checkedId == R.id.rb_live_forward_quality_sd) {
            return StreamQuality.SD;
        }
        if (checkedId == R.id.rb_live_forward_quality_fhd) {
            return StreamQuality.FULL_HD;
        }
        return StreamQuality.HD;
    }

    private void updateStatus(LiveStreamStatus status) {
        if (status == null) {
            setStatusText(StringUtils.getResStr(ContextUtil.getContext(), R.string.uxsdk_live_forward_status_idle));
            updateButtonStates(false);
            return;
        }

        String resolution = "--";
        if (status.getResolution() != null) {
            resolution = status.getResolution().getWidth() + "x" + status.getResolution().getHeight();
        }
        String statusText = StringUtils.getResStr(ContextUtil.getContext(),
                R.string.uxsdk_live_forward_status_template,
                status.isStreaming(),
                status.getFps(),
                status.getVbps(),
                status.getPacketLoss(),
                status.getRtt(),
                resolution);
        setStatusText(statusText);
        updateButtonStates(status.isStreaming());
    }

    private void updateAvailableCameraList(List<ComponentIndexType> cameraList) {
        if (rgCamera == null || cameraList == null || cameraList.isEmpty()) {
            return;
        }

        View firstVisibleView = null;
        boolean checkedCameraHidden = false;
        for (int i = 0; i < rgCamera.getChildCount(); i++) {
            View child = rgCamera.getChildAt(i);
            ComponentIndexType cameraIndex = ComponentIndexType.UNKNOWN;
            Object tag = child.getTag();
            if (tag != null) {
                try {
                    cameraIndex = ComponentIndexType.find(Integer.parseInt(String.valueOf(tag)));
                } catch (NumberFormatException ignored) {
                    cameraIndex = ComponentIndexType.UNKNOWN;
                }
            }
            boolean visible = cameraList.contains(cameraIndex);
            child.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible && firstVisibleView == null) {
                firstVisibleView = child;
            }
            if (!visible && rgCamera.getCheckedRadioButtonId() == child.getId()) {
                checkedCameraHidden = true;
            }
        }

        if (checkedCameraHidden && firstVisibleView != null) {
            rgCamera.check(firstVisibleView.getId());
        }
    }

    private int parsePort(EditText editText, String portText) {
        try {
            int port = Integer.parseInt(portText);
            if (port <= 0 || port > 65535) {
                setInputError(editText, R.string.uxsdk_live_forward_error_invalid_port);
                return -1;
            }
            return port;
        } catch (NumberFormatException e) {
            setInputError(editText, R.string.uxsdk_live_forward_error_invalid_port);
            return -1;
        }
    }

    private void clearInputErrors() {
        clearInputError(etRtmpUrl);
        clearInputError(etGbServerIp);
        clearInputError(etGbServerPort);
        clearInputError(etGbServerId);
        clearInputError(etGbAgentId);
        clearInputError(etGbChannel);
        clearInputError(etGbLocalPort);
        clearInputError(etGbPassword);
    }

    private void updateButtonStates(boolean streaming) {
        if (btnStart != null) {
            btnStart.setEnabled(!streaming);
        }
        if (btnStop != null) {
            btnStop.setEnabled(streaming);
        }
        if (rgProtocol != null) {
            setGroupEnabled(rgProtocol, !streaming);
        }
    }

    private void setGroupEnabled(RadioGroup group, boolean enabled) {
        group.setEnabled(enabled);
        for (int i = 0; i < group.getChildCount(); i++) {
            group.getChildAt(i).setEnabled(enabled);
        }
    }

    private String getInputText(EditText editText) {
        return editText == null ? "" : editText.getText().toString().trim();
    }

    private void setEditText(EditText editText, String value) {
        if (editText == null || value == null) {
            return;
        }
        editText.setText(value);
        editText.setSelection(editText.length());
    }

    private void setInputError(EditText editText, int stringRes) {
        if (editText != null) {
            editText.setError(StringUtils.getResStr(ContextUtil.getContext(), stringRes));
        }
    }

    private void clearInputError(EditText editText) {
        if (editText != null) {
            editText.setError(null);
        }
    }

    private void setStatusText(String text) {
        if (tvStatus != null) {
            tvStatus.setText(text);
        }
    }

    private String getErrorDescription(IDJIError error) {
        return error == null ? "--" : error.description();
    }

    private void runOnUiThread(Runnable runnable) {
        if (getActivity() != null && runnable != null) {
            getActivity().runOnUiThread(runnable);
        }
    }

    @Override
    public void onDestroyView() {
        if (streamManager != null) {
            streamManager.removeLiveStreamStatusListener(liveStreamStatusListener);
        }
        if (cameraStreamManager != null) {
            cameraStreamManager.removeAvailableCameraUpdatedListener(availableCameraUpdatedListener);
        }

        streamManager = null;
        cameraStreamManager = null;
        rgProtocol = null;
        rgCamera = null;
        rgQuality = null;
        layoutRtmp = null;
        layoutGb28181 = null;
        etRtmpUrl = null;
        etGbServerIp = null;
        etGbServerPort = null;
        etGbServerId = null;
        etGbAgentId = null;
        etGbChannel = null;
        etGbLocalPort = null;
        etGbPassword = null;
        tvStatus = null;
        btnStart = null;
        btnStop = null;

        super.onDestroyView();
    }
}
