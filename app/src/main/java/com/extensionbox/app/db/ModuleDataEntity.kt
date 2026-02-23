package com.extensionbox.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.LinkedHashMap

@Entity(tableName = "module_data")
data class ModuleDataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val moduleKey: String,
    val data: LinkedHashMap<String, String>,
    val timestamp: Long = System.currentTimeMillis()
)
