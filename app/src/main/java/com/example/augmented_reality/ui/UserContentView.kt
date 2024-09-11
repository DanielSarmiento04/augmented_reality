package com.example.augmented_reality.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.augmented_reality.viewmodel.UserViewModel

@Composable
fun UserContentView(
    navController: NavHostController,
    userViewModel: UserViewModel = viewModel()
) {
    val user by userViewModel.user.collectAsState()

    // If the user is not authenticated, navigate back to the login screen
    LaunchedEffect(user) {
        if (user == null || !user.isAuthenticated) {
            navController.navigate("login") {
                popUpTo("userContent") { inclusive = true }
            }
        }
    }

    // Display user content if authenticated
    user?.let {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Welcome, ${it.username}!")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                userViewModel.logout()
                // Navigate back to login after logout
                navController.navigate("login") {
                    popUpTo("userContent") { inclusive = true }
                }
            }) {
                Text(text = "Logout")
            }
        }
    } ?: run {
        // Optional: Show a loading indicator or placeholder
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Loading...")
        }
    }
}
