package com.example.augmented_reality.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.augmented_reality.viewmodel.ManualViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualView(
    navController: NavHostController,
    manualViewModel: ManualViewModel = viewModel(),
    pdfName: String
) {
    val context = LocalContext.current

    // State observing from ViewModel
    val pdfPages by manualViewModel.pdfPages.collectAsState()
    val errorMessage by manualViewModel.errorMessage.collectAsState()

    // State variables for zooming
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Current page state
    var currentPage by remember { mutableStateOf(0) }

    // Load the PDF when the composable is first created
    LaunchedEffect(Unit) {
        manualViewModel.displayPdf(context, pdfName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Manual") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 2.dp,
                    end = 8.dp,
                    top = paddingValues.calculateTopPadding() / 2, // Reduced top padding
                    bottom = paddingValues.calculateBottomPadding() / 2 // Reduced bottom padding
                )
        ) {
            if (errorMessage != null) {
                // Error message display
                Text(
                    text = errorMessage ?: "Unknown error",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(8.dp) // Smaller internal padding
                )
            } else {
                // Zoomable PDF display
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale *= zoom
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale.coerceIn(1f, 3f), // Limit zoom
                            scaleY = scale.coerceIn(1f, 3f),
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (pdfPages.isNotEmpty() && currentPage in pdfPages.indices) {
                        Image(
                            bitmap = pdfPages[currentPage].asImageBitmap(),
                            contentDescription = "Page $currentPage",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp)) // Reduced space between components

                // Page navigation controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp), // Reduced padding for navigation buttons
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0
                    ) {
                        Text(text = "Anterior")
                    }
                    Text(
                        text = "Pagina ${currentPage + 1} / ${pdfPages.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { if (currentPage < pdfPages.size - 1) currentPage++ },
                        enabled = currentPage < pdfPages.size - 1
                    ) {
                        Text(text = "Siguiente")
                    }
                }
            }
        }
    }
}
