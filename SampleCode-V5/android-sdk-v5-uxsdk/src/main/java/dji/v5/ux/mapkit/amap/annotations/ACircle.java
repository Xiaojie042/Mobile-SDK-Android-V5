package dji.v5.ux.mapkit.amap.annotations;

import androidx.annotation.ColorInt;

import com.amap.api.maps.model.Circle;

import dji.v5.ux.mapkit.amap.utils.AMapUtils;
import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.models.annotations.DJICircle;

public class ACircle implements DJICircle {

    private final Circle circle;

    public ACircle(Circle circle) {
        this.circle = circle;
    }

    @Override
    public void remove() {
        circle.remove();
    }

    @Override
    public void setVisible(boolean visible) {
        circle.setVisible(visible);
    }

    @Override
    public boolean isVisible() {
        return circle.isVisible();
    }

    @Override
    public void setCenter(DJILatLng center) {
        circle.setCenter(AMapUtils.fromDJILatLng(center));
    }

    @Override
    public DJILatLng getCenter() {
        return AMapUtils.fromLatLng(circle.getCenter());
    }

    @Override
    public void setRadius(double radius) {
        circle.setRadius(radius);
    }

    @Override
    public double getRadius() {
        return circle.getRadius();
    }

    @Override
    public void setFillColor(@ColorInt int color) {
        circle.setFillColor(color);
    }

    @Override
    public int getFillColor() {
        return circle.getFillColor();
    }

    @Override
    public void setStrokeColor(@ColorInt int color) {
        circle.setStrokeColor(color);
    }

    @Override
    public int getStrokeColor() {
        return circle.getStrokeColor();
    }

    @Override
    public void setZIndex(float zIndex) {
        circle.setZIndex(zIndex);
    }

    @Override
    public float getZIndex() {
        return circle.getZIndex();
    }

    @Override
    public void setCircle(DJILatLng center, Double radius) {
        circle.setCenter(AMapUtils.fromDJILatLng(center));
        circle.setRadius(radius);
    }

    @Override
    public void setStrokeWidth(float strokeWidth) {
        circle.setStrokeWidth(strokeWidth);
    }

    @Override
    public float getStrokeWidth() {
        return circle.getStrokeWidth();
    }
}
