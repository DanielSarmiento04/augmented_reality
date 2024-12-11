package com.example.augmented_reality.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.ar.node.PlacementMode
import kotlin.math.roundToInt

@Composable
fun ARView(
    selectedMachine: String
) {
    val nodes = remember { mutableListOf<ArNode>() }
    val modelNode = remember { mutableStateOf<ArModelNode?>(null) }
    val placeModelButton = remember { mutableStateOf(false) }
    val lockObject = remember { mutableStateOf(false) }

    val formattedName = selectedMachine.lowercase().replace(" ", "_")
    val formattedFullNameFile = "$formattedName.glb"
    val assetPath = "$formattedName/$formattedFullNameFile"
    Log.d("ARView", "Asset path: $assetPath")

    Box(modifier = Modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            nodes = nodes,
            planeRenderer = true,
            onCreate = { arSceneView ->
                arSceneView.planeRenderer.isVisible = true
                modelNode.value = ArModelNode(arSceneView.engine, PlacementMode.INSTANT).apply {
                    // Load the machine-specific .glb file from assets
                    loadModelGlbAsync(
                        glbFileLocation = assetPath,
                        scaleToUnits = 0.5f
                    ) {
                        Log.d("ARView", "Model loaded: $selectedMachine")
                    }

                    onAnchorChanged = {
                        placeModelButton.value = !isAnchored
                    }
                }
                nodes.add(modelNode.value!!)
            }
        )

        // Button to place the model in the AR environment
        if (placeModelButton.value) {
            Button(
                onClick = { modelNode.value?.anchor() },
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(text = "Place Model")
            }
        }

        // Bottom controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Previous Button
            Button(onClick = { /* No action for now */ }) {
                Text(text = "Previous")
            }

            Button(onClick = {
                lockObject.value = !lockObject.value
                if (lockObject.value) {
                    modelNode.value?.anchor() // Anchor the model to lock its position
                } else {
                    modelNode.value?.detachAnchor() // Detach the anchor to unlock the model
                }
            }) {
                Text(text = if (lockObject.value) "Unlock Object" else "Lock Object")
            }


            // Next Button
            Button(onClick = { /* No action for now */ }) {
                Text(text = "Next")
            }
        }
    }
}
