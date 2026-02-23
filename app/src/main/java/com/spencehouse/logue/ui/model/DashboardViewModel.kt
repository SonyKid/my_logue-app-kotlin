package com.spencehouse.logue.ui.model

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spencehouse.logue.service.AuthService
import com.spencehouse.logue.service.VehicleService
import com.spencehouse.logue.service.mqtt.AwsMqttClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val authService: AuthService,
    private val vehicleService: VehicleService
) : ViewModel() {

    private val TAG = "DashboardViewModel"
    var uiState by mutableStateOf(DashboardUiState())
        private set

    private var mqttClient: AwsMqttClient? = null
    private var refreshJob: Job? = null
    
    var isRefreshing by mutableStateOf(false)
        private set

    init {
        Log.d(TAG, "Initializing DashboardViewModel")
        
        viewModelScope.launch {
            if (authService.vehicles.isEmpty()) {
                Log.d(TAG, "Vehicles empty, attempting silent login")
                authService.login()
            }
            
            val isEv = checkIfEv(authService.getVehicleName())
            uiState = uiState.copy(
                vehicleName = authService.getVehicleName(),
                vehicles = authService.vehicles,
                selectedVin = authService.selectedVin,
                isEv = isEv,
                useCelsius = authService.sessionManager.useCelsius,
                useKilometers = authService.sessionManager.useKilometers,
                useKpa = authService.sessionManager.useKpa
            )
            
            if (isEv) {
                connectMqtt()
            } else {
                updateStatus("Not an EV")
                refreshData() // Still get climate for non-EVs
            }
        }
        startAutoRefresh()
    }

    private fun checkIfEv(name: String): Boolean {
        val evModels = listOf("ZDX", "PROLOGUE", "EV")
        return evModels.any { name.uppercase().contains(it) }
    }

    private fun connectMqtt() {
        val vin = authService.selectedVin
        if (vin == null || !uiState.isEv) return
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Connecting MQTT for VIN: $vin")
                updateStatus("Authenticating MQTT...")
                val credsResult = vehicleService.getCigToken(vin)
                val creds = credsResult.getOrElse {
                    Log.e(TAG, "Failed to get CIG token", it)
                    val errorMsg = it.message ?: ""
                    if (errorMsg.contains("scope is invalid")) {
                        updateStatus("Not an EV")
                    } else {
                        updateStatus("Auth Error: $errorMsg")
                    }
                    return@launch
                }
                
                Log.d(TAG, "CIG Token received, initializing AwsMqttClient")
                updateStatus("Connecting to AWS IoT...")
                mqttClient?.disconnect()
                mqttClient = AwsMqttClient(
                    vin = vin,
                    cigToken = creds.token,
                    cigSignature = creds.tokenSignature,
                    onMessageCallback = { topic, payload -> 
                        Log.d(TAG, "MQTT Message received on $topic")
                        onMqttMessage(topic, payload) 
                    },
                    onConnected = {
                        Log.i(TAG, "MQTT Connected successfully")
                        updateStatus("Connected")
                        refreshData()
                    },
                    onError = { error ->
                        Log.e(TAG, "MQTT Client Error: $error")
                        updateStatus("Connection Error: $error")
                    }
                )
                mqttClient?.connect()
            } catch (e: Exception) {
                Log.e(TAG, "connectMqtt exception", e)
                updateStatus("Connection Error: ${e.message}")
            }
        }
    }

    private fun onMqttMessage(topic: String, payload: String) {
        try {
            val json = JSONObject(payload)
            Log.v(TAG, "Processing MQTT payload: $payload")
            if (topic.contains("DASHBOARD_ASYNC")) {
                updateDashboardUi(json)
            } else if (topic.contains("ENGINE_START_STOP_ASYNC")) {
                Log.d(TAG, "Engine start/stop update received")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MQTT message", e)
        }
    }

    private fun updateDashboardUi(data: JSONObject) {
        val reported = data.optJSONObject("state")?.optJSONObject("reported") ?: return
        val rb = reported.optJSONObject("responseBody") ?: return
        
        Log.d(TAG, "Updating UI with reported dashboard data")
        val evStatus = rb.optJSONObject("evStatus")
        val odometerData = rb.optJSONObject("odometer")
        val tireStatus = rb.optJSONObject("tireStatus")
        val chargeMode = rb.optJSONObject("getChargeMode")

        val battery = evStatus?.optInt("soc")
        val rangeVal = evStatus?.optInt("evRange")
        val chargeStatus = evStatus?.optString("chargeStatus")
        val plugStatus = evStatus?.optString("plugStatus")
        
        val targetLevel = chargeMode?.optJSONObject("generalAwayTargetChargeLevel")?.optInt("value") ?: 80

        val isPluggedIn = plugStatus?.lowercase() == "plugged" || chargeStatus?.lowercase() == "charging" || chargeStatus?.lowercase() == "complete"

        val (mainStatus, voltage) = formatChargeStatus(chargeStatus, plugStatus, rb)

        uiState = uiState.copy(
            batteryPercentage = battery,
            range = rangeVal,
            chargeStatus = mainStatus,
            chargeVoltage = voltage,
            isPluggedIn = isPluggedIn,
            targetChargeLevel = targetLevel,
            odometer = odometerData?.optInt("value"),
            tirePressures = parseTires(tireStatus),
            lastUpdated = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date()),
            statusText = "Data Received"
        )
    }

    private fun formatChargeStatus(chargeStatus: String?, plugStatus: String?, rb: JSONObject): Pair<String, String?> {
        val status = chargeStatus?.lowercase()
        val pStatus = plugStatus?.lowercase()
        
        if (pStatus != "plugged") {
            return "Unplugged" to null
        }

        var mainStatus = "Plugged In"
        when (status) {
            "charging" -> mainStatus = "Charging"
            "complete" -> mainStatus = "Complete"
        }
        
        val powerLevel = rb.optJSONObject("chargerPowerLevel")?.optString("value")
        val voltage = if (!powerLevel.isNullOrEmpty() && powerLevel != "0") {
            "${powerLevel}V"
        } else {
            null
        }

        return mainStatus to voltage
    }

    private fun parseTires(tireStatus: JSONObject?): Map<String, Double?> {
        val tires = mutableMapOf<String, Double?>()
        val positions = listOf("frontLeft", "frontRight", "rearLeft", "rearRight")
        positions.forEach { pos ->
            tires[pos] = tireStatus?.optJSONObject(pos)?.optJSONObject("pressureData")?.optDouble("value")
        }
        return tires
    }

    fun refreshData(): Job {
        val vin = authService.selectedVin ?: return viewModelScope.launch {}
        
        return viewModelScope.launch {
            isRefreshing = true
            Log.d(TAG, "Refreshing data and checking connection for VIN: $vin")
            
            if (uiState.isEv) {
                // Reconnect if status indicates an error or disconnect
                if (uiState.statusText.contains("Error") || uiState.statusText.contains("lost")) {
                    connectMqtt()
                }
                
                updateStatus("Requesting update...")
                val result = vehicleService.requestDashboard(vin)
                result.onFailure {
                    Log.e(TAG, "Manual dashboard request failed", it)
                    val errorMsg = it.message ?: ""
                    if (errorMsg.contains("scope is invalid")) {
                        updateStatus("Not an EV")
                        uiState = uiState.copy(isEv = false)
                    } else {
                        updateStatus("Refresh failed: $errorMsg")
                    }
                }
            } else {
                updateStatus("Not an EV")
            }
            
            val climateResult = vehicleService.getClimateStatus(vin)
            climateResult.onSuccess {
                val status = it["climateStatus"] as? String ?: "OFF"
                Log.d(TAG, "Climate status received: $status")
                uiState = uiState.copy(climateStatus = status.uppercase())
            }.onFailure {
                Log.e(TAG, "Climate status request failed", it)
            }
            
            isRefreshing = false
        }
    }

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                delay(60000)
                Log.d(TAG, "Auto-refreshing data")
                refreshData()
            }
        }
    }

    fun toggleCelsius(value: Boolean) {
        authService.sessionManager.useCelsius = value
        uiState = uiState.copy(useCelsius = value)
    }

    fun toggleKilometers(value: Boolean) {
        authService.sessionManager.useKilometers = value
        uiState = uiState.copy(useKilometers = value)
    }

    fun toggleKpa(value: Boolean) {
        authService.sessionManager.useKpa = value
        uiState = uiState.copy(useKpa = value)
    }

    private fun updateStatus(text: String) {
        uiState = uiState.copy(statusText = text)
    }

    fun onVehicleChange(vin: String) {
        if (vin == authService.selectedVin) return
        
        Log.i(TAG, "Changing vehicle to VIN: $vin")
        mqttClient?.disconnect()
        
        // This was the missing link - update persistent storage!
        authService.updateSelectedVin(vin)
        
        val isEv = checkIfEv(authService.getVehicleName())
        uiState = uiState.copy(
            selectedVin = vin,
            vehicleName = authService.getVehicleName(),
            isEv = isEv,
            batteryPercentage = null,
            range = null,
            statusText = if (isEv) "Switching vehicles..." else "Not an EV"
        )
        
        if (isEv) {
            connectMqtt()
        } else {
            refreshData()
        }
    }

    fun logout() {
        Log.i(TAG, "User logged out from Dashboard")
        mqttClient?.disconnect()
        authService.logout()
    }

    fun setTargetChargeLevel(level: Int) {
        val vin = authService.selectedVin ?: return
        if (!uiState.isEv) return
        
        viewModelScope.launch {
            Log.d(TAG, "Setting target charge level to $level%")
            vehicleService.setTargetChargeLevel(vin, level)
            refreshData()
        }
    }

    fun sendCommand(name: String, action: suspend (String) -> Result<String?>, pin: String, targetStatus: String? = null) {
        viewModelScope.launch {
            Log.i(TAG, "Sending command: $name")
            updateStatus("Sending $name command...")
            val result = action(pin)
            result.onSuccess {
                Log.i(TAG, "Command $name sent successfully")
                updateStatus("$name command sent!")
                if (targetStatus != null) {
                    startAggressivePolling(targetStatus)
                }
            }.onFailure {
                Log.e(TAG, "Command $name failed", it)
                updateStatus("$name failed: ${it.message}")
            }
        }
    }

    private fun startAggressivePolling(targetStatus: String) {
        viewModelScope.launch {
            Log.d(TAG, "Starting aggressive polling for target status: $targetStatus")
            for (i in 1..12) {
                if (uiState.climateStatus == targetStatus) {
                    Log.i(TAG, "Target status reached after $i polls")
                    break
                }
                delay(5000)
                refreshData()
            }
        }
    }

    fun startClimate(pin: String) = sendCommand("Start Climate", { p ->
        val temp = if (uiState.useCelsius) 22 else 72
        vehicleService.startClimate(authService.selectedVin!!, p, temp)
    }, pin, "ON")

    fun stopClimate(pin: String) = sendCommand("Stop Climate", { p -> 
        vehicleService.stopClimate(authService.selectedVin!!, p) 
    }, pin, "OFF")

    fun flashLights(pin: String) = sendCommand("Flash Lights", { p -> 
        vehicleService.requestLightHorn(authService.selectedVin!!, p, "lgt") 
    }, pin)

    fun soundHorn(pin: String) = sendCommand("Sound Horn", { p -> 
        vehicleService.requestLightHorn(authService.selectedVin!!, p, "hrn") 
    }, pin)

    fun lockDoors(pin: String) = sendCommand("Lock Doors", { p -> 
        vehicleService.requestDoorLock(authService.selectedVin!!, p, "alk") 
    }, pin)

    fun unlockDoors(pin: String) = sendCommand("Unlock Doors", { p -> 
        vehicleService.requestDoorLock(authService.selectedVin!!, p, "dulk") 
    }, pin)

    override fun onCleared() {
        Log.d(TAG, "DashboardViewModel cleared, disconnecting MQTT")
        mqttClient?.disconnect()
        refreshJob?.cancel()
        super.onCleared()
    }
}
