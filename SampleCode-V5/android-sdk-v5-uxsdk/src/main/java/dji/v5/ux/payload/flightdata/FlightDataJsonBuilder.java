package dji.v5.ux.payload.flightdata;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import dji.sdk.keyvalue.key.AirLinkKey;
import dji.sdk.keyvalue.key.BatteryKey;
import dji.sdk.keyvalue.key.DJIKey;
import dji.sdk.keyvalue.key.FlightAssistantKey;
import dji.sdk.keyvalue.key.FlightControllerKey;
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
import dji.v5.utils.common.LocationUtil;
import dji.v5.utils.common.LogUtils;

public final class FlightDataJsonBuilder {

    private static final String TAG = "FlightDataJsonBuilder";

    private FlightDataJsonBuilder() {
    }

    public static String buildFlightDataJson() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        String timestamp = sdf.format(new Date());

        JSONObject json = new JSONObject();
        try {
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
            LogUtils.e(TAG, "Failed to build flight data JSON: " + e.getMessage());
        }

        return json.toString();
    }

    public static String formatJsonForDisplay(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "";
        }

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

    private static JSONObject buildAttitudeJson(Attitude attitude) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("pitch", attitude.getPitch());
        json.put("roll", attitude.getRoll());
        json.put("yaw", attitude.getYaw());
        return json;
    }

    private static JSONObject buildLocation3DJson(LocationCoordinate3D location) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("latitude", location.getLatitude());
        json.put("longitude", location.getLongitude());
        json.put("altitude", location.getAltitude());
        return json;
    }

    private static JSONObject buildLocation2DJson(LocationCoordinate2D location) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("latitude", location.getLatitude());
        json.put("longitude", location.getLongitude());
        return json;
    }

    private static JSONObject buildVelocityJson(Velocity3D velocity) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("x", velocity.getX());
        json.put("y", velocity.getY());
        json.put("z", velocity.getZ());
        json.put("horizontal_speed", calculateHorizontalSpeed(velocity));
        json.put("total_speed", calculateTotalSpeed(velocity));
        return json;
    }

    private static JSONObject buildLowBatteryRthInfoJson(LowBatteryRTHInfo info) throws JSONException {
        JSONObject json = new JSONObject();
        if (info == null) {
            return json;
        }
        json.put("battery_percent_needed_to_land", info.getBatteryPercentNeededToLand());
        json.put("battery_percent_needed_to_go_home", info.getBatteryPercentNeededToGoHome());
        json.put("remaining_flight_time", info.getRemainingFlightTime());
        return json;
    }

    private static JSONObject buildWindJson(Integer windSpeed, WindDirection windDirection, WindWarning windWarning) throws JSONException {
        JSONObject json = new JSONObject();
        putIfNotNull(json, "speed", windSpeed);
        putEnumNameIfNotNull(json, "direction", windDirection);
        putEnumNameIfNotNull(json, "warning", windWarning);
        return json;
    }

    private static JSONObject buildVpsJson(Boolean visionPositioningEnabled,
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

    private static JSONObject buildBatteryStatusJson() throws JSONException {
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

    private static JSONArray buildBatteryOverviewJson(List<BatteryOverviewValue> batteryOverviews) throws JSONException {
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

    private static JSONObject buildBatterySlotStatusJson(int batteryIndex) throws JSONException {
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

    private static JSONObject buildAirLinkStatusJson() throws JSONException {
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

    private static JSONObject buildRemoteControllerStatusJson() throws JSONException {
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

    private static JSONObject buildGimbalStatusJson() throws JSONException {
        JSONObject json = new JSONObject();

        Boolean gimbalConnected = getValue(KeyTools.createKey(GimbalKey.KeyConnection, ComponentIndexType.LEFT_OR_MAIN));
        Attitude gimbalAttitude = getValue(KeyTools.createKey(GimbalKey.KeyGimbalAttitude, ComponentIndexType.LEFT_OR_MAIN));

        putIfNotNull(json, "main_gimbal_connected", gimbalConnected);
        if (gimbalAttitude != null) {
            json.put("main_gimbal_attitude", buildAttitudeJson(gimbalAttitude));
        }

        return json;
    }

    private static JSONObject buildAircraftStatusJson(LocationCoordinate3D location,
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

    private static double calculateHorizontalSpeed(Velocity3D velocity) {
        double x = velocity.getX();
        double y = velocity.getY();
        return Math.sqrt(x * x + y * y);
    }

    private static double calculateTotalSpeed(Velocity3D velocity) {
        double x = velocity.getX();
        double y = velocity.getY();
        double z = velocity.getZ();
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static <T> T getValue(DJIKey<T> key) {
        return KeyManager.getInstance().getValue(key);
    }

    private static void putIfNotNull(JSONObject json, String key, Object value) throws JSONException {
        if (value != null) {
            json.put(key, value);
        }
    }

    private static void putEnumNameIfNotNull(JSONObject json, String key, Enum<?> value) throws JSONException {
        if (value != null) {
            json.put(key, value.name());
        }
    }

    private static void putJsonObjectIfNotEmpty(JSONObject parent, String key, JSONObject child) throws JSONException {
        if (child != null && child.length() > 0) {
            parent.put(key, child);
        }
    }

    private static void putJsonArrayIfNotEmpty(JSONObject parent, String key, JSONArray child) throws JSONException {
        if (child != null && child.length() > 0) {
            parent.put(key, child);
        }
    }

    private static String safeEnumName(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum) {
            return ((Enum<?>) value).name();
        }
        return String.valueOf(value);
    }
}
