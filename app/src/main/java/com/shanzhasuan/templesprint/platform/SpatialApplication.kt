package com.shanzhasuan.templesprint.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.shanzhasuan.templesprint.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
