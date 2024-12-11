package com.example.augmented_reality.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.augmented_reality.viewmodel.UserViewModel
import com.example.augmented_reality.viewmodel.ManualViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserContentView(
    navController: NavHostController,
    userViewModel: UserViewModel,
    manualViewModel: ManualViewModel = viewModel()
) {
    val user by userViewModel.user.collectAsState()

    var machineExpanded by remember { mutableStateOf(false) }
    var selectedMachine by remember { mutableStateOf("Seleccione una máquina") }

    var rutinaExpanded by remember { mutableStateOf(false) }
    var selectedRutina by remember { mutableStateOf("Seleccione una rutina de mantenimiento") }

    val machines = listOf("Motor Mono W22")
    val rutinas = listOf("Diaria", "Mensual", "Anual")

    LaunchedEffect(user.isAuthenticated) {
        if (!user.isAuthenticated) {
            navController.navigate("login") {
                popUpTo("userContent") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Placeholder for user initials
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.username.take(2).uppercase(),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Bienvenido, ${user.username}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Rol: ${user.role}",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = machineExpanded,
                onExpandedChange = { machineExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedMachine,
                    onValueChange = { selectedMachine = it },
                    readOnly = true,
                    label = { Text("Seleccione una máquina") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = machineExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
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
            }

            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = rutinaExpanded,
                onExpandedChange = { rutinaExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedRutina,
                    onValueChange = { selectedRutina = it },
                    readOnly = true,
                    label = { Text("Seleccione una rutina de mantenimiento") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = rutinaExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
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
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (selectedMachine != "Seleccione una máquina") {
                        navController.navigate("manualView/$selectedMachine")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Ver Documentación")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (selectedMachine != "Seleccione una máquina") {
                        navController.navigate("arView/$selectedMachine")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Iniciar Mantenimiento")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { userViewModel.logout() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(text = "Cerrar Sesión", color = Color.White)
            }
        }
    }
}
