package com.spencehouse.logue.service.remote.dto

import com.google.gson.annotations.SerializedName

// region Register Client
data class ClientRegistrationRequest(
    @SerializedName("client_id") val clientId: String,
    @SerializedName("client_secret") val clientSecret: String
)

data class ClientRegistrationResponse(
    @SerializedName("clientregistrationkey") val clientRegistrationKey: ClientRegistrationKey
)

data class ClientRegistrationKey(
    @SerializedName("client_reg_key") val clientRegKey: String
)
// endregion

// region Generate Token
data class TokenRequest(
    @SerializedName("client_reg_key") val clientRegKey: String,
    @SerializedName("device_description") val deviceDescription: String,
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class TokenResponse(
    @SerializedName("request_status") val requestStatus: String,
    @SerializedName("token") val token: Token,
    @SerializedName("user") val user: User
)

data class Token(
    @SerializedName("access_token") val accessToken: String
)

data class User(
    @SerializedName("hidas_ident") val hidasIdent: String
)
// endregion

// region My Vehicle
data class VehicleInfoResponse(
    @SerializedName("status") val status: String,
    @SerializedName("vehicleInfo") val vehicleInfo: List<Vehicle>
)

data class Vehicle(
    @SerializedName("VIN") val vin: String,
    @SerializedName("ModelYear") val modelYear: String,
    @SerializedName("DivisionName") val divisionName: String,
    @SerializedName("ModelCode") val modelCode: String
)
// endregion

// region CIG Token
data class CigTokenRequest(
    val device: String
)

data class CigTokenResponse(
    val status: String,
    val responseBody: CigTokenResponseBody
)

data class CigTokenResponseBody(
    val token: String,
    val tokenSignature: String
)

//endregion

// region Remote Commands
data class DashboardRequest(val device: String, val filters: List<String>)

data class DashboardResponse(
    val status: String,
    val responseBody: DashboardResponseBody
)

data class DashboardResponseBody(
    val cigServiceRequestId: String
)

data class ClimateRequest(
    val device: String,
    val extend: Boolean,
    val pin: String,
    val vehicleControl: VehicleControl
)

data class VehicleControl(
    val acSetting: AcSetting
)

data class AcSetting(
    val acDefSetting: String, // "autoOn", "autoOff"
    val acTempVal: String
)

data class ClimateResponse(
    val status: String,
    val responseBody: ClimateResponseBody
)

data class ClimateResponseBody(
    val cigServiceRequestId: String
)

data class RemoteCommandRequest(
    val device: String,
    val pin: String
)

data class RemoteCommandResponse(
    val status: String, // "IN_PROGRESS", "success"
    val responseBody: RemoteCommandResponseBody? = null
)

data class RemoteCommandResponseBody(
    val cigServiceRequestId: String
)

data class TargetChargeLevelRequest(
    val device: String,
    val targetChargeLevel: Int
)

// endregion
