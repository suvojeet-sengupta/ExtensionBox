package com.extensionbox.app.modules

import android.content.Context
import com.extensionbox.app.SystemAccess
import java.util.LinkedHashMap

interface Module {
    fun key(): String
    fun name(): String
    fun emoji(): String
    fun description(): String
    fun defaultEnabled(): Boolean
    fun start(ctx: Context, sys: SystemAccess)
    fun stop()
    fun tick()
    fun tickIntervalMs(): Int
    fun compact(): String
    fun detail(): String
    fun dataPoints(): LinkedHashMap<String, String>
    fun alive(): Boolean
    fun checkAlerts(ctx: Context)

    /**
     * Priority for notification ordering. Lower = higher priority.
     * Battery=10, Screen=20, Sleep=30, Network=40, Data=50, Unlock=60, Steps=70, SpeedTest=80, etc.
     */
    fun priority(): Int
}
