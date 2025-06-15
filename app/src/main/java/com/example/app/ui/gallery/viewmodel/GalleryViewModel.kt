package com.example.app.ui.gallery.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.data.repository.FavoritesRepository
import com.example.app.data.repository.ModelsRepository
import com.example.app.model.GalleryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel для GalleryActivity
 * Отвечает за бизнес-логику работы с моделями и избранными элементами
 */
@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val modelsRepository: ModelsRepository,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    private val _galleryItems = MutableStateFlow<List<GalleryEntry>>(emptyList())
    val galleryItems: StateFlow<List<GalleryEntry>> = _galleryItems

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories

    private val _currentFilter = MutableStateFlow<String?>(null)
    val currentFilter: StateFlow<String?> = _currentFilter

    private val _showOnlyFavorites = MutableStateFlow(false)
    val showOnlyFavorites: StateFlow<Boolean> = _showOnlyFavorites

    init {
        loadData()
    }

    /**
     * Загружает данные о моделях и их статусе "избранное"
     */
    private fun loadData() {
        viewModelScope.launch {
            // Загрузка категорий
            _categories.value = modelsRepository.getCategories()

            // Загрузка моделей с учетом статуса избранного
            favoritesRepository.getModelsWithFavoriteStatus().collectLatest { models ->
                // Применяем текущие фильтры
                _galleryItems.value = applyFilters(models)
            }
        }
    }

    /**
     * Устанавливает фильтр по категории
     * @param category категория для фильтрации или null для отображения всех категорий
     */
    fun setFilter(category: String?) {
        _currentFilter.value = category
        refreshData()
    }

    /**
     * Включает/выключает режим отображения только избранных элементов
     * @param showOnlyFavorites true для отображения только избранных, false для отображения всех
     */
    fun setShowOnlyFavorites(showOnlyFavorites: Boolean) {
        _showOnlyFavorites.value = showOnlyFavorites
        refreshData()
    }

    /**
     * Переключает статус избранного для модели
     * @param model модель для переключения статуса
     */
    fun toggleFavorite(model: GalleryEntry.Item) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(model)
        }
    }

    /**
     * Обновляет данные с учетом текущих фильтров
     */
    fun refreshData() {
        viewModelScope.launch {
            favoritesRepository.getModelsWithFavoriteStatus().collectLatest { models ->
                _galleryItems.value = applyFilters(models)
            }
        }
    }

    /**
     * Применяет фильтры к списку моделей
     * @param models список моделей для фильтрации
     * @return отфильтрованный список моделей
     */
    private fun applyFilters(models: List<GalleryEntry>): List<GalleryEntry> {
        // Фильтрация по категории
        var filteredModels = if (_currentFilter.value != null) {
            // Начинаем со всех моделей
            val result = mutableListOf<GalleryEntry>()

            // Находим индекс нужной категории
            val categoryIndex = models.indexOfFirst {
                it is GalleryEntry.Category && it.name == _currentFilter.value
            }

            if (categoryIndex != -1) {
                // Добавляем категорию
                result.add(models[categoryIndex])

                // Добавляем все модели до следующей категории
                var i = categoryIndex + 1
                while (i < models.size && models[i] !is GalleryEntry.Category) {
                    result.add(models[i])
                    i++
                }
            }

            result
        } else {
            models
        }

        // Фильтрация по избранным
        if (_showOnlyFavorites.value) {
            // Сначала собираем все категории, у которых есть избранные модели
            val categoriesWithFavorites = mutableSetOf<String>()
            var currentCategory: String? = null

            for (item in filteredModels) {
                when (item) {
                    is GalleryEntry.Category -> currentCategory = item.name
                    is GalleryEntry.Item -> {
                        if (item.favorite && currentCategory != null) {
                            categoriesWithFavorites.add(currentCategory)
                        }
                    }
                }
            }

            // Затем фильтруем модели, оставляя только избранные и их категории
            val result = mutableListOf<GalleryEntry>()
            currentCategory = null

            for (item in filteredModels) {
                when (item) {
                    is GalleryEntry.Category -> {
                        currentCategory = item.name
                        if (currentCategory in categoriesWithFavorites) {
                            result.add(item)
                        }
                    }
                    is GalleryEntry.Item -> {
                        if (item.favorite && currentCategory in categoriesWithFavorites) {
                            result.add(item)
                        }
                    }
                }
            }

            filteredModels = result
        }

        return filteredModels
    }
}