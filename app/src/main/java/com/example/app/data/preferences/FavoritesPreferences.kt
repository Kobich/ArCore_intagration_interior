package com.example.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

private const val FAVORITES_PREFERENCES_NAME = "favorites_preferences"

val Context.favoritesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = FAVORITES_PREFERENCES_NAME
)

@Singleton
class FavoritesPreferences(@ApplicationContext private val context: Context) {

    companion object {
        val FAVORITE_ITEMS_IDS = stringSetPreferencesKey("favorite_items_ids")
    }

    val favoriteItemsIds: Flow<Set<String>> = context.favoritesDataStore.data
        .map { preferences ->
            preferences[FAVORITE_ITEMS_IDS] ?: emptySet()
        }

    suspend fun toggleFavorite(id: String) {
        context.favoritesDataStore.edit { preferences ->
            val currentIds = preferences[FAVORITE_ITEMS_IDS] ?: emptySet()
            preferences[FAVORITE_ITEMS_IDS] = if (id in currentIds) {
                currentIds - id
            } else {
                currentIds + id
            }
        }
    }
}
