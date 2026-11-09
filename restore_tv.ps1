param(
    [string]$TvIp = "192.168.1.12"
)

$adb = "C:\Users\italo\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$dev = "${TvIp}:5555"

Write-Host "Conectando a $dev..."
& $adb connect $dev

$uninstalledPkgs = @(
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

foreach ($pkg in $uninstalledPkgs) {
    Write-Host "Reinstalando paquete del sistema $pkg..."
    & $adb -s $dev shell cmd package install-existing $pkg
    & $adb -s $dev shell pm enable $pkg
}

if (Test-Path "backup\debloated_by_cleantv.txt") {
    $pkgs = Get-Content "backup\debloated_by_cleantv.txt"
    foreach ($pkg in $pkgs) {
        if (-not [string]::IsNullOrWhiteSpace($pkg)) {
            Write-Host "Habilitando $pkg..."
            & $adb -s $dev shell pm enable $pkg
        }
    }
}

Write-Host "Restaurando escalas de animación a 1.0x..."
& $adb -s $dev shell settings put global window_animation_scale 1.0
& $adb -s $dev shell settings put global transition_animation_scale 1.0
& $adb -s $dev shell settings put global animator_duration_scale 1.0

Write-Host "Restaurando Google TV Launcher original como Home..."
& $adb -s $dev shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
& $adb -s $dev shell input keyevent KEYCODE_HOME
Write-Host "¡Restauración completada!"
