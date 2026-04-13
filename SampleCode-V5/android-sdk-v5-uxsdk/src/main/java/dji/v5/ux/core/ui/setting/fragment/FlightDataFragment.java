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
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import dji.sdk.keyvalue.key.AirLinkKey;
import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.BatteryKey;
import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.FlightAssistantKey;
import dji.sdk.keyvalue.key.GimbalKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.key.ProductKey;
import dji.sdk.keyvalue.key.RemoteControllerKey;
import dji.sdk.keyvalue.value.battery.BatteryOverviewValue;
import dji.sdk.keyvalue.value.common.Attitude;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
import dji.sdk.keyvalue.value.common.LocationCoordinate3D;
import dji.sdk.keyvalue.value.common.Velocity3D;
import dji.sdk.keyvalue.value.flightassistant.LandingProtectionState;
import dji.sdk.keyvalue.value.flightcontroller.FCAutoRTHReason;
import dji.sdk.keyvalue.value.flightcontroller.FCBatteryThresholdBehavior;
import dji.sdk.keyvalue.value.flightcontroller.FCFlightMode;
import dji.sdk.keyvalue.value.flightcontroller.FailsafeAction;
import dji.sdk.keyvalue.value.flightcontroller.FlightMode;
import dji.sdk.keyvalue.value.flightcontroller.GPSSignalLevel;
import dji.sdk.keyvalue.value.flightcontroller.LowBatteryRTHInfo;
import dji.sdk.keyvalue.value.flightcontroller.WindDirection;
import dji.sdk.keyvalue.value.flightcontroller.WindWarning;
import dji.sdk.keyvalue.value.product.ProductType;
import dji.sdk.keyvalue.value.remotecontroller.BatteryInfo;
import dji.sdk.keyvalue.value.remotecontroller.RCMode;
import dji.sdk.keyvalue.value.remotecontroller.RcGPSInfo;
import dji.v5.manager.KeyManager;
import dji.v5.utils.common.ContextUtil;
import dji.v5.utils.common.LocationUtil;
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

        JSONObject json = new JSONObject();
        try {
            // 保留旧版顶层字段，并追加更完整的状态块，方便PC端平滑升级解析逻辑。
            json.put("type", "flight_data");
            json.put("schema_version", 2);
            json.put("timestamp", timestamp);

            Attitude attitude = getValue(KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude));
            if (attitude != null) {
                json.put("attitude", buildAttitudeJson(attitude));
            }

            LocationCoordinate3D location = getValue(KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D));
            if (location != null) {
                json.put("location", buildLocation3DJson(location));
                json.put("altitude", location.getAltitude());
            }

            Velocity3D velocity = getValue(KeyTools.createKey(FlightControllerKey.KeyAircraftVelocity));
            if (velocity != null) {
                json.put("velocity", buildVelocityJson(velocity));
                json.put("horizontal_speed", calculateHorizontalSpeed(velocity));
                json.put("speed_total", calculateTotalSpeed(velocity));
            }

            FlightMode flightMode = getValue(KeyTools.createKey(FlightControllerKey.KeyFlightMode));
            putEnumNameIfNotNull(json, "flight_mode", flightMode);

            LocationCoordinate2D homeLocation = getValue(KeyTools.createKey(FlightControllerKey.KeyHomeLocation));
            if (homeLocation != null) {
                json.put("home_location", buildLocation2DJson(homeLocation));
            }

            Integer batteryPercent = getValue(KeyTools.createKey(BatteryKey.KeyChargeRemainingInPercent, ComponentIndexType.AGGREGATION));
            putIfNotNull(json, "battery_percentage", batteryPercent);

            Boolean isFlying = getValue(KeyTools.createKey(FlightControllerKey.KeyIsFlying));
            putIfNotNull(json, "is_flying", isFlying);

            String aircraftName = getValue(KeyTools.createKey(FlightControllerKey.KeyAircraftName));
            putIfNotNull(json, "aircraft_name", aircraftName);

            ProductType productType = getValue(KeyTools.createKey(ProductKey.KeyProductType));
            putEnumNameIfNotNull(json, "product_type", productType);

            String productFirmwareVersion = getValue(KeyTools.createKey(ProductKey.KeyFirmwareVersion));
            putIfNotNull(json, "product_firmware_version", productFirmwareVersion);

            String flightControllerSerialNumber = getValue(KeyTools.createKey(FlightControllerKey.KeySerialNumber));
            putIfNotNull(json, "flight_controller_serial_number", flightControllerSerialNumber);

            Boolean flightControllerConnected = getValue(KeyTools.createKey(FlightControllerKey.KeyConnection));
            putIfNotNull(json, "flight_controller_connected", flightControllerConnected);

            Boolean remoteControllerConnected = getValue(KeyTools.createKey(RemoteControllerKey.KeyConnection));
            putIfNotNull(json, "remote_controller_connected", remoteControllerConnected);

            Double compassHeading = getValue(KeyTools.createKey(FlightControllerKey.KeyCompassHeading));
            putIfNotNull(json, "aircraft_heading", compassHeading);

            Double altitude = getValue(KeyTools.createKey(FlightControllerKey.KeyAltitude));
            putIfNotNull(json, "relative_altitude", altitude);

            Integer gpsSatelliteCount = getValue(KeyTools.createKey(FlightControllerKey.KeyGPSSatelliteCount));
            putIfNotNull(json, "gps_satellite_count", gpsSatelliteCount);

            GPSSignalLevel gpsSignalLevel = getValue(KeyTools.createKey(FlightControllerKey.KeyGPSSignalLevel));
            putEnumNameIfNotNull(json, "gps_signal_level", gpsSignalLevel);

            String flightModeString = getValue(KeyTools.createKey(FlightControllerKey.KeyFlightModeString));
            putIfNotNull(json, "flight_mode_string", flightModeString);

            FCFlightMode fcFlightMode = getValue(KeyTools.createKey(FlightControllerKey.KeyFCFlightMode));
            putEnumNameIfNotNull(json, "fc_flight_mode", fcFlightMode);

            Boolean areMotorsOn = getValue(KeyTools.createKey(FlightControllerKey.KeyAreMotorsOn));
            putIfNotNull(json, "are_motors_on", areMotorsOn);

            Boolean isInLandingMode = getValue(KeyTools.createKey(FlightControllerKey.KeyIsInLandingMode));
            putIfNotNull(json, "is_in_landing_mode", isInLandingMode);

            Boolean isLandingConfirmationNeeded = getValue(KeyTools.createKey(FlightControllerKey.KeyIsLandingConfirmationNeeded));
            putIfNotNull(json, "is_landing_confirmation_needed", isLandingConfirmationNeeded);

            Integer flightTimeInSeconds = getValue(KeyTools.createKey(FlightControllerKey.KeyFlightTimeInSeconds));
            putIfNotNull(json, "flight_time_in_seconds", flightTimeInSeconds);

            Integer goHomeHeight = getValue(KeyTools.createKey(FlightControllerKey.KeyGoHomeHeight));
            putIfNotNull(json, "go_home_height", goHomeHeight);

            Integer heightLimit = getValue(KeyTools.createKey(FlightControllerKey.KeyHeightLimit));
            putIfNotNull(json, "height_limit", heightLimit);

            Boolean distanceLimitEnabled = getValue(KeyTools.createKey(FlightControllerKey.KeyDistanceLimitEnabled));
            putIfNotNull(json, "distance_limit_enabled", distanceLimitEnabled);

            Integer distanceLimit = getValue(KeyTools.createKey(FlightControllerKey.KeyDistanceLimit));
            putIfNotNull(json, "distance_limit", distanceLimit);

            Integer batteryPercentNeededToGoHome = getValue(KeyTools.createKey(FlightControllerKey.KeyBatteryPercentNeededToGoHome));
            putIfNotNull(json, "battery_percent_needed_to_go_home", batteryPercentNeededToGoHome);

            Integer lowBatteryWarningThreshold = getValue(KeyTools.createKey(FlightControllerKey.KeyLowBatteryWarningThreshold));
            putIfNotNull(json, "low_battery_warning_threshold", lowBatteryWarningThreshold);

            Integer seriousLowBatteryWarningThreshold = getValue(KeyTools.createKey(FlightControllerKey.KeySeriousLowBatteryWarningThreshold));
            putIfNotNull(json, "serious_low_battery_warning_threshold", seriousLowBatteryWarningThreshold);

            FCBatteryThresholdBehavior batteryThresholdBehavior = getValue(KeyTools.createKey(FlightControllerKey.KeyBatteryThresholdBehavior));
            putEnumNameIfNotNull(json, "battery_threshold_behavior", batteryThresholdBehavior);

            FCAutoRTHReason autoRTHReason = getValue(KeyTools.createKey(FlightControllerKey.KeyAutoRTHReason));
            putEnumNameIfNotNull(json, "auto_rth_reason", autoRTHReason);

            FailsafeAction failsafeAction = getValue(KeyTools.createKey(FlightControllerKey.KeyFailsafeAction));
            putEnumNameIfNotNull(json, "failsafe_action", failsafeAction);

            LowBatteryRTHInfo lowBatteryRTHInfo = getValue(KeyTools.createKey(FlightControllerKey.KeyLowBatteryRTHInfo));
            putJsonObjectIfNotEmpty(json, "low_battery_rth_info", buildLowBatteryRthInfoJson(lowBatteryRTHInfo));

            WindDirection windDirection = getValue(KeyTools.createKey(FlightControllerKey.KeyWindDirection));
            Integer windSpeed = getValue(KeyTools.createKey(FlightControllerKey.KeyWindSpeed));
            WindWarning windWarning = getValue(KeyTools.createKey(FlightControllerKey.KeyWindWarning));
            putJsonObjectIfNotEmpty(json, "wind", buildWindJson(windSpeed, windDirection, windWarning));

            Boolean visionPositioningEnabled = getValue(KeyTools.createKey(FlightAssistantKey.KeyVisionPositioningEnabled));
            Boolean isUltrasonicUsed = getValue(KeyTools.createKey(FlightControllerKey.KeyIsUltrasonicUsed));
            Integer ultrasonicHeight = getValue(KeyTools.createKey(FlightControllerKey.KeyUltrasonicHeight));
            LandingProtectionState landingProtectionState = getValue(KeyTools.createKey(FlightAssistantKey.KeyLandingProtectionState));
            putJsonObjectIfNotEmpty(json, "vps_status", buildVpsJson(visionPositioningEnabled, isUltrasonicUsed, ultrasonicHeight, landingProtectionState));

            putJsonObjectIfNotEmpty(json, "battery_status", buildBatteryStatusJson());
            putJsonObjectIfNotEmpty(json, "air_link_status", buildAirLinkStatusJson());
            putJsonObjectIfNotEmpty(json, "remote_controller_status", buildRemoteControllerStatusJson());
            putJsonObjectIfNotEmpty(json, "gimbal_status", buildGimbalStatusJson());
            putJsonObjectIfNotEmpty(json, "aircraft_status", buildAircraftStatusJson(location, homeLocation));
        } catch (JSONException e) {
            LogUtils.e(TAG, "构造飞控数据JSON失败: " + e.getMessage());
        }

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
        try {
            return new JSONObject(json).toString(2);
        } catch (JSONException e) {
            return json.replace(",\"", ",\n\"")
                    .replace("{", "{\n  ")
                    .replace("}", "\n}")
                    .replace(":{", ":\n  {")
                    .replace("},", "\n  },");
        }
    }

    private JSONObject buildAttitudeJson(Attitude attitude) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("pitch", attitude.getPitch());
        json.put("roll", attitude.getRoll());
        json.put("yaw", attitude.getYaw());
        return json;
    }

    private JSONObject buildLocation3DJson(LocationCoordinate3D location) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("latitude", location.getLatitude());
        json.put("longitude", location.getLongitude());
        json.put("altitude", location.getAltitude());
        return json;
    }

    private JSONObject buildLocation2DJson(LocationCoordinate2D location) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("latitude", location.getLatitude());
        json.put("longitude", location.getLongitude());
        return json;
    }

    private JSONObject buildVelocityJson(Velocity3D velocity) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("x", velocity.getX());
        json.put("y", velocity.getY());
        json.put("z", velocity.getZ());
        json.put("horizontal_speed", calculateHorizontalSpeed(velocity));
        json.put("total_speed", calculateTotalSpeed(velocity));
        return json;
    }

    private JSONObject buildLowBatteryRthInfoJson(LowBatteryRTHInfo info) throws JSONException {
        JSONObject json = new JSONObject();
        if (info == null) {
            return json;
        }
        json.put("battery_percent_needed_to_land", info.getBatteryPercentNeededToLand());
        json.put("battery_percent_needed_to_go_home", info.getBatteryPercentNeededToGoHome());
        json.put("remaining_flight_time", info.getRemainingFlightTime());
        return json;
    }

    private JSONObject buildWindJson(Integer windSpeed, WindDirection windDirection, WindWarning windWarning) throws JSONException {
        JSONObject json = new JSONObject();
        putIfNotNull(json, "speed", windSpeed);
        putEnumNameIfNotNull(json, "direction", windDirection);
        putEnumNameIfNotNull(json, "warning", windWarning);
        return json;
    }

    private JSONObject buildVpsJson(Boolean visionPositioningEnabled,
                                    Boolean isUltrasonicUsed,
                                    Integer ultrasonicHeight,
                                    LandingProtectionState landingProtectionState) throws JSONException {
        JSONObject json = new JSONObject();
        putIfNotNull(json, "vision_positioning_enabled", visionPositioningEnabled);
        putIfNotNull(json, "ultrasonic_used", isUltrasonicUsed);
        putIfNotNull(json, "ultrasonic_height_cm", ultrasonicHeight);
        putEnumNameIfNotNull(json, "landing_protection_state", landingProtectionState);
        return json;
    }

    private JSONObject buildBatteryStatusJson() throws JSONException {
        JSONObject json = new JSONObject();

        Integer aggregatePercentage = getValue(KeyTools.createKey(BatteryKey.KeyChargeRemainingInPercent, ComponentIndexType.AGGREGATION));
        Integer connectedCount = getValue(KeyTools.createKey(BatteryKey.KeyNumberOfConnectedBatteries, ComponentIndexType.AGGREGATION));
        Boolean anyBatteryDisconnected = getValue(KeyTools.createKey(BatteryKey.KeyIsAnyBatteryDisconnected, ComponentIndexType.AGGREGATION));
        Boolean cellDamaged = getValue(KeyTools.createKey(BatteryKey.KeyIsCellDamaged, ComponentIndexType.AGGREGATION));
        Boolean firmwareDifferenceDetected = getValue(KeyTools.createKey(BatteryKey.KeyIsFirmwareDifferenceDetected, ComponentIndexType.AGGREGATION));
        Boolean voltageDifferenceDetected = getValue(KeyTools.createKey(BatteryKey.KeyIsVoltageDifferenceDetected, ComponentIndexType.AGGREGATION));
        Boolean lowCellVoltageDetected = getValue(KeyTools.createKey(BatteryKey.KeyIsLowCellVoltageDetected, ComponentIndexType.AGGREGATION));
        List<BatteryOverviewValue> batteryOverviews = getValue(KeyTools.createKey(BatteryKey.KeyBatteryOverviews, ComponentIndexType.AGGREGATION));

        putIfNotNull(json, "aggregate_percentage", aggregatePercentage);
        putIfNotNull(json, "connected_count", connectedCount);
        putIfNotNull(json, "any_battery_disconnected", anyBatteryDisconnected);
        putIfNotNull(json, "cell_damaged", cellDamaged);
        putIfNotNull(json, "firmware_difference_detected", firmwareDifferenceDetected);
        putIfNotNull(json, "voltage_difference_detected", voltageDifferenceDetected);
        putIfNotNull(json, "low_cell_voltage_detected", lowCellVoltageDetected);
        putJsonArrayIfNotEmpty(json, "overview", buildBatteryOverviewJson(batteryOverviews));
        putJsonObjectIfNotEmpty(json, "main_battery", buildBatterySlotStatusJson(0));
        putJsonObjectIfNotEmpty(json, "secondary_battery", buildBatterySlotStatusJson(1));

        return json;
    }

    private JSONArray buildBatteryOverviewJson(List<BatteryOverviewValue> batteryOverviews) throws JSONException {
        JSONArray array = new JSONArray();
        if (batteryOverviews == null) {
            return array;
        }

        for (BatteryOverviewValue overview : batteryOverviews) {
            if (overview == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("index", overview.getIndex());
            item.put("is_connected", overview.getIsConnected());
            array.put(item);
        }
        return array;
    }

    private JSONObject buildBatterySlotStatusJson(int batteryIndex) throws JSONException {
        JSONObject json = new JSONObject();

        Boolean connection = getValue(KeyTools.createKey(BatteryKey.KeyConnection, batteryIndex));
        Integer percentage = getValue(KeyTools.createKey(BatteryKey.KeyChargeRemainingInPercent, batteryIndex));
        Double temperature = getValue(KeyTools.createKey(BatteryKey.KeyBatteryTemperature, batteryIndex));
        Integer voltage = getValue(KeyTools.createKey(BatteryKey.KeyVoltage, batteryIndex));
        String serialNumber = getValue(KeyTools.createKey(BatteryKey.KeySerialNumber, batteryIndex));
        List<Integer> cellVoltages = getValue(KeyTools.createKey(BatteryKey.KeyCellVoltages, batteryIndex));

        putIfNotNull(json, "index", batteryIndex);
        putIfNotNull(json, "connected", connection);
        putIfNotNull(json, "percentage", percentage);
        putIfNotNull(json, "temperature_celsius", temperature);
        putIfNotNull(json, "voltage_mv", voltage);
        putIfNotNull(json, "serial_number", serialNumber);
        if (cellVoltages != null && !cellVoltages.isEmpty()) {
            JSONArray cellVoltageArray = new JSONArray();
            for (Integer cellVoltage : cellVoltages) {
                cellVoltageArray.put(cellVoltage);
            }
            json.put("cell_voltages_mv", cellVoltageArray);
        }

        return json;
    }

    private JSONObject buildAirLinkStatusJson() throws JSONException {
        JSONObject json = new JSONObject();

        Boolean connection = getValue(KeyTools.createKey(AirLinkKey.KeyConnection));
        Integer downLinkQuality = getValue(KeyTools.createKey(AirLinkKey.KeyDownLinkQuality));
        Integer downLinkQualityRaw = getValue(KeyTools.createKey(AirLinkKey.KeyDownLinkQualityRaw));
        Integer upLinkQuality = getValue(KeyTools.createKey(AirLinkKey.KeyUpLinkQuality));
        Integer upLinkQualityRaw = getValue(KeyTools.createKey(AirLinkKey.KeyUpLinkQualityRaw));
        Integer linkSignalQuality = getValue(KeyTools.createKey(AirLinkKey.KeyLinkSignalQuality));
        Double dynamicDataRate = getValue(KeyTools.createKey(AirLinkKey.KeyDynamicDataRate));
        Integer frequencyPoint = getValue(KeyTools.createKey(AirLinkKey.KeyFrequencyPoint));
        Object frequencyBand = getValue(KeyTools.createKey(AirLinkKey.KeyFrequencyBand));

        putIfNotNull(json, "connected", connection);
        putIfNotNull(json, "down_link_quality", downLinkQuality);
        putIfNotNull(json, "down_link_quality_raw", downLinkQualityRaw);
        putIfNotNull(json, "up_link_quality", upLinkQuality);
        putIfNotNull(json, "up_link_quality_raw", upLinkQualityRaw);
        putIfNotNull(json, "link_signal_quality", linkSignalQuality);
        putIfNotNull(json, "dynamic_data_rate", dynamicDataRate);
        putIfNotNull(json, "frequency_point", frequencyPoint);
        putIfNotNull(json, "frequency_band", safeEnumName(frequencyBand));

        return json;
    }

    private JSONObject buildRemoteControllerStatusJson() throws JSONException {
        JSONObject json = new JSONObject();

        Boolean connection = getValue(KeyTools.createKey(RemoteControllerKey.KeyConnection));
        RCMode rcMode = getValue(KeyTools.createKey(RemoteControllerKey.KeyRcMachineMode));
        BatteryInfo batteryInfo = getValue(KeyTools.createKey(RemoteControllerKey.KeyBatteryInfo));
        RcGPSInfo rcGPSInfo = getValue(KeyTools.createKey(RemoteControllerKey.KeyRcGPSInfo));
        String rcSerialNumber = getValue(KeyTools.createKey(RemoteControllerKey.KeyRcRK3399SirialNumber));

        putIfNotNull(json, "connected", connection);
        putEnumNameIfNotNull(json, "mode", rcMode);
        putIfNotNull(json, "serial_number", rcSerialNumber);

        if (batteryInfo != null) {
            json.put("battery_percentage", batteryInfo.getBatteryPercent());
        }

        if (rcGPSInfo != null) {
            JSONObject rcGpsJson = new JSONObject();
            rcGpsJson.put("valid", rcGPSInfo.getIsValid());
            if (rcGPSInfo.getIsValid() && rcGPSInfo.getLocation() != null) {
                rcGpsJson.put("location", buildLocation2DJson(rcGPSInfo.getLocation()));
            }
            putJsonObjectIfNotEmpty(json, "gps", rcGpsJson);
        }

        return json;
    }

    private JSONObject buildGimbalStatusJson() throws JSONException {
        JSONObject json = new JSONObject();

        Boolean gimbalConnected = getValue(KeyTools.createKey(GimbalKey.KeyConnection, ComponentIndexType.LEFT_OR_MAIN));
        Attitude gimbalAttitude = getValue(KeyTools.createKey(GimbalKey.KeyGimbalAttitude, ComponentIndexType.LEFT_OR_MAIN));

        putIfNotNull(json, "main_gimbal_connected", gimbalConnected);
        if (gimbalAttitude != null) {
            json.put("main_gimbal_attitude", buildAttitudeJson(gimbalAttitude));
        }

        return json;
    }

    private JSONObject buildAircraftStatusJson(LocationCoordinate3D location,
                                               LocationCoordinate2D homeLocation) throws JSONException {
        JSONObject json = new JSONObject();

        if (location != null) {
            json.put("aircraft_location", buildLocation3DJson(location));
        }
        if (homeLocation != null) {
            json.put("home_location", buildLocation2DJson(homeLocation));
        }
        if (location != null && homeLocation != null) {
            json.put("distance_to_home", LocationUtil.distanceBetween(
                    location.getLatitude(),
                    location.getLongitude(),
                    homeLocation.getLatitude(),
                    homeLocation.getLongitude()));
        }

        return json;
    }

    private double calculateHorizontalSpeed(Velocity3D velocity) {
        double x = velocity.getX();
        double y = velocity.getY();
        return Math.sqrt(x * x + y * y);
    }

    private double calculateTotalSpeed(Velocity3D velocity) {
        double x = velocity.getX();
        double y = velocity.getY();
        double z = velocity.getZ();
        return Math.sqrt(x * x + y * y + z * z);
    }

    private <T> T getValue(DJIKey<T> key) {
        return KeyManager.getInstance().getValue(key);
    }

    private void putIfNotNull(JSONObject json, String key, Object value) throws JSONException {
        if (value != null) {
            json.put(key, value);
        }
    }

    private void putEnumNameIfNotNull(JSONObject json, String key, Enum<?> value) throws JSONException {
        if (value != null) {
            json.put(key, value.name());
        }
    }

    private void putJsonObjectIfNotEmpty(JSONObject parent, String key, JSONObject child) throws JSONException {
        if (child != null && child.length() > 0) {
            parent.put(key, child);
        }
    }

    private void putJsonArrayIfNotEmpty(JSONObject parent, String key, JSONArray child) throws JSONException {
        if (child != null && child.length() > 0) {
            parent.put(key, child);
        }
    }

    private String safeEnumName(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        return String.valueOf(value);
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
