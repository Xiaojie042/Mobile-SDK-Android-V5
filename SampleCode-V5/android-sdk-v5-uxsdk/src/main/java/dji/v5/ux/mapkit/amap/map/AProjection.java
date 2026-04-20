package dji.v5.ux.mapkit.amap.map;

import android.graphics.Point;

import com.amap.api.maps.Projection;
import com.amap.api.maps.model.LatLng;

import dji.v5.ux.mapkit.amap.utils.AMapUtils;
import dji.v5.ux.mapkit.core.maps.DJIProjection;
import dji.v5.ux.mapkit.core.models.DJILatLng;

public class AProjection implements DJIProjection {

    private final Projection projection;

    public AProjection(Projection projection) {
        this.projection = projection;
    }

    @Override
    public DJILatLng fromScreenLocation(Point point) {
        LatLng latLng = projection.fromScreenLocation(point);
        return latLng == null ? null : AMapUtils.fromLatLng(latLng);
    }

    @Override
    public Point toScreenLocation(DJILatLng location) {
        return projection.toScreenLocation(AMapUtils.fromDJILatLng(location));
    }
}
