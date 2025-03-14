package com.example.augmented_reality.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.Size
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import com.example.opencv_tutorial.YOLO11Detector
import com.google.android.filament.Engine
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.*
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.collision.CollisionSystem
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * Enhanced ARView with optimized detection integration and smooth interactions
 * Prioritizes frame rate while maintaining high detection quality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ARView(
    selectedMachine: String,
    lifecycleOwner: LifecycleOwner = LocalContext.current as LifecycleOwner
) {
    val TAG = "ARView"
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    // Performance monitoring
    val fpsCounter = remember { FpsCounter() }
    var currentFps by remember { mutableStateOf(0f) }
    val performanceMode = remember { mutableStateOf(PerformanceMode.BALANCED) }
    
    // AR components
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberARCameraNode(engine)
    val childNodes = rememberNodes()
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)
    
    // Dedicated thread pool for ML operations to avoid blocking the main thread or the rendering thread
    val mlExecutor = remember { 
        Executors.newFixedThreadPool(2)
    }
    
    // State management for AR experience
    var trackingState by remember { mutableStateOf<TrackingState?>(null) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var frame by remember { mutableStateOf<Frame?>(null) }
    var lightingEstimate by remember { mutableStateOf<LightEstimate?>(null) }
    
    // UI control states
    var planeRenderer by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var showDetectionVisualizations by remember { mutableStateOf(true) }
    var lockObject by remember { mutableStateOf(false) }
    var selectedNode by remember { mutableStateOf<ModelNode?>(null) }
    var showObjectMenu by remember { mutableStateOf(false) }
    var showPerformanceStats by remember { mutableStateOf(false) }
    
    // Detection states
    val detectionManager = remember { 
        ARDetectionManager(context, coroutineScope, mlExecutor)
    }
    val detectedObjects by detectionManager.detectedObjects.collectAsState()
    
    // Object visualization
    val objectHighlighter = remember { ObjectHighlighter(engine, materialLoader) }
    
    // Gesture tracking state
    val gestureTracker = remember { GestureTrackingState() }
    
    // Model path handling
    val formattedName = selectedMachine.lowercase().replace(" ", "_")
    val formattedFullNameFile = "$formattedName.glb"
    val assetPath = "$formattedName/$formattedFullNameFile"
    
    // Initialize Detection System
    LaunchedEffect(Unit) {
        // Start detection system
        detectionManager.initialize(
            modelPath = "models/yolov11_optimized.tflite", 
            labelsPath = "models/coco_labels.txt",
            lifecycleOwner = lifecycleOwner
        )
        
        // Start FPS monitoring
        launch {
            while (isActive) {
                delay(1000)
                currentFps = fpsCounter.getFps()
                
                // Adaptive quality based on performance
                when {
                    currentFps < 20f && performanceMode.value != PerformanceMode.LOW -> {
                        performanceMode.value = PerformanceMode.LOW
                        detectionManager.setDetectionFrequency(DetectionFrequency.LOW)
                    }
                    currentFps > 45f && performanceMode.value != PerformanceMode.HIGH -> {
                        performanceMode.value = PerformanceMode.HIGH
                        detectionManager.setDetectionFrequency(DetectionFrequency.HIGH)
                    }
                    currentFps in 20f..45f && performanceMode.value != PerformanceMode.BALANCED -> {
                        performanceMode.value = PerformanceMode.BALANCED
                        detectionManager.setDetectionFrequency(DetectionFrequency.MEDIUM)
                    }
                }
            }
        }
    }
    
    // Clean up resources
    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_DESTROY) {
        detectionManager.shutdown()
        mlExecutor.shutdown()
        objectHighlighter.cleanup()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main AR Scene
        ARScene(
            modifier = Modifier.fillMaxSize(),
            childNodes = childNodes,
            engine = engine,
            view = view,
            modelLoader = modelLoader,
            collisionSystem = collisionSystem,
            sessionConfiguration = { session, config ->
                // Enhanced session configuration for better tracking
                configureARSession(session, config, performanceMode.value)
            },
            cameraNode = cameraNode,
            planeRenderer = planeRenderer,
            onTrackingFailureChanged = {
                trackingFailureReason = it
            },
            onSessionUpdated = { session, updatedFrame ->
                // Track frame rate
                fpsCounter.frameRendered()
                
                frame = updatedFrame
                trackingState = updatedFrame.camera.trackingState
                lightingEstimate = updatedFrame.lightEstimate
                
                // Feed camera image to detector when tracking is stable
                if (trackingState == TrackingState.TRACKING) {
                    detectionManager.processFrame(updatedFrame, session)
                    
                    // Auto-place model on horizontal plane if no models exist
                    if (childNodes.isEmpty()) {
                        tryAutoPlaceModel(
                            updatedFrame, 
                            childNodes, 
                            engine, 
                            modelLoader, 
                            materialLoader, 
                            assetPath,
                            coroutineScope
                        )
                    }
                    
                    // Update highlights for objects that match detected classes
                    updateObjectHighlights(
                        detectedObjects,
                        childNodes,
                        objectHighlighter,
                        showDetectionVisualizations
                    )
                }
            },
            onGestureListener = rememberOnGestureListener(
                onDown = { motionEvent, node ->
                    gestureTracker.onDown(motionEvent.x, motionEvent.y)
                    false // Continue processing other gestures
                },
                onSingleTapConfirmed = { motionEvent, node ->
                    handleTap(
                        motionEvent, 
                        node, 
                        frame, 
                        childNodes,
                        engine, 
                        modelLoader, 
                        materialLoader, 
                        lockObject,
                        assetPath,
                        haptic,
                        coroutineScope,
                        onNodeSelected = { selectedModelNode ->
                            selectedNode = selectedModelNode
                            showObjectMenu = true
                        }
                    )
                },
                onLongPress = { motionEvent, node ->
                    if (node != null && node is ModelNode) {
                        // Toggle edit mode with haptic feedback
                        node.isEditable = !node.isEditable
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                onScaleBegin = { _, _ ->
                    gestureTracker.onScaleStart()
                    true // Consume the event
                },
                onScale = { _, _, scaleFactor, node ->
                    if (node != null && node is ModelNode && node.isEditable) {
                        // Apply scale with constraints
                        val currentScale = node.scale
                        val newScale = Scale(
                            currentScale.x * scaleFactor,
                            currentScale.y * scaleFactor,
                            currentScale.z * scaleFactor
                        )
                        // Constrain scale within reasonable limits
                        val constrainedScale = constrainScale(newScale, 0.2f, 2.0f)
                        node.scale = constrainedScale
                        
                        // Provide subtle haptic feedback during scaling
                        if (gestureTracker.shouldTriggerHaptic()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    true // Consume the event
                },
                onScroll = { _, _, distanceX, distanceY, node ->
                    if (node != null && node is ModelNode && node.isEditable) {
                        // Rotate the object based on finger movement
                        val sensitivity = 0.1f
                        val deltaRotation = Rotation(
                            0f, 
                            distanceX * sensitivity, 
                            0f
                        )
                        node.rotation = node.rotation.multiplied(deltaRotation)
                        
                        // Provide subtle haptic feedback during rotation
                        if (gestureTracker.shouldTriggerHaptic()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                    true // Consume the event
                }
            )
        )
        
        // AR overlays
        OverlayContainer(
            modifier = Modifier.fillMaxSize(),
            showControls = showControls,
            trackingState = trackingState,
            trackingFailureReason = trackingFailureReason,
            detectedObjects = detectedObjects,
            showDetectionVisualizations = showDetectionVisualizations,
            lockObject = lockObject,
            showPerformanceStats = showPerformanceStats,
            currentFps = currentFps,
            performanceMode = performanceMode.value,
            onToggleControls = { showControls = !showControls },
            onToggleDetection = { showDetectionVisualizations = !showDetectionVisualizations },
            onToggleLock = { lockObject = !lockObject },
            onClearScene = { 
                childNodes.clear()
                planeRenderer = true
            },
            onTogglePerformanceStats = { showPerformanceStats = !showPerformanceStats }
        )
        
        // Object manipulation menu
        if (showObjectMenu && selectedNode != null) {
            ObjectManipulationMenu(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
                onDismiss = { showObjectMenu = false },
                onDelete = {
                    selectedNode?.parent?.removeChildNode(selectedNode!!)
                    selectedNode = null
                    showObjectMenu = false
                },
                onToggleEdit = {
                    selectedNode?.isEditable = !(selectedNode?.isEditable ?: false)
                },
                isEditable = selectedNode?.isEditable ?: false
            )
        }
    }
}

/**
 * Configures AR session with optimized settings for tracking stability and performance
 */
private fun configureARSession(session: Session, config: Config, performanceMode: PerformanceMode) {
    // Enable depth if supported for better occlusion
    config.depthMode = when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
        true -> Config.DepthMode.AUTOMATIC
        else -> Config.DepthMode.DISABLED
    }
    
    // Enable instant placement for better UX
    config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
    
    // HDR lighting for realistic rendering
    config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
    
    // Optimize plane finding based on performance mode
    config.planeFindingMode = when (performanceMode) {
        PerformanceMode.HIGH -> Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        PerformanceMode.BALANCED -> Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
        PerformanceMode.LOW -> Config.PlaneFindingMode.HORIZONTAL // Optimize for performance
    }
    
    // Set focus mode for better tracking
    config.focusMode = Config.FocusMode.AUTO
    
    // Set update mode based on performance level
    config.updateMode = when (performanceMode) {
        PerformanceMode.HIGH -> Config.UpdateMode.BLOCKING
        else -> Config.UpdateMode.LATEST_CAMERA_IMAGE
    }
    
    // Optimize CPU usage
    config.setFocusMode(Config.FocusMode.AUTO)
}

/**
 * Tries to auto-place a model on a suitable horizontal surface
 */
private fun tryAutoPlaceModel(
    frame: Frame,
    childNodes: MutableList<Node>,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    modelPath: String,
    coroutineScope: CoroutineScope
) {
    // Find best plane for placement
    frame.getUpdatedPlanes()
        .filter { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.isPoseInPolygon(frame.camera.pose) }
        .maxByOrNull { it.extentX * it.extentZ } // Select largest plane
        ?.let { plane ->
            // Create anchor at plane center
            plane.createAnchorOrNull(plane.centerPose)?.let { anchor ->
                coroutineScope.launch(Dispatchers.Main) {
                    try {
                        childNodes += createOptimizedAnchorNode(
                            engine = engine,
                            modelLoader = modelLoader,
                            materialLoader = materialLoader,
                            anchor = anchor,
                            modelPath = modelPath
                        )
                    } catch (e: Exception) {
                        Log.e("ARView", "Error auto-placing model", e)
                    }
                }
            }
        }
}

/**
 * Creates an optimized anchor node with the 3D model
 */
private suspend fun createOptimizedAnchorNode(
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    anchor: Anchor,
    modelPath: String
): AnchorNode = withContext(Dispatchers.IO) {
    // Create anchor node on IO thread
    val anchorNode = AnchorNode(engine = engine, anchor = anchor)
    
    // Create model node with optimized loading
    val modelInstance = modelLoader.createModelInstance(modelPath)
    
    // Switch to Main thread for UI operations
    withContext(Dispatchers.Main) {
        val modelNode = ModelNode(
            modelInstance = modelInstance,
            // Scale to reasonable size
            scaleToUnits = 0.5f,
            // Smooth animations
            smoothTransformations = true
        ).apply {
            // Make editable for user interaction
            isEditable = true
            editableScaleRange = 0.2f..1.5f
            
            // Position slightly above surface for better visibility
            position = Position(0f, 0.05f, 0f)
            
            // Add smooth rotation for better appearance
            rotation = Rotation(0f, 30f, 0f)
        }
        
        // Create bounding box for editing visualization
        val boundingBoxNode = CubeNode(
            engine,
            size = modelNode.extents,
            center = modelNode.center,
            materialInstance = materialLoader.createColorInstance(Color.White.copy(alpha = 0.3f))
        ).apply {
            isVisible = false
        }
        
        // Add bounding box to model
        modelNode.addChildNode(boundingBoxNode)
        
        // Add model to anchor
        anchorNode.addChildNode(modelNode)
        
        // Configure editing visuals
        listOf(modelNode, anchorNode).forEach {
            it.onEditingChanged = { editingTransforms ->
                boundingBoxNode.isVisible = editingTransforms.isNotEmpty()
            }
        }
        
        anchorNode
    }
}

/**
 * Handles tap gestures on the AR surface
 */
private fun handleTap(
    motionEvent: MotionEvent,
    node: Node?,
    frame: Frame?,
    childNodes: MutableList<Node>,
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    lockObject: Boolean,
    modelPath: String,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    coroutineScope: CoroutineScope,
    onNodeSelected: (ModelNode) -> Unit
) {
    // If node is tapped, select it
    if (node != null && node is ModelNode) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onNodeSelected(node)
        return
    }
    
    // If objects are locked, don't place new ones
    if (lockObject) return
    
    // Perform hit test to place object
    frame?.let { currentFrame ->
        val hitResult = currentFrame.hitTest(motionEvent.x, motionEvent.y)
            .firstOrNull { 
                it.isValid(depthPoint = true, point = true) 
            }
            
        hitResult?.createAnchorOrNull()?.let { anchor ->
            // Provide haptic feedback for successful placement
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            
            coroutineScope.launch {
                try {
                    childNodes += createOptimizedAnchorNode(
                        engine = engine,
                        modelLoader = modelLoader,
                        materialLoader = materialLoader,
                        anchor = anchor,
                        modelPath = modelPath
                    )
                } catch (e: Exception) {
                    Log.e("ARView", "Error placing model", e)
                }
            }
        }
    }
}

/**
 * Updates visual highlights for objects that match detected classes
 */
private fun updateObjectHighlights(
    detectedObjects: List<DetectedObject>,
    nodes: List<Node>,
    highlighter: ObjectHighlighter,
    showHighlights: Boolean
) {
    // Clear existing highlights
    highlighter.clearHighlights()
    
    if (!showHighlights) return
    
    // Find nodes that match detected object classes
    val modelNodes = nodes.filterIsInstance<ModelNode>()
    
    for (detection in detectedObjects) {
        // Find matching nodes (in a real app, you'd have better object-node matching logic)
        val matchingNodes = modelNodes.filter { 
            it.name?.contains(detection.label, ignoreCase = true) ?: false ||
            detection.label.contains("person") // Special case for detecting people around objects
        }
        
        // Highlight matching nodes
        matchingNodes.forEach { node ->
            highlighter.highlightNode(node, detection.confidence)
        }
    }
}

/**
 * Constrains scale values within minimum and maximum bounds
 */
private fun constrainScale(scale: Scale, minScale: Float, maxScale: Float): Scale {
    return Scale(
        x = scale.x.coerceIn(minScale, maxScale),
        y = scale.y.coerceIn(minScale, maxScale),
        z = scale.z.coerceIn(minScale, maxScale)
    )
}

/**
 * AR Overlay Container that shows controls and information
 */
@Composable
private fun OverlayContainer(
    modifier: Modifier = Modifier,
    showControls: Boolean,
    trackingState: TrackingState?,
    trackingFailureReason: TrackingFailureReason?,
    detectedObjects: List<DetectedObject>,
    showDetectionVisualizations: Boolean,
    lockObject: Boolean,
    showPerformanceStats: Boolean,
    currentFps: Float,
    performanceMode: PerformanceMode,
    onToggleControls: () -> Unit,
    onToggleDetection: () -> Unit,
    onToggleLock: () -> Unit,
    onClearScene: () -> Unit,
    onTogglePerformanceStats: () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Top status bar with tracking info
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            TrackingStatusBar(
                trackingState = trackingState,
                trackingFailureReason = trackingFailureReason
            )
        }
        
        // Performance stats
        AnimatedVisibility(
            visible = showPerformanceStats,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            PerformanceStatsBar(
                fps = currentFps,
                performanceMode = performanceMode,
                detectionCount = detectedObjects.size
            )
        }
        
        // Spacer to push content to bottom
        Spacer(modifier = Modifier.weight(1f))
        
        // Detection overlays
        if (showDetectionVisualizations && detectedObjects.isNotEmpty()) {
            DetectionLabelsOverlay(detectedObjects)
        }
        
        // Bottom control bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            BottomControlBar(
                onToggleDetection = onToggleDetection,
                onToggleLock = onToggleLock,
                onClearScene = onClearScene,
                onTogglePerformanceStats = onTogglePerformanceStats,
                showingDetection = showDetectionVisualizations,
                isLocked = lockObject,
                showingPerformanceStats = showPerformanceStats
            )
        }
        
        // Floating button to show/hide controls
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)) {
            FloatingActionButton(
                onClick = onToggleControls,
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = if (showControls) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle controls visibility"
                )
            }
        }
    }
}

/**
 * Tracking status bar showing AR session status
 */
@Composable
private fun TrackingStatusBar(
    trackingState: TrackingState?,
    trackingFailureReason: TrackingFailureReason?
) {
    val backgroundColor = when (trackingState) {
        TrackingState.TRACKING -> Color(0x8800AA00) // Semi-transparent green
        TrackingState.PAUSED -> Color(0x88AA6600) // Semi-transparent amber
        TrackingState.STOPPED -> Color(0x88AA0000) // Semi-transparent red
        null -> Color(0x88000000) // Semi-transparent black
    }
    
    val statusText = when (trackingState) {
        TrackingState.TRACKING -> "Tracking OK"
        TrackingState.PAUSED -> getTrackingFailureReasonText(trackingFailureReason)
        TrackingState.STOPPED -> "Tracking Stopped"
        null -> "Initializing..."
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(8.dp)
    ) {
        Text(
            text = statusText,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

/**
 * Performance statistics display bar
 */
@Composable
private fun PerformanceStatsBar(
    fps: Float,
    performanceMode: PerformanceMode,
    detectionCount: Int
) {
    val fpsColor = when {
        fps >= 45f -> Color(0xFF00CC00) // Good FPS - green
        fps >= 30f -> Color(0xFFCCCC00) // Acceptable FPS - yellow
        else -> Color(0xFFCC0000) // Poor FPS - red
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x88000000))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = "FPS: ${fps.toInt()}",
                color = fpsColor
            )
            Text(
                text = "Mode: ${performanceMode.name}",
                color = Color.White
            )
            Text(
                text = "Detections: $detectionCount",
                color = Color.White
            )
        }
    }
}

/**
 * Bottom control bar with AR interaction buttons
 */
@Composable
private fun BottomControlBar(
    onToggleDetection: () -> Unit,
    onToggleLock: () -> Unit,
    onClearScene: () -> Unit,
    onTogglePerformanceStats: () -> Unit,
    showingDetection: Boolean,
    isLocked: Boolean,
    showingPerformanceStats: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xCC000000) // Semi-transparent black
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Detection toggle
                IconButton(onClick = onToggleDetection) {
                    Icon(
                        imageVector = if (showingDetection) Icons.Default.FindInPage else Icons.Default.FindInPageOutlined,
                        contentDescription = "Toggle detection",
                        tint = if (showingDetection) Color.Green else Color.White
                    )
                }
                
                // Lock objects toggle
                IconButton(onClick = onToggleLock) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Toggle lock",
                        tint = if (isLocked) Color.Yellow else Color.White
                    )
                }
                
                // Clear scene button
                IconButton(onClick = onClearScene) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear scene",
                        tint = Color.White
                    )
                }
                
                // Performance stats toggle
                IconButton(onClick = onTogglePerformanceStats) {
                    Icon(
                        imageVector = if (showingPerformanceStats) 
                            Icons.Default.BarChart else Icons.Default.ShowChart,
                        contentDescription = "Toggle performance stats",
                        tint = if (showingPerformanceStats) Color.Cyan else Color.White
                    )
                }
            }
        }
    }
}

/**
 * Detection labels overlay for showing detected objects
 */
@Composable
private fun DetectionLabelsOverlay(
    detectedObjects: List<DetectedObject>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Show maximum 5 most confident detections
        val topDetections = detectedObjects
            .sortedByDescending { it.confidence }
            .take(5)
        
        for (detection in topDetections) {
            DetectionLabel(detection)
        }
    }
}

/**
 * Individual detection label with confidence
 */
@Composable
private fun DetectionLabel(detection: DetectedObject) {
    val backgroundColor = rememberDetectionColor(detection.classId)
    
    Card(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(backgroundColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = detection.label,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Text(
                text = "${(detection.confidence * 100).toInt()}%",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Object manipulation menu for selected objects
 */
@Composable
private fun ObjectManipulationMenu(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onToggleEdit: () -> Unit,
    isEditable: Boolean
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xDDFFFFFF)
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Close button
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.Black
                )
            }
            
            // Edit toggle button
            IconButton(onClick = onToggleEdit) {
                Icon(
                    imageVector = if (isEditable) Icons.Default.Edit else Icons.Default.EditOff,
                    contentDescription = if (isEditable) "Stop editing" else "Edit object",
                    tint = if (isEditable) Color.Blue else Color.Black
                )
            }
            
            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete object",
                    tint = Color.Red
                )
            }
        }
    }
}

/**
 * Utility class for tracking FPS
 */
private class FpsCounter {
    private val frameTimestamps = ArrayDeque<Long>(MAX_FRAMES)
    private val lock = Any()

    companion object {
        private const val MAX_FRAMES = 60
        private const val NANOS_PER_SECOND = 1_000_000_000L
    }

    fun frameRendered() {
        synchronized(lock) {
            val currentTime = System.nanoTime()
            frameTimestamps.addLast(currentTime)

            // Keep queue size bounded
            while (frameTimestamps.size > MAX_FRAMES) {
                frameTimestamps.removeFirst()
            }
        }
    }

    fun getFps(): Float {
        synchronized(lock) {
            if (frameTimestamps.size < 2) return 0f

            val oldestTs = frameTimestamps.first()
            val newestTs = frameTimestamps.last()
            val timeSpanNanos = newestTs - oldestTs
            
            if (timeSpanNanos <= 0) return 0f
            
            val frameCount = frameTimestamps.size - 1
            return frameCount * NANOS_PER_SECOND.toFloat() / timeSpanNanos
        }
    }
}

/**
 * Gets a descriptive message for tracking failure reason
 */
private fun getTrackingFailureReasonText(reason: TrackingFailureReason?): String {
    return when (reason) {
        TrackingFailureReason.NONE -> "Tracking OK"
        TrackingFailureReason.BAD_STATE -> "Bad state - try restarting"
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Not enough light"
        TrackingFailureReason.EXCESSIVE_MOTION -> "Hold device still"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Move around to scan environment" 
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
        null -> "Unknown tracking issue"
    }
}

/**
 * Performance mode for adaptive quality settings
 */
enum class PerformanceMode {
    LOW,       // Optimized for low-end devices
    BALANCED,  // Balance between quality and performance
    HIGH       // Maximum quality on high-end devices
}

/**
 * Detection frequency settings for ML operations
 */
enum class DetectionFrequency {
    LOW,    // Process every 500ms
    MEDIUM, // Process every 250ms
    HIGH    // Process every 100ms
}

/**
 * Utility class for tracking gesture state
 * Used to avoid excessive haptic feedback
 */
private class GestureTrackingState {
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastHapticTime: Long = 0
    private var scaleStartTime: Long = 0
    
    fun onDown(x: Float, y: Float) {
        lastX = x
        lastY = y
    }
    
    fun onScaleStart() {
        scaleStartTime = System.currentTimeMillis()
    }
    
    fun shouldTriggerHaptic(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastHapticTime > 100) { // Limit haptics to avoid overwhelming feedback
            lastHapticTime = now
            return true
        }
        return false
    }
}

/**
 * Composable function to remember a color for a detection class
 */
@Composable
private fun rememberDetectionColor(classId: Int): Color {
    // Generate deterministic colors based on class ID
    return remember(classId) {
        val hue = (classId * 137.5f) % 360f
        Color.hsl(hue, 0.8f, 0.5f)
    }
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
 * AR Detection Manager handles ML processing on camera frames
 */
class ARDetectionManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val executor: Executor
) {
    private val TAG = "ARDetectionManager"
    private var detector: YOLO11Detector? = null
    private var isInitialized = false
    private var lastProcessTime = 0L
    private var detectionFrequency = DetectionFrequency.MEDIUM
    
    // Detection results published as StateFlow
    private val _detectedObjects = MutableStateFlow<List<DetectedObject>>(emptyList())
    val detectedObjects: StateFlow<List<DetectedObject>> = _detectedObjects.asStateFlow()
    
    // Processing state to avoid redundant work
    private var isProcessing = false
    
    /**
     * Initialize the detector with model
     */
    fun initialize(
        modelPath: String,
        labelsPath: String,
        lifecycleOwner: LifecycleOwner
    ) {
        try {
            coroutineScope.launch(Dispatchers.IO) {
                detector = YOLO11Detector(
                    context = context,
                    modelPath = modelPath,
                    labelsPath = labelsPath,
                    useGPU = true
                )
                
                isInitialized = true
                Log.d(TAG, "Detector initialized successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize detector", e)
        }
    }
    
    /**
     * Process an AR frame for object detection
     */
    fun processFrame(frame: Frame, session: Session) {
        if (!isInitialized || isProcessing) return
        
        val currentTime = System.currentTimeMillis()
        val processingInterval = when (detectionFrequency) {
            DetectionFrequency.LOW -> 500L
            DetectionFrequency.MEDIUM -> 250L
            DetectionFrequency.HIGH -> 100L
        }
        
        // Skip processing if too soon after last frame
        if (currentTime - lastProcessTime < processingInterval) return
        
        lastProcessTime = currentTime
        isProcessing = true
        
        executor.execute {
            try {
                // Get camera image
                val image = frame.tryAcquireCameraImage() ?: return@execute
                
                // Convert to bitmap
                val bitmap = convertYuvImageToBitmap(image, session)
                image.close()
                
                // Run detector
                val detector = detector ?: return@execute
                val detections = detector.detect(bitmap)
                
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
                
                // Update state flow
                coroutineScope.launch {
                    _detectedObjects.emit(results)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing frame", e)
            } finally {
                isProcessing = false
            }
        }
    }
    
    /**
     * Convert AR Core YUV image to bitmap
     */
    private fun convertYuvImageToBitmap(image: Image, session: Session): Bitmap {
        // Get image dimensions
        val width = image.width
        val height = image.height

        // Create output bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Use ARCore to convert from YUV to RGB
        session.convertYuv(image, bitmap)
        
        return bitmap
    }
    
    /**
     * Set detection frequency based on device performance
     */
    fun setDetectionFrequency(frequency: DetectionFrequency) {
        detectionFrequency = frequency
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        try {
            detector?.close()
            detector = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down detector", e)
        }
    }
}

/**
 * Highlights objects in AR scene based on detection
 */
class ObjectHighlighter(
    private val engine: Engine,
    private val materialLoader: MaterialLoader
) {
    private val highlightedNodes = mutableMapOf<ModelNode, CubeNode>()
    
    /**
     * Highlight a node with glow effect proportional to confidence
     */
    fun highlightNode(node: ModelNode, confidence: Float) {
        // Remove any existing highlight
        clearHighlight(node)
        
        // Create highlight box
        val highlightColor = Color(0f, 0.8f, 1f, confidence * 0.5f + 0.2f)
        val highlightBox = CubeNode(
            engine = engine,
            size = node.extents.scaled(1.05f), // 5% larger than object
            center = node.center,
            materialInstance = materialLoader.createColorInstance(highlightColor)
        )
        
        // Add to object
        node.addChildNode(highlightBox)
        highlightedNodes[node] = highlightBox
    }
    
    /**
     * Clear highlight from a specific node
     */
    fun clearHighlight(node: ModelNode) {
        highlightedNodes[node]?.let { highlight ->
            node.removeChildNode(highlight)
            highlightedNodes.remove(node)
        }
    }
    
    /**
     * Clear all highlights
     */
    fun clearHighlights() {
        highlightedNodes.forEach { (node, highlight) ->
            node.removeChildNode(highlight)
        }
        highlightedNodes.clear()
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        clearHighlights()
    }
}

/**
 * Vibration feedback utility
 */
@SuppressLint("MissingPermission")
fun getVibrator(context: Context): Vibrator? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Trigger vibration with pattern appropriate for the action
 */
@SuppressLint("MissingPermission")
fun vibrateForAction(vibrator: Vibrator?, type: VibrationActionType) {
    vibrator ?: return
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        when (type) {
            VibrationActionType.OBJECT_PLACED -> {
                val effect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            }
            VibrationActionType.OBJECT_SELECTED -> {
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 20, 20, 20), -1)
                vibrator.vibrate(effect)
            }
            VibrationActionType.ERROR -> {
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1)
                vibrator.vibrate(effect)
            }
        }
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(50)
    }
}

/**
 * Types of vibration feedback
 */
enum class VibrationActionType {
    OBJECT_PLACED,
    OBJECT_SELECTED,
    ERROR
}