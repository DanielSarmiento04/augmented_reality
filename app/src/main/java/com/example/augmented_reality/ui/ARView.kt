package com.example.augmented_reality.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale

@Composable
fun ARView(navController: NavHostController) {
    val context = LocalContext.current

    // Use AndroidView to integrate ARSceneView
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            ARSceneView(ctx).apply {
                // Configure ARSceneView directly
                isInstantPlacementEnabled = true // Enable instant placement

                // Add touch listener for placing 3D models
                setOnTapArPlaneListener { hitResult, _ ->
                    addNode(
                        modelGlbFileLocation = "https://modelviewer.dev/shared-assets/models/Astronaut.glb",
                        position = Position(hitResult.hitPose.translation),
                        scale = Scale(0.5f)
                    )
                }
            }

            }
        },
        update = { arSceneView ->
            // Additional updates for ARSceneView if necessary
        }
    )
}