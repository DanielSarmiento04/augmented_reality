// UserContentView.kt

package com.example.augmented_reality.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import com.example.augmented_reality.viewmodel.UserViewModel


@OptIn(ExperimentalMaterial3Api::class)
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
    val machines = listOf("motor", "Máquina 2", "Máquina 3")
    val rutinas = listOf("Rutina A", "Rutina B", "Rutina C")

    // State of current Drop down menu box

    // Navigate back to LoginView when user logs out
    LaunchedEffect(user.isAuthenticated) {
        if (!user.isAuthenticated) {
            navController.navigate("login") {
                popUpTo("userContent") { inclusive = true }
            }
        }
    }

    // UI layout structure
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
                .clip(RoundedCornerShape(20.dp)) // Apply clip modifier
                .wrapContentHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val context = LocalContext.current
            val input_stream = context.assets.open("bomba-centrifuga.webp")
            val image_bitmap = BitmapFactory.decodeStream(input_stream)

            // Header section with welcome and role texts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bienvenido, ${user.username}",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Role, ${user.username}",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Image section
            Image(
                bitmap = image_bitmap.asImageBitmap(),
                contentDescription = "Imagen de la máquina",
                modifier = Modifier
                    .size(250.dp)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dropdown for selecting a machine
//            Button(onClick = { machineExpanded = true }) {
//                Text(text = selectedMachine)
//            }
            ExposedDropdownMenuBox(
                expanded = machineExpanded,
                onExpandedChange = { machineExpanded = it }
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    textStyle = MaterialTheme.typography.bodySmall,
                    value = selectedMachine,
                    readOnly = true,
                    onValueChange = {
                        selectedMachine = it
                    },
                    shape = RoundedCornerShape(50),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null
                        )
                    },
                    maxLines = 1,
                    minLines = 1,
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

            // Dropdown for selecting a rutina de mantenimiento
            ExposedDropdownMenuBox(
                expanded = rutinaExpanded,
                onExpandedChange = { rutinaExpanded = it }
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    textStyle = MaterialTheme.typography.bodySmall,
                    value = selectedRutina,
                    readOnly = true,
                    onValueChange = {
                        selectedRutina = it
                    },
                    shape = RoundedCornerShape(50),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null
                        )
                    },
                    maxLines = 1,
                    minLines = 1,
                )
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
            }
            Spacer(modifier = Modifier.height(32.dp))

            // Button for initiating (Iniciar)
            Button(onClick = { /* Start action */ }) {
                Text(text = "Iniciar")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout button (Salir)
            Button(onClick = { userViewModel.logout() }) {
                Text(text = "Salir")
            }
        }
    }
}