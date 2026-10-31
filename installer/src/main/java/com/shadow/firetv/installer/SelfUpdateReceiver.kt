package com.shadow.firetv.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SelfUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val launch = Intent(context, InstallerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        try {
            context.startActivity(launch)
        } catch (_: Exception) {
        }
    }
}
