package com.example.app.ui.gallery.fragment

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.app.R
import com.example.app.aractivity.ArActivity
import com.example.app.databinding.ActivityGalleryBinding
import com.example.app.ui.gallery.adapter.GalleryAdapter
import com.example.app.ui.gallery.viewmodel.GalleryViewModel
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider

@AndroidEntryPoint
class GalleryFragment : Fragment(), NavigationView.OnNavigationItemSelectedListener {

    private val viewModel: GalleryViewModel by viewModels()
    private var _binding: ActivityGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var galleryAdapter: GalleryAdapter

    companion object {
        fun newInstance() = GalleryFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Setup toolbar + drawer toggle
        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val toggle = ActionBarDrawerToggle(
            activity,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener(this)

        // RecyclerView
        binding.rvGallery.layoutManager = GridLayoutManager(requireContext(), 2).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    if (galleryAdapter.currentList.getOrNull(position) is com.example.app.model.GalleryEntry.Category) 2
                    else 1
            }
        }
        galleryAdapter = GalleryAdapter(
            onItemClicked = { item ->
                startActivity(
                    Intent(requireContext(), ArActivity::class.java)
                        .putExtra("modelPath", item.path)
                )
            },
            onFavoriteClicked = { item ->
                viewModel.toggleFavorite(item)
            }
        )
        binding.rvGallery.adapter = galleryAdapter

        // --- New: MenuProvider вместо deprecated setHasOptionsMenu() ---
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                // inflate toolbar menu
                menuInflater.inflate(R.menu.gallery_toolbar_menu, menu)
                // update favorite icon
                val favItem = menu.findItem(R.id.action_favorite)
                favItem?.setIcon(
                    if (viewModel.showOnlyFavorites.value)
                        R.drawable.favorite_ic_2
                    else
                        R.drawable.favorite_empty_ic_2
                )
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_favorite -> {
                        viewModel.setShowOnlyFavorites(!viewModel.showOnlyFavorites.value)
                        true
                    }
                    R.id.action_filter -> {
                        Toast.makeText(requireContext(), "Toolbar Filter clicked", Toast.LENGTH_SHORT).show()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        // observe flows
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.galleryItems.collectLatest { items ->
                        galleryAdapter.submitList(items)
                    }
                }
                launch {
                    viewModel.showOnlyFavorites.collectLatest {
                        // re-create toolbar menu to update favorite icon
                        requireActivity().invalidateMenu()
                    }
                }
            }
        }
    }

    /**
     * Обработка пунктов бокового меню
     */
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return when (item.itemId) {
            R.id.nav_scan_qr -> {
                // Запускаем фрагмент/активити для сканирования QR
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, QrModelDownloadFragment())
                    .addToBackStack(null)
                    .commit()
                true
            }
            // другие пункты...
            else -> false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
