package com.example.augmented_reality.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow

class ManualViewModel : ViewModel() {

    private val _exists_file =  MutableStateFlow(false)
    private val _base_path   =  MutableStateFlow("")

    fun display_pdf (name_pdf:String) {
        Log.d("ManualViewModel", "Verify the path.")

    }
}