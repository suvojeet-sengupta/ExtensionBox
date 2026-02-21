package com.extensionbox.app

import android.content.Context

object Prefs {
    private const val FILE = "ebox"

    private fun p(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isModuleEnabled(c: Context, key: String, def: Boolean): Boolean =
        p(c).getBoolean("m_${key}_enabled", def)

    fun setModuleEnabled(c: Context, key: String, val_: Boolean) =
        p(c).edit().putBoolean("m_${key}_enabled", val_).apply()

    fun isRunning(c: Context): Boolean = p(c).getBoolean("running", false)

    fun setRunning(c: Context, val_: Boolean) =
        p(c).edit().putBoolean("running", val_).apply()

    fun getInt(c: Context, key: String, def: Int): Int = p(c).getInt(key, def)

    fun setInt(c: Context, key: String, val_: Int) =
        p(c).edit().putInt(key, val_).apply()

    fun getLong(c: Context, key: String, def: Long): Long = p(c).getLong(key, def)

    fun setLong(c: Context, key: String, val_: Long) =
        p(c).edit().putLong(key, val_).apply()

    fun getBool(c: Context, key: String, def: Boolean): Boolean = p(c).getBoolean(key, def)

    fun setBool(c: Context, key: String, val_: Boolean) =
        p(c).edit().putBoolean(key, val_).apply()

    fun getString(c: Context, key: String, def: String?): String? = p(c).getString(key, def)

    fun setString(c: Context, key: String, val_: String?) =
        p(c).edit().putString(key, val_).apply()

    fun getAll(c: Context): Map<String, *> = p(c).all

    fun clearAll(c: Context) = p(c).edit().clear().apply()

    fun resetDailyStats(c: Context) {
        p(c).edit().apply {
            putInt("ulk_today", 0)
            putLong("stp_today", 0L)
            putLong("dat_daily_total", 0L)
            putLong("dat_daily_wifi", 0L)
            putLong("dat_daily_mobile", 0L)
            putLong("scr_on_acc", 0L)
            putInt("fap_today", 0)
            apply()
        }
    }

    fun importJson(c: Context, json: String) {
        try {
            val jsonObject = org.json.JSONObject(json)
            val editor = p(c).edit()
            val iterator = jsonObject.keys()
            while (iterator.hasNext()) {
                val key = iterator.next()
                when (val value = jsonObject.get(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    else -> editor.putString(key, value.toString())
                }
            }
            editor.apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
