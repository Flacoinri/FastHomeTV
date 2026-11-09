package com.fasthometv.launcher

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder

object SystemCommandRunner {

    private const val LOCAL_BRIDGE_PORT = 5039

    /**
     * Lista blindada: JAMÁS permitir que ningún comando ADB desinstale o deshabilite FastHomeTv.
     */
    val PROTECTED_PACKAGES = setOf(
        "com.fasthometv.launcher"
    )

    val DEBLOAT_DISABLE_PACKAGES = listOf(
        "com.ipgglobe.upgrade",
        "com.ipgglobe.apkupgrade",
        "android.autoinstalls.config.com.ipgglobe",
        "com.ipg.apkadapter",
        "com.ipgglobe.stbcheck",
        "com.ipgglobe.bleremoteupgrade",
        "com.ipgglobe.agentService"
    )

    val DEBLOAT_UNINSTALL_PACKAGES = listOf(
        "com.ipgglobe.upgrade",
        "com.ipgglobe.apkupgrade",
        "android.autoinstalls.config.com.ipgglobe",
        "com.ipg.apkadapter",
        "com.ipgglobe.stbcheck",
        "com.ipgglobe.bleremoteupgrade",
        "com.ipgglobe.agentService",
        "com.google.android.tvlauncher",
        "com.amazon.amazonvideo.livingroom",
        "com.szjiuzhou.setupcustomizer",
        "com.google.android.videos",
        "com.google.android.play.games",
        "com.google.android.feedback",
        "com.google.android.backdrop"
    )

    /**
     * Verifica que un comando shell jamás intente desinstalar o deshabilitar FastHomeTv.
     */
    private fun isSafeCommand(cmd: String): Boolean {
        val lower = cmd.lowercase()
        for (protectedPkg in PROTECTED_PACKAGES) {
            if ((lower.contains("uninstall") || lower.contains("disable") || lower.contains("clear")) &&
                lower.contains(protectedPkg.lowercase())
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Ejecuta un comando de sistema intentando primero el puente Shell local (UID 2000 en 127.0.0.1:5039),
     * luego root (su) si el TV Box está rooteado, y finalmente sh local.
     */
    fun execShellCommand(cmd: String, timeoutMs: Int = 4500): Pair<Boolean, String> {
        if (!isSafeCommand(cmd)) {
            return false to "Bloqueado por protección anti-desinstalación de FastHomeTv"
        }

        // 1. Puente ADB Shell Local (127.0.0.1:5039 con permisos UID 2000 shell)
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", LOCAL_BRIDGE_PORT), 650)
                socket.soTimeout = timeoutMs
                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                writer.println("$cmd; echo __EOF__; exit")
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line == "__EOF__") break
                    response.append(line).append("\n")
                }
                return true to response.toString().trim()
            }
        } catch (_: Exception) {
        }

        // 2. Intento con su (para TV Boxes rooteados)
        try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val code = p.waitFor()
            if (code == 0) {
                val out = p.inputStream.bufferedReader().readText().trim()
                return true to out
            }
        } catch (_: Exception) {
        }

        // 3. Intento con sh estándar
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val code = p.waitFor()
            val out = p.inputStream.bufferedReader().readText().trim()
            (code == 0) to out
        } catch (e: Exception) {
            false to (e.message ?: "error")
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isUsageStatsGranted(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    fun isDefaultHomeLauncher(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val res = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            res?.activityInfo?.packageName == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Activa automáticamente el Servicio de Accesibilidad (estilo Projectivy Launcher),
     * protege FastHomeTv contra cierre o desinstalación y otorga permisos de sistema.
     */
    fun enableAccessibilityAndDefaultHome(context: Context): Boolean {
        val pkg = context.packageName
        val component = "$pkg/${LauncherAccessibilityService::class.java.name}"
        var ok = false

        // Vía WRITE_SECURE_SETTINGS directo
        try {
            val cr = context.contentResolver
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val updated = if (current.isBlank()) {
                component
            } else if (!current.contains(component)) {
                "$current:$component"
            } else {
                current
            }
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            ok = true
        } catch (_: Exception) {
        }

        // Vía Puente Shell ADB (UID 2000): blinda FastHomeTv y concede todos los permisos automáticamente
        val shellCmd = buildString {
            append("pm enable $pkg; ")
            append("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS; ")
            append("cmd appops set $pkg SYSTEM_ALERT_WINDOW allow; ")
            append("cmd appops set $pkg GET_USAGE_STATS allow; ")
            append("cmd appops set $pkg RUN_IN_BACKGROUND allow; ")
            append("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow; ")
            append("settings put secure enabled_accessibility_services $component; ")
            append("settings put secure accessibility_enabled 1; ")
            append("cmd package set-home-activity $pkg/.MainActivity; ")
            append("dumpsys deviceidle whitelist +$pkg; ")
            append("settings put secure screensaver_components $pkg/.ClockDream; ")
            append("settings put secure screensaver_enabled 1")
        }
        val (shellOk, _) = execShellCommand(shellCmd)
        return ok || shellOk || LauncherAccessibilityService.isEnabled(context)
    }

    /**
     * Ejecuta TODOS los comandos ADB de optimización, desinstalación de bloatware,
     * bloqueo de actualizaciones OTA, mejora de velocidad 0.5x y blindaje de FastHomeTv.
     */
    fun runFullAdbOptimizationSuite(
        context: Context,
        onProgress: (String) -> Unit
    ): String {
        val myPkg = context.packageName
        onProgress("1/5 Blindando FastHomeTv ($myPkg) y aplicando bloqueo OTA + velocidad 0.5x...")
        try {
            val cr = context.contentResolver
            Settings.Global.putInt(cr, "ota_disable_automatic_update", 1)
            Settings.Global.putInt(cr, "auto_update_apps", 0)
            Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, 0.5f)
            Settings.Global.putFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 0.5f)
            Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 0.5f)
        } catch (_: Exception) {
        }

        execShellCommand(
            "pm enable $myPkg; " +
                "dumpsys deviceidle whitelist +$myPkg; " +
                "settings put global ota_disable_automatic_update 1; " +
                "settings put global auto_update_apps 0; " +
                "settings put global window_animation_scale 0.5; " +
                "settings put global transition_animation_scale 0.5; " +
                "settings put global animator_duration_scale 0.5"
        )

        onProgress("2/5 Desactivando servicios de telemetría y actualización de operadora...")
        for (pkg in DEBLOAT_DISABLE_PACKAGES) {
            if (pkg == myPkg || pkg in PROTECTED_PACKAGES) continue
            execShellCommand("pm disable-user --user 0 $pkg")
        }

        onProgress("3/5 Desinstalando bloatware (protegiendo siempre a FastHomeTv)...")
        for (pkg in DEBLOAT_UNINSTALL_PACKAGES) {
            if (pkg == myPkg || pkg in PROTECTED_PACKAGES) continue
            execShellCommand("pm uninstall --user 0 $pkg")
        }

        onProgress("4/5 Forzando FastHomeTv (Accesibilidad + Permisos + Launcher Predeterminado)...")
        enableAccessibilityAndDefaultHome(context)

        onProgress("5/5 Limpiando caché (2G) y compilando paquetes en speed-profile...")
        execShellCommand("pm trim-caches 2G", timeoutMs = 6000)
        execShellCommand("cmd package compile -m speed-profile -a", timeoutMs = 15000)

        return "Optimización completada (FastHomeTv blindado • OTA bloqueado • Animaciones 0.5x • Bloatware limpio)"
    }

    /**
     * Suspender / Apagar pantalla del dispositivo (Deco / TV Box / Google TV).
     */
    fun sleepDevice(context: Context) {
        Thread {
            // 1. Vía Puente Shell UID 2000 (KEYCODE_SLEEP 223)
            val (ok, _) = execShellCommand("input keyevent 223")
            if (ok) return@Thread

            // 2. Vía AccessibilityService nativo (GLOBAL_ACTION_LOCK_SCREEN API 28+)
            enableAccessibilityAndDefaultHome(context)
            if (LauncherAccessibilityService.lockScreen()) {
                return@Thread
            }

            // 3. Fallback a Daydream / Somnambulator OLED
            try {
                context.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }.start()
    }

    /**
     * Reiniciar físicamente el Decodificador / TV Box / Android TV.
     */
    fun rebootDevice(context: Context) {
        Thread {
            // 1. Intento vía Puente Shell UID 2000 / su (`svc power reboot` o `reboot`)
            val (ok, _) = execShellCommand("svc power reboot || reboot")
            if (ok) return@Thread

            // 2. Intento vía PowerManager
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.reboot(null)
                return@Thread
            } catch (_: Exception) {
            }

            // 3. Broadcast nativo de Android TV Settings (DeviceSettingBroadcastReceiver)
            try {
                context.sendBroadcast(
                    Intent("com.android.tv.settings.REBOOT_DEVICE")
                        .setClassName("com.android.tv.settings", "com.android.tv.settings.DeviceSettingBroadcastReceiver")
                )
            } catch (_: Exception) {
            }

            // 4. Abre el diálogo nativo de energía de Android TV (donde está Reiniciar)
            enableAccessibilityAndDefaultHome(context)
            if (!LauncherAccessibilityService.showPowerDialog()) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    /**
     * Apagar el Decodificador / Abrir el menú nativo de Apagado y Reinicio de Android TV.
     */
    fun showSystemPowerMenuOrShutdown(context: Context, directShutdown: Boolean = false) {
        Thread {
            if (directShutdown) {
                val (ok, _) = execShellCommand("svc power shutdown || reboot -p || input keyevent 26")
                if (ok) return@Thread
            }

            // 1. Vía AccessibilityService nativo (GLOBAL_ACTION_POWER_DIALOG)
            enableAccessibilityAndDefaultHome(context)
            if (LauncherAccessibilityService.showPowerDialog()) {
                return@Thread
            }

            // 2. Vía Puente Shell UID 2000 (`input keyevent --longpress 26`)
            val (ok, _) = execShellCommand("input keyevent --longpress 26")
            if (ok) return@Thread

            // 3. Fallback a Acerca de / Reiniciar en Android TV Settings
            try {
                context.startActivity(
                    Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }.start()
    }

    /**
     * Devuelve el resumen en vivo de Memoria RAM y Almacenamiento disponible.
     */
    fun getRamAndStorageSummary(context: Context): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val freeRamMb = (memInfo.availMem / (1024 * 1024)).coerceAtLeast(0)
        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).coerceAtLeast(1)
        val usedRamMb = (totalRamMb - freeRamMb).coerceAtLeast(0)
        val usedPct = ((usedRamMb * 100) / totalRamMb).coerceIn(0, 100)

        val freeDiskMb = getFreeStorageMb()
        return "RAM Libre: ${freeRamMb} MB / ${totalRamMb} MB (${usedPct}% en uso) • Espacio libre: ${freeDiskMb} MB"
    }

    fun getFreeRamMb(context: Context): Long {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            (memInfo.availMem / (1024 * 1024)).coerceAtLeast(0)
        } catch (_: Exception) {
            0L
        }
    }

    fun getFreeStorageMb(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            (stat.availableBytes / (1024 * 1024)).coerceAtLeast(0)
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Elimina el caché de TODAS las aplicaciones instaladas y archivos temporales.
     */
    fun clearAllAppsCache(context: Context): String {
        val beforeDiskMb = getFreeStorageMb()
        try {
            context.cacheDir?.deleteRecursively()
            context.externalCacheDir?.deleteRecursively()
        } catch (_: Exception) {
        }
        execShellCommand("pm trim-caches 999G; pm trim-caches 32G; pm trim-caches 4G", timeoutMs = 8000)
        val afterDiskMb = getFreeStorageMb()
        val gainedMb = (afterDiskMb - beforeDiskMb).coerceAtLeast(0)
        return if (gainedMb > 0) {
            "Caché de todas las apps eliminado (+${gainedMb} MB recuperados • ${afterDiskMb} MB libres)"
        } else {
            "Caché de todas las aplicaciones vaciado (${afterDiskMb} MB disponibles en disco)"
        }
    }

    /**
     * Cierra todas las aplicaciones en segundo plano y optimiza la memoria RAM,
     * protegiendo siempre a FastHomeTv y servicios críticos del sistema.
     */
    fun optimizeRamAndCloseBackgroundApps(
        context: Context,
        installedPackages: List<String>
    ): String {
        val beforeRam = getFreeRamMb(context)
        val myPkg = context.packageName
        val systemKeep = setOf(
            myPkg,
            "com.fasthometv.launcher",
            "com.android.systemui",
            "com.android.tv.settings",
            "com.droidlogic.tv.settings",
            "com.google.android.katniss",
            "com.google.android.gms",
            "com.google.android.gsf",
            "android"
        )

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        var closedCount = 0
        val shellForceStops = StringBuilder("am kill-all")

        for (pkg in installedPackages) {
            if (pkg in systemKeep || pkg in PROTECTED_PACKAGES) continue
            try {
                am?.killBackgroundProcesses(pkg)
                closedCount++
            } catch (_: Exception) {
            }
            shellForceStops.append("; am force-stop ").append(pkg)
        }

        execShellCommand(shellForceStops.toString(), timeoutMs = 8000)
        System.gc()
        Runtime.getRuntime().gc()

        try {
            Thread.sleep(250)
        } catch (_: Exception) {
        }

        val afterRam = getFreeRamMb(context)
        val freedMb = (afterRam - beforeRam).coerceAtLeast(0)
        return if (freedMb > 0) {
            "¡RAM optimizada! $closedCount apps cerradas en 2do plano (+${freedMb} MB liberados • ${afterRam} MB libres)"
        } else {
            "¡RAM optimizada! $closedCount apps en 2do plano cerradas (${afterRam} MB libres)"
        }
    }

    /**
     * Ejecuta el Turbo Boost Completo: Cierra apps en 2do plano + Limpia Caché + Optimiza RAM + Escalas 0.5x.
     */
    fun runTurboBoostAll(
        context: Context,
        installedPackages: List<String>,
        onProgress: (String) -> Unit
    ): String {
        val beforeRam = getFreeRamMb(context)
        val beforeDisk = getFreeStorageMb()

        onProgress("1/3 Cerrando aplicaciones abiertas en segundo plano...")
        optimizeRamAndCloseBackgroundApps(context, installedPackages)

        onProgress("2/3 Eliminando caché de todas las aplicaciones instaladas...")
        clearAllAppsCache(context)

        onProgress("3/3 Compactando memoria RAM y acelerando interfaz a 0.5x...")
        try {
            val cr = context.contentResolver
            Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, 0.5f)
            Settings.Global.putFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 0.5f)
            Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 0.5f)
        } catch (_: Exception) {
        }
        execShellCommand(
            "settings put global window_animation_scale 0.5; " +
                "settings put global transition_animation_scale 0.5; " +
                "settings put global animator_duration_scale 0.5"
        )
        System.gc()

        val afterRam = getFreeRamMb(context)
        val afterDisk = getFreeStorageMb()
        val ramDiff = (afterRam - beforeRam).coerceAtLeast(0)
        val diskDiff = (afterDisk - beforeDisk).coerceAtLeast(0)

        return "¡Turbo Boost completado! RAM libre: ${afterRam} MB (+${ramDiff} MB) • Disco libre: ${afterDisk} MB (+${diskDiff} MB)"
    }

    /**
     * Envía una orden directa al Asistente de Google integrado en el Android TV (com.google.android.katniss)
     * para controlar ampolletas Nexxt Home, interruptores inteligentes y cámaras Wi-Fi vinculadas en Google Home.
     */
    fun sendGoogleHomeAssistantCommand(context: Context, commandText: String) {
        Thread {
            val escaped = commandText.replace("\"", "\\\"")
            // 1. Intento vía Shell local directo a SearchActivityTrampoline de Google Assistant TV
            val cmd = "am start -n com.google.android.katniss/com.google.android.apps.tvsearch.app.launch.trampoline.SearchActivityTrampoline " +
                "-a com.google.android.googlequicksearchbox.GOOGLE_SEARCH " +
                "--es query \"$escaped\" --es ${SearchManager.USER_QUERY} \"$escaped\""
            val (ok, _) = execShellCommand(cmd)
            if (ok) return@Thread

            // 2. Intento directo por Intent de Android
            val intents = listOf(
                Intent("com.google.android.googlequicksearchbox.GOOGLE_SEARCH").apply {
                    setPackage("com.google.android.katniss")
                    putExtra(SearchManager.QUERY, commandText)
                    putExtra(SearchManager.USER_QUERY, commandText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                Intent("android.speech.action.WEB_SEARCH").apply {
                    setPackage("com.google.android.katniss")
                    putExtra(SearchManager.QUERY, commandText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://assistant.google.com/search?q=${URLEncoder.encode(commandText, "UTF-8")}")
                ).apply {
                    setPackage("com.google.android.katniss")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                Intent(Intent.ACTION_ASSIST).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            for (intent in intents) {
                try {
                    context.startActivity(intent)
                    return@Thread
                } catch (_: Exception) {
                }
            }
        }.start()
    }
}
