package com.example.app.data.repository

import com.example.app.data.preferences.FavoritesPreferences
import com.example.app.model.GalleryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    private val modelsRepository: ModelsRepository,
    private val favoritesPreferences: FavoritesPreferences
) {
    val favoriteIds: Flow<Set<String>> = favoritesPreferences.favoriteItemsIds

    fun getModelsWithFavoriteStatus(): Flow<List<GalleryEntry>> {
        val models = flowOf(modelsRepository.loadModels())

        return models.combine(favoriteIds) { allModels, favoriteIds ->
            allModels.map { entry ->
                if (entry is GalleryEntry.Item) {
                    entry.copy(favorite = entry.id in favoriteIds)
                } else {
                    entry
                }
            }
        }
    }

    suspend fun toggleFavorite(model: GalleryEntry.Item) {
        favoritesPreferences.toggleFavorite(model.id)
    }
}
