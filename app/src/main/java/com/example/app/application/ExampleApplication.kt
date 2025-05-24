package com.example.app.application

import android.app.Application
import com.google.android.filament.utils.Utils
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ExampleApplication : Application() {
    companion object {
        lateinit var instance: ExampleApplication private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Utils.init()
    }
}