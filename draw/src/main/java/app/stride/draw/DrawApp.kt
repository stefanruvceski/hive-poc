package app.stride.draw

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.stride.host.OpenRequest
import app.stride.ui.theme.StrideColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.tan

private const val SMOOTHING = 0.08f

/**
 * Drawify inside Hive: an image laid semi-transparently over the camera so you can trace it. Drag,
 * pinch and rotate it; tilt correction from the gravity sensor or four draggable corners make it
 * sit flat on the paper. The camera view and the overlay are the original Android views.
 */
@Composable
fun DrawApp(open: OpenRequest, onHome: (() -> Unit)?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("draw", Context.MODE_PRIVATE) }
    val previewView = remember { PreviewView(context) }
    val overlay = remember { OverlayView(context) }

    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var hasImage by remember { mutableStateOf(false) }
    var opacity by remember { mutableFloatStateOf(prefs.getInt("opacity", 50) / 100f) }
    var filter by remember { mutableStateOf(ImageFilter.entries.getOrElse(prefs.getInt("filter", 0)) { ImageFilter.ORIGINAL }) }
    var locked by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(true) }
    var tiltOn by remember { mutableStateOf(false) }
    var corners by remember { mutableStateOf(false) }
    var tiltLabel by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    val lens = remember { FloatArray(2) } // focal length mm, sensor long side mm
    var filterJob by remember { mutableStateOf<Job?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun applyFilter(fit: Boolean) {
        val src = source ?: return
        val f = filter
        filterJob?.cancel()
        filterJob = scope.launch {
            val out = withContext(Dispatchers.Default) { ImageFilters.apply(src, f) }
            overlay.setBitmap(out, fit)
            hasImage = true
        }
    }

    fun setCorners(on: Boolean) {
        if (on && !overlay.hasImage) return
        if (on && tiltOn) {
            tiltOn = false
            tiltLabel = null
        }
        overlay.cornerMode = on
        corners = overlay.cornerMode
        if (corners) {
            overlay.locked = false
            locked = false
            toast("Drag the 4 corners onto the paper corners")
        }
    }

    fun loadImage(uri: Uri) {
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { BitmapLoader.load(context, uri).also { BitmapLoader.saveLast(context, it) } }.getOrNull()
            }
            if (bmp == null) toast("Could not load the image") else {
                setCorners(false)
                if (!tiltOn) overlay.clearPerspective()
                source = bmp
                applyFilter(fit = true)
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) loadImage(uri) }
    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; denied = !ok }
    fun openPicker() = pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // Shared from another app ("draw/share:<uri>"), else the last image.
    LaunchedEffect(open.id) {
        if (open.target.startsWith("share:")) loadImage(Uri.parse(open.target.removePrefix("share:")))
        else if (source == null) {
            val last = withContext(Dispatchers.IO) { BitmapLoader.loadLast(context) }
            if (last != null && source == null) { source = last; applyFilter(fit = true) }
        }
    }
    LaunchedEffect(Unit) {
        overlay.opacity = opacity
        if (!granted) requestCamera.launch(Manifest.permission.CAMERA)
    }

    // Keep the screen on while tracing.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Camera: back camera preview, bound to the screen's lifecycle; released when leaving Drawify.
    DisposableEffect(granted) {
        var provider: ProcessCameraProvider? = null
        if (granted) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching {
                    val p = future.get()
                    provider = p
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    p.unbindAll()
                    val cam = p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                    camera = cam
                    torch = false
                    readLens(cam, lens)
                }.onFailure { toast("Could not start the camera") }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose { provider?.unbindAll(); camera = null }
    }

    // The camera closes in the background, which switches the torch off.
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) torch = false }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Tilt correction from the gravity sensor (smoothed), while on and not locked.
    DisposableEffect(tiltOn) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gravity = FloatArray(3)
        var has = false
        val m = Matrix()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!tiltOn || overlay.locked) return
                if (!has) { event.values.copyInto(gravity, 0, 0, 3); has = true }
                else for (i in 0 until 3) gravity[i] += SMOOTHING * (event.values[i] - gravity[i])
                val w = overlay.width
                val h = overlay.height
                if (w == 0 || h == 0) return
                val deg = TiltPerspective.tiltDegrees(gravity).roundToInt()
                if (TiltPerspective.compute(gravity, w, h, focalPx(w, h, lens), m)) {
                    tiltLabel = "Tilt $deg°"
                    overlay.setPerspective(m)
                } else tiltLabel = "Too steep ($deg°)"
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        if (tiltOn && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    fun setTilt(on: Boolean) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (on && sm.getDefaultSensor(Sensor.TYPE_GRAVITY) == null && sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) {
            toast("This phone has no motion sensor for tilt correction")
            return
        }
        tiltOn = on
        if (on) {
            setCorners(false)
            toast("Tilt correction on: lay the paper flat on the table and aim the phone at it")
        } else {
            tiltLabel = null
            overlay.clearPerspective()
        }
        // The old placement was made under a different perspective, so start fresh.
        overlay.resetPosition()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) AndroidView({ previewView }, Modifier.fillMaxSize())
        AndroidView({ overlay }, Modifier.fillMaxSize(), update = { it.visibility = if (shown) View.VISIBLE else View.INVISIBLE })

        if (denied || !hasImage) {
            Text(
                if (denied) "Camera permission is required.\nTap here to try again." else "Pick an image from your gallery\n(tap “Image” below)",
                color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp).clip(RoundedCornerShape(18.dp))
                    .background(StrideColors.Elev.copy(alpha = 0.9f))
                    .clickable { if (denied) requestCamera.launch(Manifest.permission.CAMERA) else openPicker() }.padding(16.dp),
            )
        }

        // Top bar over a soft scrim.
        Row(
            Modifier.fillMaxWidth().background(GradientBrush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onHome != null) RoundIcon(Icons.Filled.Apps, "All apps") { onHome() }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Drawify", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                tiltLabel?.let { Text(it, color = StrideColors.Amber, fontSize = 13.sp) }
            }
            RoundIcon(if (shown) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "Show or hide image") { shown = !shown }
            RoundIcon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, "Flashlight", enabled = camera?.cameraInfo?.hasFlashUnit() == true) {
                camera?.let { torch = !torch; it.cameraControl.enableTorch(torch) }
            }
            RoundIcon(if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen, "Lock") {
                overlay.locked = !overlay.locked
                locked = overlay.locked
                toast(if (locked) "Image locked: moving disabled" else "Image unlocked: drag, pinch to zoom and rotate")
            }
        }

        // Bottom panel: opacity and tools.
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(StrideColors.Elev.copy(alpha = 0.92f)).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Opacity", color = Color.White, fontSize = 14.sp)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it; overlay.opacity = it },
                    onValueChangeFinished = { prefs.edit().putInt("opacity", (opacity * 100).roundToInt()).apply() },
                    colors = SliderDefaults.colors(thumbColor = StrideColors.Amber, activeTrackColor = StrideColors.Amber),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("${(opacity * 100).roundToInt()}%", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Tool(Icons.Filled.Image, "Image", false) { openPicker() }
                Tool(Icons.Filled.Brush, filter.label, filter != ImageFilter.ORIGINAL) {
                    filter = ImageFilter.entries[(filter.ordinal + 1) % ImageFilter.entries.size]
                    prefs.edit().putInt("filter", filter.ordinal).apply()
                    applyFilter(fit = false)
                }
                Tool(Icons.Filled.ScreenRotation, "Tilt", tiltOn) { setTilt(!tiltOn) }
                Tool(Icons.Filled.CropFree, "Corners", corners) { setCorners(!corners) }
                Tool(Icons.Filled.Flip, "Mirror", false) { overlay.flipHorizontal() }
                Tool(Icons.Filled.CenterFocusStrong, "Reset", false) {
                    setCorners(false)
                    if (!tiltOn) overlay.clearPerspective()
                    overlay.resetPosition()
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 6.dp).size(42.dp).alpha(if (enabled) 1f else 0.4f).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White) }
}

@Composable
private fun Tool(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) StrideColors.Amber else Color.White
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = color)
        Spacer(Modifier.height(2.dp))
        Text(label, color = color, fontSize = 11.sp, maxLines = 1)
    }
}

@OptIn(ExperimentalCamera2Interop::class)
private fun readLens(cam: Camera, out: FloatArray) {
    runCatching {
        val info = Camera2CameraInfo.from(cam.cameraInfo)
        val focal = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val size = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (focal != null && focal.isNotEmpty() && size != null) {
            out[0] = focal[0]
            out[1] = max(size.width, size.height)
        }
    }
}

/** Camera focal length in screen pixels, for a 4:3 preview filling the view. */
private fun focalPx(viewW: Int, viewH: Int, lens: FloatArray): Float {
    val longView = max(viewH.toFloat(), viewW * 4f / 3f)
    return if (lens[0] > 0f && lens[1] > 0f) lens[0] / lens[1] * longView
    // Typical phone main camera: ~70° field of view across the long side.
    else longView / 2f / tan(Math.toRadians(35.0)).toFloat()
}
