package com.example.app.ui.preview


import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.app.R
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.launch
import java.io.File

class PreviewFragment : Fragment(R.layout.fragment_preview_model) {

    private lateinit var sceneView: SceneView
    private lateinit var loadingView: View

    private fun loadModel(modelPath: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                loadingView.isGone = false

                // Загружаем окружение
                val hdrFile = "environments/studio_small_09_2k.hdr"
                sceneView.environmentLoader.loadHDREnvironment(hdrFile).apply {
                    sceneView.indirectLight = this?.indirectLight
                    sceneView.skybox = this?.skybox
                }

                // Настраиваем камеру
                sceneView.cameraNode.apply {
                    position = Position(z = 4.0f)
                }

                // Проверяем, это путь к файлу или ассету
                val modelInstance = if (modelPath.startsWith("/")) {
                    // Загрузка из файла
                    sceneView.modelLoader.createModelInstance(File(modelPath))
                } else {
                    // Загрузка из ассетов
                    sceneView.modelLoader.createModelInstance(modelPath)
                }

                val modelNode = ModelNode(
                    modelInstance = modelInstance,
                    scaleToUnits = 2.0f,
                )
                modelNode.scale = Scale(0.05f)
                sceneView.addChildNode(modelNode)

            } catch (e: Exception) {
                Log.e("PreviewFragment", "Ошибка загрузки модели", e)
                // Показать ошибку пользователю
            } finally {
                loadingView.isGone = true
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("PreviewFragment", "onViewCreated вызван")
        sceneView = view.findViewById(R.id.sceneView)
        loadingView = view.findViewById(R.id.loadingView)

        val modelPath = arguments?.getString("MODEL_PATH") ?: "models/chairs/AR-Code-12.glb"
        Log.d("PreviewFragment", "Загружаем модель из: $modelPath")

        loadModel(modelPath)
    }
}