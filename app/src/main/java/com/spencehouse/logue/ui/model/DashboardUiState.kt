package com.spencehouse.logue.ui.model

import com.spencehouse.logue.service.remote.dto.Vehicle

data class DashboardUiState(
    val vehicleName: String = "",
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVin: String? = null,
    val isEv: Boolean = true,
    val batteryPercentage: Int? = null,
    val range: Int? = null,
    val chargeStatus: String = "--",
    val chargeVoltage: String? = null,
    val odometer: Int? = null,
    val tirePressures: Map<String, Double?> = emptyMap(),
    val climateStatus: String = "OFF",
    val statusText: String = "Connecting...",
    val lastUpdated: String = "Never",
    val useMetric: Boolean = false,
    val isPluggedIn: Boolean = false,
    val targetChargeLevel: Int = 80,
    val isLoading: Boolean = false,
    val error: String? = null
)
