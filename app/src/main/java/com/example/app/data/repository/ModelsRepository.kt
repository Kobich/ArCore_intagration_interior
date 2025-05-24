package com.example.app.data.repository

import android.content.Context
import com.example.app.model.GalleryEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelsRepository @Inject constructor(
    private val context: Context
) {

    fun loadModels(): List<GalleryEntry> {
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
                            preview = m["preview"]!!
                        )
                    }
                }
                list
            }
        }
    }

    fun getCategories(): List<String> {
        val allModels = loadModels()
        return allModels.filterIsInstance<GalleryEntry.Category>().map { it.name }
    }

}
