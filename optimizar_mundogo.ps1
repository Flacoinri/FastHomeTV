param(
    [string]$TvIp = "192.168.1.12"
)

$adb = "C:\Users\italo\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$dev = "${TvIp}:5555"

Write-Host "Conectando a $dev..."
& $adb connect $dev

Write-Host "1. Deshabilitando paquetes IPG / Mundo GO..."
$disableList = @(
    "com.ipgglobe.upgrade",
    "com.ipgglobe.apkupgrade",
    "android.autoinstalls.config.com.ipgglobe",
    "com.ipg.apkadapter",
    "com.ipgglobe.stbcheck",
    "com.ipgglobe.bleremoteupgrade",
    "com.ipgglobe.agentService"
)
foreach ($pkg in $disableList) {
    & $adb -s $dev shell pm disable-user --user 0 $pkg
}

Write-Host "2. Desinstalando bloatware (--user 0)..."
$uninstallList = @(
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
foreach ($pkg in $uninstallList) {
    & $adb -s $dev shell pm uninstall --user 0 $pkg
}

Write-Host "3. Bloqueando actualizaciones automáticas del sistema..."
& $adb -s $dev shell settings put global ota_disable_automatic_update 1
& $adb -s $dev shell settings put global auto_update_apps 0

Write-Host "4. Aplicando mejoras de velocidad y compilación ART..."
& $adb -s $dev shell settings put global window_animation_scale 0.5
& $adb -s $dev shell settings put global transition_animation_scale 0.5
& $adb -s $dev shell settings put global animator_duration_scale 0.5
& $adb -s $dev shell pm trim-caches 2G
& $adb -s $dev shell cmd package compile -m speed-profile -a

Write-Host "5. Activando Accesibilidad (estilo Projectivy), Puente Local y Launcher Predeterminado..."
& $adb -s $dev shell pm uninstall com.italo.launcher 2>$null
& $adb -s $dev shell pm grant com.fasthometv.launcher android.permission.WRITE_SECURE_SETTINGS
& $adb -s $dev shell appops set com.fasthometv.launcher ACCESS_RESTRICTED_SETTINGS allow
& $adb -s $dev shell appops set com.fasthometv.launcher SYSTEM_ALERT_WINDOW allow
& $adb -s $dev shell appops set com.fasthometv.launcher GET_USAGE_STATS allow
& $adb -s $dev shell settings put secure enabled_accessibility_services com.fasthometv.launcher/com.fasthometv.launcher.LauncherAccessibilityService
& $adb -s $dev shell settings put secure accessibility_enabled 1
& $adb -s $dev shell cmd package set-home-activity com.fasthometv.launcher/.MainActivity
& $adb -s $dev shell dumpsys deviceidle whitelist +com.fasthometv.launcher
& $adb -s $dev shell settings put secure screensaver_components com.fasthometv.launcher/.ClockDream
& $adb -s $dev shell "pkill -f 'nc -s 127.0.0.1 -p 5039'; nohup /system/bin/nc -s 127.0.0.1 -p 5039 -L /system/bin/sh >/dev/null 2>&1 &"

Write-Host "¡Optimización Universal Android TV / Mundo GO con FastHomeTv (com.fasthometv.launcher) completada!"
