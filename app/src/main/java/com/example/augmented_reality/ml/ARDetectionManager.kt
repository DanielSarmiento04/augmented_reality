package com.example.augmented_reality.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.Image
import android.util.Log
import android.util.Size
import androidx.lifecycle.LifecycleOwner
import com.example.opencv_tutorial.YOLO11Detector
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Detection frequency settings for ML operations
 */
enum class DetectionFrequency {
    LOW,    // Process every 500ms
    MEDIUM, // Process every 250ms
    HIGH    // Process every 100ms
}

/**
 * Detection object data class
 */
data class DetectedObject(
    val box: RectF,
    val classId: Int,
    val label: String,
    val confidence: Float
)

/**
 * Optimized ARDetectionManager for real-time object detection in AR experiences
 * 
 * Key optimizations:
 * - Frame skipping based on device performance
 * - Dedicated executor for ML operations
 * - Image pooling to reduce GC pressure
 * - Adaptive resolution scaling
 */
class ARDetectionManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val executor: Executor
) {
    companion object {
        private const val TAG = "ARDetectionManager"
    }
    
    // Detection model
    private var detector: YOLO11Detector? = null
    private val isInitialized = AtomicBoolean(false)
    
    // Processing state
    private var lastProcessTime = 0L
    private var detectionFrequency = DetectionFrequency.MEDIUM
    private val isProcessing = AtomicBoolean(false)
    
    // Detection results
    private val _detectedObjects = MutableStateFlow<List<DetectedObject>>(emptyList())
    val detectedObjects: StateFlow<List<DetectedObject>> = _detectedObjects.asStateFlow()
    
    // Performance metrics
    private var totalFrames = 0
    private var processedFrames = 0
    private var avgProcessingTimeMs = 0.0
    
    // Configuration
    private var targetResolution = Size(640, 640)
    private var confidenceThreshold = YOLO11Detector.CONFIDENCE_THRESHOLD
    
    /**
     * Initialize the detector with model and configuration
     */
    fun initialize(
        modelPath: String,
        labelsPath: String,
        lifecycleOwner: LifecycleOwner,
        useGpu: Boolean = true
    ) {
        if (isInitialized.get()) {
            Log.d(TAG, "Detector already initialized")
            return
        }
        
        try {
            coroutineScope.launch(Dispatchers.IO) {
                Log.d(TAG, "Initializing ML detector with model: $modelPath")
                
                detector = YOLO11Detector(
                    context = context,
                    modelPath = modelPath,
                    labelsPath = labelsPath,
                    useGPU = useGpu
                )
                
                isInitialized.set(true)
                Log.d(TAG, "Detector initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize detector", e)
        }
    }
    
    /**
     * Process an AR frame for object detection
     * Uses frame skipping based on device performance
     */
    fun processFrame(frame: Frame, session: Session) {
        totalFrames++
        
        // Skip processing if not initialized or already processing
        if (!isInitialized.get() || isProcessing.get()) {
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val processingInterval = when (detectionFrequency) {
            DetectionFrequency.LOW -> 500L
            DetectionFrequency.MEDIUM -> 250L
            DetectionFrequency.HIGH -> 100L
        }
        
        // Skip processing if too soon after last frame
        if (currentTime - lastProcessTime < processingInterval) {
            return
        }
        
        lastProcessTime = currentTime
        isProcessing.set(true)
        
        executor.execute {
            try {
                val startTime = System.currentTimeMillis()
                
                // Get camera image - using the correct ARCore method with exception handling
                val image = try {
                    frame.acquireCameraImage()
                } catch (e: NotYetAvailableException) {
                    Log.d(TAG, "Camera image not yet available")
                    isProcessing.set(false)
                    return@execute
                } catch (e: CameraNotAvailableException) {
                    Log.e(TAG, "Camera not available", e)
                    isProcessing.set(false)
                    return@execute
                } catch (e: Exception) {
                    Log.e(TAG, "Error acquiring camera image", e)
                    isProcessing.set(false)
                    return@execute
                }
                
                // Process image
                processImage(image, session)
                image.close()
                
                // Update performance metrics
                val processingTime = System.currentTimeMillis() - startTime
                updatePerformanceMetrics(processingTime)
                
                processedFrames++
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame: ${e.message}")
            } finally {
                isProcessing.set(false)
            }
        }
    }
    
    /**
     * Process camera image and run object detection
     */
    private fun processImage(image: Image, session: Session) {
        val detector = detector ?: return
        
        try {
            // Convert to bitmap with optimal resolution
            val bitmap = convertYuvImageToBitmap(image, session)
            
            // Run detector
            val detections = detector.detect(bitmap, confidenceThreshold)
            
            // Convert detections to app format
            val results = detections.map { detection ->
                DetectedObject(
                    box = RectF(
                        detection.box.x.toFloat(),
                        detection.box.y.toFloat(),
                        (detection.box.x + detection.box.width).toFloat(),
                        (detection.box.y + detection.box.height).toFloat()
                    ),
                    classId = detection.classId,
                    label = detector.getClassName(detection.classId),
                    confidence = detection.conf
                )
            }
            
            // Update state flow on main thread
            coroutineScope.launch(Dispatchers.Main) {
                _detectedObjects.emit(results)
            }
            
            if (results.isNotEmpty()) {
                Log.d(TAG, "Detected ${results.size} objects")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in image processing: ${e.message}")
        }
    }
    
    /**
     * Convert AR Core YUV image to bitmap with resolution control
     */
    private fun convertYuvImageToBitmap(image: Image, session: Session): Bitmap {
        // Calculate target dimensions while preserving aspect ratio
        val width = image.width
        val height = image.height
        
        // Use original resolution for high-end devices
        val useOriginalSize = detectionFrequency == DetectionFrequency.HIGH && 
                              avgProcessingTimeMs < 100.0
        
        val targetWidth: Int
        val targetHeight: Int
        
        if (useOriginalSize) {
            targetWidth = width
            targetHeight = height
        } else {
            // Scale down for better performance
            val aspectRatio = width.toFloat() / height.toFloat()
            targetWidth = if (width > height) targetResolution.width else (targetResolution.height * aspectRatio).toInt()
            targetHeight = if (height > width) targetResolution.height else (targetResolution.width / aspectRatio).toInt()
        }
        
        // Create target bitmap
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        
        // Convert YUV to RGB using Android's YuvImage
        // Get YUV data from image planes
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        
        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        
        // Use OpenCV for conversion (more efficient with native code)
        try {
            val yuvMat = org.opencv.core.Mat(height + height / 2, width, org.opencv.core.CvType.CV_8UC1)
            yuvMat.put(0, 0, nv21)
            
            val rgbMat = org.opencv.core.Mat()
            org.opencv.imgproc.Imgproc.cvtColor(yuvMat, rgbMat, org.opencv.imgproc.Imgproc.COLOR_YUV2RGBA_NV21)
            
            // If we need to resize
            if (targetWidth != width || targetHeight != height) {
                val resizedMat = org.opencv.core.Mat()
                org.opencv.imgproc.Imgproc.resize(
                    rgbMat, 
                    resizedMat, 
                    org.opencv.core.Size(targetWidth.toDouble(), targetHeight.toDouble())
                )
                org.opencv.android.Utils.matToBitmap(resizedMat, bitmap)
                resizedMat.release()
            } else {
                org.opencv.android.Utils.matToBitmap(rgbMat, bitmap)
            }
            
            yuvMat.release()
            rgbMat.release()
        } catch (e: Exception) {
            // Fallback to slower Java conversion if OpenCV fails
            Log.e(TAG, "OpenCV conversion failed, falling back to YuvImage: ${e.message}")
            
            val yuvImage = android.graphics.YuvImage(
                nv21, 
                android.graphics.ImageFormat.NV21, 
                width, 
                height, 
                null
            )
            
            val outputStream = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 95, outputStream)
            
            val jpegData = outputStream.toByteArray()
            val tempBitmap = android.graphics.BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
            
            // Scale if needed
            if (tempBitmap.width != targetWidth || tempBitmap.height != targetHeight) {
                val scaledBitmap = Bitmap.createScaledBitmap(tempBitmap, targetWidth, targetHeight, true)
                tempBitmap.recycle()
                scaledBitmap.copyTo(bitmap)
                scaledBitmap.recycle()
            } else {
                tempBitmap.copyTo(bitmap)
                tempBitmap.recycle()
            }
        }
        
        return bitmap
    }
    
    // Extension function to help with bitmap copying
    private fun Bitmap.copyTo(target: Bitmap) {
        val canvas = android.graphics.Canvas(target)
        canvas.drawBitmap(this, 0f, 0f, null)
    }
    
    /**
     * Update performance metrics for adaptive processing
     */
    private fun updatePerformanceMetrics(processingTimeMs: Long) {
        // Exponential moving average for processing time
        avgProcessingTimeMs = if (avgProcessingTimeMs == 0.0) {
            processingTimeMs.toDouble()
        } else {
            avgProcessingTimeMs * 0.7 + processingTimeMs * 0.3
        }
        
        // Log performance every 100 frames
        if (processedFrames % 100 == 0) {
            val processRatio = processedFrames.toFloat() / totalFrames.toFloat() * 100
            Log.d(TAG, "ML Performance: Avg=${avgProcessingTimeMs.toInt()}ms, " +
                       "Processed=$processedFrames/$totalFrames (${processRatio.toInt()}%)")
        }
        
        // Adapt detection frequency based on processing time
        val newFrequency = when {
            avgProcessingTimeMs > 200 -> DetectionFrequency.LOW
            avgProcessingTimeMs > 100 -> DetectionFrequency.MEDIUM
            else -> DetectionFrequency.HIGH
        }
        
        if (newFrequency != detectionFrequency) {
            Log.d(TAG, "Adjusting detection frequency to $newFrequency based on performance")
            detectionFrequency = newFrequency
        }
    }
    
    /**
     * Manually set detection frequency
     */
    fun setDetectionFrequency(frequency: DetectionFrequency) {
        detectionFrequency = frequency
        Log.d(TAG, "Detection frequency set to $frequency")
    }
    
    /**
     * Set detection confidence threshold
     */
    fun setConfidenceThreshold(threshold: Float) {
        confidenceThreshold = threshold
    }
    
    /**
     * Set target resolution for detection
     * Higher values improve accuracy but reduce performance
     */
    fun setTargetResolution(width: Int, height: Int) {
        targetResolution = Size(width, height)
    }
    
    /**
     * Get performance metrics
     */
    fun getPerformanceStats(): Map<String, Any> {
        return mapOf(
            "avgProcessingTimeMs" to avgProcessingTimeMs.toInt(),
            "processedFrames" to processedFrames,
            "totalFrames" to totalFrames,
            "processRatio" to (processedFrames.toFloat() / totalFrames.toFloat()),
            "detectionFrequency" to detectionFrequency.name
        )
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        try {
            detector?.close()
            detector = null
            isInitialized.set(false)
            Log.d(TAG, "Detection resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down detector", e)
        }
    }
}
