package com.spencehouse.logue.service

import android.util.Log
import com.spencehouse.logue.service.remote.HondaWscApi
import com.spencehouse.logue.service.remote.IdentityApi
import com.spencehouse.logue.service.remote.dto.Vehicle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthService @Inject constructor(
    private val identityApi: IdentityApi,
    private val wscApi: HondaWscApi,
    val sessionManager: SessionManager
) {
    private val TAG = "AuthService"
    var vehicles: List<Vehicle> = emptyList()
    var selectedVin: String? = null

    suspend fun login(username: String? = null, password: String? = null, vin: String? = null): Result<Unit> {
        return try {
            val finalUsername = username ?: sessionManager.username ?: return Result.failure(Exception("No username provided"))
            val finalPassword = password ?: sessionManager.password ?: return Result.failure(Exception("No password provided"))
            
            Log.d(TAG, "Starting login for $finalUsername")
            // 1. Register Client
            val regResp = identityApi.registerClient(
                mapOf(
                    "client_id" to Config.CLIENT_ID,
                    "client_secret" to Config.CLIENT_SECRET
                )
            )
            val clientRegKey = regResp.body()?.clientRegistrationKey?.clientRegKey
                ?: return Result.failure(Exception("Failed to register client: ${regResp.code()}"))
            Log.d(TAG, "Client registered successfully")

            // 2. Generate Token
            val tokenResp = identityApi.generateToken(
                mapOf(
                    "client_reg_key" to clientRegKey,
                    "device_description" to "Android_Logue_Client",
                    "username" to finalUsername,
                    "password" to finalPassword
                )
            )
            val tokenData = tokenResp.body() ?: return Result.failure(Exception("Auth failed: ${tokenResp.code()}"))
            if (tokenData.requestStatus != "success") {
                return Result.failure(Exception("Auth status: ${tokenData.requestStatus}"))
            }

            sessionManager.accessToken = tokenData.token.accessToken
            sessionManager.hidasIdent = tokenData.user.hidasIdent
            sessionManager.username = finalUsername
            sessionManager.password = finalPassword
            Log.d(TAG, "Token generated and session saved")

            // 3. Get Vehicles
            val vehicleHeaders = Config.COMMON_HEADERS.toMutableMap().apply {
                put("Authorization", "Bearer ${sessionManager.accessToken}")
                put("hondaHeaderType.version", "2.0")
                put("hondaHeaderType.siteId", "00e0e97f0fb543208a918fc946dea334")
                put("hondaHeaderType.messageId", UUID.randomUUID().toString())
                put("hondaHeaderType.systemId", "com.honda.dealer.cv_android")
                put("hondaHeaderType.userId", sessionManager.hidasIdent!!)
                put("hondaHeaderType.clientType", "Mobile")
                put("hondaHeaderType.collectedTimeStamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()))
                put("Content-Type", "application/json")
                put("Accept", "application/json")
            }

            val vehiclesResp = wscApi.getVehicles(vehicleHeaders)
            val vehicleData = vehiclesResp.body() ?: return Result.failure(Exception("Failed to get vehicles: ${vehiclesResp.code()}"))
            
            if (vehicleData.status != "SUCCESS") {
                return Result.failure(Exception("Get vehicles failed: ${vehicleData.status}"))
            }

            this.vehicles = vehicleData.vehicleInfo
            Log.d(TAG, "Fetched ${vehicles.size} vehicles")
            if (vehicles.isEmpty()) {
                return Result.failure(Exception("No vehicles found on this account"))
            }

            val savedVin = sessionManager.vin
            this.selectedVin = vin ?: savedVin ?: vehicles.first().vin
            sessionManager.vin = selectedVin
            Log.d(TAG, "Selected VIN: $selectedVin")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Login exception", e)
            Result.failure(e)
        }
    }

    fun updateSelectedVin(vin: String) {
        this.selectedVin = vin
        sessionManager.vin = vin
        Log.d(TAG, "Persistence updated for VIN: $vin")
    }

    fun logout() {
        Log.d(TAG, "Logging out")
        sessionManager.logout()
        vehicles = emptyList()
        selectedVin = null
    }

    fun getVehicleName(): String {
        val vehicle = vehicles.find { it.vin == selectedVin } ?: vehicles.firstOrNull()
        val name = vehicle?.let { "${it.modelYear} ${it.divisionName} ${it.modelCode}" } ?: "Unknown Vehicle"
        Log.d(TAG, "getVehicleName: $name (selectedVin: $selectedVin, vehicleCount: ${vehicles.size})")
        return name
    }

    fun isLoggedIn(): Boolean {
        return sessionManager.username != null && sessionManager.password != null
    }
}
