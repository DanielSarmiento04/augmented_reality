package com.example.augmented_reality.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.augmented_reality.viewmodel.UserViewModel

@Composable
fun UserContentView(navController: NavHostController, userViewModel: UserViewModel = viewModel()) {
    // Collect StateFlow as state
    val user by userViewModel.user.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Welcome, ${user.username}")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            userViewModel.logout()
            navController.navigate("login")
        }) {
            Text(text = "Logout")
        }
    }
}
