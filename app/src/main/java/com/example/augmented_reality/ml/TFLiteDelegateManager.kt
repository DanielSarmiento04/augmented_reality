package com.example.opencv_tutorial

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.Closeable

/**
 * Backend delegate types supported by the application
 */
enum class DelegateType {
    CPU,      // Regular CPU execution
    GPU,      // GPU delegate
    NNAPI,    // Neural Network API delegate
    AUTO      // Auto-selection based on device capabilities
}

/**
 * Performance stats from inference
 */
data class InferenceStats(
    val inferenceTimeMs: Long,
    val delegateType: DelegateType,
    val success: Boolean,
    val errorMessage: String? = null,
    val memoryUsage: Long = -1,
    val preprocessingTimeMs: Long = -1,
    val postprocessingTimeMs: Long = -1,
    val totalTimeMs: Long = -1
)

/**
 * Manages TensorFlow Lite delegates for optimized model execution on different hardware
 */
class TFLiteDelegateManager(private val context: Context) : Closeable {
    companion object {
        private const val TAG = "TFLiteDelegateManager"

        // Constants for NNAPI configuration
        private const val NNAPI_ALLOW_FP16 = true
        private const val NNAPI_EXECUTION_PREFERENCE = NnApiDelegate.Options.EXECUTION_PREFERENCE_FAST_SINGLE_ANSWER
        
        // Constants for GPU configuration
        private const val GPU_PRECISION_LOSS_ALLOWED = true
        private const val GPU_QUANTIZED_MODELS_ALLOWED = true
        
        // CPU configuration constants
        private const val CPU_USE_XNNPACK = true
    }

    // Track current active delegate for resource management
    private var currentDelegate: Delegate? = null
    private var currentDelegateType: DelegateType = DelegateType.CPU
    
    // Store benchmark results to make intelligent delegate selections
    private val benchmarkResults = mutableMapOf<DelegateType, Long>()
    
    // Device-specific optimizations
    private val deviceModel = Build.MODEL
    private val manufacturer = Build.MANUFACTURER
    private val sdkVersion = Build.VERSION.SDK_INT
    private val cpuCores = Runtime.getRuntime().availableProcessors()

    // GPU compatibility checker
    private val gpuCompatibilityList = CompatibilityList()

    /**
     * Configure interpreter options with the appropriate delegate based on type
     * @param options The interpreter options to configure
     * @param delegateType The delegate type to use
     * @return The chosen delegate type (may differ if requested type is unavailable)
     */
    fun configureTFLiteInterpreter(options: Interpreter.Options, delegateType: DelegateType): DelegateType {
        // Close any existing delegate first to prevent memory leaks
        closeDelegate()

        // Choose appropriate delegate or let the system decide
        val actualDelegateType = when (delegateType) {
            DelegateType.AUTO -> selectOptimalDelegate()
            else -> delegateType
        }

        Log.d(TAG, "Configuring interpreter with $actualDelegateType delegate")

        // Apply the selected delegate configuration with better error handling
        val appliedType = when (actualDelegateType) {
            DelegateType.GPU -> if (configureGpuDelegate(options)) DelegateType.GPU else DelegateType.CPU
            DelegateType.NNAPI -> if (configureNnApiDelegate(options)) DelegateType.NNAPI else DelegateType.CPU
            DelegateType.CPU -> {
                configureCpuDelegate(options)
                DelegateType.CPU
            }
            else -> {
                configureCpuDelegate(options)
                DelegateType.CPU
            }
        }

        currentDelegateType = appliedType
        return appliedType
    }

    /**
     * Select the best delegate based on device capabilities and benchmarking history
     */
    fun selectOptimalDelegate(): DelegateType {
        Log.d(TAG, "Selecting optimal delegate based on device capabilities and history")
        
        // If we have benchmark data, use the fastest delegate
        if (benchmarkResults.isNotEmpty()) {
            val fastest = benchmarkResults.minByOrNull { it.value }?.key
            fastest?.let {
                Log.d(TAG, "Selected $it based on previous benchmark results")
                return it
            }
        }

        // Device-specific optimizations based on known characteristics
        if (isKnownGoodGpuDevice()) {
            Log.d(TAG, "Device ($manufacturer $deviceModel) is known to perform well with GPU")
            return DelegateType.GPU
        }
        
        if (isKnownGoodNnapiDevice()) {
            Log.d(TAG, "Device ($manufacturer $deviceModel) is known to perform well with NNAPI")
            return DelegateType.NNAPI
        }

        // General capability-based selection
        if (isGpuDelegateAvailable()) {
            Log.d(TAG, "GPU delegate selected as optimal")
            return DelegateType.GPU
        }

        // Then check if NNAPI is available and reasonably modern (Android 10+)
        if (isNnApiDelegateAvailable() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "NNAPI delegate selected as optimal")
            return DelegateType.NNAPI
        }

        // Fallback to CPU
        Log.d(TAG, "CPU delegate selected as optimal")
        return DelegateType.CPU
    }

    /**
     * Configure with GPU delegate with optimizations for specific devices
     */
    private fun configureGpuDelegate(options: Interpreter.Options): Boolean {
        try {
            if (!isGpuDelegateAvailable()) {
                Log.w(TAG, "GPU delegate requested but not available, falling back to CPU")
                configureCpuDelegate(options)
                return false
            }

            val delegateOptions = GpuDelegate.Options().apply {
                // Set precision configuration based on device capability
                setPrecisionLossAllowed(getGpuPrecisionConfig())
                setQuantizedModelsAllowed(GPU_QUANTIZED_MODELS_ALLOWED)
                
                // Device-specific inference preferences
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val inferencePreference = getGpuInferencePreference()
                    setInferencePreference(inferencePreference)
                }
            }

            val gpuDelegate = GpuDelegate(delegateOptions)
            options.addDelegate(gpuDelegate)
            currentDelegate = gpuDelegate

            // Add CPU fallback options for operations not supported by GPU
            configureCpuOptions(options)

            Log.d(TAG, "GPU delegate configured successfully with device-specific optimizations")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure GPU delegate: ${e.message}")
            configureCpuDelegate(options)
            return false
        }
    }

    /**
     * Configure with NNAPI delegate with optimizations for different Android versions
     */
    private fun configureNnApiDelegate(options: Interpreter.Options): Boolean {
        try {
            if (!isNnApiDelegateAvailable()) {
                Log.w(TAG, "NNAPI delegate requested but not available, falling back to CPU")
                configureCpuDelegate(options)
                return false
            }

            val nnApiOptions = NnApiDelegate.Options().apply {
                // Configure for lower latency or better accuracy based on device
                setAllowFp16(NNAPI_ALLOW_FP16)
                setExecutionPreference(getNnApiExecutionPreference())

                // Android version-specific optimizations
                when {
                    // Android 13+ (API 33+): Advanced accelerator configuration
                    Build.VERSION.SDK_INT >= 33 -> {
                        setModelToken("yolov11_optimized_${Build.VERSION.SDK_INT}")
                        setUseNnapiCpu(false)  // Prefer hardware accelerators
                        
                        // For YOLOv11, fewer partitions often works better
                        setMaxNumberOfDelegatedPartitions(getNnApiOptimalPartitions())
                    }
                    // Android 10+ (API 29+): Accelerator selection
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        // Get optimal accelerator for this device if known
                        val accelerator = getBestNnApiAccelerator()
                        if (accelerator.isNotEmpty()) {
                            setAcceleratorName(accelerator)
                        } else {
                            setAcceleratorName("")  // Empty string = use any available
                        }
                        setUseNnapiCpu(true)  // Allow NNAPI CPU as fallback
                    }
                }
                
                // Disable problematic operations on certain devices
                if (hasKnownNnapiIssues()) {
                    // Just an example - implementation would depend on specific known issues
                    // disableProblemOperations(this)
                }
            }

            val nnApiDelegate = NnApiDelegate(nnApiOptions)
            options.addDelegate(nnApiDelegate)
            currentDelegate = nnApiDelegate

            // Configure CPU options as fallback
            configureCpuOptions(options)

            Log.d(TAG, "NNAPI delegate configured successfully with API ${Build.VERSION.SDK_INT} optimizations")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure NNAPI delegate: ${e.message}")
            configureCpuDelegate(options)
            return false
        }
    }

    /**
     * Configure with CPU options
     */
    private fun configureCpuDelegate(options: Interpreter.Options) {
        configureCpuOptions(options)
        currentDelegateType = DelegateType.CPU
        Log.d(TAG, "CPU execution configured")
    }

    /**
     * Configure optimal CPU options based on device capabilities
     */
    private fun configureCpuOptions(options: Interpreter.Options) {
        try {
            // Determine optimal thread count based on CPU cores and device thermal characteristics
            val optimalThreads = when {
                cpuCores <= 2 -> 1
                cpuCores <= 4 -> 2
                cpuCores <= 6 -> 3
                else -> cpuCores / 2  // Use half for better thermal management
            }

            // Additional thread adjustment based on known device characteristics
            val adjustedThreads = when {
                isLowEndDevice() -> maxOf(1, optimalThreads - 1)  // Reduce threads on low-end devices
                isHighEndDevice() -> minOf(cpuCores - 1, optimalThreads + 1)  // More threads on high-end
                else -> optimalThreads
            }

            options.setNumThreads(adjustedThreads)

            // Enable XNNPACK acceleration for better vector operations
            options.setUseXNNPACK(CPU_USE_XNNPACK)
            
            // Configure precision and memory allocation based on device capabilities
            options.setAllowFp16PrecisionForFp32(!requiresHighPrecision())
            options.setAllowBufferHandleOutput(true)

            Log.d(TAG, "CPU options configured with $adjustedThreads threads and XNNPACK=${CPU_USE_XNNPACK}")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring CPU options: ${e.message}")
            // Use safe defaults
            options.setNumThreads(2)
        }
    }

    /**
     * Check if GPU delegate is available on this device
     */
    fun isGpuDelegateAvailable(): Boolean {
        return gpuCompatibilityList.isDelegateSupportedOnThisDevice
    }

    /**
     * Check if NNAPI delegate is available on this device
     */
    fun isNnApiDelegateAvailable(): Boolean {
        // NNAPI was introduced in Android 8.1 (API 27)
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
    }

    /**
     * Get the current delegate type
     */
    fun getCurrentDelegateType(): DelegateType = currentDelegateType

    /**
     * Free resources used by delegates
     */
    fun closeDelegate() {
        currentDelegate?.let {
            try {
                when (it) {
                    is GpuDelegate -> it.close()
                    is NnApiDelegate -> it.close()
                    // Other delegate types may need special handling
                }
                Log.d(TAG, "Closed delegate of type $currentDelegateType")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing delegate: ${e.message}")
            }
            currentDelegate = null
        }
    }

    /**
     * Run a quick benchmark to compare delegate performance
     * @param interpreter The TFLite interpreter to benchmark
     * @param inputData Sample input data for inference
     * @param iterations Number of iterations to run
     * @return InferenceStats with performance metrics
     */
    fun benchmarkDelegate(
        interpreter: Interpreter,
        inputData: Any,
        iterations: Int = 10
    ): InferenceStats {
        val startTime = SystemClock.elapsedRealtime()
        var success = true
        var errorMessage: String? = null
        
        try {
            // Warmup run
            for (i in 0 until 3) {
                interpreter.run(inputData, HashMap<Int, Any>())
            }
            
            // Timed runs
            val inferenceTimesMs = mutableListOf<Long>()
            for (i in 0 until iterations) {
                val iterStartTime = SystemClock.elapsedRealtime()
                interpreter.run(inputData, HashMap<Int, Any>())
                inferenceTimesMs.add(SystemClock.elapsedRealtime() - iterStartTime)
            }
            
            // Calculate stats
            val avgInferenceTime = inferenceTimesMs.average().toLong()
            val memoryUsage = getMemoryUsage()
            
            // Store benchmark result for future delegate selection
            benchmarkResults[currentDelegateType] = avgInferenceTime
            
            return InferenceStats(
                inferenceTimeMs = avgInferenceTime,
                delegateType = currentDelegateType,
                success = true,
                memoryUsage = memoryUsage,
                totalTimeMs = SystemClock.elapsedRealtime() - startTime
            )
        } catch (e: Exception) {
            success = false
            errorMessage = e.message
            return InferenceStats(
                inferenceTimeMs = -1,
                delegateType = currentDelegateType,
                success = false,
                errorMessage = errorMessage,
                totalTimeMs = SystemClock.elapsedRealtime() - startTime
            )
        }
    }

    /**
     * Auto-tune the delegate selection based on benchmarking
     * @param modelPath Path to the model file for creating test interpreters
     * @param inputData Sample input data
     * @return The best delegate type
     */
    fun autoTuneDelegates(modelPath: String, inputData: Any): DelegateType {
        Log.d(TAG, "Auto-tuning delegates for best performance")
        
        val delegateTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            listOf(DelegateType.CPU, DelegateType.GPU, DelegateType.NNAPI)
        } else {
            listOf(DelegateType.CPU, DelegateType.GPU)
        }
        
        var bestDelegate = DelegateType.CPU
        var bestTime = Long.MAX_VALUE
        
        // Load model file for creating test interpreters
        val modelManager = TFLiteModelManager(context)
        try {
            val optimizedModelPath = modelManager.prepareModelForDevice(modelPath)
            val modelBuffer = modelManager.loadModelFile(optimizedModelPath)
            
            for (delegateType in delegateTypes) {
                try {
                    // Skip delegates that aren't available
                    if (delegateType == DelegateType.GPU && !isGpuDelegateAvailable()) continue
                    if (delegateType == DelegateType.NNAPI && !isNnApiDelegateAvailable()) continue
                    
                    // Configure this delegate
                    val tfliteOptions = Interpreter.Options()
                    configureTFLiteInterpreter(tfliteOptions, delegateType)
                    
                    // Create a new interpreter with these options for testing
                    val testInterpreter = Interpreter(modelBuffer, tfliteOptions)
                    
                    // Run benchmark
                    val stats = benchmarkDelegate(testInterpreter, inputData, 5)
                    if (stats.success && stats.inferenceTimeMs > 0 && stats.inferenceTimeMs < bestTime) {
                        bestTime = stats.inferenceTimeMs
                        bestDelegate = delegateType
                    }
                    
                    // Close test interpreter
                    testInterpreter.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error testing delegate $delegateType: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during auto-tuning: ${e.message}")
        }
        
        Log.d(TAG, "Auto-tuning selected $bestDelegate as optimal (${bestTime}ms)")
        return bestDelegate
    }
    
    /**
     * Helper methods for device-specific optimizations
     */
    private fun isKnownGoodGpuDevice(): Boolean {
        // High-performance GPU devices known to work well with GPU delegate
        return when {
            manufacturer.equals("samsung", ignoreCase = true) && 
                    (deviceModel.contains("S20") || deviceModel.contains("S21") || 
                     deviceModel.contains("S22") || deviceModel.contains("S23")) -> true
            manufacturer.equals("google", ignoreCase = true) && 
                    (deviceModel.contains("Pixel 6") || deviceModel.contains("Pixel 7")) -> true
            else -> false
        }
    }
    
    private fun isKnownGoodNnapiDevice(): Boolean {
        // Devices known to have good NNAPI implementation
        return when {
            // Google Pixels with Tensor chip have good NNAPI performance
            manufacturer.equals("google", ignoreCase = true) && 
                    (deviceModel.contains("Pixel 6") || deviceModel.contains("Pixel 7")) -> true
            // Samsung devices with newer Exynos chips
            manufacturer.equals("samsung", ignoreCase = true) && 
                    (deviceModel.contains("S22") || deviceModel.contains("S23")) -> true
            else -> false
        }
    }
    
    private fun getGpuPrecisionConfig(): Boolean {
        // YOLOv11 works well with precision loss on most modern devices
        return when {
            isLowEndDevice() -> true  // Prioritize performance on low-end devices
            // For devices known to have precision issues
            hasKnownPrecisionIssues() -> false
            // Default behavior
            else -> GPU_PRECISION_LOSS_ALLOWED
        }
    }
    
    private fun getGpuInferencePreference(): Int {
        // For YOLOv11, prefer sustained performance over peak speed
        return GpuDelegate.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED
    }
    
    private fun getNnApiExecutionPreference(): Int {
        return NNAPI_EXECUTION_PREFERENCE
    }
    
    private fun getBestNnApiAccelerator(): String {
        // Return specific accelerator names for known devices
        return when {
            manufacturer.equals("google", ignoreCase = true) && deviceModel.contains("Pixel") -> {
                // Google Tensor devices - use tensor accelerator if available
                "google-edgetpu"
            }
            manufacturer.equals("qualcomm", ignoreCase = true) || 
                    (manufacturer.equals("samsung", ignoreCase = true) && 
                     deviceModel.contains("SM-") && deviceModel.contains("U")) -> {
                // Qualcomm devices - use Hexagon DSP
                "qti-dsp"
            }
            else -> {
                // Let the system decide
                ""
            }
        }
    }
    
    private fun getNnApiOptimalPartitions(): Int {
        // YOLOv11 generally works better with fewer NNAPI partitions
        return when {
            isHighEndDevice() -> 3  // More partitions for high-end devices
            isLowEndDevice() -> 1   // Fewer partitions for low-end devices
            else -> 2               // Default middle ground
        }
    }
    
    // Device capability detection helpers
    private fun isLowEndDevice(): Boolean {
        return cpuCores <= 4 || Build.VERSION.SDK_INT < Build.VERSION_CODES.P
    }
    
    private fun isHighEndDevice(): Boolean {
        return cpuCores >= 6 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
    
    private fun requiresHighPrecision(): Boolean {
        // Override based on application needs - default to false for performance
        return false
    }
    
    private fun hasKnownPrecisionIssues(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
    }
    
    private fun hasKnownNnapiIssues(): Boolean {
        return manufacturer.equals("samsung", ignoreCase = true) &&
                Build.VERSION.SDK_INT in 28..29
    }
    
    private fun getMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    /**
     * Implementation of Closeable interface
     */
    override fun close() {
        closeDelegate()
    }
}
