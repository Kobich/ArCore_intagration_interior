package com.example.app.ui.gallery.activity

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.app.R
import com.example.app.aractivity.ArActivity
import com.example.app.model.GalleryEntry
import com.example.app.ui.gallery.adapter.GalleryAdapter
import com.example.app.ui.gallery.fragment.GalleryFragment
import com.example.app.ui.gallery.viewmodel.GalleryViewModel
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Активити для отображения галереи 3D моделей
 * Позволяет просматривать, фильтровать и добавлять модели в избранное
 */
@AndroidEntryPoint
class GalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.gallery_main_activity)

        supportFragmentManager.setFragmentResultListener(
            GalleryFragment.RESULT_KEY,
            this
        ) { _, bundle ->
            val modelPath = bundle.getString(GalleryFragment.ITEM_PATH) ?: return@setFragmentResultListener

            val intent = Intent(this, ArActivity::class.java)
            intent.putExtra("modelPath", modelPath)
            startActivity(intent)
        }


        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, GalleryFragment.newInstance())
                .commit()
        }
    }
}
