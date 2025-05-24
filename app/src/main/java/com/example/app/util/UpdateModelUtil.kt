package com.example.app.util

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.io.FileWriter

// 1. Описываем структуру JSON
data class ModelInfo(
    val id: String,
    val displayName: String,
    val path: String,
    // thumbnail оставляем nullable — в UI будем подставлять дефолтный drawable, если null
    val thumbnail: String? = null
)

typealias ModelsManifest = Map<String, List<ModelInfo>>

fun updateModelsManifest(context: Context) {
    val manifest = mutableMapOf<String, MutableList<ModelInfo>>()
    val assetManager = context.assets

    fun scanDir(assetPath: String) {
        val list = assetManager.list(assetPath) ?: return
        if (list.isEmpty()) return

        for (name in list) {
            val fullPath = if (assetPath.isEmpty()) name else "$assetPath/$name"
            // Проверяем, есть ли в этом "подкаталоге" ещё файлы/папки
            val subList = assetManager.list(fullPath)
            when {
                subList != null && subList.isNotEmpty() -> {
                    // Это папка — углубляемся
                    scanDir(fullPath)
                }
                name.endsWith(".glb", ignoreCase = true) -> {
                    // Это наша модель
                    // Категория — первый сегмент пути после "models"
                    val segments = fullPath.split("/")
                    val category = segments.getOrNull(1) ?: "uncategorized"
                    val filename = name.substringBeforeLast(".")
                    val displayName = filename
                        .replace('_', ' ')
                        .replaceFirstChar { it.uppercaseChar() }

                    val info = ModelInfo(
                        id = filename,
                        displayName = displayName,
                        path = fullPath,
                        thumbnail = null
                    )
                    manifest.getOrPut(category) { mutableListOf() }
                        .add(info)
                }
            }
        }
    }

    // Стартуем сканирование с корня assets/models
    scanDir("models")

    // Сериализуем и сохраняем в файл
    val gson = Gson().newBuilder().setPrettyPrinting().create()
    val jsonString = gson.toJson(manifest as ModelsManifest)

    val outFile = File(context.filesDir, "models.json")
    FileWriter(outFile).use { it.write(jsonString) }

    // Теперь в context.filesDir/models.json — актуальный манифест


}
