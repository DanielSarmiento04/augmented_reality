package com.example.augmented_reality.model

data class User(
    val username: String,
    val password: String,  // Add password field
    val isAuthenticated: Boolean = false
)
