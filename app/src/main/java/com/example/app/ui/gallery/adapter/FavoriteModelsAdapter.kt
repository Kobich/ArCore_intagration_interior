package com.example.app.ui.gallery.adapter

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

class FavoriteModelsAdapter(
    val visibleItems: Int = 3
) : ListAdapter<GalleryEntry.Item, FavoriteModelsAdapter.ViewHolder>(Diff) {

    var selectedPosition: Int = 0

    object Diff : DiffUtil.ItemCallback<GalleryEntry.Item>() {
        override fun areItemsTheSame(oldItem: GalleryEntry.Item, newItem: GalleryEntry.Item) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: GalleryEntry.Item, newItem: GalleryEntry.Item) = oldItem == newItem
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val image = view.findViewById<ImageView>(R.id.previewImage)
        private val name = view.findViewById<TextView>(R.id.modelName)
        private val container = view as ViewGroup

        fun bind(item: GalleryEntry.Item, isSelected: Boolean) {
            name.text = item.displayName

            Glide.with(image.context)
                .load(Uri.parse("file:///android_asset/${item.preview}"))
                .placeholder(R.drawable.ic_furniture)
                .into(image)

            container.alpha = if (isSelected) 1.0f else 0.4f
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position == selectedPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_model_compact, parent, false)
        return ViewHolder(view)
    }
}

