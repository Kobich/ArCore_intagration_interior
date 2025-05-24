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
class GalleryActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private val viewModel: GalleryViewModel by viewModels()

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var galleryAdapter: GalleryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        drawerLayout = findViewById(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        navigationView.setNavigationItemSelectedListener(this)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, findViewById(R.id.toolbar),
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()


        val galleryRecyclerView = findViewById<RecyclerView>(R.id.rvGallery)
        galleryRecyclerView.layoutManager = LinearLayoutManager(this)

        galleryRecyclerView.layoutManager = GridLayoutManager(this, 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (galleryAdapter.currentList.getOrNull(position) is GalleryEntry.Category) 2 else 1
            }
        }

        galleryAdapter = GalleryAdapter(
            onItemClicked = { item ->
                val intent = Intent(this, ArActivity::class.java)
                intent.putExtra("model_path", item.path)
                startActivity(intent)
            },
            onFavoriteClicked = { item ->

                viewModel.toggleFavorite(item)
            }
        )


        galleryRecyclerView.adapter = galleryAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.galleryItems.collectLatest { items ->
                        galleryAdapter.submitList(items)
                    }
                }

                launch {
                    viewModel.showOnlyFavorites.collectLatest { showOnlyFavorites ->
                        invalidateOptionsMenu()
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gallery_toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_favorite -> {
                val newState = !viewModel.showOnlyFavorites.value
                viewModel.setShowOnlyFavorites(newState)
                true
            }

            R.id.action_filter -> {
                Toast.makeText(this, "Toolbar Filter clicked", Toast.LENGTH_SHORT).show()
                true
            }

            android.R.id.home -> {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    drawerLayout.openDrawer(GravityCompat.START)
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {

        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
