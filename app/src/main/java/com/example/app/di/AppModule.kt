package com.example.app.di

import android.content.Context
import com.example.app.data.preferences.FavoritesPreferences
import com.example.app.data.repository.FavoritesRepository
import com.example.app.data.repository.ModelsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideModelsRepository(
        @ApplicationContext context: Context
    ): ModelsRepository {
        return ModelsRepository(context)
    }

    @Provides
    @Singleton
    fun provideFavoritesPreferences(
        @ApplicationContext context: Context
    ): FavoritesPreferences {
        return FavoritesPreferences(context)
    }

    @Provides
    @Singleton
    fun provideFavoritesRepository(
        modelsRepository: ModelsRepository,
        favoritesPreferences: FavoritesPreferences
    ): FavoritesRepository {
        return FavoritesRepository(modelsRepository, favoritesPreferences)
    }
}