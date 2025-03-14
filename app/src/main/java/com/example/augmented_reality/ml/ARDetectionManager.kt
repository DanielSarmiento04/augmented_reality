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
                
                // Get camera image
                val image = frame.tryAcquireCameraImage()
                if (image == null) {
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
        
        val targetBitmap = if (useOriginalSize) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } else {
            // Scale down for better performance
            val aspectRatio = width.toFloat() / height.toFloat()
            val targetW = if (width > height) targetResolution.width else (targetResolution.height * aspectRatio).toInt()
            val targetH = if (height > width) targetResolution.height else (targetResolution.width / aspectRatio).toInt()
            
            Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        }
        
        // Use ARCore to convert from YUV to RGB
        session.convertYuv(image, targetBitmap)
        
        return targetBitmap
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
