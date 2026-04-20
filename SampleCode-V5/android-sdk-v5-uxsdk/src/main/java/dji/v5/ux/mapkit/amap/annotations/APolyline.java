package dji.v5.ux.mapkit.amap.annotations;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Polyline;

import java.util.ArrayList;
import java.util.List;

import dji.v5.ux.mapkit.amap.utils.AMapUtils;
import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolyline;

public class APolyline implements DJIPolyline {

    private final Polyline polyline;

    public APolyline(@NonNull Polyline polyline) {
        this.polyline = polyline;
    }

    @Override
    public void remove() {
        polyline.remove();
    }

    @Override
    public void setWidth(float width) {
        polyline.setWidth(width);
    }

    @Override
    public float getWidth() {
        return polyline.getWidth();
    }

    @Override
    public void setPoints(List<DJILatLng> points) {
        List<LatLng> aMapPoints = new ArrayList<>(points.size());
        for (DJILatLng point : points) {
            aMapPoints.add(AMapUtils.fromDJILatLng(point));
        }
        polyline.setPoints(aMapPoints);
    }

    @Override
    public List<DJILatLng> getPoints() {
        List<DJILatLng> points = new ArrayList<>(polyline.getPoints().size());
        for (LatLng point : polyline.getPoints()) {
            points.add(AMapUtils.fromLatLng(point));
        }
        return points;
    }

    @Override
    public void setColor(@ColorInt int color) {
        polyline.setColor(color);
    }

    @Override
    public int getColor() {
        return polyline.getColor();
    }

    @Override
    public void setZIndex(float zIndex) {
        polyline.setZIndex(zIndex);
    }

    @Override
    public float getZIndex() {
        return polyline.getZIndex();
    }
}
