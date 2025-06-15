package com.example.app.renderer

import android.content.Context
import android.opengl.Matrix
import com.example.app.util.V3
import com.example.app.aractivity.ScreenPosition
import com.example.app.arcore.ArCore
import com.example.app.util.clampToTau
import com.example.app.filament.Filament
import com.example.app.util.m4Identity
import com.example.app.util.rotate
import com.example.app.util.scale
import com.example.app.util.toDegrees
import com.example.app.util.translate
import com.example.app.util.x
import com.example.app.util.y
import com.example.app.util.z
import com.google.android.filament.gltfio.FilamentAsset
import com.google.ar.core.Frame
import com.google.ar.core.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class ModelRenderer(private val context: Context, private val arCore: ArCore, private val filament: Filament) {
    sealed class ModelEvent {
        data class Move(val screenPosition: ScreenPosition) : ModelEvent()
        data class Update(val rotate: Float, val scale: Float) : ModelEvent()
        data class Place(val screenPosition: ScreenPosition, val modelPath: String) : ModelEvent()
    }

    private val modelBytesCache = mutableMapOf<String, ByteArray>()

    private suspend fun getModelBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        modelBytesCache[path] ?: run {
            val bytes = if (path.startsWith("/")) {
                File(path).inputStream().use { it.readBytes() }
            } else {
                context.assets.open(path).use { it.readBytes() }
            }

            modelBytesCache[path] = bytes
            bytes
        }
    }

    // 2) Список инстансов и описатель
    private data class Instance(
        val asset: FilamentAsset,
        var translation: V3,
        var rotate: Float,
        var scale: Float,

        val boundCenter: V3,
        val boundRadius: Float,

        var lastTransformMatrix: FloatArray? = null,
        var lastTransformMatrixUpdateTime: Long? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Instance

            if (asset != other.asset) return false
            if (translation != other.translation) return false
            if (rotate != other.rotate) return false
            if (scale != other.scale) return false
            if (boundCenter != other.boundCenter) return false
            if (boundRadius != other.boundRadius) return false
            if (lastTransformMatrix != null) {
                if (other.lastTransformMatrix == null) return false
                if (!lastTransformMatrix.contentEquals(other.lastTransformMatrix)) return false
            } else if (other.lastTransformMatrix != null) return false
            if (lastTransformMatrixUpdateTime != other.lastTransformMatrixUpdateTime) return false

            return true
        }

        override fun hashCode(): Int {
            var result = asset.hashCode()
            result = 31 * result + translation.hashCode()
            result = 31 * result + rotate.hashCode()
            result = 31 * result + scale.hashCode()
            result = 31 * result + boundCenter.hashCode()
            result = 31 * result + boundRadius.hashCode()
            result = 31 * result + (lastTransformMatrix?.contentHashCode() ?: 0)
            result = 31 * result + (lastTransformMatrixUpdateTime?.hashCode() ?: 0)
            return result
        }
    }

    private val instances = mutableListOf<Instance>()
    // индекс «текущего» (последнего) инстанса

    private var selectedIndex: Int = -1

    private val maxInstances = 3

    val modelEvents: MutableSharedFlow<ModelEvent> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val doFrameEvents: MutableSharedFlow<Frame> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val coroutineScope: CoroutineScope =
        CoroutineScope(Dispatchers.Main)

    init {
        coroutineScope.launch {

            // A) Place — на каждый tap создаём новый экземпляр
            launch {
                modelEvents.filterIsInstance<ModelEvent.Place>()
                    .mapNotNull { ev ->
                        arCore.frame.hitTest(
                            filament.surfaceView.width * ev.screenPosition.x,
                            filament.surfaceView.height * ev.screenPosition.y
                        ).maxByOrNull { it.trackable is Point }?.let { hit ->
                            Pair(ev.modelPath, V3(hit.hitPose.translation).apply { y += 0.005f })
                        }
                    }
                    .collect { (modelPath, pos) ->
                        // 1) получить байты из кеша или assets
                        val bytes = getModelBytes(modelPath)
                        // 2) создать FilamentAsset
                        val asset = withContext(Dispatchers.IO) {
                            filament.assetLoader.createAsset(ByteBuffer.wrap(bytes))!!
                        }
                        filament.resourceLoader.loadResources(asset)

                        val bbox       = asset.boundingBox
                        val centerArr  = bbox.center
                        val halfArr    = bbox.halfExtent
                        val scale = 0.75f
                        val radius     = sqrt(
                            halfArr[0]*halfArr[0] +
                                    halfArr[1]*halfArr[1] +
                                    halfArr[2]*halfArr[2]
                        )
                        val minY = centerArr[1] - halfArr[1]
                        val yOffset = -minY* scale
                        val placePos = pos.copy()
                        placePos.y += yOffset

                        filament.scene.addEntities(asset.entities)
                        instances += Instance(
                            asset       = asset,
                            translation = placePos,
                            rotate      = 0f,
                            scale       = scale,
                            boundCenter = V3(floatArrayOf(centerArr[0],centerArr[1],centerArr[2])),
                            boundRadius = radius,
                        )
                        // --- Ограничение количества моделей ---
                        while (instances.size > maxInstances) {
                            val removed = instances.removeAt(0)
                            filament.scene.removeEntities(removed.asset.entities)
                            // TODO: destroy FilamentAsset полностью при необходимости
                        }
                        selectedIndex = instances.lastIndex
                    }
            }

            // B) Move — перемещаем последний добавленный
            launch {
                modelEvents.filterIsInstance<ModelEvent.Move>()
                    .mapNotNull { ev ->
                        arCore.frame.hitTest(
                            filament.surfaceView.width * ev.screenPosition.x,
                            filament.surfaceView.height * ev.screenPosition.y
                        ).maxByOrNull { it.trackable is Point }
                    }
                    .map { hit ->
                        V3(hit.hitPose.translation).apply { y += 0.002f }
                    }
                    .collect { newPos ->
                        instances.getOrNull(selectedIndex)?.let { inst ->
                            val bbox = inst.asset.boundingBox
                            val centerArr = bbox.center
                            val halfArr = bbox.halfExtent
                            val minY = centerArr[1] - halfArr[1]
                            val yOffset = -minY*inst.scale

                            val correctedPos = newPos.copy()
                            correctedPos.y += yOffset

                            inst.translation = correctedPos
                        }
                    }
            }

            // C) Update — вращение/масштаб для выбранного
            launch {
                modelEvents.filterIsInstance<ModelEvent.Update>()
                    .collect { ev ->
                        instances.getOrNull(selectedIndex)?.let {
                            it.rotate = (it.rotate + ev.rotate).clampToTau
                            it.scale *= ev.scale
                        }
                    }
            }

            // D) Рендер каждый кадр всех инстансов (оптимизированный: setTransform() только при изменении)
            launch {
                doFrameEvents.collect { frame ->
                    instances.forEach { inst ->
                        // --- Отключаем анимацию, если у модели нет ---
                        val animator = inst.asset.instance.animator
                        if (animator.animationCount > 0) {
                            animator.applyAnimation(
                                0,
                                (frame.timestamp / TimeUnit.SECONDS.toNanos(1).toDouble()).toFloat() %
                                        animator.getAnimationDuration(0)
                            )
                            animator.updateBoneMatrices()
                        }
                        // --- Если модель статична, не обновлять transform чаще 30 Гц ---
                        val now = System.currentTimeMillis()
                        if (inst.lastTransformMatrix != null && now - (inst.lastTransformMatrixUpdateTime ?: 0) < 33) return@forEach
                        val newMatrix = m4Identity()
                            .translate(inst.translation.x, inst.translation.y, inst.translation.z)
                            .rotate(inst.rotate.toDegrees, 0f, 1f, 0f)
                            .scale(inst.scale, inst.scale, inst.scale)
                            .floatArray
                        if (inst.lastTransformMatrix == null || !inst.lastTransformMatrix!!.contentEquals(newMatrix)) {
                            val tm = filament.engine.transformManager
                            val ti = tm.getInstance(inst.asset.root)
                            tm.setTransform(ti, newMatrix)
                            inst.lastTransformMatrix = newMatrix
                            inst.lastTransformMatrixUpdateTime = now
                        }
                    }
                }
            }

        }
    }

    fun destroy() {
        coroutineScope.cancel()
    }

    fun doFrame(frame: Frame) {
        doFrameEvents.tryEmit(frame)
    }

    fun pickInstanceByScreenRadius(
        screenPos: ScreenPosition,
        viewWidth: Int,
        viewHeight: Int
    ): Boolean {
        if (instances.isEmpty()) return false

        // 1) Считаем PV‑матрицу
        val frame = arCore.frame
        val cam   = frame.camera
        val proj  = FloatArray(16).also { cam.getProjectionMatrix(it, 0, 0.1f, 100f) }
        val viewM = FloatArray(16).also { cam.getViewMatrix(it, 0) }
        val pv    = FloatArray(16).also { Matrix.multiplyMM(it, 0, proj, 0, viewM, 0) }

        // 2) Инвертируем view‑матрицу, чтобы получить вектор “право” камеры
        val viewInv = FloatArray(16)
        Matrix.invertM(viewInv, 0, viewM, 0)
        val camRight4 = FloatArray(4)
        // вектор (1,0,0,0) в камере → мировому пространству
        Matrix.multiplyMV(camRight4, 0, viewInv, 0, floatArrayOf(1f,0f,0f,0f), 0)
        val camRight = V3(floatArrayOf(camRight4[0], camRight4[1], camRight4[2]))

        var bestIdx   = 0
        var bestDist2 = Float.MAX_VALUE
        val tmp       = FloatArray(4)

        instances.forEachIndexed { idx, inst ->
            // 3) мировой центр объекта
            val cx = inst.translation.x + inst.boundCenter.x * inst.scale
            val cy = inst.translation.y + inst.boundCenter.y * inst.scale
            val cz = inst.translation.z + inst.boundCenter.z * inst.scale

            // 4) вычисляем экранный центр
            Matrix.multiplyMV(tmp, 0, pv, 0, floatArrayOf(cx, cy, cz, 1f), 0)
            if (tmp[3] == 0f) return@forEachIndexed
            val ndcX = tmp[0]/tmp[3]
            val ndcY = tmp[1]/tmp[3]
            val scrX = (ndcX * .5f + .5f) * viewWidth
            val scrY = (1f - (ndcY * .5f + .5f)) * viewHeight

            // 5) вычисляем экранный радиус сферы
            val rad = inst.boundRadius * inst.scale
            // точка на краю: center + camRight * rad
            val ex = cx + camRight.x * rad
            val ey = cy + camRight.y * rad
            val ez = cz + camRight.z * rad
            Matrix.multiplyMV(tmp, 0, pv, 0, floatArrayOf(ex, ey, ez, 1f), 0)
            if (tmp[3] == 0f) return@forEachIndexed
            val ndcXe = tmp[0]/tmp[3]
            val ndcYe = tmp[1]/tmp[3]
            val edgeX  = (ndcXe * .5f + .5f) * viewWidth
            val edgeY  = (1f - (ndcYe * .5f + .5f)) * viewHeight
            val radius2 = (edgeX - scrX).let { dx -> dx*dx } +
                    (edgeY - scrY).let { dy -> dy*dy }

            // 6) сравниваем расстояние до тап‑точки
            val dx = scrX - screenPos.x * viewWidth
            val dy = scrY - screenPos.y * viewHeight
            val d2 = dx*dx + dy*dy

            // 7) выбираем ближайший объект, по которому тапнут _внутри_ его radius
            if (d2 <= radius2 && d2 < bestDist2) {
                bestDist2 = d2
                bestIdx   = idx
            }
        }

        selectedIndex = bestIdx
        return true
    }
}
