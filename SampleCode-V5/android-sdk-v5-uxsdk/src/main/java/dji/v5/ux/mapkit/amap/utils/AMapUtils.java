package dji.v5.ux.mapkit.amap.utils;

import androidx.annotation.NonNull;

import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.CircleOptions;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.PolygonOptions;
import com.amap.api.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

import dji.v5.ux.mapkit.core.camera.DJICameraUpdate;
import dji.v5.ux.mapkit.core.camera.DJICameraUpdateFactory;
import dji.v5.ux.mapkit.core.models.DJIBitmapDescriptor;
import dji.v5.ux.mapkit.core.models.DJICameraPosition;
import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.models.DJILatLngBounds;
import dji.v5.ux.mapkit.core.models.annotations.DJICircleOptions;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolygonOptions;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolylineOptions;
import dji.v5.ux.mapkit.core.utils.DJIGpsUtils;

public final class AMapUtils {

    private AMapUtils() {
    }

    public static LatLng fromDJILatLng(@NonNull DJILatLng latLng) {
        DJILatLng gcjLatLng = DJIGpsUtils.wgs2gcjInChina(latLng);
        return new LatLng(gcjLatLng.getLatitude(), gcjLatLng.getLongitude());
    }

    public static DJILatLng fromLatLng(@NonNull LatLng latLng) {
        DJILatLng ll = new DJILatLng(latLng.latitude, latLng.longitude);
        return DJIGpsUtils.gcj2wgsInChina(ll);
    }

    public static CameraUpdate fromDJICameraUpdate(@NonNull DJICameraUpdate cameraUpdate) {
        CameraUpdate update = CameraUpdateFactory.newLatLng(new LatLng(0.0, 0.0));
        if (cameraUpdate instanceof DJICameraUpdateFactory.CameraBoundsUpdate) {
            DJICameraUpdateFactory.CameraBoundsUpdate boundsUpdate =
                    (DJICameraUpdateFactory.CameraBoundsUpdate) cameraUpdate;
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            DJILatLngBounds bounds = boundsUpdate.getBounds();
            builder.include(fromDJILatLng(bounds.getNortheast()))
                    .include(fromDJILatLng(bounds.getSouthwest()));

            if (boundsUpdate.getPadding() >= 0) {
                int width = boundsUpdate.getWidth();
                int height = boundsUpdate.getHeight();
                if (width == 0 || height == 0) {
                    update = CameraUpdateFactory.newLatLngBounds(builder.build(), boundsUpdate.getPadding());
                } else {
                    update = CameraUpdateFactory.newLatLngBounds(
                            builder.build(),
                            width,
                            height,
                            boundsUpdate.getPadding());
                }
            } else {
                update = CameraUpdateFactory.newLatLngBoundsRect(
                        builder.build(),
                        boundsUpdate.getPaddingLeft(),
                        boundsUpdate.getPaddingRight(),
                        boundsUpdate.getPaddingTop(),
                        boundsUpdate.getPaddingBottom());
            }
        } else if (cameraUpdate instanceof DJICameraUpdateFactory.CameraPositionUpdate) {
            DJICameraUpdateFactory.CameraPositionUpdate positionUpdate =
                    (DJICameraUpdateFactory.CameraPositionUpdate) cameraUpdate;
            CameraPosition position = new CameraPosition.Builder()
                    .target(fromDJILatLng(positionUpdate.getTarget()))
                    .zoom(positionUpdate.getZoom())
                    .tilt(positionUpdate.getTilt())
                    .bearing(positionUpdate.getBearing())
                    .build();
            update = CameraUpdateFactory.newCameraPosition(position);
        }
        return update;
    }

    public static DJICameraPosition fromCameraPosition(@NonNull CameraPosition cameraPosition) {
        return new DJICameraPosition.Builder()
                .target(fromLatLng(cameraPosition.target))
                .zoom(cameraPosition.zoom)
                .tilt(cameraPosition.tilt)
                .bearing(cameraPosition.bearing)
                .build();
    }

    public static BitmapDescriptor fromDJIBitmapDescriptor(@NonNull DJIBitmapDescriptor descriptor) {
        switch (descriptor.getType()) {
            case BITMAP:
                return BitmapDescriptorFactory.fromBitmap(descriptor.getBitmap());
            case PATH_ABSOLUTE:
                return BitmapDescriptorFactory.fromPath(descriptor.getPath());
            case PATH_ASSET:
                return BitmapDescriptorFactory.fromAsset(descriptor.getPath());
            case PATH_FILEINPUT:
                return BitmapDescriptorFactory.fromFile(descriptor.getPath());
            case RESOURCE_ID:
                return BitmapDescriptorFactory.fromResource(descriptor.getResourceId());
            case VIEW:
                return BitmapDescriptorFactory.fromView(descriptor.getView());
            default:
                throw new IllegalArgumentException("Unsupported bitmap descriptor type: " + descriptor.getType());
        }
    }

    public static PolygonOptions fromDJIPolygonOptions(@NonNull DJIPolygonOptions options) {
        PolygonOptions polygonOptions = new PolygonOptions();
        List<LatLng> points = new ArrayList<>(options.getPoints().size());
        for (DJILatLng point : options.getPoints()) {
            points.add(fromDJILatLng(point));
        }
        polygonOptions.strokeWidth(options.getStrokeWidth())
                .zIndex(options.getZIndex())
                .strokeColor(options.getStrokeColor())
                .fillColor(options.getFillColor())
                .visible(options.isVisible())
                .addAll(points);
        return polygonOptions;
    }

    public static PolylineOptions fromDJIPolylineOptions(@NonNull DJIPolylineOptions options) {
        PolylineOptions polylineOptions = new PolylineOptions();
        List<LatLng> points = new ArrayList<>(options.getPoints().size());
        for (DJILatLng point : options.getPoints()) {
            points.add(fromDJILatLng(point));
        }
        polylineOptions.width(options.getWidth())
                .zIndex(options.getZIndex())
                .color(options.getColor())
                .visible(options.isVisible())
                .geodesic(options.isGeodesic())
                .addAll(points);
        if (options.isDashed()) {
            polylineOptions.setDottedLine(true);
        }
        return polylineOptions;
    }

    public static CircleOptions fromDJICircleOptions(@NonNull DJICircleOptions options) {
        return new CircleOptions()
                .center(fromDJILatLng(options.getCenter()))
                .radius(options.getRadius())
                .strokeWidth(options.getStrokeWidth())
                .strokeColor(options.getStrokeColor())
                .fillColor(options.getFillColor())
                .zIndex(options.getZIndex());
    }
}
