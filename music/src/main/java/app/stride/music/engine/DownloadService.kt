package app.stride.music.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.stride.core.R
import app.stride.host.Host
import app.stride.music.data.MusicRepo
import app.stride.music.domain.Args
import app.stride.music.model.DlOptions
import app.stride.music.model.DlStatus
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/** Jobs running now, so the UI can cancel them. */
object Registry {
    val jobs = ConcurrentHashMap<Long, Job>()

    fun cancel(id: Long) {
        MusicRepo.cancel(id)
        YoutubeDL.destroyProcessById("dl-$id")
        YoutubeDL.destroyProcessById("info-$id")
        jobs[id]?.cancel()
    }
}

/** Runs the download queue in the foreground (a few at a time), saves the files and reports progress. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pumpLock = Mutex()
    private lateinit var sem: Semaphore
    private var wake: PowerManager.WakeLock? = null
    private var lastNotif = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        MusicRepo.init(this)
        sem = Semaphore(MusicRepo.data.value.settings.concurrency.coerceIn(1, 4))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, progressNotif(this, "Getting ready…", null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (intent?.action == ACTION_CANCEL_ALL) Registry.jobs.keys.toList().forEach(Registry::cancel)
        scope.launch { pump() }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWake()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun pump() = pumpLock.withLock {
        val ready = Engine.ready.await()
        if (ready.isFailure) {
            val msg = "yt-dlp didn't start: ${ready.exceptionOrNull()?.message}"
            MusicRepo.queued().forEach { MusicRepo.setStatus(it.id, DlStatus.FAILED, msg) }
            stopIfIdle()
            return@withLock
        }
        if (Registry.jobs.isEmpty()) MusicRepo.resetStuck() // left over after the process was killed
        acquireWake()
        MusicRepo.queued().forEach { item ->
            if (Registry.jobs.containsKey(item.id)) return@forEach
            val job = scope.launch(start = CoroutineStart.LAZY) { sem.withPermit { process(item.id) } }
            Registry.jobs[item.id] = job
            job.invokeOnCompletion {
                Registry.jobs.remove(item.id)
                refreshNotif()
                scope.launch { pumpLock.withLock { stopIfIdle() } }
            }
            job.start()
        }
        stopIfIdle()
    }

    private fun stopIfIdle() {
        if (Registry.jobs.isEmpty() && MusicRepo.queued().isEmpty()) {
            releaseWake()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun process(id: Long) {
        val item = MusicRepo.get(id) ?: return
        if (item.status != DlStatus.QUEUED) return
        val o: DlOptions = item.options
        MusicRepo.setStatus(id, DlStatus.FETCHING, null)
        Engine.fetchInfo(item.url, o, "info-$id")?.let { MusicRepo.setInfo(id, it.title, it.thumbnail, it.uploader ?: it.channel) }
        if (MusicRepo.get(id)?.status == DlStatus.CANCELED) return

        MusicRepo.setStatus(id, DlStatus.RUNNING, null)
        val tmp = File(cacheDir, "music/$id").apply { deleteRecursively(); mkdirs() }
        var note: String? = null
        try {
            try {
                Engine.download(item.url, o, tmp, "dl-$id") { p, eta, line -> onProgress(id, p, eta, line) }
            } catch (e: YoutubeDL.CanceledException) {
                throw e
            } catch (e: Exception) {
                // a playlist keeps what worked
                if (!(o.playlist && Engine.outputs(tmp).isNotEmpty())) throw e
                note = "Some items failed"
            }
            val files = Engine.outputs(tmp)
            if (files.isEmpty()) error("No file was made")
            val uris = files.map { Engine.save(this, it, MusicRepo.data.value.settings.saveToDownloads).toString() }
            MusicRepo.setDone(id, uris, note)
            doneNotif(this, id, MusicRepo.get(id)?.title ?: item.url, ok = true, detail = null)
        } catch (e: YoutubeDL.CanceledException) {
            MusicRepo.cancel(id)
        } catch (e: CancellationException) {
            MusicRepo.cancel(id)
            throw e
        } catch (t: Throwable) {
            val msg = t.message?.lines()?.lastOrNull { it.isNotBlank() }?.take(300) ?: t.javaClass.simpleName
            MusicRepo.setStatus(id, DlStatus.FAILED, msg)
            doneNotif(this, id, item.url, ok = false, detail = msg)
        } finally {
            tmp.deleteRecursively()
            ProgressBus.remove(id)
        }
    }

    private fun onProgress(id: Long, p: Float, eta: Long, line: String) {
        ProgressBus.set(id, Prog(p, eta, line))
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotif > 1000) {
            lastNotif = now
            refreshNotif()
        }
    }

    private fun refreshNotif() {
        val n = Registry.jobs.size
        val pct = ProgressBus.map.value.values.firstOrNull()?.pct?.takeIf { it >= 0 }?.toInt()
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, progressNotif(this, if (n <= 1) "Downloading" else "$n downloads", pct))
        }
    }

    private fun acquireWake() {
        if (wake?.isHeld == true) return
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hive:music-download").apply { acquire(2 * 60 * 60 * 1000L) }
    }

    private fun releaseWake() {
        runCatching { if (wake?.isHeld == true) wake?.release() }
    }

    companion object {
        const val NOTIF_ID = 4242
        const val ACTION_CANCEL_ALL = "app.stride.music.CANCEL_ALL"
        private const val CH_PROGRESS = "music_progress"
        private const val CH_DONE = "music_done"

        fun channels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_PROGRESS, "Music downloads in progress", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CH_DONE, "Music downloads finished", NotificationManager.IMPORTANCE_DEFAULT))
        }

        private fun open(ctx: Context): PendingIntent =
            PendingIntent.getActivity(ctx, 77, Host.openIntent(ctx, "music"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun progressNotif(ctx: Context, text: String, pct: Int?): Notification {
            val cancelAll = PendingIntent.getService(
                ctx, 1, Intent(ctx, DownloadService::class.java).setAction(ACTION_CANCEL_ALL),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return Notification.Builder(ctx, CH_PROGRESS)
                .setSmallIcon(R.drawable.ic_stat_hive)
                .setContentTitle("Hive Music")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open(ctx))
                .setProgress(100, pct ?: 0, pct == null)
                .addAction(Notification.Action.Builder(null, "Cancel all", cancelAll).build())
                .build()
        }

        fun doneNotif(ctx: Context, id: Long, title: String, ok: Boolean, detail: String?) {
            val text = if (ok) title else (detail ?: title)
            val n = Notification.Builder(ctx, CH_DONE)
                .setSmallIcon(R.drawable.ic_stat_hive)
                .setContentTitle(if (ok) "Downloaded" else "Download failed")
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(open(ctx))
                .build()
            runCatching { ctx.getSystemService(NotificationManager::class.java).notify(5000 + id.toInt(), n) }
        }

        /** Adds a download to the queue and starts the service. */
        fun enqueue(ctx: Context, url: String, o: DlOptions): Long {
            MusicRepo.init(ctx)
            val id = MusicRepo.insert(url, o, Args.summary(o))
            kick(ctx)
            return id
        }

        fun kick(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
        }
    }
}
