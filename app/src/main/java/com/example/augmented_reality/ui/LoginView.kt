package com.example.augmented_reality.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.augmented_reality.viewmodel.UserViewModel

@Composable
fun LoginView(navController: NavHostController, userViewModel: UserViewModel = viewModel()) {
    val isLoading by userViewModel.isLoading.collectAsState()
    val errorMessage by userViewModel.errorMessage.collectAsState()
    val user by userViewModel.user.collectAsState()

    // Navigate to UserContentView if the user is authenticated
    LaunchedEffect(user) {
        if (user != null && user.isAuthenticated) {
            navController.navigate("userContent") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    // Username and password states
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Username input field
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(text = "Username") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password input field with masking
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(text = "Password") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Show a progress indicator if loading
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            // Login button
            Button(onClick = {
                // Call the view model to start the authorization process
                userViewModel.authorize(username, password)
            }) {
                Text(text = "Login")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display error message if login fails
        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = androidx.compose.ui.graphics.Color.Red)
        }
    }
}
