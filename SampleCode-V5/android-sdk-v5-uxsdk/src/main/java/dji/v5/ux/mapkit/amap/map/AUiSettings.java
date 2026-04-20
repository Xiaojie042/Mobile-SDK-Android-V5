package dji.v5.ux.mapkit.amap.map;

import com.amap.api.maps.UiSettings;

import dji.v5.ux.mapkit.core.maps.DJIUiSettings;

public class AUiSettings implements DJIUiSettings {

    private final UiSettings uiSettings;

    public AUiSettings(UiSettings uiSettings) {
        this.uiSettings = uiSettings;
    }

    @Override
    public void setZoomControlsEnabled(boolean enabled) {
        uiSettings.setZoomControlsEnabled(enabled);
    }

    @Override
    public void setCompassEnabled(boolean enabled) {
        uiSettings.setCompassEnabled(enabled);
    }

    @Override
    public void setMapToolbarEnabled(boolean enabled) {
        // AMap does not expose a direct toolbar toggle like Google Maps.
    }

    @Override
    public void setMyLocationButtonEnabled(boolean enabled) {
        uiSettings.setMyLocationButtonEnabled(enabled);
    }

    @Override
    public void setRotateGesturesEnabled(boolean enabled) {
        uiSettings.setRotateGesturesEnabled(enabled);
    }

    @Override
    public void setTiltGesturesEnabled(boolean enabled) {
        uiSettings.setTiltGesturesEnabled(enabled);
    }

    @Override
    public void setZoomGesturesEnabled(boolean enabled) {
        uiSettings.setZoomGesturesEnabled(enabled);
    }

    @Override
    public void setScrollGesturesEnabled(boolean enabled) {
        uiSettings.setScrollGesturesEnabled(enabled);
    }
}
