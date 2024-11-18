package com.example.augmented_reality.viewmodel

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.github.barteksc.pdfviewer.PDFView

class PdfViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val machineId = intent.getStringExtra("MACHINE_ID")
        val pdfPath = "assets/$machineId/$machineId.pdf"

        // Set up PDFView
        val pdfView = PDFView(this, null)
        setContentView(pdfView)

        // Load the PDF
        pdfView.fromAsset(pdfPath)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .load()
    }
}