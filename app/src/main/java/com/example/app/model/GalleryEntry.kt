package com.example.app.model

sealed class GalleryEntry {
    data class Category(val name: String) : GalleryEntry()
    data class Item(
        val id: String,
        val displayName: String,
        val path: String,
        val preview: String
    ) : GalleryEntry()
}