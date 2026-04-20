package dji.v5.ux.mapkit.amap.map;

import android.content.Context;

import androidx.annotation.NonNull;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;

import java.util.ArrayList;
import java.util.List;

import dji.v5.ux.mapkit.core.Mapkit;
import dji.v5.ux.mapkit.core.maps.DJIMapView;
import dji.v5.ux.mapkit.core.maps.DJIMapViewInternal;

public class AMapView extends MapView implements DJIMapViewInternal {

    private final List<DJIMapView.OnDJIMapReadyCallback> pendingCallbacks = new ArrayList<>();
    private AMapDelegate mapDelegate;

    public AMapView(@NonNull Context context) {
        super(context);
        post(this::ensureMapInitialized);
    }

    @Override
    public void onStart() {
        // AMap MapView does not require an explicit onStart callback.
    }

    @Override
    public void onStop() {
        // AMap MapView does not require an explicit onStop callback.
    }

    @Override
    public void getDJIMapAsync(DJIMapView.OnDJIMapReadyCallback callback) {
        if (mapDelegate != null) {
            callback.onDJIMapReady(mapDelegate);
            return;
        }
        pendingCallbacks.add(callback);
        post(this::ensureMapInitialized);
    }

    private void ensureMapInitialized() {
        if (mapDelegate != null) {
            flushCallbacks();
            return;
        }
        AMap aMap = getMap();
        if (aMap == null) {
            return;
        }
        mapDelegate = new AMapDelegate(aMap);
        mapDelegate.setMapType(Mapkit.getMapType());
        flushCallbacks();
    }

    private void flushCallbacks() {
        if (mapDelegate == null || pendingCallbacks.isEmpty()) {
            return;
        }
        List<DJIMapView.OnDJIMapReadyCallback> callbacks = new ArrayList<>(pendingCallbacks);
        pendingCallbacks.clear();
        for (DJIMapView.OnDJIMapReadyCallback callback : callbacks) {
            callback.onDJIMapReady(mapDelegate);
        }
    }
}
