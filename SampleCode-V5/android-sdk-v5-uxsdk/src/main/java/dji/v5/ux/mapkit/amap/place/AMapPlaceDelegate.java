package dji.v5.ux.mapkit.amap.place;

import dji.v5.ux.mapkit.core.models.DJILatLng;
import dji.v5.ux.mapkit.core.places.DJIPlacesClient;
import dji.v5.ux.mapkit.core.places.DJIPoiSearchQuery;
import dji.v5.ux.mapkit.core.places.IInternalPlacesClient;

public class AMapPlaceDelegate implements IInternalPlacesClient {

    @Override
    public void searchPOIAsyn(DJILatLng latLng) {
        searchPOIAsyn(latLng, POI_RADIUS);
    }

    @Override
    public void searchPOIAsyn(DJILatLng latLng, int radius) {
        // Reserved for future POI capability wiring.
    }

    @Override
    public void setOnPoiSearchListener(DJIPlacesClient.OnPoiSearchListener onPoiSearchListener) {
        // Reserved for future POI capability wiring.
    }

    @Override
    public void setPoiSearchQuery(DJIPoiSearchQuery poiSearchQuery) {
        // Reserved for future POI capability wiring.
    }

    @Override
    public void setOnRegeocodeSearchListener(DJIPlacesClient.OnRegeocodeSearchListener onRegeocodeSearchListener) {
        // Reserved for future reverse geocode capability wiring.
    }

    @Override
    public void regeocodeSearchAsyn(DJILatLng latLng) {
        // Reserved for future reverse geocode capability wiring.
    }
}
