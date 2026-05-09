package dji.v5.ux.core.ui.setting.fragment;

import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.manager.datacenter.MediaDataCenter;
import dji.v5.manager.datacenter.livestream.LiveStreamSettings;
import dji.v5.manager.datacenter.livestream.LiveStreamStatus;
import dji.v5.manager.datacenter.livestream.LiveStreamStatusListener;
import dji.v5.manager.datacenter.livestream.LiveStreamType;
import dji.v5.manager.datacenter.livestream.LiveVideoBitrateMode;
import dji.v5.manager.datacenter.livestream.StreamQuality;
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
    private static final int MIN_VIDEO_BITRATE = 2 * 1024 * 1024;
    private static final int MAX_VIDEO_BITRATE = 16 * 1024 * 1024;
    private static final int DEFAULT_VIDEO_BITRATE_PROGRESS = 20;

    private ILiveStreamManager streamManager;
    private ICameraStreamManager cameraStreamManager;
    private RadioGroup rgCamera;
    private RadioGroup rgQuality;
    private RadioGroup rgBitrate;
    private EditText etRtmpUrl;
    private TextView tvStatus;
    private TextView tvBitrateValue;
    private SeekBar sbBitrate;
    private Button btnStart;
    private Button btnStop;
    private boolean isBindingUiState;

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
                    // No-op.
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
        setupSelections();
        setupButtons();
        populateInputFields();
        bindCurrentStreamState();

        if (streamManager != null) {
            streamManager.addLiveStreamStatusListener(liveStreamStatusListener);
            updateButtonStates(streamManager.isStreaming());
        }
        if (cameraStreamManager != null) {
            cameraStreamManager.addAvailableCameraUpdatedListener(availableCameraUpdatedListener);
        }
    }

    private void initViews(View view) {
        rgCamera = view.findViewById(R.id.rg_live_forward_camera);
        rgQuality = view.findViewById(R.id.rg_live_forward_quality);
        rgBitrate = view.findViewById(R.id.rg_live_forward_bitrate);
        etRtmpUrl = view.findViewById(R.id.et_live_forward_rtmp_url);
        tvStatus = view.findViewById(R.id.tv_live_forward_status);
        tvBitrateValue = view.findViewById(R.id.tv_live_forward_bitrate_value);
        sbBitrate = view.findViewById(R.id.sb_live_forward_bitrate);
        btnStart = view.findViewById(R.id.btn_live_forward_start);
        btnStop = view.findViewById(R.id.btn_live_forward_stop);
    }

    private void setupSelections() {
        if (rgCamera != null) {
            rgCamera.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    if (!isBindingUiState && streamManager != null) {
                        streamManager.setCameraIndex(getSelectedCameraIndex());
                    }
                }
            });
        }

        if (rgQuality != null) {
            rgQuality.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    if (!isBindingUiState && streamManager != null) {
                        streamManager.setLiveStreamQuality(getSelectedQuality());
                    }
                }
            });
        }

        setupBitrateSelection();
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

    private void setupBitrateSelection() {
        if (sbBitrate != null) {
            sbBitrate.setMax(100);
            sbBitrate.setProgress(DEFAULT_VIDEO_BITRATE_PROGRESS);
            sbBitrate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateBitrateValueText();
                    if (fromUser && !isBindingUiState && isManualBitrateSelected() && streamManager != null) {
                        streamManager.setLiveVideoBitrate(getSelectedVideoBitrate());
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    // No-op.
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (!isBindingUiState && isManualBitrateSelected() && streamManager != null) {
                        streamManager.setLiveVideoBitrate(getSelectedVideoBitrate());
                    }
                }
            });
        }

        if (rgBitrate != null) {
            rgBitrate.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    updateBitrateControls();
                    if (!isBindingUiState) {
                        applyBitrateSettings();
                    }
                }
            });
        }
    }

    private void populateInputFields() {
        String rtmpUrl = DjiSharedPreferencesManager.getString(ContextUtil.getContext(),
                PREF_RTMP_URL, "rtmp://192.168.1.100/live/drone");
        if (streamManager != null) {
            LiveStreamSettings settings = streamManager.getLiveStreamSettings();
            if (settings != null
                    && settings.getLiveStreamType() == LiveStreamType.RTMP
                    && settings.getRtmpSettings() != null
                    && !TextUtils.isEmpty(settings.getRtmpSettings().getUrl())) {
                rtmpUrl = settings.getRtmpSettings().getUrl();
            }
        }
        setEditText(etRtmpUrl, rtmpUrl);
    }

    private void bindCurrentStreamState() {
        isBindingUiState = true;
        try {
            checkCameraButton(getSafeCameraIndex());
            checkQualityButton(getSafeStreamQuality());

            LiveVideoBitrateMode bitrateMode = getSafeBitrateMode();
            if (rgBitrate != null) {
                int targetId = bitrateMode == LiveVideoBitrateMode.MANUAL
                        ? R.id.rb_live_forward_bitrate_manual
                        : R.id.rb_live_forward_bitrate_auto;
                if (rgBitrate.getCheckedRadioButtonId() != targetId) {
                    rgBitrate.check(targetId);
                }
            }

            if (sbBitrate != null) {
                int bitrate = streamManager == null ? 0 : streamManager.getLiveVideoBitrate();
                if (bitrate > 0) {
                    sbBitrate.setProgress(toBitrateProgress(bitrate));
                } else {
                    sbBitrate.setProgress(DEFAULT_VIDEO_BITRATE_PROGRESS);
                }
            }
        } finally {
            isBindingUiState = false;
        }
        updateBitrateControls();
    }

    private void startLiveForward() {
        if (streamManager == null) {
            return;
        }

        clearInputErrors();
        streamManager.setCameraIndex(getSelectedCameraIndex());
        streamManager.setLiveStreamQuality(getSelectedQuality());
        streamManager.setLiveStreamScaleType(ICameraStreamManager.ScaleType.CENTER_CROP);
        applyBitrateSettings();

        if (!applyRtmpConfig()) {
            return;
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

    private ComponentIndexType getSafeCameraIndex() {
        if (streamManager == null || streamManager.getCameraIndex() == null) {
            return ComponentIndexType.LEFT_OR_MAIN;
        }
        return streamManager.getCameraIndex();
    }

    private StreamQuality getSafeStreamQuality() {
        if (streamManager == null || streamManager.getLiveStreamQuality() == null) {
            return StreamQuality.HD;
        }
        return streamManager.getLiveStreamQuality();
    }

    private LiveVideoBitrateMode getSafeBitrateMode() {
        if (streamManager == null || streamManager.getLiveVideoBitrateMode() == null) {
            return LiveVideoBitrateMode.AUTO;
        }
        return streamManager.getLiveVideoBitrateMode();
    }

    private void checkCameraButton(ComponentIndexType cameraIndex) {
        if (rgCamera == null) {
            return;
        }
        int targetId = R.id.rb_live_forward_camera_left;
        if (cameraIndex == ComponentIndexType.RIGHT) {
            targetId = R.id.rb_live_forward_camera_right;
        } else if (cameraIndex == ComponentIndexType.FPV) {
            targetId = R.id.rb_live_forward_camera_fpv;
        }
        if (rgCamera.getCheckedRadioButtonId() != targetId) {
            rgCamera.check(targetId);
        }
    }

    private void checkQualityButton(StreamQuality quality) {
        if (rgQuality == null) {
            return;
        }
        int targetId = R.id.rb_live_forward_quality_hd;
        if (quality == StreamQuality.SD) {
            targetId = R.id.rb_live_forward_quality_sd;
        } else if (quality == StreamQuality.FULL_HD) {
            targetId = R.id.rb_live_forward_quality_fhd;
        } else if (quality == StreamQuality.ORIGINAL) {
            targetId = R.id.rb_live_forward_quality_original;
        }
        if (rgQuality.getCheckedRadioButtonId() != targetId) {
            rgQuality.check(targetId);
        }
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
        if (checkedId == R.id.rb_live_forward_quality_original) {
            return StreamQuality.ORIGINAL;
        }
        return StreamQuality.HD;
    }

    private boolean isManualBitrateSelected() {
        return rgBitrate != null && rgBitrate.getCheckedRadioButtonId() == R.id.rb_live_forward_bitrate_manual;
    }

    private int getSelectedVideoBitrate() {
        int progress = sbBitrate == null ? DEFAULT_VIDEO_BITRATE_PROGRESS : sbBitrate.getProgress();
        return MIN_VIDEO_BITRATE + (MAX_VIDEO_BITRATE - MIN_VIDEO_BITRATE) * progress / 100;
    }

    private int toBitrateProgress(int bitrate) {
        int safeBitrate = Math.max(MIN_VIDEO_BITRATE, Math.min(MAX_VIDEO_BITRATE, bitrate));
        return (safeBitrate - MIN_VIDEO_BITRATE) * 100 / (MAX_VIDEO_BITRATE - MIN_VIDEO_BITRATE);
    }

    private void applyBitrateSettings() {
        if (streamManager == null) {
            return;
        }
        if (isManualBitrateSelected()) {
            streamManager.setLiveVideoBitrateMode(LiveVideoBitrateMode.MANUAL);
            streamManager.setLiveVideoBitrate(getSelectedVideoBitrate());
        } else {
            streamManager.setLiveVideoBitrateMode(LiveVideoBitrateMode.AUTO);
        }
    }

    private void updateBitrateControls() {
        boolean manual = isManualBitrateSelected();
        if (sbBitrate != null) {
            sbBitrate.setVisibility(manual ? View.VISIBLE : View.GONE);
        }
        updateBitrateValueText();
        if (tvBitrateValue != null) {
            tvBitrateValue.setVisibility(manual ? View.VISIBLE : View.GONE);
        }
    }

    private void updateBitrateValueText() {
        if (tvBitrateValue != null) {
            tvBitrateValue.setText(String.format(Locale.US, "%.1f Mbps", getSelectedVideoBitrate() / 1000000f));
        }
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

    private void clearInputErrors() {
        clearInputError(etRtmpUrl);
    }

    private void updateButtonStates(boolean streaming) {
        if (btnStart != null) {
            btnStart.setEnabled(!streaming);
        }
        if (btnStop != null) {
            btnStop.setEnabled(streaming);
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
        rgCamera = null;
        rgQuality = null;
        rgBitrate = null;
        etRtmpUrl = null;
        tvStatus = null;
        tvBitrateValue = null;
        sbBitrate = null;
        btnStart = null;
        btnStop = null;
        isBindingUiState = false;

        super.onDestroyView();
    }
}
