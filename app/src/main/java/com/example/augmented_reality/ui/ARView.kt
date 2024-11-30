package com.example.augmented_reality.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.ar.node.PlacementMode

@Composable
fun ARView(selectedMachine: String) {
    val nodes = remember { mutableListOf<ArNode>() }
    val modelNode = remember { mutableStateOf<ArModelNode?>(null) }
    val placeModelButton = remember { mutableStateOf(false) }
    val context = LocalContext.current

    val formattedName = selectedMachine.lowercase().replace(" ", "_")
    val formattedFullNameFile = formattedName + ".glb"
    val assetPath = "$formattedName/$formattedFullNameFile"
    Log.d("ARView", assetPath)

    Box(modifier = Modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            nodes = nodes,
            planeRenderer = true,
            onCreate = { arSceneView ->
                // Configure ARSceneView
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
    }
}
