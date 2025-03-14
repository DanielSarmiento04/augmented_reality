package com.example.opencv_tutorial

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

/**
 * Application class that initializes OpenCV at app startup
 */
class YoloApplication : Application() {
    // Application scope for background tasks
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Initialize OpenCV in a background thread to avoid blocking UI
        applicationScope.launch {
            try {
                // Initialize OpenCV asynchronously
                val initSuccess = OpenCVLoader.initDebug()
                if (!initSuccess) {
                    Log.e(TAG, "OpenCV initialization failed")
                } else {
                    Log.i(TAG, "OpenCV initialization succeeded")
                    
                    // Load the native library
                    System.loadLibrary("opencv_java4")
                    Log.i(TAG, "OpenCV native library loaded")
                    
                    // Preload YOLO detector in background to reduce first detection latency
                    preloadDetector()
                }
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load OpenCV native library", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error during OpenCV initialization", e)
            }
        }
    }
    
    /**
     * Preload detector to warm up TFLite and reduce first-run latency
     */
    private fun preloadDetector() {
        try {
            // Initialize detector in background
            applicationScope.launch {
                Log.d(TAG, "Preloading YOLO detector")
                YOLODetectorProvider.getDetector(this@YoloApplication)
                Log.d(TAG, "YOLO detector preloaded successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to preload detector", e)
        }
    }

    companion object {
        private const val TAG = "YoloApplication"
    }
}
