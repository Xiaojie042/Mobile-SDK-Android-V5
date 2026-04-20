package dji.sampleV5.aircraft

import android.content.Context
import com.amap.api.maps.MapsInitializer
import dji.v5.utils.common.LogUtils

/**
 * Class Description
 *
 * @author Hoker
 * @date 2022/3/2
 *
 * Copyright (c) 2022, DJI All Rights Reserved.
 */
class DJIAircraftApplication : DJIApplication() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        com.cySdkyc.clx.Helper.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        initAMapSdk()
    }

    private fun initAMapSdk() {
        try {
            MapsInitializer.updatePrivacyShow(this, true, true)
            MapsInitializer.updatePrivacyAgree(this, true)
            MapsInitializer.initialize(this)
        } catch (throwable: Throwable) {
            LogUtils.e("DJIAircraftApplication", "Failed to initialize AMap SDK: ${throwable.message}")
        }
    }
}
