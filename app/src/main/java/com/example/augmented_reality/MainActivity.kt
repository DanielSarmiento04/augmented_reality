// MainActivity.kt

package com.example.augmented_reality

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels  // Import this
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.augmented_reality.ui.LoginView
import com.example.augmented_reality.ui.UserContentView
import com.example.augmented_reality.ui.theme.Augmented_realityTheme
import com.example.augmented_reality.viewmodel.UserViewModel  // Import your ViewModel
import com.example.augmented_reality.ui.ManualView

class MainActivity : ComponentActivity() {
    private val userViewModel: UserViewModel by viewModels()  // Use viewModels() delegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Augmented_realityTheme {
                val navController = rememberNavController()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "login",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("login") {
                            LoginView(
                                navController = navController,
                                userViewModel = userViewModel  // Pass down the ViewModel
                            )
                        }
                        composable("userContent") {
                            UserContentView(
                                navController = navController,
                                userViewModel = userViewModel  // Pass down the ViewModel
                            )
                        }
                        composable("manualView/{pdfName}") { backStackEntry ->
                            val pdfName = backStackEntry.arguments?.getString("pdfName") ?: "unknown"
                            ManualView(navController = navController, pdfName = pdfName)
                        }


                    }
                }
            }
        }
    }
}
