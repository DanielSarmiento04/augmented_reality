package com.example.augmented_reality.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.augmented_reality.model.User
import com.example.augmented_reality.service.AuthenticationRequest
import com.example.augmented_reality.service.RetrofitInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log


class UserViewModel : ViewModel() {

    private val _user = MutableStateFlow(User(username = "", password = "", isAuthenticated = false))
    val user: StateFlow<User> = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _accessToken = MutableStateFlow<String?>(null)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val client_username = "Daniel"
    private val client_password = "Contravene"

    fun login(username: String, password: String) {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                // Step 1: Authenticate the client to get the access token
                val response = RetrofitInstance.authorizationService.authorize(
                    username = client_username,
                    password = client_password
                )
                _accessToken.value = response.access_token

                // Step 2: Authorize the user using the access token
                val isAuthenticated = RetrofitInstance.authenticationService.authenticate(
                    token = "Bearer ${_accessToken.value}",
                    request = AuthenticationRequest(username = username, password = password)
                )

                if (isAuthenticated) {
                    _user.value = User(username = username, password = password, isAuthenticated = true)
                    Log.d("UserViewModel", "User authenticated successfully.")
                } else {
                    _errorMessage.value = "Authentication failed. Please check your credentials."
                    _user.value = User(username = "", password = "", isAuthenticated = false)
                    Log.d("UserViewModel", "User authentication failed.")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Login failed. Please check your credentials."
                _user.value = User(username = "", password = "", isAuthenticated = false)
                Log.e("UserViewModel", "Exception during login", e)
            } finally {
                _isLoading.value = false
            }
        }
    }



    // Handle authentication check with the updated User model
    fun authenticate() {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val isAuthenticated = RetrofitInstance.authenticationService.authenticate(
                    token = "bearer ${_accessToken.value}",
                    request = AuthenticationRequest(username = _user.value.username, password = _user.value.password)
                )
                _user.value = _user.value.copy(isAuthenticated = isAuthenticated)
            } catch (e: Exception) {
                _errorMessage.value = "Authentication failed."
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
