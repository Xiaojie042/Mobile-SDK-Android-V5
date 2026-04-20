package dji.v5.ux.mapkit.amap.map;

import android.graphics.Bitmap;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.Circle;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polygon;
import com.amap.api.maps.model.Polyline;

import java.util.HashMap;

import dji.v5.ux.mapkit.amap.annotations.ACircle;
import dji.v5.ux.mapkit.amap.annotations.AMarker;
import dji.v5.ux.mapkit.amap.annotations.APolygon;
import dji.v5.ux.mapkit.amap.annotations.APolyline;
import dji.v5.ux.mapkit.amap.utils.AMapUtils;
import dji.v5.ux.mapkit.core.callback.MapScreenShotListener;
import dji.v5.ux.mapkit.core.callback.OnCameraChangeListener;
import dji.v5.ux.mapkit.core.callback.OnMapTypeLoadedListener;
import dji.v5.ux.mapkit.core.camera.DJICameraUpdate;
import dji.v5.ux.mapkit.core.maps.DJIBaseMap;
import dji.v5.ux.mapkit.core.maps.DJIMap;
import dji.v5.ux.mapkit.core.maps.DJIProjection;
import dji.v5.ux.mapkit.core.maps.DJIUiSettings;
import dji.v5.ux.mapkit.core.models.DJIBitmapDescriptor;
import dji.v5.ux.mapkit.core.models.DJICameraPosition;
import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.models.annotations.DJICircle;
import dji.v5.ux.mapkit.core.models.annotations.DJICircleOptions;
import dji.v5.ux.mapkit.core.models.annotations.DJIGroupCircle;
import dji.v5.ux.mapkit.core.models.annotations.DJIGroupCircleOptions;
import dji.v5.ux.mapkit.core.models.annotations.DJIMarker;
import dji.v5.ux.mapkit.core.models.annotations.DJIMarkerOptions;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolygon;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolygonOptions;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolyline;
import dji.v5.ux.mapkit.core.models.annotations.DJIPolylineOptions;

public class AMapDelegate extends DJIBaseMap implements DJIMap,
        AMap.OnMarkerClickListener,
        AMap.OnMapClickListener,
        AMap.OnMarkerDragListener,
        AMap.OnInfoWindowClickListener,
        AMap.OnCameraChangeListener {

    private final AMap map;
    private final HashMap<Marker, AMarker> markerMap = new HashMap<>();

    public AMapDelegate(AMap map) {
        this.map = map;
        map.setOnMarkerClickListener(this);
        map.setOnMapClickListener(this);
        map.setOnMarkerDragListener(this);
        map.setOnInfoWindowClickListener(this);
        map.setOnCameraChangeListener(this);
        map.setOnMapLongClickListener(latLng -> onMapLongClick(AMapUtils.fromLatLng(latLng)));
    }

    @Override
    public DJIMarker addMarker(DJIMarkerOptions markerOptions) {
        DJILatLng latLng = markerOptions.getPosition();
        if (latLng == null) {
            throw new IllegalArgumentException("DJIMarkerOptions parameter must have position set");
        }

        MarkerOptions options = new MarkerOptions()
                .draggable(markerOptions.getDraggable())
                .position(AMapUtils.fromDJILatLng(latLng))
                .anchor(markerOptions.getAnchorU(), markerOptions.getAnchorV())
                .rotateAngle(markerOptions.getRotation())
                .zIndex(markerOptions.getZIndex())
                .visible(markerOptions.getVisible())
                .title(markerOptions.getTitle())
                .setFlat(markerOptions.isFlat());
        DJIBitmapDescriptor icon = markerOptions.getIcon();
        if (icon != null) {
            options.icon(AMapUtils.fromDJIBitmapDescriptor(icon));
        }
        options.infoWindowEnable(markerOptions.isInfoWindowEnable());
        Marker marker = map.addMarker(options);
        AMarker aMarker = new AMarker(marker, this);
        aMarker.setPositionCache(markerOptions.getPosition());
        aMarker.setRotationCache(markerOptions.getRotation());
        markerMap.put(marker, aMarker);
        return aMarker;
    }

    @Override
    public Object getMap() {
        return map;
    }

    @Override
    public void animateCamera(DJICameraUpdate cameraUpdate) {
        CameraUpdate update = AMapUtils.fromDJICameraUpdate(cameraUpdate);
        map.animateCamera(update);
    }

    @Override
    public void setOnCameraChangeListener(OnCameraChangeListener listener) {
        super.setOnCameraChangeListener(listener);
    }

    @Override
    public DJICameraPosition getCameraPosition() {
        return AMapUtils.fromCameraPosition(map.getCameraPosition());
    }

    @Override
    public void moveCamera(@NonNull DJICameraUpdate cameraUpdate) {
        map.moveCamera(AMapUtils.fromDJICameraUpdate(cameraUpdate));
    }

    @Override
    public void setInfoWindowAdapter(final InfoWindowAdapter adapter) {
        map.setInfoWindowAdapter(new AMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return adapter.getInfoWindow(markerMap.get(marker));
            }

            @Override
            public View getInfoContents(Marker marker) {
                return adapter.getInfoContents(markerMap.get(marker));
            }
        });
    }

    @Override
    public void setMapType(MapType type) {
        switch (type) {
            case SATELLITE:
                map.setMapType(AMap.MAP_TYPE_SATELLITE);
                map.showMapText(false);
                break;
            case HYBRID:
                map.setMapType(AMap.MAP_TYPE_SATELLITE);
                map.showMapText(true);
                break;
            case NORMAL:
            default:
                map.setMapType(AMap.MAP_TYPE_NORMAL);
                map.showMapText(true);
                break;
        }
    }

    @Override
    public void setMapType(MapType type, OnMapTypeLoadedListener listener) {
        setMapType(type);
        listener.onMapTypeLoaded();
    }

    @Override
    public void setMapType(int type) {
        setMapType(MapType.find(type));
    }

    @Override
    public DJIPolyline addPolyline(DJIPolylineOptions options) {
        Polyline polyline = map.addPolyline(AMapUtils.fromDJIPolylineOptions(options));
        return new APolyline(polyline);
    }

    @Nullable
    @Override
    public DJIPolygon addPolygon(DJIPolygonOptions options) {
        Polygon polygon = map.addPolygon(AMapUtils.fromDJIPolygonOptions(options));
        return new APolygon(polygon);
    }

    @Override
    public DJICircle addMarkerCircle(DJICircleOptions options) {
        return null;
    }

    @Override
    public DJIGroupCircle addGroupCircle(DJIGroupCircleOptions options) {
        return null;
    }

    @Nullable
    @Override
    public DJICircle addSingleCircle(DJICircleOptions options) {
        if (options.getRadius() <= 0 || options.getCenter() == null) {
            return null;
        }
        Circle circle = map.addCircle(AMapUtils.fromDJICircleOptions(options));
        return new ACircle(circle);
    }

    @Override
    public DJIUiSettings getUiSettings() {
        return new AUiSettings(map.getUiSettings());
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        if (markerMap.containsKey(marker)) {
            onMarkerClick(markerMap.get(marker));
        }
        return true;
    }

    @Override
    public void onMapClick(com.amap.api.maps.model.LatLng latLng) {
        onMapClick(AMapUtils.fromLatLng(latLng));
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        if (markerMap.containsKey(marker)) {
            onInfoWindowClick(markerMap.get(marker));
        }
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
        if (markerMap.containsKey(marker)) {
            onMarkerDragStart(markerMap.get(marker));
        }
    }

    @Override
    public void onMarkerDrag(Marker marker) {
        if (markerMap.containsKey(marker)) {
            AMarker aMarker = markerMap.get(marker);
            aMarker.setPosition(AMapUtils.fromLatLng(marker.getPosition()));
            onMarkerDrag(aMarker);
        }
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        if (markerMap.containsKey(marker)) {
            AMarker aMarker = markerMap.get(marker);
            aMarker.setPosition(AMapUtils.fromLatLng(marker.getPosition()));
            onMarkerDragEnd(aMarker);
        }
    }

    @Override
    public void onCameraChange(CameraPosition cameraPosition) {
        onCameraChange(AMapUtils.fromCameraPosition(cameraPosition));
    }

    @Override
    public void onCameraChangeFinish(CameraPosition cameraPosition) {
        onCameraChangeFinish(AMapUtils.fromCameraPosition(cameraPosition));
    }

    @Override
    public void snapshot(MapScreenShotListener callback) {
        map.getMapScreenShot(new AMap.OnMapScreenShotListener() {
            @Override
            public void onMapScreenShot(Bitmap bitmap) {
                callback.onMapScreenShot(bitmap);
            }

            @Override
            public void onMapScreenShot(Bitmap bitmap, int status) {
                callback.onMapScreenShot(bitmap);
            }
        });
    }

    @Override
    public DJIProjection getProjection() {
        return new AProjection(map.getProjection());
    }

    @Override
    public void clear() {
        map.clear();
        markerMap.clear();
    }

    public void onMarkerRemove(Marker marker) {
        markerMap.remove(marker);
    }
}
