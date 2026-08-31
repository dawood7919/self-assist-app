package com.dawood.orbit.tools.videodownloader.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * The only way the UI talks to the download service.
 *
 * Screens call these four functions and read state back from the repository,
 * so no composable ever needs to know a service exists.
 */
object DownloadController {

    fun start(context: Context, id: String) = send(context, DownloadService.ACTION_START, id)

    fun pause(context: Context, id: String) = send(context, DownloadService.ACTION_PAUSE, id)

    fun cancel(context: Context, id: String) = send(context, DownloadService.ACTION_CANCEL, id)

    private fun send(context: Context, action: String, id: String) {
        val intent = Intent(context.applicationContext, DownloadService::class.java).apply {
            this.action = action
            putExtra(DownloadService.EXTRA_ID, id)
        }
        ContextCompat.startForegroundService(context.applicationContext, intent)
    }
}
