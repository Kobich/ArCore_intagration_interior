package com.example.app.renderer

import android.view.Choreographer
import com.example.app.arcore.ArCore
import com.google.ar.core.Frame
import java.util.concurrent.TimeUnit

class FrameCallback(
    private val arCore: ArCore,
    private val doFrame: (frame: Frame) -> Unit,
) : Choreographer.FrameCallback {
    companion object {
        private const val MAX_FRAMES_PER_SECOND: Long = 30
    }

    @Suppress("unused")
    enum class FrameRate(val factor: Long) {
        Full(1),
        Half(2),
        Third(3),
    }

    private val choreographer: Choreographer = Choreographer.getInstance()
    private var lastTick: Long = 0
    private var frameRate: FrameRate = FrameRate.Full

    override fun doFrame(frameTimeNanos: Long) {
        choreographer.postFrameCallback(this)

        // limit to max fps
        val nanoTime = System.nanoTime()
        val tick = nanoTime / (TimeUnit.SECONDS.toNanos(1) / MAX_FRAMES_PER_SECOND)

        if (lastTick / frameRate.factor == tick / frameRate.factor) {
            return
        }

        lastTick = tick

        // 1. Получить новый кадр
        val frame = arCore.session.update()

        // 2. Если кадр новый — обновить ArCore и вызвать doFrame
        if (frame.timestamp != 0L &&
            frame.timestamp != arCore.timestamp
        ) {
            arCore.timestamp = frame.timestamp
            arCore.update(frame, arCore.filament)
            doFrame(frame)
        }

        // 3. Рендер текущего состояния
        if (
            arCore.timestamp != 0L &&
            arCore.filament.uiHelper.isReadyToRender &&
            arCore.filament.renderer.beginFrame(arCore.filament.swapChain!!, frameTimeNanos)
        ) {
            arCore.filament.timestamp = arCore.timestamp
            arCore.filament.renderer.render(arCore.filament.view)
            arCore.filament.renderer.endFrame()
        }
    }

    fun start() {
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        choreographer.removeFrameCallback(this)
    }
}
