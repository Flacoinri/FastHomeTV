package com.fasthometv.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class LauncherAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: LauncherAccessibilityService? = null
            private set

        private val HIJACK_LAUNCHER_PACKAGES = setOf(
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.google.android.tungsten.setupwraith",
            "com.szjiuzhou.setupcustomizer",
            "com.ipgglobe.stbcheck",
            "com.ipgglobe.agentService"
        )

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            return try {
                val enabled = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                enabled.contains("${context.packageName}/${LauncherAccessibilityService::class.java.name}") ||
                    enabled.contains("${context.packageName}/.LauncherAccessibilityService")
            } catch (_: Exception) {
                false
            }
        }

        fun lockScreen(): Boolean {
            val svc = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                svc.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                false
            }
        }

        fun showPowerDialog(): Boolean {
            val svc = instance ?: return false
            return svc.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
            if (!prefs.getBoolean("force_launcher_override", true)) return

            if (pkg in HIJACK_LAUNCHER_PACKAGES) {
                launchOurHome()
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        val prefs = getSharedPreferences("launcher", MODE_PRIVATE)
        if (!prefs.getBoolean("force_launcher_override", true)) return false

        if (event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                launchOurHome()
            }
            return true
        }
        return false
    }

    override fun onInterrupt() {
    }

    private fun launchOurHome() {
        try {
            val homeIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
            }
            startActivity(homeIntent)
        } catch (_: Exception) {
        }
    }
}
