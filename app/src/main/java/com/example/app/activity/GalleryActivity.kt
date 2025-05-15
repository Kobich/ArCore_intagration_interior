package com.example.app.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.app.R
import com.example.app.adapter.GalleryAdapter
import com.example.app.aractivity.ArActivity
import com.example.app.databinding.ActivityGalleryBinding

import com.example.app.model.GalleryEntry
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

class GalleryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGalleryBinding
    private lateinit var entries: List<GalleryEntry>

    companion object {
        private const val REQUEST_CODE_SCAN = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Настройка toolbar
        setSupportActionBar(binding.toolbar)

        // Настройка выезжающего меню
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Обработка кликов по пунктам меню
        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_scan_qr -> {

                }
                R.id.nav_download_link -> {
                    //DownloadLinkDialog().show(supportFragmentManager, "download_link")
                }
                R.id.nav_filter -> {

                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Загрузка данных
        entries = loadEntries()

        // Сетка: 2 колонки, заголовки растягиваются
        val layoutManager = GridLayoutManager(this, 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (entries[position] is GalleryEntry.Category) 2 else 1
            }
        }

        binding.rvGallery.layoutManager = layoutManager
        binding.rvGallery.adapter = GalleryAdapter(entries) { item ->
            startActivity(Intent(this, ArActivity::class.java)
                .putExtra("modelPath", item.path))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCAN && resultCode == Activity.RESULT_OK) {
            val url = data?.getStringExtra("scannedUrl") ?: return
            // TODO: Реализовать downloadModel(url)
        }
    }

    private fun loadEntries(): List<GalleryEntry> {
        assets.open("models.json").use { input ->
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
                return list
            }
        }
    }
}
