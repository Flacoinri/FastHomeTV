package com.fasthometv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Thread {
            try {
                SystemCommandRunner.enableAccessibilityAndDefaultHome(context)
            } catch (_: Exception) {
            }
        }.start()

        val start = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            context.startActivity(start)
        } catch (_: Exception) {
        }
    }
}
