package dji.v5.ux.mapkit.amap.annotations;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Polygon;

import java.util.ArrayList;
import java.util.List;

import dji.v5.ux.mapkit.amap.utils.AMapUtils;
import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolygon;

public class APolygon implements DJIPolygon {

    private final Polygon polygon;

    public APolygon(@NonNull Polygon polygon) {
        this.polygon = polygon;
    }

    @Override
    public void remove() {
        polygon.remove();
    }

    @Override
    public boolean isVisible() {
        return polygon.isVisible();
    }

    @Override
    public void setVisible(boolean visible) {
        polygon.setVisible(visible);
    }

    @Override
    public void setPoints(List<DJILatLng> points) {
        List<LatLng> aMapPoints = new ArrayList<>(points.size());
        for (DJILatLng point : points) {
            aMapPoints.add(AMapUtils.fromDJILatLng(point));
        }
        polygon.setPoints(aMapPoints);
    }

    @Override
    public List<DJILatLng> getPoints() {
        List<DJILatLng> points = new ArrayList<>(polygon.getPoints().size());
        for (LatLng point : polygon.getPoints()) {
            points.add(AMapUtils.fromLatLng(point));
        }
        return points;
    }

    @Override
    public void setFillColor(@ColorInt int color) {
        polygon.setFillColor(color);
    }

    @Override
    public int getFillColor() {
        return polygon.getFillColor();
    }

    @Override
    public void setStrokeColor(@ColorInt int color) {
        polygon.setStrokeColor(color);
    }

    @Override
    public int getStrokeColor() {
        return polygon.getStrokeColor();
    }

    @Override
    public void setStrokeWidth(float strokeWidth) {
        polygon.setStrokeWidth(strokeWidth);
    }

    @Override
    public float getStrokeWidth() {
        return polygon.getStrokeWidth();
    }
}
