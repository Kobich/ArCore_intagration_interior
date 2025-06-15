package com.example.app.data.repository

import android.content.Context
import com.example.app.model.GalleryEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelsRepository @Inject constructor(
    private val context: Context
) {
    fun loadModels(): List<GalleryEntry> {
        val assetModels = loadAssetModels()
        val downloadedModels = loadDownloadedModels()

        return assetModels + downloadedModels
    }

    private fun loadAssetModels(): List<GalleryEntry> {
        return context.assets.open("models.json").use { input ->
            InputStreamReader(input).use { reader ->
                val type = object : TypeToken<Map<String, List<Map<String, String>>>>() {}.type
                val map: Map<String, List<Map<String, String>>> = Gson().fromJson(reader, type)
                val list = mutableListOf<GalleryEntry>()
                map.forEach { (category, items) ->
                    list += GalleryEntry.Category(category)
                    items.forEach { m ->
                        list += GalleryEntry.Item(
                            id = m["id"]!!,
                            displayName = m["displayName"]!!,
                            path = m["path"]!!,
                            preview = m["preview"]!!,
                            favorite = false
                        )
                    }
                }
                list
            }
        }
    }

    private fun loadDownloadedModels(): List<GalleryEntry> {
        val models = mutableListOf<GalleryEntry.Item>()
        val modelsDir = File(context.filesDir, "downloaded_models")

        if (!modelsDir.exists()) return emptyList()
        val categoryName = "Downloaded"
        val category = GalleryEntry.Category(categoryName)
        val items = mutableListOf<GalleryEntry.Item>()

        modelsDir.listFiles()?.forEach { modelFolder ->
            if (modelFolder.isDirectory) {
                val glbFile = modelFolder.listFiles()?.firstOrNull { it.extension == "glb" }
                val previewFile = modelFolder.listFiles()?.firstOrNull { it.extension == "png" }

                if (glbFile != null) {
                    items += GalleryEntry.Item(
                        id = "downloaded_${glbFile.nameWithoutExtension}", // чтобы избежать конфликтов
                        displayName = glbFile.nameWithoutExtension.replaceFirstChar { it.uppercase() },
                        path = glbFile.absolutePath,
                        preview = previewFile?.absolutePath ?: "",
                        favorite = false
                    )
                }
            }
        }

        return if (items.isNotEmpty()) listOf(category) + items else emptyList()
    }

    fun getCategories(): List<String> {
        val models = loadModels()
        return models.filterIsInstance<GalleryEntry.Category>().map { it.name }
    }
}

