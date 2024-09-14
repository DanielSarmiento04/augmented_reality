// UserContentView.kt

package com.example.augmented_reality.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.example.augmented_reality.R
import com.example.augmented_reality.viewmodel.UserViewModel

@Composable
fun UserContentView(
    navController: NavHostController,
    userViewModel: UserViewModel
) {
    // Observe user state
    val user by userViewModel.user.collectAsState()

    // States for dropdowns
    var machineExpanded by remember { mutableStateOf(false) }
    var selectedMachine by remember { mutableStateOf("Seleccione una máquina") }

    var rutinaExpanded by remember { mutableStateOf(false) }
    var selectedRutina by remember { mutableStateOf("Seleccione una rutina de mantenimiento") }

    // Sample data for machines and rutinas
    val machines = listOf("Máquina 1", "Máquina 2", "Máquina 3")
    val rutinas = listOf("Rutina A", "Rutina B", "Rutina C")

    // Navigate back to LoginView when user logs out
    LaunchedEffect(user.isAuthenticated) {
        if (!user.isAuthenticated) {
            navController.navigate("login") {
                popUpTo("userContent") { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val context = LocalContext.current
        val input_stream = context.assets.open("bomba-playstore.png")
        val image_bitmap = BitmapFactory.decodeStream(input_stream)

        Text(text = "Bienvenido, ${user.username}!")

        Spacer(modifier = Modifier.height(16.dp))

        // Static image representing machine selection
        Image(
            bitmap = image_bitmap.asImageBitmap(),
            contentDescription = "Imagen de la máquina",
            modifier = Modifier.size(200.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Dropdown for selecting a machine
        Button(onClick = { machineExpanded = true }) {
            Text(text = selectedMachine)
        }
        DropdownMenu(
            expanded = machineExpanded,
            onDismissRequest = { machineExpanded = false }
        ) {
            machines.forEach { machine ->
                DropdownMenuItem(
                    text = { Text(text = machine) },
                    onClick = {
                        selectedMachine = machine
                        machineExpanded = false
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dropdown for selecting a rutina de mantenimiento
        Button(onClick = { rutinaExpanded = true }) {
            Text(text = selectedRutina)
        }
        DropdownMenu(
            expanded = rutinaExpanded,
            onDismissRequest = { rutinaExpanded = false }
        ) {
            rutinas.forEach { rutina ->
                DropdownMenuItem(
                    text = { Text(text = rutina) },
                    onClick = {
                        selectedRutina = rutina
                        rutinaExpanded = false
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Logout button
        Button(onClick = { userViewModel.logout() }) {
            Text(text = "Cerrar sesión")
        }
    }
}
