package com.example.augmented_reality.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.augmented_reality.viewmodel.ManualViewModel
import androidx.compose.material3.Text


@Composable
fun ManualView(
    navController: NavHostController,
    manualViewModel: ManualViewModel = viewModel(),
    pdfName: String
)
{
    val context = LocalContext.current

    // Observe state from the ManualViewModel
    val pdfPages by manualViewModel.pdfPages.collectAsState()
    val errorMessage by manualViewModel.errorMessage.collectAsState()

    // Load the PDF when the view is created
    LaunchedEffect(Unit) {
        manualViewModel.displayPdf(context, pdfName)
    }

    // Display the PDF pages or an error message
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (errorMessage != null) {
            Text(text = errorMessage ?: "Unknown error")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                pdfPages.forEach { bitmap: Bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .aspectRatio(1f)
                    )
                }
            }
        }
    }
}