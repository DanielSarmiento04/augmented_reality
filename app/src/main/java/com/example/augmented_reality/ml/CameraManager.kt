package com.example.opencv_tutorial

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.IOException
import kotlin.math.roundToInt
import org.opencv.core.Size as OpenCVSize
import java.util.Locale

/**
 * Manages camera preview and frame processing for real-time object detection
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewFinder: PreviewView
) {
    // Executor for camera operations
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    // Coroutine scope for processing frames
    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Tracking for frame processing performance
    private val performanceTracker = PerformanceTracker(10) // Average over 10 frames
    
    // Camera properties
    private var camera: Camera? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    
    // Resolution management
    private val preferredResolution = Size(640, 640) // Match model input size for efficiency
    
    // Processing state flags
    @Volatile private var isProcessing = false
    @Volatile private var processingSkipCount = 0
    
    // Frame processing callback
    var onFrameProcessed: ((Bitmap, List<YOLO11Detector.Detection>, Long) -> Unit)? = null
    
    companion object {
        private const val TAG = "CameraManager"
        private const val MAX_SKIP_FRAMES = 2 // Skip at most 2 frames when busy
    }

    /**
     * Start camera preview and analysis
     */
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
                Log.d(TAG, "Camera initialization completed")
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Bind camera use cases
     */
    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        // Select camera based on lens facing
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        // Preview use case
        val preview = Preview.Builder()
            .setTargetResolution(preferredResolution)
            .build()
            .also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

        // Image analysis use case
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(preferredResolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { image ->
                    processImage(image)
                }
            }

        try {
            // Unbind any bound use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            
            configureCamera()
            
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
        }
    }
    
    /**
     * Configure camera settings for optimal detection
     */
    private fun configureCamera() {
        camera?.let { camera ->
            // Enable auto-exposure, auto-focus
            camera.cameraControl.enableTorch(false)
            
            try {
                // Set optimal camera mode for video/preview
                val cameraControl = camera.cameraControl
                val cameraInfo = camera.cameraInfo
                
                // Try to set frame rate range for smoother preview
                if (BuildConfig.DEBUG) {
                    // Log exposure state information instead of checking hasCamera
                    Log.d(TAG, "Camera exposure state: ${cameraInfo.exposureState}")
                }
                
                // Request auto-focus mode for moving objects
                cameraControl.cancelFocusAndMetering()
            } catch (e: Exception) {
                Log.e(TAG, "Error configuring camera: ${e.message}")
            }
        }
    }

    /**
     * Process image from camera with optimized handling
     */
    private fun processImage(image: ImageProxy) {
        // Skip processing if we're still working on the previous frame
        if (isProcessing) {
            processingSkipCount++
            if (processingSkipCount <= MAX_SKIP_FRAMES) {
                image.close()
                return
            }
            // Force processing after skipping too many frames
            processingSkipCount = 0
        }

        isProcessing = true
        val startTime = System.currentTimeMillis()

        // Convert image to bitmap in IO dispatcher
        processingScope.launch {
            try {
                // Get bitmap from image proxy (efficient conversion)
                val bitmap = image.toBitmap()
                
                // Reorient bitmap if needed - use the more efficient rotateBitmapIfNeeded
                val rotatedBitmap = rotateBitmapIfNeeded(bitmap, image.imageInfo.rotationDegrees)
                
                // Get detector instance from the parent activity
                val detector = YOLODetectorProvider.getDetector(context)
                
                if (detector != null) {
                    // Run detection with high-quality preprocessed image
                    val detections = detector.detect(rotatedBitmap)
                    
                    // Track performance
                    val processingTime = System.currentTimeMillis() - startTime
                    performanceTracker.addMeasurement(processingTime)
                    
                    // Log performance every 30 frames
                    if (performanceTracker.totalFrames % 30 == 0) {
                        val avgFps = 1000.0 / performanceTracker.getAverageProcessingTime()
                        Log.d(TAG, "Avg processing time: ${performanceTracker.getAverageProcessingTime()}ms, " +
                                "FPS: ${"%.1f".format(avgFps)}, " +
                                "Detections: ${detections.size}")
                    }
                    
                    // Notify UI on main thread
                    withContext(Dispatchers.Main) {
                        onFrameProcessed?.invoke(rotatedBitmap, detections, processingTime)
                    }
                }
                
                // Clean up temporary bitmaps to avoid memory leaks
                if (bitmap != rotatedBitmap && rotatedBitmap !== viewFinder.bitmap) {
                    bitmap.recycle()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame: ${e.message}", e)
            } finally {
                // Always close the image and reset processing state when done
                image.close()
                isProcessing = false
            }
        }
    }

    /**
     * Efficiently convert ImageProxy to Bitmap using OpenCV for better performance
     */
    private suspend fun ImageProxy.toBitmap(): Bitmap = withContext(Dispatchers.Default) {
        try {
            val startTime = System.currentTimeMillis()
            
            // Use direct YUV to RGB conversion using ImageProxy planes
            val yuvMat = imageToYuvMat(this@toBitmap)
            
            // Convert YUV to RGB - detect format and use appropriate conversion
            val rgbMat = Mat()
            
            // CameraX in Android typically uses NV21 or YUV_420_888, which OpenCV processes differently
            if (format == ImageFormat.YUV_420_888) {
                Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2BGR_NV21)
            } else {
                // Fallback path for other formats
                Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2BGR_NV21)
            }
            
            // Apply moderate image enhancement suitable for real-time
            val enhancedRgbMat = enhanceImageQuality(rgbMat)
            
            // Reuse bitmap when possible to reduce allocations
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            
            // Convert Mat to Bitmap efficiently
            Utils.matToBitmap(enhancedRgbMat, bitmap)
            
            // Release Mats immediately to prevent memory leaks
            yuvMat.release()
            rgbMat.release()
            if (enhancedRgbMat !== rgbMat) {
                enhancedRgbMat.release()
            }
            
            val duration = System.currentTimeMillis() - startTime
            if (duration > 20) { // Only log slow conversions
                Log.d(TAG, "Image conversion took $duration ms")
            }
            
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error converting image: ${e.message}")
            // Fallback with simpler but potentially lower quality approach
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint()
            paint.color = android.graphics.Color.BLACK
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            bitmap
        }
    }

    /**
     * Convert ImageProxy to YUV Mat with proper handling of stride and formats
     */
    private fun imageToYuvMat(image: ImageProxy): Mat {
        val width = image.width
        val height = image.height
        val planes = image.planes
        
        // Calculate needed buffer size based on plane strides
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        
        // Allocate a single continuous buffer for all YUV data
        val yuvTotalSize = width * height * 3 / 2 // Standard size for YUV420
        val yuvMat = Mat(yuvTotalSize, 1, CvType.CV_8UC1)
        val yuvBytes = ByteArray(yuvTotalSize)
        
        // Get buffers
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        
        // Get strides
        val yPixelStride = yPlane.pixelStride
        val yRowStride = yPlane.rowStride
        val uPixelStride = uPlane.pixelStride 
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val vRowStride = vPlane.rowStride
        
        // If pixel stride is 1, we can copy rows directly for Y plane
        var position = 0
        if (yPixelStride == 1) {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(yuvBytes, position, width)
                position += width
            }
        } else {
            // Handle y plane with pixel stride > 1
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                for (col in 0 until width) {
                    yuvBytes[position++] = yBuffer.get()
                    if (col < width - 1) yBuffer.position(yBuffer.position() + yPixelStride - 1)
                }
            }
        }
        
        // Process UV planes - CRITICAL FIX: Correctly interleave U and V planes
        // Android camera often provides NV21 (VU order) but OpenCV expects it in specific order
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        
        // FIXED: Swap U and V channels based on detected format to ensure correct colors
        val isUVInterleaved = uPixelStride == 2 && vPixelStride == 2 && 
                             uPlane.buffer.capacity() == vPlane.buffer.capacity()
        val isVUFormat = isUVInterleaved && uPlane.buffer.compareTo(vPlane.buffer) > 0
        
        if (isUVInterleaved) {
            // Handle interleaved UV/VU format
            for (row in 0 until chromaHeight) {
                for (col in 0 until chromaWidth) {
                    val uvIndex = (row * uRowStride) + (col * 2)
                    if (isVUFormat) {
                        // NV21: VU order
                        yuvBytes[position++] = vBuffer.get(uvIndex)  // V first for NV21
                        yuvBytes[position++] = uBuffer.get(uvIndex)  // U second
                    } else {
                        // NV12: UV order
                        yuvBytes[position++] = uBuffer.get(uvIndex)  // U
                        yuvBytes[position++] = vBuffer.get(uvIndex)  // V
                    }
                }
            }
        } else {
            // Fall back to pixel-by-pixel copy for non-interleaved formats
            for (row in 0 until chromaHeight) {
                for (col in 0 until chromaWidth) {
                    val uIndex = (row * uRowStride) + (col * uPixelStride)
                    val vIndex = (row * vRowStride) + (col * vPixelStride)
                    // Default to NV21 format
                    yuvBytes[position++] = vBuffer.get(vIndex)  // V
                    yuvBytes[position++] = uBuffer.get(uIndex)  // U
                }
            }
        }
        
        // Put the data into the Mat
        yuvMat.put(0, 0, yuvBytes)
        
        // Reshape to proper dimensions for OpenCV processing
        val yuv420sp = yuvMat.reshape(1, height + height/2)
        return yuv420sp
    }
    
    /**
     * Apply image enhancements to improve visual quality
     * Includes auto white balance, contrast enhancement,
     * and noise reduction optimized for real-time processing
     */
    private fun enhanceImageQuality(inputMat: Mat): Mat {
        val result = Mat()
        
        try {
            // FIXED: Scale back image enhancements to prevent distortion
            // Simply convert colors without heavy processing for real-time performance
            inputMat.copyTo(result)
            
            // Apply very minimal enhancements - just slight white balancing
            val yuv = Mat()
            Imgproc.cvtColor(result, yuv, Imgproc.COLOR_BGR2YUV)
            val yuvChannels = ArrayList<Mat>(3)
            Core.split(yuv, yuvChannels)
            
            // FIXED: Dramatically reduce CLAHE strength to prevent color distortion
            val clahe = Imgproc.createCLAHE(1.2, OpenCVSize(3.0, 3.0))
            clahe.apply(yuvChannels[0], yuvChannels[0])
            
            // FIXED: Don't normalize UV channels, which causes color shifts
            // Just merge back the channels as they are
            Core.merge(yuvChannels, yuv)
            Imgproc.cvtColor(yuv, result, Imgproc.COLOR_YUV2BGR)
            
            // FIXED: Remove Gaussian blur entirely - it was causing image softening
            // Only apply minimal denoising if absolutely necessary on older devices
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                // Use very minimal blur only on older devices
                Imgproc.GaussianBlur(result, result, OpenCVSize(3.0, 3.0), 0.3)
            }
            
            // Clean up resources
            yuv.release()
            for (mat in yuvChannels) {
                mat.release()
            }
            
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Image enhancement failed: ${e.message}")
            // Return original if enhancement fails
            inputMat.copyTo(result)
            return result
        }
    }
    
    /**
     * Rotate bitmap if needed based on camera orientation
     * Optimized to avoid quality loss during rotation
     */
    private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotation: Int): Bitmap {
        // Skip if no rotation needed
        if (rotation == 0) return bitmap
        
        try {
            // Create transformation matrix
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            
            // Optimize memory allocation by reusing bitmaps when possible
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 
                0, 0, 
                bitmap.width, bitmap.height, 
                matrix, 
                true
            )
            
            // Only recycle original if it's different from what we started with
            if (bitmap != rotatedBitmap && bitmap != viewFinder.bitmap) {
                bitmap.recycle()
            }
            
            return rotatedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error during bitmap rotation: ${e.message}")
            return bitmap
        }
    }
    
    /**
     * Switch between front and back camera
     */
    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        
        // Get camera provider and rebind use cases
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            bindCameraUseCases(cameraProviderFuture.get())
        }, ContextCompat.getMainExecutor(context))
    }
    
    /**
     * Change detection resolution
     */
    fun setAnalysisResolution(width: Int, height: Int) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                imageAnalyzer?.let {
                    cameraProvider.unbind(it)
                }
                
                imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(Size(width, height))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { image ->
                            processImage(image)
                        }
                    }
                
                // Rebind use cases
                bindCameraUseCases(cameraProvider)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update resolution: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    /**
     * Release resources when no longer needed
     */
    fun shutdown() {
        processingScope.cancel()
        cameraExecutor.shutdown()
    }
    
    /**
     * Helper class to track performance metrics
     */
    private class PerformanceTracker(private val windowSize: Int) {
        private val processingTimes = ArrayDeque<Long>(windowSize)
        var totalFrames = 0
            private set
        
        fun addMeasurement(timeMs: Long) {
            if (processingTimes.size >= windowSize) {
                processingTimes.removeFirst()
            }
            processingTimes.add(timeMs)  // Changed from addLast to add
            totalFrames++
        }
        
        fun getAverageProcessingTime(): Double {
            if (processingTimes.isEmpty()) return 0.0
            return processingTimes.average()
        }
    }
}

/**
 * Singleton to provide detector instance to avoid multiple initializations
 */
object YOLODetectorProvider {
    private var detector: YOLO11Detector? = null
    private val initLock = Any()
    private const val TAG = "YOLODetectorProvider"
    
    fun getDetector(context: Context): YOLO11Detector? {
        if (detector == null) {
            synchronized(initLock) {
                if (detector == null) {
                    try {
                        // Try multiple model variants, matching MainActivity's approach
                        val modelVariants = listOf(
                            "best_float16.tflite",  // Try float16 first (smaller, works on many devices)
                            "best_float32.tflite",  // Try float32 as fallback (more compatible but larger)
                            "best.tflite"           // Try default naming as last resort
                        )
                        
                        val labelsPath = "classes.txt"
                        
                        // Check GPU compatibility
                        val useGPU = checkGpuCompatibility(context)
                        Log.d(TAG, "GPU acceleration decision: $useGPU")
                        
                        // Try model variants in sequence until one works
                        var lastException: Exception? = null
                        
                        for (modelFile in modelVariants) {
                            try {
                                // Check if file exists in assets
                                try {
                                    context.assets.open(modelFile).close()
                                } catch (e: IOException) {
                                    Log.d(TAG, "Model file $modelFile not found in assets, skipping")
                                    continue
                                }
                                
                                Log.d(TAG, "Attempting to load model: $modelFile")
                                
                                // Create detector with current model variant
                                detector = YOLO11Detector(
                                    context = context,
                                    modelPath = modelFile,
                                    labelsPath = labelsPath,
                                    useGPU = useGPU
                                )
                                
                                // If we get here, initialization succeeded
                                Log.d(TAG, "Successfully initialized detector with model: $modelFile")
                                break
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to initialize with model $modelFile: ${e.message}")
                                lastException = e
                                
                                // If this is GPU mode and failed, try again with CPU
                                if (useGPU) {
                                    try {
                                        Log.d(TAG, "Retrying model $modelFile with CPU only")
                                        detector = YOLO11Detector(
                                            context = context,
                                            modelPath = modelFile,
                                            labelsPath = labelsPath,
                                            useGPU = false
                                        )
                                        
                                        // If we get here, CPU initialization succeeded
                                        Log.d(TAG, "Successfully initialized detector with CPU and model: $modelFile")
                                        break
                                    } catch (cpuEx: Exception) {
                                        Log.e(TAG, "CPU fallback also failed for $modelFile: ${cpuEx.message}")
                                    }
                                }
                            }
                        }
                        
                        // If no model worked, log error
                        if (detector == null && lastException != null) {
                            Log.e(TAG, "Failed to initialize detector with any available model", lastException)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize detector: ${e.message}")
                        return null
                    }
                }
            }
        }
        return detector
    }

    /**
     * Check if the device is compatible with GPU acceleration
     */
    private fun checkGpuCompatibility(context: Context): Boolean {
        // Check if GPU delegation is supported
        val compatList = org.tensorflow.lite.gpu.CompatibilityList()
        val isGpuSupported = compatList.isDelegateSupportedOnThisDevice
        
        // Check if running on emulator
        val isEmulator = android.os.Build.FINGERPRINT.contains("generic") ||
                android.os.Build.FINGERPRINT.startsWith("unknown") ||
                android.os.Build.MODEL.contains("google_sdk") ||
                android.os.Build.MODEL.contains("Emulator") ||
                android.os.Build.MODEL.contains("Android SDK")
        
        // Check known problematic device models and manufacturers
        val deviceModel = android.os.Build.MODEL.toLowerCase(java.util.Locale.ROOT)
        val manufacturer = android.os.Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT)
        
        // List of known problematic device patterns
        val problematicPatterns = listOf(
            "mali-g57", "mali-g72", "mali-g52", "mali-g76",  // Some Mali GPUs have TFLite issues
            "adreno 6", "adreno 5",                          // Some older Adreno GPUs
            "mediatek", "mt6", "helio"                        // Some MediaTek chips
        )
        
        val isProblematicDevice = problematicPatterns.any { pattern ->
            deviceModel.contains(pattern) || manufacturer.contains(pattern)
        }
        
        // Check Android version - some versions have known TFLite GPU issues
        val androidVersion = android.os.Build.VERSION.SDK_INT
        val isProblematicAndroidVersion = androidVersion < android.os.Build.VERSION_CODES.P  // Android 9-
        
        // Check available memory - GPU acceleration needs sufficient memory
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        activityManager.getMemoryInfo(memoryInfo)
        
        val availableMem = memoryInfo.availMem / (1024 * 1024)  // Convert to MB
        val lowMemory = availableMem < 200  // Less than 200MB available
        
        // Final decision based on all factors
        return isGpuSupported &&
                !isEmulator &&
                !isProblematicDevice &&
                !isProblematicAndroidVersion &&
                !lowMemory
    }
    
    fun releaseDetector() {
        detector?.close()
        detector = null
    }
}
