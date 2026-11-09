package online.aireport.split.data.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val iban: String? = null,
)

@Serializable
data class AuthResponse(val token: String, val user: User)

@Serializable
data class ServerConfig(val receiptScanEnabled: Boolean)

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

/** `iban = ""` clears the IBAN; the server normalises spaces and case. */
@Serializable
data class UpdateProfileRequest(val name: String, val iban: String)
