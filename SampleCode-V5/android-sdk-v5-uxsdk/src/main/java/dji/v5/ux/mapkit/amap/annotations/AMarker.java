package dji.v5.ux.mapkit.amap.annotations;

import com.amap.api.maps.model.Marker;

import dji.v5.ux.mapkit.amap.map.AMapDelegate;
import dji.v5.ux.mapkit.amap.utils.AMapUtils;
import dji.v5.ux.mapkit.core.models.DJIBitmapDescriptor;
import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.models.annotations.DJIMarker;

public class AMarker extends DJIMarker {

    private final Marker marker;
    private final AMapDelegate mapDelegate;

    public AMarker(Marker marker, AMapDelegate mapDelegate) {
        this.marker = marker;
        this.mapDelegate = mapDelegate;
    }

    @Override
    public void setPosition(DJILatLng latLng) {
        setPositionCache(latLng);
        marker.setPosition(AMapUtils.fromDJILatLng(latLng));
    }

    @Override
    public void setRotation(float rotation) {
        setRotationCache(rotation);
        marker.setRotateAngle(rotation);
    }

    @Override
    public void setIcon(DJIBitmapDescriptor bitmap) {
        marker.setIcon(AMapUtils.fromDJIBitmapDescriptor(bitmap));
    }

    @Override
    public void setAnchor(float u, float v) {
        marker.setAnchor(u, v);
    }

    @Override
    public void setTitle(String title) {
        marker.setTitle(title);
    }

    @Override
    public String getTitle() {
        return marker.getTitle();
    }

    @Override
    public void setVisible(boolean visible) {
        marker.setVisible(visible);
    }

    @Override
    public boolean isVisible() {
        return marker.isVisible();
    }

    @Override
    public void showInfoWindow() {
        marker.showInfoWindow();
    }

    @Override
    public void hideInfoWindow() {
        marker.hideInfoWindow();
    }

    @Override
    public boolean isInfoWindowShown() {
        return marker.isInfoWindowShown();
    }

    @Override
    public void remove() {
        marker.remove();
        mapDelegate.onMarkerRemove(marker);
    }

    @Override
    public void setDraggable(boolean draggable) {
        marker.setDraggable(draggable);
    }

    @Override
    public boolean isDraggable() {
        return marker.isDraggable();
    }
}
