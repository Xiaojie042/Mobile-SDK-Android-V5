package dji.v5.ux.core.util;

/**
 * Central UI feature switches for experimental functions.
 *
 * Keep untested features disabled by default. Change these constants to true
 * when the corresponding feature is ready to appear in the front-end UI.
 */
public final class UXFeatureFlags {

    public static final boolean ENABLE_MAP_WAYPOINT_WAYLINE_UI = false;
    public static final boolean ENABLE_MQTT_UI = false;
    public static final boolean ENABLE_LIVE_FORWARD_UI = true;

    private UXFeatureFlags() {
        // Utility class.
    }
}
