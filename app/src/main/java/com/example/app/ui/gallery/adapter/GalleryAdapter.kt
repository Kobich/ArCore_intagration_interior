package com.example.app.ui.gallery.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.app.R
import com.example.app.model.GalleryEntry
import java.io.IOException

/**
 * Адаптер для отображения списка категорий и моделей в RecyclerView
 * Поддерживает два типа элементов: категории и модели
 */
class GalleryAdapter(
    private val onItemClicked: (GalleryEntry.Item) -> Unit,
    private val onFavoriteClicked: (GalleryEntry.Item) -> Unit
) : ListAdapter<GalleryEntry, RecyclerView.ViewHolder>(GalleryDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_CATEGORY = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is GalleryEntry.Category -> VIEW_TYPE_CATEGORY
            is GalleryEntry.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_CATEGORY -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_header, parent, false)
                CategoryViewHolder(view)
            }
            VIEW_TYPE_ITEM -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_model, parent, false)
                ItemViewHolder(view, onItemClicked, onFavoriteClicked)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GalleryEntry.Category -> (holder as CategoryViewHolder).bind(item)
            is GalleryEntry.Item -> (holder as ItemViewHolder).bind(item)
        }
    }

    /**
     * ViewHolder для отображения категории
     */
    class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val categoryNameTextView: TextView = itemView.findViewById(R.id.headerTitle)

        fun bind(category: GalleryEntry.Category) {
            categoryNameTextView.text = category.name
        }
    }

    /**
     * ViewHolder для отображения модели
     */
    class ItemViewHolder(
        itemView: View,
        private val onItemClicked: (GalleryEntry.Item) -> Unit,
        private val onFavoriteClicked: (GalleryEntry.Item) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val modelNameTextView: TextView = itemView.findViewById(R.id.modelName)
        private val modelPreviewImageView: ImageView = itemView.findViewById(R.id.previewImage)
        private val favoriteButton: ImageView = itemView.findViewById(R.id.favoriteIcon)

        fun bind(item: GalleryEntry.Item) {
            modelNameTextView.text = item.displayName

            val assetPath = item.preview
            val context = itemView.context

            try{
            context.assets.open(assetPath)
                Glide.with(context)
                    .load(Uri.parse("file:///android_asset/$assetPath"))
                    .placeholder(R.drawable.ic_furniture)
                    .into(modelPreviewImageView)
            } catch (e: IOException) {
                modelPreviewImageView.setImageResource(R.drawable.ic_furniture)
            }

            favoriteButton.setImageResource(
                if (item.favorite) R.drawable.favorite_ic_2
                else R.drawable.favorite_empty_ic_2
            )

            itemView.setOnClickListener { onItemClicked(item) }
            favoriteButton.setOnClickListener { onFavoriteClicked(item) }
        }
    }

    /**
     * DiffCallback для оптимизации обновлений списка
     */
    class GalleryDiffCallback : DiffUtil.ItemCallback<GalleryEntry>() {
        override fun areItemsTheSame(oldItem: GalleryEntry, newItem: GalleryEntry): Boolean {
            return when {
                oldItem is GalleryEntry.Category && newItem is GalleryEntry.Category ->
                    oldItem.name == newItem.name
                oldItem is GalleryEntry.Item && newItem is GalleryEntry.Item ->
                    oldItem.id == newItem.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: GalleryEntry, newItem: GalleryEntry): Boolean {
            return when {
                oldItem is GalleryEntry.Category && newItem is GalleryEntry.Category ->
                    oldItem.name == newItem.name
                oldItem is GalleryEntry.Item && newItem is GalleryEntry.Item -> {
                    oldItem.displayName == newItem.displayName &&
                    oldItem.path == newItem.path &&
                    oldItem.preview == newItem.preview &&
                    oldItem.favorite == newItem.favorite
                }
                else -> false
            }
        }

    }
}
