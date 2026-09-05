package com.dawood.orbit.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for a newer Orbit APK, downloads it into app cache,
 * and surfaces an install notification. Silent install is not possible on
 * stock Android without device-owner privileges — the user always confirms.
 */
class AppUpdateManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    var autoUpdateEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO, value).apply()
            if (value) {
                UpdateScheduler.schedule(context)
            } else {
                UpdateScheduler.cancel(context)
            }
        }

    fun currentVersionCode(): Long {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        } catch (_: Exception) {
            0L
        }
    }

    fun currentVersionName(): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }
    }

    /**
     * @return result describing what happened so UI / worker can react.
     */
    fun checkAndMaybeDownload(forceDownload: Boolean = false): UpdateResult {
        ensureNotificationChannel()
        val remote = fetchLatestRelease() ?: return UpdateResult.Error("Could not reach GitHub releases")
        val local = currentVersionCode()
        if (remote.versionCode <= local) {
            return UpdateResult.UpToDate(local, remote.versionCode, remote.tag)
        }

        val shouldDownload = forceDownload || autoUpdateEnabled
        if (!shouldDownload) {
            showUpdateAvailableNotification(remote, apkFile = null)
            return UpdateResult.Available(remote, downloaded = false)
        }

        val file = downloadApk(remote) ?: return UpdateResult.Error("Download failed for ${remote.tag}")
        showUpdateAvailableNotification(remote, apkFile = file)
        prefs.edit()
            .putLong(KEY_LAST_CODE, remote.versionCode)
            .putString(KEY_LAST_PATH, file.absolutePath)
            .apply()
        return UpdateResult.Available(remote, downloaded = true)
    }

    fun installPendingApk(): Boolean {
        val path = prefs.getString(KEY_LAST_PATH, null) ?: return false
        val file = File(path)
        if (!file.exists()) return false
        return launchInstall(file)
    }

    fun hasPendingApk(): Boolean {
        val path = prefs.getString(KEY_LAST_PATH, null) ?: return false
        val code = prefs.getLong(KEY_LAST_CODE, 0)
        return code > currentVersionCode() && File(path).exists()
    }

    private fun fetchLatestRelease(): RemoteRelease? {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Orbit-Updater")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                parseRelease(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseRelease(json: String): RemoteRelease? {
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name").orEmpty()
        if (tag.isBlank()) return null
        val versionCode = tag.removePrefix("build-").toLongOrNull()
            ?: Regex("\\d+").find(tag)?.value?.toLongOrNull()
            ?: return null

        val assets = obj.optJSONArray("assets") ?: return null
        val preferRelease = !isDebuggable()
        var releaseUrl: String? = null
        var debugUrl: String? = null
        var anyApk: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name").lowercase()
            val url = asset.optString("browser_download_url")
            if (!name.endsWith(".apk") || url.isBlank()) continue
            anyApk = url
            when {
                name.contains("release") -> releaseUrl = url
                name.contains("debug") -> debugUrl = url
            }
        }
        val apkUrl = if (preferRelease) {
            releaseUrl ?: anyApk ?: debugUrl
        } else {
            debugUrl ?: anyApk ?: releaseUrl
        } ?: return null

        return RemoteRelease(
            tag = tag,
            versionCode = versionCode,
            name = obj.optString("name").ifBlank { tag },
            apkUrl = apkUrl,
        )
    }

    private fun isDebuggable(): Boolean =
        (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun downloadApk(remote: RemoteRelease): File? {
        val dir = File(context.cacheDir, "updates").also { it.mkdirs() }
        val target = File(dir, "orbit-${remote.versionCode}.apk")
        if (target.exists() && target.length() > 100_000) return target

        val request = Request.Builder()
            .url(remote.apkUrl)
            .header("User-Agent", "Orbit-Updater")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val tmp = File(dir, "orbit-${remote.versionCode}.tmp")
                tmp.outputStream().use { out ->
                    body.byteStream().use { input -> input.copyTo(out) }
                }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                target
            }
        } catch (_: Exception) {
            null
        }
    }

    fun launchInstall(file: File): Boolean {
        if (!file.exists()) return false
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    // Open the system page so the user can allow installs from Orbit.
                    val settings = Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(settings)
                    return false
                }
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun showUpdateAvailableNotification(remote: RemoteRelease, apkFile: File?) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val installIntent = if (apkFile != null && apkFile.exists()) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                apkFile,
            )
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent()
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_INSTALL,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (apkFile != null) {
            "Build ${remote.versionCode} downloaded — tap to install"
        } else {
            "Build ${remote.versionCode} is available"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Orbit update ready")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${remote.name}\n$text"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .addAction(0, "Install", pending)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Notifies when a new Orbit build is ready to install"
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val PREFS = "orbit_updates"
        private const val KEY_AUTO = "auto_update"
        private const val KEY_LAST_CODE = "pending_code"
        private const val KEY_LAST_PATH = "pending_path"
        private const val CHANNEL_ID = "orbit_updates"
        private const val NOTIFICATION_ID = 9101
        private const val REQUEST_INSTALL = 9102
        private const val RELEASES_URL =
            "https://api.github.com/repos/dawood7919/self-assist-app/releases/latest"

        fun get(context: Context) = AppUpdateManager(context.applicationContext)
    }
}

data class RemoteRelease(
    val tag: String,
    val versionCode: Long,
    val name: String,
    val apkUrl: String,
)

sealed class UpdateResult {
    data class UpToDate(val local: Long, val remote: Long, val tag: String) : UpdateResult()
    data class Available(val release: RemoteRelease, val downloaded: Boolean) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}
