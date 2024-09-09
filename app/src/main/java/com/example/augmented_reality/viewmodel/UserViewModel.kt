package com.example.augmented_reality.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.augmented_reality.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserViewModel : ViewModel() {

    // StateFlow for user data
    private val _user = MutableStateFlow(User(username = "", isAuthenticated = false))
    val user: StateFlow<User> = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun login(username: String) {
        _isLoading.value = true
        viewModelScope.launch {
            delay(2000) // Simulate network call
            _user.value = User(username = username, isAuthenticated = true)
            _isLoading.value = false
        }
    }

    fun logout() {
        _user.value = User(username = "", isAuthenticated = false)
    }
}
