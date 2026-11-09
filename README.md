# FastHomeTv (`com.fasthometv.launcher`)

**FastHomeTv** es un Launcher universal de alto rendimiento para **Android TV, Google TV, TV Box y Decodificadores (como Deco Mundo GO)**, diseñado con una arquitectura híbrida inspirada en [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) y [simbasjungles/free-my-tv](https://github.com/simbasjungles/free-my-tv).

## Características Principales
- **Identidad Visual Única (`FastHomeTv` • `com.fasthometv.launcher`)**: Ícono vectorial exclusivo ([`ic_launcher.xml`](app/src/main/res/drawable/ic_launcher.xml)) y banner `16:9` nativo para Android TV ([`ic_banner.xml`](app/src/main/res/drawable/ic_banner.xml)).
- **10 Temas Dinámicos de Sistema Completo**:
  - **Tema Claro (Blanco Perla • Free My TV)**
  - **Auto Día / Noche (Inteligente según horario solar de Open-Meteo)**
  - **Obsidiana Puro (Negro OLED • CleanYourTV)**
  - **Azul Zafiro Intenso (Cinema Blue)**
  - **Púrpura Nébula (Violeta Real)**
  - **Esmeralda Aurora (Verde Boreal)**
  - **Rojo Carmesí (Crimson Cinema • Estilo Netflix)**
  - **Dorado Ámbar (Sunset Gold • Cálido)**
  - **Cian Ártico (Cyber Titanium • Azul Hielo)**
  - **Gris Titanio (Graphite Studio • Apple Pro)**
  *(Adapta el fondo, el Dock, las píldoras, los textos y el panel lateral completo de Ajustes).*
- **Vitrina Multimedia 100% Automática (`EXPLORA | PELÍCULAS | SHOWS | SERIES`)**: Selección persistente de pestañas, memoria de foco por sección y rotación automática entre más de 32 estrenos y clásicos (promoviendo automáticamente una tarjeta principal `16:9` + pósters `2:3` optimizados en `RGB_565` con caché en disco).
- **Salvapantallas OLED Propio Integrado (Activo por defecto • 5 min)**: Funciona al 100% sin depender de `com.google.android.backdrop`. Muestra reloj, fecha y clima flotante sobre negro puro `#000000` (o modo *Pantalla 100% Negra Total*) y despierta al instante con cualquier tecla.
- **Clima en Vivo (Open-Meteo)**: Detección automática de ubicación y pronóstico meteorológico actualizado en la cabecera y en el salvapantallas OLED.
- **Módulo de Rendimiento, RAM y Caché (`Turbo Boost`)**: Monitor en vivo de RAM y almacenamiento con limpieza en 1 clic del caché de todas las aplicaciones (`pm trim-caches 999G`), cierre de aplicaciones en segundo plano (`killBackgroundProcesses` + `am force-stop`), cierre individual por aplicación, modos de animación (`0.5x` y `0.0x Instantáneo`) y compilación `ART speed-profile`.
- **Google Home, Nexxt Home y Domótica Smart TV**: Controla tus ampolletas inteligentes **Nexxt Home** (encender, apagar, *Modo Cine 20%*, cambiar color), interruptores inteligentes y cámaras Wi-Fi directamente desde la TV a través del motor de **Google Home / Assistant** (`com.google.android.katniss`) o mediante Webhooks de **Home Assistant**.
- **Dock Estilo Apple TV y Filtro Estricto de Visibilidad**: Las aplicaciones ocultas se excluyen automáticamente de *Todas*, del *Dock* y de *Recientes*.
- **Blindaje Anti-Desinstalación y Asistente de Permisos (Ideal para Link / Código Downloader)**: Protege a `com.fasthometv.launcher` contra cualquier borrado ADB e incluye un asistente interactivo para que usuarios que descarguen [`FastHomeTv.apk`](FastHomeTv.apk) directamente acepten los permisos de *Accesibilidad*, *Batería (No cerrar en 2do plano)*, *Superposición* y *Uso de Apps* con el control remoto.
- **Servicio de Accesibilidad Integrado (`FastHomeTv Override` estilo Projectivy Launcher)**: Intercepta el botón `HOME` y bloquea intentos de secuestro del launcher de fábrica u operadora en cualquier Android TV, Google TV o Decodificador.
- **Menú de Energía Universal (Apagado, Suspensión y Reinicio)**: Suspensión inmediata (`KEYCODE_SLEEP` / `GLOBAL_ACTION_LOCK_SCREEN`), reinicio del decodificador/TV (`svc power reboot`), apagado completo (`svc power shutdown`), diálogo nativo de apagado (`GLOBAL_ACTION_POWER_DIALOG`) y pantalla negra anti-quemado.
- **Módulo de Optimización y Debloat ADB Integrado**: Ejecuta desde el propio menú de Ajustes los comandos de limpieza de bloatware, bloqueo de actualizaciones OTA (`ota_disable_automatic_update 1`) y aceleración de animaciones a `0.5x`.

## Comandos ADB de Instalación Rápida
```powershell
$adb = "C:\Users\italo\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb connect 192.168.1.12:5555
& $adb -s 192.168.1.12:5555 install -r app/build/outputs/apk/debug/app-debug.apk
& $adb -s 192.168.1.12:5555 shell pm grant com.fasthometv.launcher android.permission.WRITE_SECURE_SETTINGS
& $adb -s 192.168.1.12:5555 shell settings put secure enabled_accessibility_services com.fasthometv.launcher/com.fasthometv.launcher.LauncherAccessibilityService
& $adb -s 192.168.1.12:5555 shell settings put secure accessibility_enabled 1
& $adb -s 192.168.1.12:5555 shell cmd package set-home-activity com.fasthometv.launcher/.MainActivity
```

## Scripts Incluidos
- [`optimizar_mundogo.ps1`](optimizar_mundogo.ps1): Aplica todos los comandos ADB de optimización, desinstalación de bloatware y activación de `FastHomeTv`.
- [`restore_tv.ps1`](restore_tv.ps1): Restaura los paquetes originales desde el respaldo en `backup/`.
