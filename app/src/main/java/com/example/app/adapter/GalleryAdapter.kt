package com.example.app.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.app.R
import com.example.app.model.GalleryEntry
import com.example.app.databinding.ItemHeaderBinding
import com.example.app.databinding.ItemModelBinding

class GalleryAdapter(
    private val entries: List<GalleryEntry>,
    private val onClick: (GalleryEntry.Item) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int) = when (entries[position]) {
        is GalleryEntry.Category -> TYPE_HEADER
        is GalleryEntry.Item -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        if (viewType == TYPE_HEADER) {
            val binding = ItemHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            HeaderHolder(binding)
        } else {
            val binding = ItemModelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ModelHolder(binding)
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = entries[position]) {
            is GalleryEntry.Category -> (holder as HeaderHolder).bind(entry)
            is GalleryEntry.Item -> (holder as ModelHolder).bind(entry, onClick)
        }
    }

    override fun getItemCount() = entries.size

    class HeaderHolder(private val binding: ItemHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: GalleryEntry.Category) {
            binding.headerTitle.text = category.name
        }
    }

    class ModelHolder(private val binding: ItemModelBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GalleryEntry.Item, onClick: (GalleryEntry.Item) -> Unit) {
            binding.modelName.text = item.displayName
            binding.root.setOnClickListener { onClick(item) }

            // Загружаем превью из assets/previews
            Glide.with(binding.previewImage.context)
                .load(Uri.parse("file:///android_asset/${item.preview}"))
                .placeholder(R.drawable.ic_furniture)
                .into(binding.previewImage)
        }
    }
}