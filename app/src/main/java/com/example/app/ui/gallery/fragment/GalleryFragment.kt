package com.example.app.ui.gallery.fragment

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
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

@AndroidEntryPoint
class GalleryFragment : Fragment(R.layout.activity_gallery),
    NavigationView.OnNavigationItemSelectedListener {

    private val viewModel: GalleryViewModel by viewModels()
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var galleryAdapter: GalleryAdapter

    companion object {
        fun newInstance() = GalleryFragment()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val navView = view.findViewById<NavigationView>(R.id.navigationView)
        val galleryRecyclerView = view.findViewById<RecyclerView>(R.id.rvGallery)
        drawerLayout = view.findViewById(R.id.drawerLayout)

        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }

        val toggle = ActionBarDrawerToggle(
            requireActivity(), drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        navView.setNavigationItemSelectedListener(this)

        galleryRecyclerView.layoutManager = GridLayoutManager(requireContext(), 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (galleryAdapter.currentList.getOrNull(position) is GalleryEntry.Category) 2 else 1
            }
        }

        galleryAdapter = GalleryAdapter(
            onItemClicked = { item ->
                val intent = Intent(requireContext(), ArActivity::class.java)
                intent.putExtra("modelPath", item.path)
                startActivity(intent)
            },
            onFavoriteClicked = { item ->
                viewModel.toggleFavorite(item)
            }
        )

        galleryRecyclerView.adapter = galleryAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.galleryItems.collectLatest { items ->
                        galleryAdapter.submitList(items)
                    }
                }
                launch {
                    viewModel.showOnlyFavorites.collectLatest {
                        requireActivity().invalidateOptionsMenu()
                    }
                }
            }
        }

        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.gallery_toolbar_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_favorite -> {
                val newState = !viewModel.showOnlyFavorites.value
                viewModel.setShowOnlyFavorites(newState)
                true
            }
            R.id.action_filter -> {
                Toast.makeText(requireContext(), "Toolbar Filter clicked", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

}