package app.stride.music.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.stride.music.domain.Args
import app.stride.music.model.DlStatus
import app.stride.music.model.DownloadItem
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The UI's link to [PlaybackService]; the fields are Compose state, so the player redraws by itself. */
object Playback {
    private var future: ListenableFuture<MediaController>? = null
    private var controller by mutableStateOf<MediaController?>(null)
    private var pending: ((MediaController) -> Unit)? = null

    var hasItem by mutableStateOf(false); private set
    var isPlaying by mutableStateOf(false); private set
    var title by mutableStateOf(""); private set
    var artist by mutableStateOf<String?>(null); private set
    var artwork by mutableStateOf<String?>(null); private set
    var duration by mutableLongStateOf(0L); private set
    var speed by mutableFloatStateOf(1f); private set
    var hasNext by mutableStateOf(false); private set
    var hasPrevious by mutableStateOf(false); private set
    /** "<download id>:<file index>" of the current track. */
    var currentId by mutableStateOf<String?>(null); private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    fun connect(ctx: Context) {
        if (future != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            sync(c)
            pending?.invoke(c)
            pending = null
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun disconnect() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun sync(p: Player) {
        hasItem = p.mediaItemCount > 0
        isPlaying = p.isPlaying
        val md = p.mediaMetadata
        title = md.title?.toString().orEmpty()
        artist = md.artist?.toString()
        artwork = md.artworkUri?.toString()
        duration = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        speed = p.playbackParameters.speed
        hasNext = p.hasNextMediaItem()
        hasPrevious = p.hasPreviousMediaItem()
        currentId = p.currentMediaItem?.mediaId
    }

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block) ?: run { pending = block }
    }

    val position: Long get() = controller?.currentPosition ?: 0L

    fun play(items: List<MediaItem>, start: Int) = withController {
        it.setMediaItems(items, start, 0L)
        it.prepare()
        it.play()
    }

    fun toggle() = withController {
        if (it.isPlaying) it.pause() else {
            if (it.playbackState == Player.STATE_ENDED) it.seekTo(0L)
            it.play()
        }
    }

    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun next() = withController { it.seekToNextMediaItem() }

    /** Like the iPhone: after 3 s, back restarts the song; otherwise it goes to the previous one. */
    fun previous() = withController { it.seekToPrevious() }

    fun cycleSpeed() = withController {
        val steps = listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 0.75f)
        val i = steps.indexOfFirst { s -> s == it.playbackParameters.speed }
        it.setPlaybackSpeed(steps[(i + 1) % steps.size])
    }

    fun stop() = withController {
        it.stop()
        it.clearMediaItems()
    }

    fun playable(item: DownloadItem): Boolean = item.status == DlStatus.DONE && item.outputs.isNotEmpty() && item.options.audioOnly

    /** Every downloaded audio item as one queue, starting at the chosen one. */
    suspend fun playFrom(ctx: Context, rows: List<DownloadItem>, startId: Long) {
        val (items, start) = withContext(Dispatchers.IO) {
            val out = mutableListOf<MediaItem>()
            var start = 0
            rows.filter(::playable).forEach { row ->
                if (row.id == startId) start = out.size
                row.outputs.forEachIndexed { i, s ->
                    val uri = Uri.parse(s)
                    val name = if (row.outputs.size > 1) displayName(ctx, uri) else null
                    out += MediaItem.Builder()
                        .setMediaId("${row.id}:$i")
                        .setUri(uri)
                        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(name ?: row.title.ifBlank { row.url })
                                .setArtist(row.uploader)
                                .setArtworkUri(row.thumbnail?.let(Uri::parse))
                                .build(),
                        )
                        .build()
                }
            }
            out to start
        }
        if (items.isNotEmpty()) play(items, start)
    }

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0)?.let(Args::displayName) else null
        }
    }.getOrNull()
}
