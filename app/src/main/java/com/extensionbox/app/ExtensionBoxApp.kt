package com.extensionbox.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class ExtensionBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            DynamicColors.applyToActivitiesIfAvailable(this)
        } catch (ignored: Exception) {
        }
    }
}
