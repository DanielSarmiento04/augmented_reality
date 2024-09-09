package com.example.augmented_reality.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.augmented_reality.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.augmented_reality.service.ApiClient
import com.example.augmented_reality.service.AuthorizationService
import com.example.augmented_reality.service.AuthenticationRequest
import com.example.augmented_reality.service.AuthenticationService

class UserViewModel : ViewModel() {
    private val _user = MutableStateFlow(User(username = "", password = "", isAuthenticated = false))
    val user: StateFlow<User> get() = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    private val _loginError = MutableStateFlow(false)
    val loginError: StateFlow<Boolean> get() = _loginError

    private val authorizationService = ApiClient.retrofit.create(AuthorizationService::class.java)
    private val authenticationService = ApiClient.retrofit.create(AuthenticationService::class.java)

    var accessToken: String? = null
    fun login(username: String, password: String) {
        if (username.isNotBlank() && password.isNotBlank()) {
            _isLoading.value = true
            viewModelScope.launch {
                delay(2000)
                if (username == "User123" && password == "Password123") {
                    _user.value = User(username = username, password = password, isAuthenticated = true)
                    _loginError.value = false
                } else {
                    _loginError.value = true
                }
                _isLoading.value = false
            }
        } else {
            _loginError.value = true
        }
    }

    fun logout() {
        _user.value = User(username = "", password = "", isAuthenticated = false)
    }
}
