package com.example.app.ui.gallery.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.app.R
import com.example.app.ui.gallery.fragment.GalleryFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Активити для отображения галереи 3D моделей
 * Позволяет просматривать, фильтровать и добавлять модели в избранное
 */
@AndroidEntryPoint
class GalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gallery_main_activity)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, GalleryFragment.newInstance()).commit()
        }
    }
}
