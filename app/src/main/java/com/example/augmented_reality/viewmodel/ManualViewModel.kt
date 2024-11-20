package com.example.augmented_reality.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow

class ManualViewModel : ViewModel() {

    private val _exists_file =  MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val basePath = "assets" // Base path to the assets folder
    private val _filePath = MutableStateFlow<String?>(null)
    
    fun display_pdf (name_pdf:String) {
        Log.d("ManualViewModel", "Verify the path.")

        try {
            val fixed_name_pdf = name_pdf.lowercase().replace(" ", "_") + ".pdf"
            val fullPath = "$basePath/$fixed_name_pdf"

            _exists_file.value = true

            Log.d("ManualViewModel", fullPath)
            _filePath.value = fullPath

        } catch (e: Exception) {
            _errorMessage.value = "Fallo en la muestra del pdf"
            Log.e("ManualViewModel", "Exception during show file", e)
        } finally {
            _exists_file.value = false
        }
    }
}