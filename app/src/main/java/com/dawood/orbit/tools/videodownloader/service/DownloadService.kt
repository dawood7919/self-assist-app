package com.dawood.orbit.tools.videodownloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.dawood.orbit.MainActivity
import com.dawood.orbit.R
import com.dawood.orbit.tools.videodownloader.data.DownloadRepository
import com.dawood.orbit.tools.videodownloader.engine.DownloadEngine
import com.dawood.orbit.tools.videodownloader.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps downloads running when the app is not on screen.
 *
 * Android will kill background work within seconds, so a long transfer has to
 * be a foreground service with a visible notification. Pausing is implemented
 * as cancelling the coroutine — the partial file stays on disk and the next
 * start picks up from its length.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val slots = Semaphore(MAX_CONCURRENT)

    private lateinit var repository: DownloadRepository
    private lateinit var engine: DownloadEngine
    private var notificationLoop: Job? = null

    override fun onCreate() {
        super.onCreate()
        repository = DownloadRepository.get(this)
        engine = DownloadEngine(applicationContext, repository)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID)

        // The foreground notification has to be posted almost immediately after
        // the service starts, before any work is done.
        startForegroundSafely()

        when (intent?.action) {
            ACTION_START -> id?.let(::startDownload)
            ACTION_PAUSE -> id?.let(::pauseDownload)
            ACTION_CANCEL -> id?.let(::cancelDownload)
            ACTION_PAUSE_ALL -> jobs.keys.toList().forEach(::pauseDownload)
        }

        ensureNotificationLoop()
        stopIfIdle()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ── Commands ────────────────────────────────────────────────────────────

    private fun startDownload(id: String) {
        if (jobs.containsKey(id)) return
        repository.update(id) { it.copy(status = DownloadStatus.Queued, errorMessage = null) }

        val job = scope.launch {
            try {
                slots.withPermit { engine.run(id) }
            } finally {
                jobs.remove(id)
                stopIfIdle()
            }
        }
        jobs[id] = job
    }

    private fun pauseDownload(id: String) {
        jobs.remove(id)?.cancel()
        repository.update(id) { item ->
            if (item.status == DownloadStatus.Completed) {
                item
            } else {
                item.copy(status = DownloadStatus.Paused, speedBytesPerSecond = 0L)
            }
        }
        repository.persist()
    }

    private fun cancelDownload(id: String) {
        jobs.remove(id)?.cancel()
        repository.remove(id, deleteFile = true)
    }

    private fun stopIfIdle() {
        if (jobs.isNotEmpty()) return
        notificationLoop?.cancel()
        notificationLoop = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Notification ────────────────────────────────────────────────────────

    private fun ensureNotificationLoop() {
        if (notificationLoop != null) return
        notificationLoop = scope.launch {
            while (true) {
                notificationManager().notify(NOTIFICATION_ID, buildNotification())
                delay(NOTIFICATION_INTERVAL_MS)
            }
        }
    }

    private fun startForegroundSafely() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        }
    }

    private fun buildNotification(): Notification {
        val active = repository.items.value.filter { it.isActive }
        val current = active.firstOrNull()

        val title = when {
            active.isEmpty() -> "Finishing up"
            active.size == 1 -> current?.title ?: "Downloading"
            else -> "Downloading ${active.size} files"
        }

        val progress = current?.progress
        val text = when {
            current == null -> ""
            current.status == DownloadStatus.Queued -> "Waiting"
            progress != null -> "${(progress * 100).toInt()}% · ${formatSpeed(current.speedBytesPerSecond)}"
            else -> formatSpeed(current.speedBytesPerSecond)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .apply {
                if (progress != null) {
                    setProgress(100, (progress * 100).toInt(), false)
                } else if (active.isNotEmpty()) {
                    setProgress(0, 0, true)
                }
                if (current != null) {
                    addAction(
                        0,
                        "Pause",
                        servicePendingIntent(ACTION_PAUSE_ALL, current.id, requestCode = 1),
                    )
                }
            }
            .build()
    }

    private fun servicePendingIntent(action: String, id: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).apply {
            this.action = action
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Progress for files being downloaded"
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "orbit.downloads"
        private const val NOTIFICATION_ID = 4201
        private const val NOTIFICATION_INTERVAL_MS = 1000L
        private const val MAX_CONCURRENT = 3

        const val EXTRA_ID = "download_id"
        const val ACTION_START = "com.dawood.orbit.action.START_DOWNLOAD"
        const val ACTION_PAUSE = "com.dawood.orbit.action.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL = "com.dawood.orbit.action.CANCEL_DOWNLOAD"
        const val ACTION_PAUSE_ALL = "com.dawood.orbit.action.PAUSE_ALL_DOWNLOADS"

        fun formatSpeed(bytesPerSecond: Long): String = when {
            bytesPerSecond <= 0 -> "—"
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        }
    }
}
