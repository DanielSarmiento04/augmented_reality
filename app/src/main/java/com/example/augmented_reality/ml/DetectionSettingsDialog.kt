package com.example.opencv_tutorial

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.slider.Slider

/**
 * Dialog for adjusting detection settings in real-time
 */
class DetectionSettingsDialog : DialogFragment() {

    private lateinit var confidenceSlider: Slider
    private lateinit var confidenceValueText: TextView
    private lateinit var resolutionGroup: RadioGroup
    private lateinit var boxStyleGroup: RadioGroup

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_detection_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI components
        confidenceSlider = view.findViewById(R.id.confidenceSlider)
        confidenceValueText = view.findViewById(R.id.confidenceValueText)
        resolutionGroup = view.findViewById(R.id.resolutionGroup)
        boxStyleGroup = view.findViewById(R.id.boxStyleGroup)

        val applyButton = view.findViewById<Button>(R.id.applyButton)
        val cancelButton = view.findViewById<Button>(R.id.cancelButton)

        // Get current settings
        val cameraActivity = activity as? CameraActivity
        val overlayView = cameraActivity?.findViewById<DetectionOverlayView>(R.id.detection_overlay)

        // Initialize slider with current value
        val currentConfidence = overlayView?.confidenceThreshold ?: YOLO11Detector.CONFIDENCE_THRESHOLD
        confidenceSlider.value = currentConfidence * 100f
        updateConfidenceDisplay(currentConfidence * 100f)

        // Set radio buttons based on current settings
        setCurrentBoxStyle(overlayView?.boxStyle)

        // Set up confidence slider
        confidenceSlider.addOnChangeListener { _, value, _ ->
            updateConfidenceDisplay(value)
        }

        // Apply button
        applyButton.setOnClickListener {
            applySettings()
            dismiss()
        }

        // Cancel button
        cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun updateConfidenceDisplay(value: Float) {
        confidenceValueText.text = String.format("%.0f%%", value)
    }

    private fun setCurrentBoxStyle(style: DetectionOverlayView.BoxStyle?) {
        when (style) {
            DetectionOverlayView.BoxStyle.STROKE -> boxStyleGroup.check(R.id.boxStyleStroke)
            DetectionOverlayView.BoxStyle.FILLED -> boxStyleGroup.check(R.id.boxStyleFilled)
            DetectionOverlayView.BoxStyle.CORNERS -> boxStyleGroup.check(R.id.boxStyleCorners)
            null -> boxStyleGroup.check(R.id.boxStyleFilled)
        }
    }

    private fun applySettings() {
        val cameraActivity = activity as? CameraActivity
        val overlayView = cameraActivity?.findViewById<DetectionOverlayView>(R.id.detection_overlay)
        val cameraManager = cameraActivity?.getCameraManager()

        // Apply confidence threshold
        val confidenceValue = confidenceSlider.value / 100f
        overlayView?.confidenceThreshold = confidenceValue

        // Apply box style
        val boxStyle = when (boxStyleGroup.checkedRadioButtonId) {
            R.id.boxStyleStroke -> DetectionOverlayView.BoxStyle.STROKE
            R.id.boxStyleFilled -> DetectionOverlayView.BoxStyle.FILLED
            R.id.boxStyleCorners -> DetectionOverlayView.BoxStyle.CORNERS
            else -> DetectionOverlayView.BoxStyle.FILLED
        }
        overlayView?.boxStyle = boxStyle

        // Apply resolution settings
        when (resolutionGroup.checkedRadioButtonId) {
            R.id.resolution640 -> cameraManager?.setAnalysisResolution(640, 640)
            R.id.resolution320 -> cameraManager?.setAnalysisResolution(320, 320)
            R.id.resolution1280 -> cameraManager?.setAnalysisResolution(1280, 720)
        }

        // Force redraw
        overlayView?.invalidate()
    }

    override fun onStart() {
        super.onStart()
        // Make dialog wider
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}
