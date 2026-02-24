package com.spencehouse.logue.service

import android.util.Log
import com.spencehouse.logue.service.mqtt.AwsMqttClient
import com.spencehouse.logue.service.remote.HondaWscApi
import com.spencehouse.logue.service.remote.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class DashboardData(
    val batteryPercentage: Int,
    val range: Int
)

@Serializable
private data class MqttDashboardResponse(
    val state: MqttDashboardState
)

@Serializable
private data class MqttDashboardState(
    val reported: MqttDashboardReported
)

@Serializable
private data class MqttDashboardReported(
    @SerialName("EV_RANGE")
    val range: Int,
    @SerialName("EV_BATTERY_LEVEL")
    val batteryLevel: Int
)


@Singleton
class VehicleService @Inject constructor(
    private val wscApi: HondaWscApi,
    private val sessionManager: SessionManager,
    private val json: Json
) {
    private fun getHeaders(siteId: String, version: String = "1.0", messageId: String = UUID.randomUUID().toString().uppercase()): Map<String, String> {
        val accessToken = sessionManager.accessToken ?: throw Exception("No access token")
        val hidasIdent = sessionManager.hidasIdent ?: throw Exception("No HIDAS ident")

        return Config.COMMON_HEADERS.toMutableMap().apply {
            put("Authorization", "Bearer $accessToken")
            put("hondaHeaderType.version", version)
            put("hondaHeaderType.siteId", siteId)
            put("hondaHeaderType.messageId", messageId)
            put("hondaHeaderType.systemId", "com.honda.hondalink.cv_android")
            put("hondaHeaderType.userId", hidasIdent)
            put("hondaHeaderType.hidasId", hidasIdent)
            put("hondaHeaderType.clientType", "Mobile")
            put("hondaHeaderType.collectedTimeStamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()))
            put("Content-Type", "application/json")
            put("Accept", "application/json")
        }
    }

    @Suppress("unused")
    suspend fun getDashboardData(vin: String): Result<DashboardData> = suspendCancellableCoroutine { continuation ->
        var mqttClient: AwsMqttClient? = null
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)

        continuation.invokeOnCancellation {
            job.cancel()
            mqttClient?.disconnect()
        }

        val onMessageCallback: (String, String) -> Unit = { topic, payload ->
            if (topic.contains("DASHBOARD_ASYNC/update/accepted")) {
                val response = json.decodeFromString<MqttDashboardResponse>(payload)
                val dashboardData = DashboardData(
                    batteryPercentage = response.state.reported.batteryLevel,
                    range = response.state.reported.range
                )
                continuation.resume(Result.success(dashboardData))
            }
        }

        val onConnected: () -> Unit = {
            scope.launch {
                requestDashboard(vin)
            }
        }

        val onError: (String) -> Unit = {
            continuation.resumeWithException(Exception(it))
        }

        scope.launch {
            getCigToken(vin).onSuccess { cigToken ->
                val client = AwsMqttClient(
                    vin = vin,
                    cigToken = cigToken.token,
                    cigSignature = cigToken.tokenSignature,
                    onMessageCallback = onMessageCallback,
                    onConnected = onConnected,
                    onError = onError
                )
                mqttClient = client
                client.connect()
            }.onFailure {
                continuation.resumeWithException(it)
            }
        }
    }

    suspend fun getCigToken(vin: String): Result<CigTokenResponseBody> {
        val tag = "VehicleService.CIG"
        return try {
            Log.d(tag, "Fetching CIG Token for VIN: $vin")
            val headers = getHeaders(siteId = "b407a3025b374f668475e97d2e750816")
            val resp = wscApi.getCigToken(headers, CigTokenRequest(vin))
            val body = resp.body()
            if (resp.isSuccessful && body?.status == "Success") {
                Log.d(tag, "Successfully acquired CIG Token")
                Result.success(body.responseBody)
            } else {
                val errorBody = resp.errorBody()?.string()
                Log.e(tag, "Failed CIG Token request. Code: ${resp.code()}, Error: $errorBody")
                Result.failure(Exception("Failed to get CIG token: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception during getCigToken", e)
            Result.failure(e)
        }
    }

    suspend fun requestDashboard(vin: String): Result<String> {
        val tag = "VehicleService.DashboardReq"
        return try {
            Log.d(tag, "Requesting Dashboard update for VIN: $vin")
            val headers = getHeaders(siteId = "18d216af12884813987e6b7f75a005a1", messageId = "I-13")
            val resp = wscApi.requestDashboard(headers, DashboardRequest(vin, Config.DASHBOARD_FILTERS))
            val body = resp.body()
            if (resp.isSuccessful && body?.status == "success") {
                Log.d(tag, "Successfully requested Dashboard update. CIG Request ID: ${body.responseBody.cigServiceRequestId}")
                Result.success(body.responseBody.cigServiceRequestId)
            } else {
                val errorBody = resp.errorBody()?.string()
                Log.e(tag, "Failed Dashboard request. Code: ${resp.code()}, Error: $errorBody")
                Result.failure(Exception("Dashboard request failed: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(tag, "Exception during requestDashboard", e)
            Result.failure(e)
        }
    }

    suspend fun startClimate(vin: String, pin: String, temperature: Int): Result<String> {
        return try {
            val headers = getHeaders(siteId = "18d216af12884813987e6b7f75a005a1", messageId = "S-1")
            val request = ClimateRequest(
                device = vin,
                extend = false,
                pin = pin,
                vehicleControl = VehicleControl(AcSetting("autoOn", temperature.toString()))
            )
            val resp = wscApi.startClimate(headers, request)
            val body = resp.body()
            if (resp.isSuccessful && (body?.status == "success" || body?.status == "IN_PROGRESS")) {
                Result.success(body.responseBody.cigServiceRequestId)
            } else {
                Result.failure(Exception("Start climate failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stopClimate(vin: String, pin: String): Result<String> {
        return try {
            val headers = getHeaders(siteId = "18d216af12884813987e6b7f75a005a1", messageId = "S-1")
            val request = ClimateRequest(
                device = vin,
                extend = false,
                pin = pin,
                vehicleControl = VehicleControl(AcSetting("autoOff", "72"))
            )
            val resp = wscApi.stopClimate(headers, request)
            val body = resp.body()
            if (resp.isSuccessful && (body?.status == "success" || body?.status == "IN_PROGRESS")) {
                Result.success(body.responseBody.cigServiceRequestId)
            } else {
                Result.failure(Exception("Stop climate failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setTargetChargeLevel(vin: String, level: Int): Result<String?> {
        return try {
            val headers = getHeaders(siteId = "18d216af12884813987e6b7f75a005a1", messageId = "S-1")
            val resp = wscApi.setTargetChargeLevel(headers, TargetChargeLevelRequest(vin, level))
            val body = resp.body()
            if (resp.isSuccessful && (body?.status == "success" || body?.status == "IN_PROGRESS")) {
                Result.success(body.responseBody?.cigServiceRequestId)
            } else {
                Result.failure(Exception("Set charge target failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestLightHorn(vin: String, pin: String, action: String): Result<String?> {
        return try {
            val headers = getHeaders(siteId = "18d216af12884813987e6b7f75a005a1", messageId = "S-1")
            val resp = wscApi.requestLightHorn(action, headers, RemoteCommandRequest(vin, pin))
            val body = resp.body()
            if (resp.isSuccessful && (body?.status == "success" || body?.status == "IN_PROGRESS")) {
                Result.success(body.responseBody?.cigServiceRequestId)
            } else {
                Result.failure(Exception("Light/Horn failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestDoorLock(vin: String, pin: String, action: String): Result<String?> {
        return try {
            val headers = getHeaders(siteId = "18d216af12884813987e6b7f75a005a1", messageId = "S-1")
            val resp = wscApi.requestDoorLock(action, headers, RemoteCommandRequest(vin, pin))
            val body = resp.body()
            if (resp.isSuccessful && (body?.status == "success" || body?.status == "IN_PROGRESS")) {
                Result.success(body.responseBody?.cigServiceRequestId)
            } else {
                Result.failure(Exception("Door lock failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getClimateStatus(vin: String): Result<Map<String, Any>> {
        return try {
            val headers = getHeaders(siteId = "1d216af12884813987e6b7f75a005a1")
            val resp = wscApi.getClimateStatus(vin, headers)
            if (resp.isSuccessful && resp.body() != null) {
                Result.success(resp.body()!!)
            } else {
                Result.failure(Exception("Failed to get climate status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
