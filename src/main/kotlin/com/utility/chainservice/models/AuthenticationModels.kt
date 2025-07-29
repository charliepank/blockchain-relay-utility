package com.utility.chainservice.models

data class UserIdentity(
    val userId: String,
    val email: String,
    val walletAddress: String,
    val userType: String
)

data class AuthenticationResult(
    val success: Boolean,
    val userIdentity: UserIdentity? = null,
    val error: String? = null
)