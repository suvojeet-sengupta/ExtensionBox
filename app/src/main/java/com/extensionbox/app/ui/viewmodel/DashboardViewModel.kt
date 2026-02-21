package com.extensionbox.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.extensionbox.app.MonitorService
import com.extensionbox.app.Prefs
import com.extensionbox.app.ui.ModuleRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _isRunning = MutableStateFlow(Prefs.isRunning(context))
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

    private val _dashData = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val dashData: StateFlow<Map<String, Map<String, String>>> = _dashData.asStateFlow()

    val moduleOrder = mutableStateListOf<String>()
    val expandedStates = mutableStateMapOf<String, Boolean>()

    init {
        loadOrder()
        startDataPolling()
    }

    private fun loadOrder() {
        val savedOrder = Prefs.getString(context, "dash_card_order", "") ?: ""
        val initialOrder = if (savedOrder.isEmpty()) {
            (0 until ModuleRegistry.count()).map { ModuleRegistry.keyAt(it) }
        } else {
            savedOrder.split(",").filter { it.isNotEmpty() }
        }
        moduleOrder.clear()
        moduleOrder.addAll(initialOrder)
    }

    fun updateOrder(from: Int, to: Int) {
        moduleOrder.apply {
            add(to, removeAt(from))
        }
        Prefs.setString(context, "dash_card_order", moduleOrder.joinToString(","))
    }

    private fun startDataPolling() {
        viewModelScope.launch {
            while (true) {
                val running = Prefs.isRunning(context)
                _isRunning.value = running
                
                var count = 0
                for (i in 0 until ModuleRegistry.count()) {
                    if (Prefs.isModuleEnabled(context, ModuleRegistry.keyAt(i), ModuleRegistry.defAt(i)))
                        count++
                }
                _activeCount.value = count

                if (running) {
                    val dataMap = mutableMapOf<String, Map<String, String>>()
                    for (key in moduleOrder) {
                        val data = MonitorService.getModuleData(key)
                        if (data != null && data.isNotEmpty()) {
                            dataMap[key] = data
                        }
                    }
                    _dashData.value = dataMap
                } else {
                    _dashData.value = emptyMap()
                }
                
                delay(2000)
            }
        }
    }

    fun toggleExpansion(key: String) {
        expandedStates[key] = !(expandedStates[key] ?: false)
    }

    fun isExpanded(key: String): Boolean {
        return if (Prefs.getBool(context, "dash_expand_cards", true)) {
            expandedStates[key] ?: false
        } else {
            true
        }
    }
}
