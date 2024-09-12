package com.example.augmented_reality.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.augmented_reality.model.User
import com.example.augmented_reality.service.AuthenticationRequest
import com.example.augmented_reality.service.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserViewModel : ViewModel() {

    // StateFlow for user data with the updated User model (including password)
    private val _user = MutableStateFlow(User(username = "", password = "", isAuthenticated = false))
    val user: StateFlow<User> = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _accessToken = MutableStateFlow<String?>(null)

    // Handle login (authorization) with the server
    fun login(username: String, password: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = RetrofitInstance.authorizationService.authorize(
                    username = username,
                    password = password
                )
                _accessToken.value = response.access_token
                _user.value = User(username = username, password = password, isAuthenticated = true)
            } catch (e: Exception) {
                _user.value = User(username = "", password = "", isAuthenticated = false)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Handle authentication check with the updated User model
    fun authenticate() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val isAuthenticated = RetrofitInstance.authenticationService.authenticate(
                    token = "bearer ${_accessToken.value}",
                    request = AuthenticationRequest(username = _user.value.username, password = _user.value.password)
                )
                _user.value = _user.value.copy(isAuthenticated = isAuthenticated)
            } catch (e: Exception) {
                _user.value = _user.value.copy(isAuthenticated = false)
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        _user.value = User(username = "", password = "", isAuthenticated = false)
        _accessToken.value = null
    }
}