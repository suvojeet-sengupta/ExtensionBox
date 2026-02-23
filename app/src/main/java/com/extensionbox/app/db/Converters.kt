package com.extensionbox.app.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.LinkedHashMap

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromString(value: String?): LinkedHashMap<String, String>? {
        if (value == null) return null
        val type = object : TypeToken<LinkedHashMap<String, String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromMap(map: LinkedHashMap<String, String>?): String? {
        if (map == null) return null
        return gson.toJson(map)
    }
}
