// LoginView.kt

package com.example.augmented_reality.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.augmented_reality.viewmodel.UserViewModel
import android.util.Log

@Composable
fun LoginView(
    navController: NavHostController,
    userViewModel: UserViewModel  // Remove default value
) {
    val isLoading by userViewModel.isLoading.collectAsState()
    val errorMessage by userViewModel.errorMessage.collectAsState()
    val user by userViewModel.user.collectAsState()

    // State for username and password fields
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Log the authentication state
    Log.d("LoginView", "User isAuthenticated: ${user.isAuthenticated}")

    // Navigate to UserContentView when authenticated
    if (user.isAuthenticated) {
        // Perform navigation
        LaunchedEffect(Unit) {
            navController.navigate("userContent") {
                popUpTo("login") { inclusive = true }
            }
        }
    }


    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Text(text = "Inicio de Sesión")
            Spacer(modifier = Modifier.height(16.dp))

            // Username input field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario") },
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Input Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(text = "Contraseña") },
                modifier = Modifier.fillMaxWidth(0.8f),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Show error message if present
            errorMessage?.let {
                Text(text = it, color = Color.Red)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Login Button
            Button(
                onClick = {
                    // Call the login function with user input
                    userViewModel.login(
                        username = username,
                        password = password
                    )
                },
                enabled = !isLoading // Disable the button if loading is true
            ) {
                Text(text = "Login")
            }

            // Retry button if login failed
            if (errorMessage != null) {
                TextButton(onClick = { userViewModel.login(username, password) }) {
                    Text(text = "Retry", color = Color.Blue)
                }
            }
        }
    }
}
