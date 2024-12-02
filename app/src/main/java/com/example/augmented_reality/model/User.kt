package com.example.augmented_reality.model

data class User(
    val username: String,
    val password: String,  // Add password field
    val role: String = "",
    val isAuthenticated: Boolean = false
)

data class UserResponse(
    val username: String,
    val role: String,
    val password: String
)