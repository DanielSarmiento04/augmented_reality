package com.example.opencv_tutorial

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min
import android.util.Log

/**
 * Custom view that overlays detection results on the camera preview
 */
class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Atomic references to prevent threading issues
    private val currentBitmap = AtomicReference<Bitmap?>(null)
    private val currentDetections = AtomicReference<List<YOLO11Detector.Detection>>(emptyList())

    // Configurable display options
    var confidenceThreshold = 0.25f
    var showLabels = true
    var boxThickness = 4f
    var textSize = 40f
    var boxStyle = BoxStyle.FILLED

    // Pre-allocated paint objects for better performance
    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = boxThickness
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = this@DetectionOverlayView.textSize
        typeface = Typeface.DEFAULT_BOLD
    }

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    // Drawing configuration
    enum class BoxStyle {
        STROKE, FILLED, CORNERS
    }

    // Color palette for different classes
    private val colors = listOf(
        Color.parseColor("#FF5252"), // Red
        Color.parseColor("#FF4081"), // Pink
        Color.parseColor("#E040FB"), // Purple
        Color.parseColor("#7C4DFF"), // Deep Purple
        Color.parseColor("#536DFE"), // Indigo
        Color.parseColor("#448AFF"), // Blue
        Color.parseColor("#40C4FF"), // Light Blue
        Color.parseColor("#18FFFF"), // Cyan
        Color.parseColor("#64FFDA"), // Teal
        Color.parseColor("#69F0AE"), // Green
        Color.parseColor("#B2FF59"), // Light Green
        Color.parseColor("#EEFF41"), // Lime
        Color.parseColor("#FFFF00"), // Yellow
        Color.parseColor("#FFD740"), // Amber
        Color.parseColor("#FFAB40"), // Orange
        Color.parseColor("#FF6E40")  // Deep Orange
    )

    /**
     * Update the overlay with new bitmap and detections
     * Optimized to maintain image quality in real-time
     */
    fun updateOverlay(bitmap: Bitmap, detections: List<YOLO11Detector.Detection>) {
        // Filter detections by confidence threshold
        val filteredDetections = detections.filter { it.conf >= confidenceThreshold }
        
        try {
            // Check if we need to create a new bitmap or can reuse existing one
            val oldBitmap = currentBitmap.get()
            val needNewBitmap = oldBitmap == null || 
                                oldBitmap.width != bitmap.width || 
                                oldBitmap.height != bitmap.height
            
            val displayBitmap: Bitmap
            
            if (needNewBitmap) {
                // Create a new bitmap if needed
                displayBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                
                // Clean up old bitmap if it exists
                oldBitmap?.recycle()
            } else {
                // Reuse existing bitmap for better performance
                displayBitmap = oldBitmap!!
                
                // Copy pixels from new bitmap to existing one
                val canvas = Canvas(displayBitmap)
                canvas.drawBitmap(bitmap, 0f, 0f, null)  // Direct usage since bitmap is non-null here
            }
            
            // Update atomic references - save the bitmap first
            currentBitmap.set(displayBitmap)
            currentDetections.set(filteredDetections)
        } catch (e: Exception) {
            Log.e("DetectionOverlayView", "Error updating overlay: ${e.message}")
            // Still update detections even if bitmap update fails
            currentDetections.set(filteredDetections)
        }
        
        // Request redraw
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val bitmap = currentBitmap.get() ?: return
        val detections = currentDetections.get() ?: return
        
        // Calculate scaling to fit the view
        val scale: Float
        val left: Float
        val top: Float
        
        if (width * bitmap.height > height * bitmap.width) {
            // Width constrained
            scale = width.toFloat() / bitmap.width
            top = (height - bitmap.height * scale) / 2
            left = 0f
        } else {
            // Height constrained
            scale = height.toFloat() / bitmap.height
            left = (width - bitmap.width * scale) / 2
            top = 0f
        }
        
        // Apply high-quality rendering settings
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
        
        // Scale canvas for proper display
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        
        // Draw the bitmap first
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        // Draw all detections
        for (detection in detections) {
            drawDetection(canvas, detection)
        }
        
        canvas.restore()
    }
    
    /**
     * Draw a single detection on the canvas
     */
    private fun drawDetection(canvas: Canvas, detection: YOLO11Detector.Detection) {
        // Get color for this class
        val color = colors[detection.classId % colors.size]
        boxPaint.color = color
        backgroundPaint.color = color
        
        // Get box coordinates
        val left = detection.box.x.toFloat()
        val top = detection.box.y.toFloat()
        val right = left + detection.box.width
        val bottom = top + detection.box.height
        
        // Draw box based on style
        when (boxStyle) {
            BoxStyle.STROKE -> {
                // Simple rectangle
                canvas.drawRect(left, top, right, bottom, boxPaint)
            }
            BoxStyle.FILLED -> {
                // Semi-transparent fill with stroke
                // Store original style in a temporary variable
                val originalPaintStyle = boxPaint.style
                
                // Set stroke style for drawing the border
                boxPaint.style = Paint.Style.STROKE
                canvas.drawRect(left, top, right, bottom, boxPaint)
                
                // Draw semi-transparent fill
                val fillPaint = Paint().apply {
                    style = Paint.Style.FILL

                }
                canvas.drawRect(left, top, right, bottom, fillPaint)
                
                // Restore original style
                boxPaint.style = originalPaintStyle
            }
            BoxStyle.CORNERS -> {
                // Draw corner lines
                val cornerLength = min(detection.box.width, detection.box.height) * 0.2f
                
                // Top-left
                canvas.drawLine(left, top, left + cornerLength, top, boxPaint)
                canvas.drawLine(left, top, left, top + cornerLength, boxPaint)
                
                // Top-right
                canvas.drawLine(right - cornerLength, top, right, top, boxPaint)
                canvas.drawLine(right, top, right, top + cornerLength, boxPaint)
                
                // Bottom-left
                canvas.drawLine(left, bottom - cornerLength, left, bottom, boxPaint)
                canvas.drawLine(left, bottom, left + cornerLength, bottom, boxPaint)
                
                // Bottom-right
                canvas.drawLine(right - cornerLength, bottom, right, bottom, boxPaint)
                canvas.drawLine(right, bottom, right, bottom - cornerLength, boxPaint)
            }
        }
        
        // Draw label if enabled
        if (showLabels) {
            val detector = YOLODetectorProvider.getDetector(context)
            val className = detector?.getClassName(detection.classId) ?: "Unknown"
            val confidence = (detection.conf * 100).toInt()
            
            // Create label text
            val label = "$className: $confidence%"
            
            // Measure text
            val textWidth = textPaint.measureText(label)
            
            // Calculate label position - place above object if possible
            val labelY = max(top - 5, textSize)
            
            // Draw background for text
            backgroundPaint.alpha = 180 // Semi-transparent background
            val textBgRect = RectF(left, labelY - textSize, left + textWidth + 10, labelY + 5)
            canvas.drawRect(textBgRect, backgroundPaint)
            
            // Draw label text
            textPaint.color = Color.WHITE
            canvas.drawText(label, left + 5, labelY - 5, textPaint)
        }
    }
    
    /**
     * Clear all detections and bitmap
     */
    fun clearOverlay() {
        val oldBitmap = currentBitmap.getAndSet(null)
        oldBitmap?.recycle()
        currentDetections.set(emptyList())
        invalidate()
    }
    
    /**
     * Update drawing configuration
     */
    fun updateDrawingConfig(
        boxThickness: Float = this.boxThickness,
        textSize: Float = this.textSize,
        boxStyle: BoxStyle = this.boxStyle
    ) {
        this.boxThickness = boxThickness
        boxPaint.strokeWidth = boxThickness
        
        this.textSize = textSize
        textPaint.textSize = textSize
        
        this.boxStyle = boxStyle
        
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearOverlay()
    }
}
