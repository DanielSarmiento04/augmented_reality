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

@Composable
fun ManualView(
    navController: NavHostController,
    manualViewModel: ManualViewModel = viewModel(),
    pdfName: String
) {
    val context = LocalContext.current

    // Observe state from the ManualViewModel
    val pdfPages by manualViewModel.pdfPages.collectAsState()
    val errorMessage by manualViewModel.errorMessage.collectAsState()

    // State for zooming
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // State for current page
    var currentPage by remember { mutableStateOf(0) }

    // Load the PDF when the view is created
    LaunchedEffect(Unit) {
        manualViewModel.displayPdf(context, pdfName)
    }

    // Display the PDF pages or an error message
    Column(modifier = Modifier.fillMaxSize()) {
        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "Unknown error",
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        } else {
            // Zoomable area for the PDF page
            Box(
                modifier = Modifier
                    .weight(1f)  // Take up available vertical space
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale *= zoom
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale.coerceIn(1f, 3f),  // Limit zoom
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

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
