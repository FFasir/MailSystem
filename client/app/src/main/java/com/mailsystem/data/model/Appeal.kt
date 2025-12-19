package com.mailsystem.data.model

data class AppealRequest(
    val username: String,
    val password: String,
    val reason: String
)

data class AppealResponse(
    val id: Int,
    val user_id: Int,
    val username: String,
    val reason: String,
    val status: String,
    val created_at: String
)
