package com.example.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView

class TouchSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
