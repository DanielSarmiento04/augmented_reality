package com.example.augmented_reality.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.augmented_reality.model.User
import com.example.augmented_reality.service.ApiClient
import com.example.augmented_reality.service.AuthorizationService
import com.example.augmented_reality.service.AuthenticationRequest
import com.example.augmented_reality.service.AuthenticationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

class UserViewModel : ViewModel() {

    private val authorizationService = ApiClient.retrofit.create(AuthorizationService::class.java)
    private val authenticationService = ApiClient.retrofit.create(AuthenticationService::class.java)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private var accessToken: String? = null

    fun authorize(username: String, password: String) {
        _isLoading.value = true
        _errorMessage.value = ""

        viewModelScope.launch {
            try {
                // Perform authorization request
                val response = authorizationService.authorize(
                    grantType = "password",
                    username = username,
                    password = password
                )
                accessToken = response.access_token

                // Proceed to authentication
                authenticate(username, password)

            } catch (e: HttpException) {
                _errorMessage.value = "Authorization failed: ${e.message()}"
                _isLoading.value = false
            }
        }
    }

    private fun authenticate(username: String, password: String) {
        viewModelScope.launch {
            try {
                val request = AuthenticationRequest(username, password)
                val result = authenticationService.authenticate(
                    token = "bearer $accessToken",
                    request = request
                )

                if (result) {
                    // Authentication succeeded
                    _user.value = User(username = username, isAuthenticated = true)
                } else {
                    // Authentication failed
                    _errorMessage.value = "Authentication failed."
                    _user.value = null
                }
            } catch (e: HttpException) {
                _errorMessage.value = "Authentication failed: ${e.message()}"
                _user.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        _user.value = null
        accessToken = null
    }
}
