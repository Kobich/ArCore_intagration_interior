package com.example.app.application

import android.app.Application
import com.google.android.filament.utils.Utils
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class ExampleApplication : Application() {
    companion object {
        lateinit var instance: ExampleApplication private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Инициализация Filament
        Utils.init()

        // Создаем директорию для загруженных моделей
        val modelsDir = File(filesDir, "downloaded_models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
    }
}