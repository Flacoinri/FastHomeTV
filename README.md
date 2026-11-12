# FastHomeTv (`com.fasthometv.launcher`)

![FastHomeTv Banner](FastHomeTv_Social_1280x640.png)

**FastHomeTv** es un Launcher universal de alto rendimiento para **Android TV, Google TV, TV Box y Decodificadores (como Deco Mundo GO)**, diseñado con una arquitectura híbrida inspirada en [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) y [simbasjungles/free-my-tv](https://github.com/simbasjungles/free-my-tv).

## Capturas de Pantalla

### Pantalla de Inicio y Vitrina Multimedia
![Pantalla de Inicio](INICIO?raw=true)

### Dock y Cajón de Aplicaciones
![Dock y Aplicaciones](DOCK?raw=true)

### Panel de Configuración y Rendimiento
![Configuración](CONFIGURACION?raw=true)

### Clima en Vivo (Open-Meteo)
![Clima en Vivo](CLIMA?raw=true)

### Selector de 10 Temas Dinámicos
![Temas Dinámicos](TEMA?raw=true)

### Gestión del Dock y Visibilidad de Apps
![Información de App](INFORMACION_APP?raw=true)

### Salvapantallas OLED Negro Puro
![Pantalla de Bloqueo OLED](PANTALLA_BLOQUEO?raw=true)

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
- **Salvapantallas OLED Propio Integrado (Activo por defecto • 5 min)**: Funciona al 100% sin depender de `com.google.android.backdrop`. Muestra reloj, fecha y clima flotante sobre negro puro `#000000` (o modo *Pantalla 10
