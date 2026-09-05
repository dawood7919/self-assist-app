package com.dawood.orbit.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles the Install action from the update notification when the activity
 * path is not enough (e.g. permission gate). Falls through to AppUpdateManager.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AppUpdateManager.get(context).installPendingApk()
    }
}
