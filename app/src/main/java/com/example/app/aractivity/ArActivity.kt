package com.example.app.aractivity

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.app.util.OpenGLVersionNotSupported
import com.example.app.util.PermissionResultEvent
import com.example.app.R
import com.example.app.util.UserCanceled
import com.example.app.arcore.ArCore
import com.example.app.data.repository.FavoritesRepository
import com.example.app.util.cameraPermissionRequestCode
import com.example.app.util.checkIfOpenGlVersionSupported
import com.example.app.databinding.ActivityArBinding
import com.example.app.filament.Filament
import com.example.app.gesture.DragGesture
import com.example.app.gesture.DragGestureRecognizer
import com.example.app.gesture.PinchGesture
import com.example.app.gesture.PinchGestureRecognizer
import com.example.app.gesture.TransformationSystem
import com.example.app.gesture.TwistGesture
import com.example.app.gesture.TwistGestureRecognizer
import com.example.app.model.GalleryEntry
import com.example.app.util.minOpenGlVersion
import com.example.app.renderer.FrameCallback
import com.example.app.renderer.LightRenderer
import com.example.app.renderer.ModelRenderer
import com.example.app.renderer.PlaneRenderer
import com.example.app.ui.gallery.adapter.FavoriteModelsAdapter
import com.example.app.util.showOpenGlNotSupportedDialog
import com.example.app.util.toRadians
import com.example.app.util.updateModelsManifest
import com.example.app.util.x
import com.example.app.util.y
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@AndroidEntryPoint
class ArActivity : AppCompatActivity() {

    private val resumeBehavior: MutableStateFlow<Unit?> =
        MutableStateFlow(null)
    private var selectedModel: GalleryEntry.Item? = null

    @Inject
    lateinit var favoritesRepository: FavoritesRepository

    private val requestPermissionResultEvents: MutableSharedFlow<PermissionResultEvent> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val configurationChangedEvents: MutableSharedFlow<Configuration> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val dragEvents: MutableSharedFlow<Pair<ViewRect, TouchEvent>> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val scaleEvents: MutableSharedFlow<Float> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val rotateEvents: MutableSharedFlow<Float> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val arTrackingEvents: MutableSharedFlow<Unit> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val arCoreBehavior: MutableStateFlow<Pair<ArCore, FrameCallback>?> =
        MutableStateFlow(null)

    private lateinit var transformationSystem: TransformationSystem

    private val createScope = CoroutineScope(Dispatchers.Main)
    private lateinit var startScope: CoroutineScope
    private lateinit var binding: ActivityArBinding

    private lateinit var modelRenderer: ModelRenderer


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        updateModelsManifest(this)
        binding = ActivityArBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.screenshotButton.setOnClickListener {
            takeArScreenshot()
        }

        val favoritesAdapter = FavoriteModelsAdapter()
        binding.favoritesRecycler.apply {
            layoutManager = LinearLayoutManager(this@ArActivity, RecyclerView.HORIZONTAL, false)
            adapter = favoritesAdapter
        }

        binding.backByActivity.setOnClickListener {
            finish()
        }
        // SnapHelper — центрирование
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(binding.favoritesRecycler)

        // Отслеживаем центральный айтем
        binding.favoritesRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val centerView = snapHelper.findSnapView(recyclerView.layoutManager)
                    centerView?.let {
                        val pos = recyclerView.getChildAdapterPosition(it)
                        favoritesAdapter.selectedPosition = pos
                        favoritesAdapter.notifyDataSetChanged()

                        selectedModel = favoritesAdapter.currentList.getOrNull(pos)
                    }
                }
            }
        })

        lifecycleScope.launch {
            favoritesRepository.getModelsWithFavoriteStatus().collect { entries ->
                val items = entries.filterIsInstance<GalleryEntry.Item>().filter { it.favorite }
                favoritesAdapter.submitList(items)

                // Обновить selectedModel если первый раз
                if (selectedModel == null && items.isNotEmpty()) {
                    selectedModel = items[0]
                    favoritesAdapter.selectedPosition = 0
                }
            }
        }



        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            findViewById<View>(android.R.id.content)!!.windowInsetsController!!
                .also { windowInsetsController ->
                    windowInsetsController.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                    windowInsetsController.hide(WindowInsets.Type.systemBars())
                }
        } else @Suppress("DEPRECATION") run {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility
                .or(View.SYSTEM_UI_FLAG_FULLSCREEN)       // hide status bar
                .or(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)  // hide navigation bar
                .or(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) // hide stat/nav bar after interaction timeout
        }

        transformationSystem = TransformationSystem(resources.displayMetrics)

        // pinch gesture events
        transformationSystem.pinchRecognizer.addOnGestureStartedListener(
            object : PinchGestureRecognizer.OnGestureStartedListener {
                override fun onGestureStarted(gesture: PinchGesture) {
                    update(gesture)

                    gesture.setGestureEventListener(
                        object : PinchGesture.OnGestureEventListener {
                            override fun onFinished(gesture: PinchGesture) {
                                update(gesture)
                            }

                            override fun onUpdated(gesture: PinchGesture) {
                                update(gesture)
                            }
                        },
                    )
                }

                fun update(gesture: PinchGesture) {
                    scaleEvents.tryEmit(1f + gesture.gapDeltaInches())
                }
            }
        )

        // twist gesture events
        transformationSystem.twistRecognizer.addOnGestureStartedListener(
            object : TwistGestureRecognizer.OnGestureStartedListener {
                override fun onGestureStarted(gesture: TwistGesture) {
                    update(gesture)

                    gesture.setGestureEventListener(
                        object : TwistGesture.OnGestureEventListener {
                            override fun onFinished(gesture: TwistGesture) {
                                update(gesture)
                            }

                            override fun onUpdated(gesture: TwistGesture) {
                                update(gesture)
                            }
                        },
                    )
                }

                fun update(gesture: TwistGesture) {
                    rotateEvents.tryEmit(-gesture.deltaRotationDegrees.toRadians)
                }
            }
        )



        // tap and gesture events


        createScope.launch {
            try {
                createUx()
            } catch (error: Throwable) {
                if (error !is UserCanceled) {
                    error.printStackTrace()
                }
            } finally {
                finish()
            }
        }
    }

    override fun onDestroy() {
        createScope.cancel()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        startScope = CoroutineScope(Dispatchers.Main)

        startScope.launch {
            try {
                startUx()
            } catch (error: Throwable) {
                if (error !is UserCanceled) {
                    error.printStackTrace()
                }

                finish()
            }
        }
    }

    override fun onStop() {
        startScope.cancel()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumeBehavior.tryEmit(Unit)
    }

    override fun onPause() {
        super.onPause()
        resumeBehavior.tryEmit(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationChangedEvents.tryEmit(newConfig)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        requestPermissionResultEvents.tryEmit(PermissionResultEvent(requestCode, grantResults))
    }


    private suspend fun createUx() {
        // wait for activity to resume
        resumeBehavior.filterNotNull().first()


        if (checkIfOpenGlVersionSupported(minOpenGlVersion).not()) {
            showOpenGlNotSupportedDialog(this@ArActivity)
            // finish()
            throw OpenGLVersionNotSupported
        }

        resumeBehavior.filterNotNull().first()

        // if arcore is not installed, request to install
        if (ArCoreApk
                .getInstance()
                .requestInstall(
                    this@ArActivity,
                    true,
                    ArCoreApk.InstallBehavior.REQUIRED,
                    ArCoreApk.UserMessageType.USER_ALREADY_INFORMED,
                ) == ArCoreApk.InstallStatus.INSTALL_REQUESTED
        ) {
            // make sure activity is paused before waiting for resume
            resumeBehavior.dropWhile { it != null }.filterNotNull().first()

            // check if install succeeded
            if (ArCoreApk
                    .getInstance()
                    .requestInstall(
                        this@ArActivity,
                        false,
                        ArCoreApk.InstallBehavior.REQUIRED,
                        ArCoreApk.UserMessageType.USER_ALREADY_INFORMED,
                    ) != ArCoreApk.InstallStatus.INSTALLED
            ) {
                throw UserCanceled
            }
        }

        // if permission is not granted, request permission
        if (ContextCompat.checkSelfPermission(
                this@ArActivity,
                Manifest.permission.CAMERA,
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            showCameraPermissionDialog(this@ArActivity)

            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                cameraPermissionRequestCode,
            )

            // check if permission was granted
            if (requestPermissionResultEvents
                    .filter { it.requestCode == cameraPermissionRequestCode }
                    .first()
                    .grantResults.any { it != PackageManager.PERMISSION_GRANTED }
            ) {
                throw UserCanceled
            }
        }

        // TODO: eliminate nesting of finally blocks
        val filament = Filament(this@ArActivity, binding.surfaceView)

        try {
            val arCore = ArCore(this@ArActivity, filament, binding.surfaceView)

            try {

                val lightRenderer = LightRenderer(this@ArActivity, arCore.filament)
                val planeRenderer = PlaneRenderer(this@ArActivity, arCore.filament)
                modelRenderer = ModelRenderer(this@ArActivity, arCore, arCore.filament)

                transformationSystem.dragRecognizer.addOnGestureStartedListener(
                    object : DragGestureRecognizer.OnGestureStartedListener {
                        override fun onGestureStarted(gesture: DragGesture) {
                            val rect = binding.surfaceView.toViewRect()
                            val sp = ScreenPosition(
                                x = gesture.position.x / rect.width,
                                y = gesture.position.y / rect.height
                            )
                            modelRenderer.pickInstanceByScreenRadius(sp, binding.surfaceView.width, binding.surfaceView.height)

                            Pair(rect, TouchEvent.Move(gesture.position.x, gesture.position.y))
                                .let { dragEvents.tryEmit(it) }

                            // c) слушатели обновлений/окончания
                            gesture.setGestureEventListener(
                                object : DragGesture.OnGestureEventListener {
                                    override fun onUpdated(gesture: DragGesture) {
                                        Pair(
                                            binding.surfaceView.toViewRect(),
                                            TouchEvent.Move(gesture.position.x, gesture.position.y)
                                        ).let { dragEvents.tryEmit(it) }
                                    }
                                    override fun onFinished(gesture: DragGesture) {
                                        Pair(
                                            binding.surfaceView.toViewRect(),
                                            TouchEvent.Stop(gesture.position.x, gesture.position.y)
                                        ).let { dragEvents.tryEmit(it) }
                                    }
                                }
                            )
                        }
                    }
                )

                binding.surfaceView.apply {
                    isClickable = true
                    setOnTouchListener { v, ev ->
                        if (ev.action == MotionEvent.ACTION_UP &&
                            ev.eventTime - ev.downTime < resources.getInteger(R.integer.tap_event_milliseconds)
                        ) {
                            v.performClick()
                            val sp = ScreenPosition(x = ev.x/width, y = ev.y/height)
                            selectedModel?.let { selected ->
                                modelRenderer.modelEvents.tryEmit(ModelRenderer.ModelEvent.Place(sp, selected.path))
                            }
                        }
                        transformationSystem.onTouch(ev)
                        true
                    }
                }




                try {
                    val frameCallback =
                        FrameCallback(
                            arCore,
                            doFrame = { frame ->
                                if (frame.getUpdatedTrackables(Plane::class.java)
                                        .any { it.trackingState == TrackingState.TRACKING }
                                ) {
                                    arTrackingEvents.tryEmit(Unit)
                                }

                                lightRenderer.doFrame(frame)
                                planeRenderer.doFrame(frame)
                                modelRenderer.doFrame(frame)
                            },
                        )

                    arCoreBehavior.emit(Pair(arCore, frameCallback))

                    with(CoroutineScope(coroutineContext)) {
                        launch {
                            configurationChangedEvents.collect { arCore.configurationChange() }
                        }

                        launch {
                            dragEvents
                                .map { (viewRect, touchEvent) ->
                                    ScreenPosition(
                                        x = touchEvent.x / viewRect.width,
                                        y = touchEvent.y / viewRect.height,
                                    )
                                        .let { ModelRenderer.ModelEvent.Move(it) }
                                }
                                .collect { modelRenderer.modelEvents.tryEmit(it) }
                        }

                        launch {
                            scaleEvents
                                .map { ModelRenderer.ModelEvent.Update(0f, it) }
                                .collect { modelRenderer.modelEvents.tryEmit(it) }
                        }

                        launch {
                            rotateEvents
                                .map { ModelRenderer.ModelEvent.Update(it, 1f) }
                                .collect { modelRenderer.modelEvents.tryEmit(it) }
                        }
                    }

                    awaitCancellation()
                } finally {
                    modelRenderer.destroy()
                }
            } finally {
                arCore.destroy()
            }
        } finally {
            filament.destroy()
        }
    }

    private suspend fun startUx() {
        val (arCore, frameCallback) = arCoreBehavior.filterNotNull().first()

        try {
            arCore.session.resume()
            frameCallback.start()

            coroutineScope {
                val job = launch(coroutineContext) {
                    TimeUnit.SECONDS
                        .toMillis(
                            resources
                                .getInteger(R.integer.show_hand_motion_timeout_seconds)
                                .toLong(),
                        )
                        .let { delay(it) }

                    binding.handMotionContainer.isVisible = true
                }

                launch(coroutineContext) {
                    arTrackingEvents.first()
                    job.cancel()
                    binding.handMotionContainer.isVisible = false
                }
            }

            awaitCancellation()
        } finally {
            binding.handMotionContainer.isVisible = false
            frameCallback.stop()
            arCore.session.pause()
        }
    }

    private suspend fun showCameraPermissionDialog(activity: AppCompatActivity) {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            suspendCancellableCoroutine { continuation ->
                val alertDialog = AlertDialog
                    .Builder(activity)
                    .setTitle(R.string.camera_permission_title)
                    .setMessage(R.string.camera_permission_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        continuation.resume(Unit)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        continuation.resumeWithException(UserCanceled)
                    }
                    .setCancelable(false)
                    .show()

                continuation.invokeOnCancellation { alertDialog.dismiss() }
            }
        }
    }

    private fun takeArScreenshot() {
        // 1) Скрываем UI
        //binding.favoriteRecyclerView.isVisible = false
        binding.screenshotButton.isVisible = false

        // 2) Создаём bitmap
        val view = binding.surfaceView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

        // 3) Запрос PixelCopy
        PixelCopy.request(view, bmp, { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                // 4) Сохраняем в галерею
                saveBitmapToGallery(bmp)
            } else {
                Toast.makeText(this, "Снимок не удался: $copyResult", Toast.LENGTH_SHORT).show()
            }
            // 5) Восстанавливаем UI
            //binding.favoriteRecyclerView.isVisible = true
            binding.screenshotButton.isVisible = true
        }, Handler(Looper.getMainLooper()))
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "AR_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ARApp")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )

        uri?.let {
            contentResolver.openOutputStream(it).use { out: OutputStream? ->
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(it, values, null, null)
            Toast.makeText(this, "Снимок сохранён: $filename", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this, "Не удалось сохранить снимок", Toast.LENGTH_SHORT).show()
        }
    }

}
