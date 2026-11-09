$adb = "C:\Users\italo\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$ip = "192.168.1.3:5555"

Write-Host "Conectando a $ip..." -ForegroundColor Cyan
& $adb disconnect $ip | Out-Null
& $adb connect $ip

Write-Host "Esperando a que aceptes el cartel 'Permitir depuracion USB' en el televisor ($ip)..." -ForegroundColor Yellow
for ($i = 1; $i -le 30; $i++) {
    $devs = & $adb devices
    if ($devs -match "192\.168\.1\.3:5555\s+device") {
        Write-Host "¡Autorizado! Instalando FastHomeTv..." -ForegroundColor Green
        & $adb -s $ip install -r "$PSScriptRoot\FastHomeTv.apk"
        & $adb -s $ip shell pm grant com.fasthometv.launcher android.permission.WRITE_SECURE_SETTINGS
        & $adb -s $ip shell appops set com.fasthometv.launcher ACCESS_RESTRICTED_SETTINGS allow
        & $adb -s $ip shell appops set com.fasthometv.launcher SYSTEM_ALERT_WINDOW allow
        & $adb -s $ip shell appops set com.fasthometv.launcher GET_USAGE_STATS allow
        & $adb -s $ip shell dumpsys deviceidle whitelist +com.fasthometv.launcher
        & $adb -s $ip shell settings put secure enabled_accessibility_services com.fasthometv.launcher/com.fasthometv.launcher.LauncherAccessibilityService
        & $adb -s $ip shell settings put secure accessibility_enabled 1
        & $adb -s $ip shell cmd package set-home-activity com.fasthometv.launcher/.MainActivity
        & $adb -s $ip shell "pkill -f 'nc -s 127.0.0.1 -p 5039'; nohup /system/bin/nc -s 127.0.0.1 -p 5039 -L /system/bin/sh >/dev/null 2>&1 &"
        & $adb -s $ip shell am start -n com.fasthometv.launcher/.MainActivity
        Write-Host "¡FastHomeTv instalado y activado en $ip!" -ForegroundColor Green
        exit 0
    }
    Start-Sleep -Seconds 2
}

Write-Host "El dispositivo $ip sigue figurando como 'unauthorized'." -ForegroundColor Red
Write-Host "En la TV ve a: Ajustes -> Preferencias del dispositivo -> Opciones para desarrolladores -> Apaga y vuelve a encender 'Depuracion USB' (o 'Revocar autorizaciones de depuracion USB')." -ForegroundColor Yellow

