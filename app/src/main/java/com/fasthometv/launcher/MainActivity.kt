package com.fasthometv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var rootContainer: FrameLayout
    private lateinit var mainContentColumn: ViewGroup
    private lateinit var clockView: TextClock
    private lateinit var clockDivider: View
    private lateinit var dateClockView: TextClock
    private lateinit var greeting: TextView
    private lateinit var weatherPill: TextView
    private lateinit var networkPill: TextView
    private lateinit var inputsPill: TextView
    private lateinit var settingsPill: TextView
    private lateinit var exploreSection: View
    private lateinit var tabsHeaderRow: View
    private lateinit var tabExplore: TextView
    private lateinit var tabMovies: TextView
    private lateinit var tabShows: TextView
    private lateinit var tabSeries: TextView
    private lateinit var spotlightStatus: TextView
    private lateinit var mediaRow: RecyclerView
    private lateinit var dockContainer: View
    private lateinit var pullDownHint: TextView
    private lateinit var favRow: RecyclerView
    private lateinit var appsDrawerSection: View
    private lateinit var allAppsLabel: TextView
    private lateinit var allGrid: RecyclerView
    private lateinit var hint: TextView
    private lateinit var appCount: TextView
    private lateinit var ambientGlow: View

    private lateinit var catAll: TextView
    private lateinit var catRecent: TextView
    private lateinit var catStreaming: TextView
    private lateinit var catMedia: TextView
    private lateinit var catTools: TextView

    // Custom Side Drawer
    private lateinit var sideDrawerOverlay: View
    private lateinit var sideDrawerPanel: View
    private lateinit var drawerHeaderTitle: TextView
    private lateinit var drawerHeaderSubtitle: TextView
    private lateinit var drawerHeaderDivider: View
    private lateinit var drawerList: RecyclerView

    // Built-in OLED Screensaver Overlay (activo por defecto en FastHomeTv)
    private lateinit var screensaverOverlay: FrameLayout
    private lateinit var screensaverFloatingBox: LinearLayout
    private lateinit var screensaverWeatherText: TextView
    private var isScreensaverActive: Boolean = false

    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var favAdapter: AppAdapter
    private lateinit var allAdapter: AppAdapter
    private lateinit var drawerAdapter: DrawerAdapter

    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }
    private val colorCache = HashMap<String, Int>()
    private val bitmapCache = HashMap<String, Bitmap>()
    private val lastFocusedMediaIndexByTab = HashMap<String, Int>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()

    private var cachedRawApps: List<AppEntry>? = null

    private var selectedMediaTab: String = "EXPLORE"
    private var selectedCategory: String = "ALL"
    private var lastFocusedColor: Int = 0
    private var forceDockFocusOnNextRefresh: Boolean = true
    private var isAppsExpanded: Boolean = false
    private var isDrawerOpen: Boolean = false
    private var isLightThemeActive: Boolean = false
    private var viewToRestoreFocus: View? = null

    private var weatherSummary: String = "⛅ Clima"
    private var weatherDetails: String = "Obteniendo pronóstico local desde Open-Meteo..."
    private var isDaytimeWeather: Boolean = true

    private val mediaCatalog = ArrayList<MediaItem>()

    private val screensaverIdleRunnable = Runnable {
        if (!isFinishing && !isScreensaverActive) {
            val timeoutMin = prefs.getInt("screensaver_timeout_min", 5)
            if (timeoutMin > 0) {
                startBuiltinScreensaver()
            }
        }
    }

    private val screensaverDriftRunnable = object : Runnable {
        override fun run() {
            if (isScreensaverActive) {
                driftScreensaverClock()
                mainHandler.postDelayed(this, 35_000L)
            }
        }
    }

    // Variación 100% automática de Películas, Series y Shows en segundo plano
    private val mediaAutoRotateRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isScreensaverActive && !isDrawerOpen) {
                val focused = window.currentFocus
                val userInMediaRow = focused != null && isDescendantOf(focused, mediaRow)
                if (!userInMediaRow) {
                    rotateMediaShowcaseNow()
                }
            }
            mainHandler.postDelayed(this, 180_000L)
        }
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            cachedRawApps = null
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar que el salvapantallas propio venga ACTIVO por defecto (5 min) si el usuario no lo ha cambiado
        if (!prefs.contains("screensaver_timeout_min")) {
            prefs.edit().putInt("screensaver_timeout_min", 5).apply()
        }

        selectedMediaTab = prefs.getString("selected_media_tab", "EXPLORE") ?: "EXPLORE"

        rootContainer = findViewById(R.id.rootContainer)
        mainContentColumn = findViewById(R.id.mainContentColumn)
        clockView = findViewById(R.id.clock)
        clockDivider = findViewById(R.id.clockDivider)
        dateClockView = findViewById(R.id.dateClock)
        greeting = findViewById(R.id.greeting)
        weatherPill = findViewById(R.id.weatherPill)
        networkPill = findViewById(R.id.networkPill)
        inputsPill = findViewById(R.id.inputsPill)
        settingsPill = findViewById(R.id.settings)
        exploreSection = findViewById(R.id.exploreSection)
        tabsHeaderRow = findViewById(R.id.tabsHeaderRow)
        tabExplore = findViewById(R.id.tabExplore)
        tabMovies = findViewById(R.id.tabMovies)
        tabShows = findViewById(R.id.tabShows)
        tabSeries = findViewById(R.id.tabSeries)
        spotlightStatus = findViewById(R.id.spotlightStatus)
        mediaRow = findViewById(R.id.mediaRow)
        dockContainer = findViewById(R.id.dockContainer)
        pullDownHint = findViewById(R.id.pullDownHint)
        favRow = findViewById(R.id.favRow)
        appsDrawerSection = findViewById(R.id.appsDrawerSection)
        allAppsLabel = findViewById(R.id.allAppsLabel)
        allGrid = findViewById(R.id.allGrid)
        hint = findViewById(R.id.hint)
        appCount = findViewById(R.id.appCount)
        ambientGlow = findViewById(R.id.ambientGlow)

        catAll = findViewById(R.id.catAll)
        catRecent = findViewById(R.id.catRecent)
        catStreaming = findViewById(R.id.catStreaming)
        catMedia = findViewById(R.id.catMedia)
        catTools = findViewById(R.id.catTools)

        sideDrawerOverlay = findViewById(R.id.sideDrawerOverlay)
        sideDrawerPanel = findViewById(R.id.sideDrawerPanel)
        drawerHeaderTitle = findViewById(R.id.drawerHeaderTitle)
        drawerHeaderSubtitle = findViewById(R.id.drawerHeaderSubtitle)
        drawerHeaderDivider = findViewById(R.id.drawerHeaderDivider)
        drawerList = findViewById(R.id.drawerList)

        sideDrawerPanel.clipToOutline = true

        setupBuiltinScreensaverOverlay()

        val density = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels
        val dockInnerWidth = screenWidthPx - (112 * density).toInt()
        val rowItemWidth = (dockInnerWidth / 6).coerceAtLeast((120 * density).toInt())

        mediaAdapter = MediaAdapter(::onMediaClicked, ::onMediaFocused)
        favAdapter = AppAdapter(rowItemWidth, false, ::open, ::showAppOptionsDrawer) { app ->
            onAppFocused(app, fromGrid = false)
        }
        allAdapter = AppAdapter(0, true, ::open, ::showAppOptionsDrawer) { app ->
            onAppFocused(app, fromGrid = true)
        }
        drawerAdapter = DrawerAdapter { opt -> opt.action.invoke() }

        mediaRow.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false).apply {
            initialPrefetchItemCount = 5
        }
        mediaRow.setHasFixedSize(true)
        mediaRow.adapter = mediaAdapter
        mediaRow.itemAnimator = null

        favRow.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false).apply {
            initialPrefetchItemCount = 6
        }
        favRow.setHasFixedSize(true)
        favRow.adapter = favAdapter
        favRow.itemAnimator = null

        allGrid.layoutManager = GridLayoutManager(this, 6)
        allGrid.setHasFixedSize(true)
        allGrid.adapter = allAdapter
        allGrid.itemAnimator = null

        drawerList.layoutManager = LinearLayoutManager(this)
        drawerList.adapter = drawerAdapter
        drawerList.itemAnimator = null

        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(pkgReceiver, pkgFilter)

        setupHeaderButtons()
        setupMediaTabs()
        setupCategoryTabs()
        seedCuratedMedia(advanceVariation = true)
        updateMediaShowcase()
        applyThemeBackground()
        applyAmbientGlow(Color.parseColor("#3B82F6"), force = true)

        val cachedWeather = prefs.getString("cached_weather", null)
        if (cachedWeather != null) {
            weatherPill.text = cachedWeather
            weatherSummary = cachedWeather
        }
        fetchLiveWeather()
        fetchMediaArtworkAsync()
        resetScreensaverIdleTimer()
        mainHandler.removeCallbacks(mediaAutoRotateRunnable)
        mainHandler.postDelayed(mediaAutoRotateRunnable, 180_000L)

        bgExecutor.execute {
            SystemCommandRunner.enableAccessibilityAndDefaultHome(this)
        }
    }

    private fun setupBuiltinScreensaverOverlay() {
        val density = resources.displayMetrics.density
        screensaverOverlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
            elevation = 100f * density
            isClickable = true
            isFocusable = false
            setOnClickListener {
                stopBuiltinScreensaver()
            }
        }

        screensaverFloatingBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            alpha = 0.82f
        }

        val bigClock = TextClock(this).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            textSize = 72f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }

        val subDate = TextClock(this).apply {
            format12Hour = "EEEE, d 'de' MMMM"
            format24Hour = "EEEE, d 'de' MMMM"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, (4 * density).toInt(), 0, 0)
        }

        screensaverWeatherText = TextView(this).apply {
            text = weatherSummary
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, (10 * density).toInt(), 0, 0)
        }

        val brandHint = TextView(this).apply {
            text = "FastHomeTv OLED • Pulsa cualquier tecla para volver"
            setTextColor(Color.parseColor("#334155"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, (14 * density).toInt(), 0, 0)
        }

        screensaverFloatingBox.addView(bigClock)
        screensaverFloatingBox.addView(subDate)
        screensaverFloatingBox.addView(screensaverWeatherText)
        screensaverFloatingBox.addView(brandHint)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        screensaverOverlay.addView(screensaverFloatingBox, lp)
        rootContainer.addView(screensaverOverlay)
    }

    private fun startBuiltinScreensaver(forcePureBlack: Boolean? = null) {
        if (isScreensaverActive) return
        if (isDrawerOpen) closeSideDrawer()
        isScreensaverActive = true
        mainHandler.removeCallbacks(screensaverIdleRunnable)
        mainHandler.removeCallbacks(screensaverDriftRunnable)

        val style = prefs.getInt("screensaver_style", 0)
        val pureBlack = forcePureBlack ?: (style == 1)

        screensaverWeatherText.text = weatherSummary
        screensaverFloatingBox.visibility = if (pureBlack) View.GONE else View.VISIBLE
        screensaverFloatingBox.translationX = 0f
        screensaverFloatingBox.translationY = 0f
        screensaverFloatingBox.alpha = 0.82f

        screensaverOverlay.alpha = 0f
        screensaverOverlay.visibility = View.VISIBLE
        screensaverOverlay.animate().alpha(1f).setDuration(400L).start()

        if (!pureBlack) {
            mainHandler.postDelayed(screensaverDriftRunnable, 35_000L)
        }
        notifyHomeAssistantAutoEvent("screensaver_on", "FastHomeTv OLED")
    }

    private fun stopBuiltinScreensaver() {
        if (!isScreensaverActive) return
        isScreensaverActive = false
        mainHandler.removeCallbacks(screensaverDriftRunnable)
        screensaverOverlay.visibility = View.GONE
        screensaverOverlay.alpha = 0f
        resetScreensaverIdleTimer()
        notifyHomeAssistantAutoEvent("screensaver_off", "FastHomeTv Active")
    }

    private fun driftScreensaverClock() {
        val maxDx = (rootContainer.width * 0.26f).coerceAtLeast(1f)
        val maxDy = (rootContainer.height * 0.26f).coerceAtLeast(1f)
        val nextX = Random.nextFloat() * (2f * maxDx) - maxDx
        val nextY = Random.nextFloat() * (2f * maxDy) - maxDy

        screensaverFloatingBox.animate()
            .alpha(0f)
            .setDuration(550L)
            .withEndAction {
                screensaverFloatingBox.translationX = nextX
                screensaverFloatingBox.translationY = nextY
                screensaverWeatherText.text = weatherSummary
                screensaverFloatingBox.animate()
                    .alpha(0.82f)
                    .setDuration(550L)
                    .start()
            }
            .start()
    }

    private fun resetScreensaverIdleTimer() {
        mainHandler.removeCallbacks(screensaverIdleRunnable)
        val timeoutMin = prefs.getInt("screensaver_timeout_min", 5)
        if (timeoutMin > 0 && !isScreensaverActive) {
            mainHandler.postDelayed(screensaverIdleRunnable, timeoutMin * 60_000L)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(screensaverIdleRunnable)
        mainHandler.removeCallbacks(screensaverDriftRunnable)
        mainHandler.removeCallbacks(mediaAutoRotateRunnable)
        try {
            unregisterReceiver(pkgReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(screensaverIdleRunnable)
        mainHandler.removeCallbacks(mediaAutoRotateRunnable)
        if (isScreensaverActive) {
            stopBuiltinScreensaver()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (isScreensaverActive) stopBuiltinScreensaver()
        if (isDrawerOpen) closeSideDrawer()
        mainContentColumn.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        setAppsExpanded(false)
        forceDockFocusOnNextRefresh = true
        focusDockFirstItem()
        resetScreensaverIdleTimer()
    }

    private fun focusDockFirstItem() {
        mainContentColumn.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (favAdapter.itemCount > 0) {
            favRow.visibility = View.VISIBLE
            val immediate = (favRow.findViewHolderForAdapterPosition(0)?.itemView ?: favRow.getChildAt(0))
                ?.findViewById<View>(R.id.card)
            if (immediate != null && immediate.requestFocus()) {
                return
            }
            favRow.scrollToPosition(0)
            favRow.post {
                val targetCard = (favRow.findViewHolderForAdapterPosition(0)?.itemView ?: favRow.getChildAt(0))
                    ?.findViewById<View>(R.id.card)
                if (targetCard != null) {
                    targetCard.requestFocus()
                } else {
                    focusAllGridFirstItem()
                }
            }
        } else {
            focusAllGridFirstItem()
        }
    }

    private fun focusMediaRowOrDock() {
        mainContentColumn.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (isAppsExpanded) {
            setAppsExpanded(false)
        }
        if (mediaAdapter.itemCount == 0) {
            focusDockFirstItem()
            return
        }
        val rememberedIdx = (lastFocusedMediaIndexByTab[selectedMediaTab] ?: 0)
            .coerceIn(0, (mediaAdapter.itemCount - 1).coerceAtLeast(0))
        val immediate = mediaRow.findViewHolderForAdapterPosition(rememberedIdx)?.itemView
            ?: mediaRow.getChildAt(0)
        if (immediate != null && immediate.requestFocus()) {
            return
        }
        mediaRow.scrollToPosition(rememberedIdx)
        mediaRow.post {
            val delayed = mediaRow.findViewHolderForAdapterPosition(rememberedIdx)?.itemView
                ?: mediaRow.getChildAt(0)
            if (delayed == null || !delayed.requestFocus()) {
                focusDockFirstItem()
            }
        }
    }

    private fun focusAllGridFirstItem() {
        mainContentColumn.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (!isAppsExpanded) {
            setAppsExpanded(true)
        }
        if (allAdapter.itemCount > 0) {
            val immediate = (allGrid.findViewHolderForAdapterPosition(0)?.itemView ?: allGrid.getChildAt(0))
                ?.findViewById<View>(R.id.card)
            if (immediate != null && immediate.requestFocus()) {
                return
            }
            allGrid.scrollToPosition(0)
            allGrid.post {
                val delayed = (allGrid.findViewHolderForAdapterPosition(0)?.itemView ?: allGrid.getChildAt(0))
                    ?.findViewById<View>(R.id.card)
                if (delayed == null || !delayed.requestFocus()) {
                    getActiveCategoryTabView().requestFocus()
                }
            }
        } else {
            getActiveCategoryTabView().requestFocus()
        }
    }

    private fun setupHeaderButtons() {
        weatherPill.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.05f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(100).start()
            if (focused && !isDrawerOpen) {
                if (isAppsExpanded) setAppsExpanded(false)
                spotlightStatus.text = "Clima Local ($weatherSummary) • Pulsa OK para ver pronóstico"
            }
        }

        networkPill.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.05f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(100).start()
            if (focused && !isDrawerOpen) {
                if (isAppsExpanded) setAppsExpanded(false)
                spotlightStatus.text = "Red e Internet • Pulsa OK para configurar Wi-Fi o Ethernet"
            }
        }

        inputsPill.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.05f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(100).start()
            if (focused && !isDrawerOpen) {
                if (isAppsExpanded) setAppsExpanded(false)
                spotlightStatus.text = "Entradas, Pantalla y Sonido • Pulsa OK para abrir opciones"
            }
        }

        settingsPill.setOnFocusChangeListener { v, focused ->
            val s = if (focused) 1.05f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(100).start()
            if (focused && !isDrawerOpen) {
                if (isAppsExpanded) setAppsExpanded(false)
                spotlightStatus.text = "Ajustes • Pulsa OK para abrir opciones de FastHomeTv y Android TV"
            }
        }

        weatherPill.setOnClickListener {
            showWeatherDrawer()
        }

        networkPill.setOnClickListener {
            openNetworkSettings()
        }

        inputsPill.setOnClickListener {
            showInputsAndDisplayDrawer()
        }

        settingsPill.setOnClickListener {
            showUnifiedSettingsDrawer()
        }
    }

    private fun openNetworkSettings() {
        openFirstAvailableIntent(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.connectivity.NetworkActivity"),
            Intent("android.settings.panel.action.INTERNET_CONNECTIVITY"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun openDisplayAndSoundSettings() {
        openFirstAvailableIntent(
            Intent(Settings.ACTION_SOUND_SETTINGS),
            Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.displaysound.DisplaySoundActivity"),
            Intent().setClassName("com.droidlogic.tv.settings", "com.droidlogic.tv.settings.SoundActivity"),
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun showInputsAndDisplayDrawer() {
        val options = listOf(
            DrawerOption(
                iconRes = R.drawable.ic_tv,
                title = "Pantalla, Resolución HDMI y Sonido",
                subtitle = "Ajustar resolución (4K/1080p), HDR, HDMI-CEC y formato de audio",
                badge = "HDMI",
                iconTint = Color.parseColor("#38BDF8")
            ) {
                closeSideDrawer()
                openDisplayAndSoundSettings()
            },
            DrawerOption(
                iconRes = R.drawable.ic_settings,
                title = "Ajustar Resolución de Pantalla",
                subtitle = "Cambiar modo de salida de video del Decodificador / TV Box",
                badge = "VIDEO",
                iconTint = Color.parseColor("#4ADE80")
            ) {
                closeSideDrawer()
                openFirstAvailableIntent(
                    Intent().setClassName("com.droidlogic.tv.settings", "com.droidlogic.tv.settings.sliceprovider.dialog.AdjustResolutionDialogActivity"),
                    Intent(Settings.ACTION_SOUND_SETTINGS),
                    Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.displaysound.DisplaySoundActivity"),
                    Intent(Settings.ACTION_SETTINGS)
                )
            },
            DrawerOption(
                iconRes = R.drawable.ic_tv,
                title = "Cambiar Entrada de TV (HDMI / AV)",
                subtitle = "Selector de entradas para Smart TVs con Android TV / Google TV",
                badge = "INPUTS",
                iconTint = Color.parseColor("#FBBF24")
            ) {
                closeSideDrawer()
                openFirstAvailableIntent(
                    Intent("com.android.tv.action.VIEW_INPUTS"),
                    Intent(Settings.ACTION_SOUND_SETTINGS),
                    Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.displaysound.DisplaySoundActivity"),
                    Intent(Settings.ACTION_SETTINGS)
                )
            },
            DrawerOption(
                iconRes = R.drawable.ic_wifi,
                title = "Control Remoto y Accesorios Bluetooth",
                subtitle = "Vincular control remoto Bluetooth, audífonos o gamepad",
                badge = "CONTROL",
                iconTint = Color.parseColor("#C084FC")
            ) {
                closeSideDrawer()
                openFirstAvailableIntent(
                    Intent().setClassName("com.android.tv.settings", "com.customize.ipg.rcu.IPGRemoteActivity"),
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                    Intent(Settings.ACTION_SETTINGS)
                )
            },
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Cerrar panel",
                subtitle = "Volver a la pantalla principal"
            ) {
                closeSideDrawer()
            }
        )

        openSideDrawer(
            title = "Entradas y Pantalla",
            subtitle = "Opciones de video HDMI, sonido, entradas y control remoto",
            options = options
        )
    }

    private fun getActiveMediaTabView(): TextView {
        return when (selectedMediaTab) {
            "MOVIES" -> tabMovies
            "SHOWS" -> tabShows
            "SERIES" -> tabSeries
            else -> tabExplore
        }
    }

    private fun getActiveCategoryTabView(): TextView {
        return when (selectedCategory) {
            "RECENT" -> catRecent
            "STREAMING" -> catStreaming
            "MEDIA" -> catMedia
            "TOOLS" -> catTools
            else -> catAll
        }
    }

    private fun selectMediaTab(tab: String) {
        if (selectedMediaTab != tab) {
            selectedMediaTab = tab
            prefs.edit().putString("selected_media_tab", tab).apply()
            updateMediaTabStyles()
            updateMediaShowcase()
            val targetIdx = (lastFocusedMediaIndexByTab[tab] ?: 0)
                .coerceIn(0, (mediaAdapter.itemCount - 1).coerceAtLeast(0))
            mediaRow.scrollToPosition(targetIdx)
        } else {
            updateMediaTabStyles()
        }
    }

    private fun setupMediaTabs() {
        val tabs = listOf(
            Triple(tabExplore, "EXPLORE", "Explora • Recomendaciones destacadas para hoy"),
            Triple(tabMovies, "MOVIES", "Películas • Estrenos y cine aclamado"),
            Triple(tabShows, "SHOWS", "Shows • Programas, anime y entretenimiento"),
            Triple(tabSeries, "SERIES", "Series • Las mejores series del momento")
        )
        for ((view, tab, statusDesc) in tabs) {
            view.setOnFocusChangeListener { v, focused ->
                val s = if (focused) 1.05f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(100).start()
                if (focused && !isDrawerOpen) {
                    if (isAppsExpanded) setAppsExpanded(false)
                    spotlightStatus.text = statusDesc
                    selectMediaTab(tab)
                }
            }
            view.setOnClickListener {
                selectMediaTab(tab)
            }
        }
        updateMediaTabStyles()
    }

    private fun updateMediaTabStyles() {
        val map = mapOf(
            tabExplore to "EXPLORE",
            tabMovies to "MOVIES",
            tabShows to "SHOWS",
            tabSeries to "SERIES"
        )
        for ((view, tab) in map) {
            val active = selectedMediaTab == tab
            view.isSelected = active
            view.alpha = if (active) 1.0f else 0.75f
            view.text = when (tab) {
                "EXPLORE" -> if (active) "● EXPLORA" else "EXPLORA"
                "MOVIES" -> if (active) "● PELÍCULAS" else "PELÍCULAS"
                "SHOWS" -> if (active) "● SHOWS" else "SHOWS"
                else -> if (active) "● SERIES" else "SERIES"
            }
        }
    }

    private fun setupCategoryTabs() {
        val tabs = listOf(
            Triple(catAll, "ALL", "Filtro: Todas las aplicaciones"),
            Triple(catRecent, "RECENT", "Filtro: Aplicaciones abiertas recientemente"),
            Triple(catStreaming, "STREAMING", "Filtro: Aplicaciones de Streaming y TV"),
            Triple(catMedia, "MEDIA", "Filtro: Reproductores de video y música"),
            Triple(catTools, "TOOLS", "Filtro: Herramientas y sistema")
        )
        for ((view, cat, desc) in tabs) {
            view.setOnFocusChangeListener { v, focused ->
                val s = if (focused) 1.05f else 1f
                v.animate().scaleX(s).scaleY(s).setDuration(100).start()
                if (focused && !isDrawerOpen) {
                    if (!isAppsExpanded) setAppsExpanded(true)
                    spotlightStatus.text = desc
                    if (selectedCategory != cat) {
                        selectedCategory = cat
                        updateCategoryTabStyles()
                        refresh()
                    }
                }
            }
            view.setOnClickListener {
                selectedCategory = cat
                updateCategoryTabStyles()
                refresh()
            }
        }
        updateCategoryTabStyles()
    }

    private fun updateCategoryTabStyles() {
        val map = mapOf(
            catAll to "ALL",
            catRecent to "RECENT",
            catStreaming to "STREAMING",
            catMedia to "MEDIA",
            catTools to "TOOLS"
        )
        for ((view, cat) in map) {
            val active = selectedCategory == cat
            view.isSelected = active
            view.alpha = if (active) 1.0f else 0.75f
            view.text = when (cat) {
                "ALL" -> if (active) "● Todas" else "Todas"
                "RECENT" -> if (active) "● Recientes" else "Recientes"
                "STREAMING" -> if (active) "● Streaming y TV" else "Streaming y TV"
                "MEDIA" -> if (active) "● Reproductores" else "Reproductores"
                else -> if (active) "● Herramientas" else "Herramientas"
            }
        }
    }

    private fun setAppsExpanded(expanded: Boolean) {
        if (isAppsExpanded == expanded) return
        isAppsExpanded = expanded

        val density = resources.displayMetrics.density
        if (expanded) {
            pullDownHint.visibility = View.GONE
            exploreSection.visibility = View.GONE
            appsDrawerSection.visibility = View.VISIBLE

            dockContainer.translationY = 48f * density
            dockContainer.animate()
                .translationY(0f)
                .setDuration(200L)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .start()

            appsDrawerSection.alpha = 0f
            appsDrawerSection.translationY = 36f * density
            appsDrawerSection.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .start()
        } else {
            appsDrawerSection.visibility = View.GONE
            exploreSection.visibility = View.VISIBLE
            pullDownHint.visibility = View.VISIBLE

            exploreSection.alpha = 0f
            exploreSection.translationY = -28f * density
            exploreSection.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200L)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .start()

            dockContainer.translationY = -36f * density
            dockContainer.animate()
                .translationY(0f)
                .setDuration(200L)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .start()
        }
    }

    private fun seedCuratedMedia(advanceVariation: Boolean = false) {
        val prevShift = prefs.getInt("media_variation_shift", 0)
        val shift = if (advanceVariation) (prevShift + 1) % 24 else prevShift
        if (advanceVariation) {
            prefs.edit().putInt("media_variation_shift", shift).apply()
        }

        val rawPool = listOf(
            // 1. EXPLORA (8 títulos variados)
            MediaItem("dark_matter", "Dark Matter", "Sci-Fi & Thriller • 2 temporadas • Apple TV+", "DESTACADO", "EXPLORE", "", false, Color.parseColor("#1E3A5F"), Color.parseColor("#0B1528")),
            MediaItem("fallout", "Fallout", "Sci-Fi & Acción • Temporada 1 • Prime Video", "ESTRENO #1", "EXPLORE", "", false, Color.parseColor("#B45309"), Color.parseColor("#1C1917")),
            MediaItem("the_last_of_us", "The Last of Us", "Drama & Sci-Fi • Max", "NUEVA TEMP", "EXPLORE", "", false, Color.parseColor("#14532D"), Color.parseColor("#052E16")),
            MediaItem("severance", "Severance", "Misterio & Sci-Fi • Apple TV+", "ACLAMADA", "EXPLORE", "", false, Color.parseColor("#0F766E"), Color.parseColor("#042F2E")),
            MediaItem("the_boys", "The Boys", "Acción & Sátira • 4 temporadas • Prime Video", "TENDENCIA", "EXPLORE", "", false, Color.parseColor("#991B1B"), Color.parseColor("#090A0F")),
            MediaItem("three_body", "3 Body Problem", "Sci-Fi & Misterio • Netflix", "TOP GLOBAL", "EXPLORE", "", false, Color.parseColor("#1D4ED8"), Color.parseColor("#0F172A")),
            MediaItem("jojo", "JoJo's Bizarre Adventure", "Anime • Acción & Aventura • Netflix", "FAVORITO", "EXPLORE", "", false, Color.parseColor("#6D28D9"), Color.parseColor("#1E1B4B")),
            MediaItem("law_and_order", "Law & Order", "Crimen & Drama • Prime Video", "TOP 10", "EXPLORE", "", false, Color.parseColor("#7C2D12"), Color.parseColor("#1C1917")),

            // 2. PELÍCULAS (8 títulos variados)
            MediaItem("dune_part_two", "Dune: Part Two", "Película • Sci-Fi & Aventura • 2h 46m • 4K HDR", "ESTRENO CINE", "MOVIES", "", false, Color.parseColor("#B45309"), Color.parseColor("#271206")),
            MediaItem("oppenheimer", "Oppenheimer", "Película • Drama Histórico • 3h 00m", "OSCAR WINNER", "MOVIES", "", false, Color.parseColor("#9A3412"), Color.parseColor("#1C1917")),
            MediaItem("gladiator_ii", "Gladiator II", "Película • Acción Épica • Ridley Scott", "TAQUILLERA", "MOVIES", "", false, Color.parseColor("#78350F"), Color.parseColor("#1C1917")),
            MediaItem("deadpool_wolverine", "Deadpool & Wolverine", "Película • Acción & Comedia • Disney+", "4K IMAX", "MOVIES", "", false, Color.parseColor("#BE123C"), Color.parseColor("#172554")),
            MediaItem("interstellar", "Interstellar", "Película • Sci-Fi • Christopher Nolan", "IMPRESCINDIBLE", "MOVIES", "", false, Color.parseColor("#1D4ED8"), Color.parseColor("#0F172A")),
            MediaItem("spider_verse", "Spider-Man: Across the Spider-Verse", "Película • Animación & Acción • 2h 20m", "TOP STREAMING", "MOVIES", "", false, Color.parseColor("#BE123C"), Color.parseColor("#1E1B4B")),
            MediaItem("the_batman", "The Batman", "Película • Acción & Crimen • 2h 56m", "4K DOLBY", "MOVIES", "", false, Color.parseColor("#7F1D1D"), Color.parseColor("#090A0F")),
            MediaItem("mad_max_furiosa", "Furiosa: A Mad Max Saga", "Película • Acción & Aventura • 4K HDR", "ADRENALINA", "MOVIES", "", false, Color.parseColor("#C2410C"), Color.parseColor("#18181B")),

            // 3. SHOWS (8 títulos variados)
            MediaItem("arcane", "Arcane", "Show • Animación & Acción • Netflix", "SHOW #1", "SHOWS", "", false, Color.parseColor("#4C1D95"), Color.parseColor("#0F172A")),
            MediaItem("solo_leveling", "Solo Leveling", "Anime • Acción & Fantasía • Crunchyroll", "ESTRENO", "SHOWS", "", false, Color.parseColor("#1E40AF"), Color.parseColor("#090D16")),
            MediaItem("invincible", "Invincible", "Show • Superhéroes & Acción • Prime Video", "NUEVA TEMP", "SHOWS", "", false, Color.parseColor("#1D4ED8"), Color.parseColor("#172554")),
            MediaItem("jujutsu_kaisen", "Jujutsu Kaisen", "Anime • Acción Sobrenatural • Crunchyroll", "TOP ANIME", "SHOWS", "", false, Color.parseColor("#6D28D9"), Color.parseColor("#0F172A")),
            MediaItem("rick_and_morty", "Rick and Morty", "Show • Sci-Fi & Comedia • Max", "TENDENCIA", "SHOWS", "", false, Color.parseColor("#047857"), Color.parseColor("#064E3B")),
            MediaItem("attack_on_titan", "Attack on Titan", "Anime • Acción Épica • Crunchyroll", "FINAL", "SHOWS", "", false, Color.parseColor("#7C2D12"), Color.parseColor("#1C1917")),
            MediaItem("one_piece", "One Piece", "Show • Aventura • Netflix", "POPULAR", "SHOWS", "", false, Color.parseColor("#B91C1C"), Color.parseColor("#1F2937")),
            MediaItem("x_men_97", "X-Men '97", "Show • Animación & Marvel • Disney+", "ACLAMADA", "SHOWS", "", false, Color.parseColor("#B45309"), Color.parseColor("#1E3A8A")),

            // 4. SERIES (8 títulos variados)
            MediaItem("shogun", "Shōgun", "Serie • Drama Épico • Disney+", "SERIE DEL AÑO", "SERIES", "", false, Color.parseColor("#78350F"), Color.parseColor("#1C1917")),
            MediaItem("the_penguin", "The Penguin", "Serie • Crimen & Drama • Max", "ESTRENO", "SERIES", "", false, Color.parseColor("#4C1D95"), Color.parseColor("#090A0F")),
            MediaItem("the_bear", "The Bear", "Serie • Comedia & Drama • Disney+", "3 TEMPORADAS", "SERIES", "", false, Color.parseColor("#1E40AF"), Color.parseColor("#0F172A")),
            MediaItem("house_of_dragon", "House of the Dragon", "Serie • Fantasía & Acción • Max", "TENDENCIA", "SERIES", "", false, Color.parseColor("#991B1B"), Color.parseColor("#18181B")),
            MediaItem("silo", "Silo", "Serie • Sci-Fi & Misterio • Apple TV+", "NUEVA TEMP", "SERIES", "", false, Color.parseColor("#374151"), Color.parseColor("#111827")),
            MediaItem("stranger_things", "Stranger Things", "Serie • Sci-Fi & Suspenso • Netflix", "POPULAR", "SERIES", "", false, Color.parseColor("#9F1239"), Color.parseColor("#0F172A")),
            MediaItem("breaking_bad", "Breaking Bad", "Serie • Crimen & Drama • Netflix", "OBRA MAESTRA", "SERIES", "", false, Color.parseColor("#065F46"), Color.parseColor("#022C22")),
            MediaItem("reacher", "Reacher", "Serie • Acción & Suspenso • Prime Video", "TOP ACCIÓN", "SERIES", "", false, Color.parseColor("#1E3A8A"), Color.parseColor("#0F172A"))
        )

        mediaCatalog.clear()
        val categories = listOf("EXPLORE", "MOVIES", "SHOWS", "SERIES")
        for ((catIdx, cat) in categories.withIndex()) {
            val catItems = rawPool.filter { it.category == cat }
            if (catItems.isEmpty()) continue
            val offset = (shift + catIdx) % catItems.size
            val rotated = catItems.drop(offset) + catItems.take(offset)
            // Mostrar 6 tarjetas por pestaña: la primera siempre como Hero 16:9 y las 5 siguientes como pósters 2:3
            for ((index, baseItem) in rotated.take(6).withIndex()) {
                val isWide = index == 0
                val cacheKey = "${baseItem.id}_${if (isWide) "wide" else "poster"}"
                val memBmp = synchronized(bitmapCache) { bitmapCache[cacheKey] }
                mediaCatalog.add(
                    baseItem.copy(
                        id = cacheKey,
                        isHeroWide = isWide,
                        tag = if (isWide) "DESTACADO • ${baseItem.tag}" else baseItem.tag,
                        bitmap = memBmp
                    )
                )
            }
        }
    }

    private fun rotateMediaShowcaseNow() {
        seedCuratedMedia(advanceVariation = true)
        lastFocusedMediaIndexByTab[selectedMediaTab] = 0
        updateMediaShowcase()
        fetchMediaArtworkAsync()
    }

    private fun updateMediaShowcase() {
        val filtered = mediaCatalog.filter { it.category == selectedMediaTab }
        mediaAdapter.submit(filtered)
    }

    private fun fetchMediaArtworkAsync() {
        val cacheDir = File(filesDir, "media_cache_v2").apply { mkdirs() }
        val decodeOpts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        bgExecutor.execute {
            for (item in mediaCatalog.toList()) {
                val memCached = synchronized(bitmapCache) { bitmapCache[item.id] }
                if (memCached != null) {
                    item.bitmap = memCached
                    continue
                }

                val diskFile = File(cacheDir, "${item.id}.jpg")
                if (diskFile.exists() && diskFile.length() > 500) {
                    val diskBmp = BitmapFactory.decodeFile(diskFile.absolutePath, decodeOpts)
                    if (diskBmp != null) {
                        synchronized(bitmapCache) { bitmapCache[item.id] = diskBmp }
                        item.bitmap = diskBmp
                        mainHandler.post { mediaAdapter.notifyArtworkLoaded(item.id) }
                        continue
                    }
                }

                try {
                    val resolvedUrl = resolveLiveArtworkUrl(item)
                    if (resolvedUrl.isNotBlank()) {
                        val conn = URL(resolvedUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 4000
                        conn.readTimeout = 4000
                        if (conn.responseCode == 200) {
                            val rawBytes = conn.inputStream.readBytes()
                            val rawBmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                            if (rawBmp != null) {
                                val targetW = if (item.isHeroWide) 420 else 210
                                val targetH = 260
                                val scaled = scaleCenterCrop(rawBmp, targetW, targetH)
                                if (scaled !== rawBmp) rawBmp.recycle()

                                try {
                                    FileOutputStream(diskFile).use { out ->
                                        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                    }
                                } catch (_: Exception) {
                                }

                                synchronized(bitmapCache) { bitmapCache[item.id] = scaled }
                                item.bitmap = scaled
                                mainHandler.post {
                                    mediaAdapter.notifyArtworkLoaded(item.id)
                                }
                            }
                        }
                        conn.disconnect()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun scaleCenterCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (src.width <= targetW && src.height <= targetH) return src
        val scale = maxOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val scaledW = Math.round(scale * src.width).coerceAtLeast(1)
        val scaledH = Math.round(scale * src.height).coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
    }

    private fun resolveLiveArtworkUrl(item: MediaItem): String {
        val query = URLEncoder.encode(item.title, "UTF-8")
        if (item.category == "MOVIES") {
            try {
                val api = "https://itunes.apple.com/search?term=$query&media=movie&entity=movie&limit=1"
                val conn = URL(api).openConnection() as HttpURLConnection
                conn.connectTimeout = 3500
                conn.readTimeout = 3500
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val results = json.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val art100 = results.getJSONObject(0).optString("artworkUrl100", "")
                        if (art100.isNotBlank()) {
                            conn.disconnect()
                            return art100.replace("100x100bb", if (item.isHeroWide) "600x340bb" else "300x450bb")
                        }
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
            }
        }

        try {
            val api = "https://api.tvmaze.com/singlesearch/shows?q=$query"
            val conn = URL(api).openConnection() as HttpURLConnection
            conn.connectTimeout = 3500
            conn.readTimeout = 3500
            if (conn.responseCode == 200) {
                val showJson = JSONObject(conn.inputStream.bufferedReader().readText())
                val showId = showJson.optInt("id", -1)
                val posterUrl = showJson.optJSONObject("image")?.optString("medium")
                    ?: showJson.optJSONObject("image")?.optString("original")
                    ?: ""
                conn.disconnect()

                if (item.isHeroWide && showId > 0) {
                    try {
                        val imgConn = URL("https://api.tvmaze.com/shows/$showId/images").openConnection() as HttpURLConnection
                        imgConn.connectTimeout = 3000
                        imgConn.readTimeout = 3000
                        if (imgConn.responseCode == 200) {
                            val arr = JSONArray(imgConn.inputStream.bufferedReader().readText())
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                if (obj.optString("type") == "background") {
                                    val res = obj.optJSONObject("resolutions")
                                    val bgUrl = res?.optJSONObject("medium")?.optString("url")
                                        ?: res?.optJSONObject("original")?.optString("url")
                                    if (!bgUrl.isNullOrBlank()) {
                                        imgConn.disconnect()
                                        return bgUrl
                                    }
                                }
                            }
                        }
                        imgConn.disconnect()
                    } catch (_: Exception) {
                    }
                }
                if (posterUrl.isNotBlank()) return posterUrl
            } else {
                conn.disconnect()
            }
        } catch (_: Exception) {
        }
        return item.imageUrl
    }

    private fun onMediaFocused(item: MediaItem) {
        if (isDrawerOpen) return
        if (isAppsExpanded) {
            setAppsExpanded(false)
        }
        lastFocusedMediaIndexByTab[selectedMediaTab] = mediaAdapter.indexOf(item)
        spotlightStatus.text = "${item.title} • Pulsa OK para ver o abrir en tus apps"
        applyAmbientGlow(item.fallbackColorStart)
    }

    private fun onMediaClicked(item: MediaItem) {
        val installed = getAppsCached()
        val options = mutableListOf<DrawerOption>()

        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_play,
                title = "Buscar Tráiler / Contenido en YouTube",
                subtitle = "Ver \"${item.title}\" en YouTube TV",
                badge = "YOUTUBE",
                iconTint = Color.parseColor("#F87171")
            ) {
                closeSideDrawer()
                searchOrOpenMedia(item.title, "com.google.android.youtube.tv")
            }
        )

        val streamingTargets = listOf(
            Triple(listOf("netflix"), "Abrir en Netflix", Color.parseColor("#EF4444")),
            Triple(listOf("disney"), "Abrir en Disney+", Color.parseColor("#38BDF8")),
            Triple(listOf("xuper", "mgstx", "magis"), "Abrir en Xuper TV / Magis", Color.parseColor("#A855F7")),
            Triple(listOf("mundogo"), "Abrir en Mundo GO", Color.parseColor("#4ADE80")),
            Triple(listOf("mxl"), "Abrir en MXL TV", Color.parseColor("#60A5FA"))
        )

        for ((keywords, actionTitle, tint) in streamingTargets) {
            val matchedApp = installed.firstOrNull { app ->
                val p = app.pkg.lowercase()
                val l = app.label.lowercase()
                keywords.any { kw -> (p.contains(kw) || l.contains(kw)) && !p.contains("tvmusic") }
            }
            if (matchedApp != null) {
                options.add(
                    DrawerOption(
                        iconRes = R.drawable.ic_tv,
                        title = actionTitle,
                        subtitle = "Iniciar ${matchedApp.label} para ver \"${item.title}\"",
                        badge = "ABRIR",
                        iconTint = tint
                    ) {
                        closeSideDrawer()
                        open(matchedApp)
                    }
                )
            }
        }

        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Cerrar panel",
                subtitle = "Volver al catálogo"
            ) {
                closeSideDrawer()
            }
        )

        openSideDrawer(
            title = item.title,
            subtitle = item.meta,
            options = options
        )
    }

    private fun searchOrOpenMedia(query: String, preferredPkg: String) {
        val installed = getAppsCached()
        val ytApp = installed.firstOrNull {
            it.pkg == preferredPkg || (it.pkg.lowercase().contains("youtube") && !it.pkg.lowercase().contains("music"))
        }
        try {
            val encoded = URLEncoder.encode("$query trailer español", "UTF-8")
            val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                if (ytApp != null) setPackage(ytApp.pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(ytIntent)
            return
        } catch (_: Exception) {
        }
        if (ytApp != null) {
            open(ytApp)
            return
        }
        launchPackageOrFallback(preferredPkg, query)
    }

    private fun launchPackageOrFallback(pkg: String, query: String) {
        val pm = packageManager
        val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (launch != null) {
            try {
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        try {
            val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(searchIntent)
        } catch (_: Exception) {
        }
    }

    private fun openSideDrawer(title: String, subtitle: String, options: List<DrawerOption>) {
        if (!isDrawerOpen) {
            viewToRestoreFocus = window.currentFocus
        }
        isDrawerOpen = true

        mainContentColumn.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        drawerHeaderTitle.text = title
        drawerHeaderSubtitle.text = subtitle
        drawerAdapter.submit(options)

        val density = resources.displayMetrics.density
        if (sideDrawerOverlay.visibility != View.VISIBLE) {
            sideDrawerOverlay.visibility = View.VISIBLE
            sideDrawerOverlay.alpha = 0f
            sideDrawerPanel.translationX = 410f * density

            sideDrawerOverlay.animate().alpha(1f).setDuration(180L).withLayer().start()
            sideDrawerPanel.animate()
                .translationX(0f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .withLayer()
                .start()
        }

        drawerList.post {
            drawerList.scrollToPosition(0)
            drawerList.post {
                drawerList.getChildAt(0)?.requestFocus()
            }
        }
    }

    private fun closeSideDrawer() {
        if (!isDrawerOpen) {
            mainContentColumn.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            sideDrawerOverlay.visibility = View.GONE
            return
        }
        isDrawerOpen = false
        mainContentColumn.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        val density = resources.displayMetrics.density

        val target = viewToRestoreFocus
        if (target != null && target.isShown) {
            target.requestFocus()
        } else {
            focusDockFirstItem()
        }

        sideDrawerOverlay.animate().alpha(0f).setDuration(150L).withLayer().start()
        sideDrawerPanel.animate()
            .translationX(410f * density)
            .setDuration(160L)
            .withLayer()
            .withEndAction {
                sideDrawerOverlay.visibility = View.GONE
                mainContentColumn.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                val restored = viewToRestoreFocus
                if (window.currentFocus == null || isDescendantOf(window.currentFocus!!, drawerList)) {
                    if (restored != null && restored.isShown) {
                        restored.requestFocus()
                    } else {
                        focusDockFirstItem()
                    }
                }
            }
            .start()
    }

    private fun showUnifiedSettingsDrawer() {
        val currentTheme = prefs.getInt("theme_mode", 1)
        val themeNames = arrayOf(
            "Tema Claro",
            "Auto Día/Noche",
            "Obsidiana OLED",
            "Azul Zafiro",
            "Púrpura Nébula",
            "Esmeralda",
            "Rojo Carmesí",
            "Dorado Ámbar",
            "Cian Ártico",
            "Gris Titanio"
        )
        val isAccActive = LauncherAccessibilityService.isEnabled(this)
        val isHomeDefault = SystemCommandRunner.isDefaultHomeLauncher(this)
        val ssTimeout = prefs.getInt("screensaver_timeout_min", 5)
        val ssBadge = if (ssTimeout > 0) "ACTIVO (${ssTimeout}m)" else "OFF"
        val batteryOk = SystemCommandRunner.isBatteryOptimizationIgnored(this)
        val allPermsOk = isAccActive && batteryOk
        val freeRamMb = SystemCommandRunner.getFreeRamMb(this)

        val options = listOf(
            // 1) APAGADO
            DrawerOption(
                iconRes = R.drawable.ic_power,
                title = "Apagado",
                subtitle = "Suspender, reiniciar decodificador/TV, menú de energía o pantalla OLED",
                badge = "ENERGÍA",
                badgeColor = Color.parseColor("#FCA5A5"),
                iconTint = Color.parseColor("#F87171")
            ) {
                showPowerDrawer()
            },
            // 2) RENDIMIENTO
            DrawerOption(
                iconRes = R.drawable.ic_refresh,
                title = "Rendimiento",
                subtitle = "Optimizar memoria RAM (${freeRamMb} MB libres), borrar caché de todas las apps y cerrar 2do plano",
                badge = "TURBO",
                badgeColor = Color.parseColor("#4ADE80"),
                iconTint = Color.parseColor("#4ADE80")
            ) {
                showPerformanceDrawer()
            },
            // 3) TEMA
            DrawerOption(
                iconRes = R.drawable.ic_palette,
                title = "Tema",
                subtitle = "Adapta toda la interfaz entre Tema Claro, OLED y colores de cine",
                badge = themeNames.getOrElse(currentTheme) { "Auto" },
                badgeColor = Color.parseColor("#FBBF24"),
                iconTint = Color.parseColor("#FBBF24")
            ) {
                showThemeDrawer()
            },
            // 4) SALVAPANTALLAS (SOLO QUE DIGA SALVAPANTALLAS)
            DrawerOption(
                iconRes = R.drawable.ic_moon,
                title = "Salvapantallas",
                subtitle = "Viene activo por defecto por seguridad • Iniciar ahora o ajustar minutos",
                badge = ssBadge,
                badgeColor = if (ssTimeout > 0) Color.parseColor("#4ADE80") else Color.parseColor("#F87171"),
                iconTint = Color.parseColor("#C084FC")
            ) {
                showScreensaverDrawer()
            },
            // 5) CONTROL INTELIGENTE (GOOGLE HOME, DOMÓTICA)
            DrawerOption(
                iconRes = R.drawable.ic_tv,
                title = "Control Inteligente (Google Home, Domótica)",
                subtitle = "Controla tus ampolletas Nexxt Home, interruptores inteligentes y cámaras Wi-Fi desde la TV",
                badge = "DOMÓTICA",
                badgeColor = Color.parseColor("#38BDF8"),
                iconTint = Color.parseColor("#38BDF8")
            ) {
                showHomeAssistantDrawer()
            },
            // 6) VISIBILIDAD APLICACIONES
            DrawerOption(
                iconRes = R.drawable.ic_eye,
                title = "Visibilidad Aplicaciones",
                subtitle = "Elegir qué aplicaciones se muestran u ocultan en el Launcher, Dock y Recientes",
                iconTint = Color.parseColor("#38BDF8")
            ) {
                showVisibilityDrawer()
            },
            // 7) CLIMA
            DrawerOption(
                iconRes = R.drawable.ic_weather,
                title = "Clima",
                subtitle = weatherSummary,
                badge = "VER",
                iconTint = Color.parseColor("#60A5FA")
            ) {
                showWeatherDrawer()
            },
            // 8) AJUSTES ANDROID
            DrawerOption(
                iconRes = R.drawable.ic_settings,
                title = "Ajustes Android",
                subtitle = "Configuración del sistema Android TV, Red Wi-Fi, HDMI/Sonido y Administrar Apps",
                badge = "SISTEMA",
                badgeColor = Color.parseColor("#A7F3D0"),
                iconTint = Color.parseColor("#34D399")
            ) {
                showAndroidSettingsSubDrawer()
            },
            // 9) PERMISOS Y BLINDAJE LAUNCHER
            DrawerOption(
                iconRes = R.drawable.ic_star,
                title = "Permisos y Blindaje Launcher",
                subtitle = "Aceptar permisos pendientes con el control y evitar desinstalación de FastHomeTv",
                badge = if (allPermsOk) "PROTEGIDO" else "REVISAR",
                badgeColor = if (allPermsOk) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24"),
                iconTint = if (allPermsOk) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24")
            ) {
                showPermissionsAndProtectionDrawer()
            },
            // 10) DEJAR COMO LAUNCHER PREDETERMINADO
            DrawerOption(
                iconRes = if (isHomeDefault) R.drawable.ic_check else R.drawable.ic_tv,
                title = "Dejar como Launcher Predeterminado",
                subtitle = if (isHomeDefault) {
                    "FastHomeTv ya está fijado como pantalla de inicio principal del sistema"
                } else {
                    "Fijar FastHomeTv como pantalla de inicio oficial al encender o pulsar HOME"
                },
                badge = if (isHomeDefault) "ACTIVO" else "FIJAR",
                badgeColor = if (isHomeDefault) Color.parseColor("#4ADE80") else Color.parseColor("#38BDF8"),
                iconTint = if (isHomeDefault) Color.parseColor("#4ADE80") else Color.parseColor("#38BDF8")
            ) {
                showDefaultLauncherDrawer()
            },
            // 11) FORZAR LAUNCHER
            DrawerOption(
                iconRes = R.drawable.ic_star,
                title = "Forzar Launcher",
                subtitle = "Fuerza siempre el inicio de FastHomeTv en Android TV / Google TV / Decos (Accesibilidad)",
                badge = if (isAccActive) "ACTIVO" else "ACTIVAR",
                badgeColor = if (isAccActive) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24"),
                iconTint = if (isAccActive) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24")
            ) {
                showAccessibilityForceDrawer()
            },
            // 12) COMANDOS ADB Y DEBLOAT
            DrawerOption(
                iconRes = R.drawable.ic_check,
                title = "Comandos ADB y Debloat",
                subtitle = "Debloat Mundo GO / Android TV, bloqueo OTA y velocidad 0.5x (FastHomeTv blindado)",
                badge = "ADB",
                badgeColor = Color.parseColor("#38BDF8"),
                iconTint = Color.parseColor("#38BDF8")
            ) {
                showAdbOptimizationDrawer()
            }
        )

        openSideDrawer(
            title = "Configuración",
            subtitle = "Opciones de FastHomeTv y Ajustes de Android TV",
            options = options
        )
    }

    private fun showAndroidSettingsSubDrawer() {
        val options = listOf(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Volver a Configuración",
                subtitle = "Regresar al menú principal de ajustes"
            ) {
                showUnifiedSettingsDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_settings,
                title = "Configuración de Android TV",
                subtitle = "Abrir el panel nativo de ajustes del sistema Android",
                badge = "SISTEMA",
                badgeColor = Color.parseColor("#A7F3D0"),
                iconTint = Color.parseColor("#34D399")
            ) {
                closeSideDrawer()
                openFirstAvailableIntent(
                    Intent(Settings.ACTION_SETTINGS),
                    Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings")
                )
            },
            DrawerOption(
                iconRes = R.drawable.ic_wifi,
                title = "Internet y Red Wi-Fi",
                subtitle = "Configurar red inalámbrica o Ethernet",
                badge = "RED",
                badgeColor = Color.parseColor("#A7F3D0"),
                iconTint = Color.parseColor("#34D399")
            ) {
                closeSideDrawer()
                openNetworkSettings()
            },
            DrawerOption(
                iconRes = R.drawable.ic_tv,
                title = "Entradas, Pantalla y Sonido",
                subtitle = "Resolución HDMI, sonido, entradas de TV y control remoto",
                badge = "TV",
                badgeColor = Color.parseColor("#A7F3D0"),
                iconTint = Color.parseColor("#34D399")
            ) {
                showInputsAndDisplayDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_apps,
                title = "Administrar Aplicaciones (Android)",
                subtitle = "Permisos, almacenamiento y desinstalación del sistema",
                badge = "APPS",
                badgeColor = Color.parseColor("#A7F3D0"),
                iconTint = Color.parseColor("#34D399")
            ) {
                closeSideDrawer()
                openFirstAvailableIntent(
                    Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
                    Intent(Settings.ACTION_APPLICATION_SETTINGS),
                    Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.apps.AppsActivity"),
                    Intent(Settings.ACTION_SETTINGS)
                )
            }
        )

        openSideDrawer(
            title = "Ajustes Android",
            subtitle = "Accesos directos a la configuración nativa del dispositivo",
            options = options
        )
    }

    private fun showScreensaverDrawer() {
        val currentTimeout = prefs.getInt("screensaver_timeout_min", 5)
        val currentStyle = prefs.getInt("screensaver_style", 0)

        val options = mutableListOf<DrawerOption>()
        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Volver a Configuración",
                subtitle = "Regresar al menú principal de ajustes"
            ) {
                showUnifiedSettingsDrawer()
            }
        )
        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_moon,
                title = "Iniciar Salvapantallas OLED Ahora",
                subtitle = "Activar inmediatamente el reloj + clima flotante sobre fondo negro puro",
                badge = "INICIAR",
                badgeColor = Color.parseColor("#4ADE80"),
                iconTint = Color.parseColor("#C084FC")
            ) {
                startBuiltinScreensaver(forcePureBlack = false)
            }
        )
        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_power,
                title = "Pantalla 100% Negra Total (Sin Reloj)",
                subtitle = "Apagar visualmente toda la luz de la pantalla (despierta con cualquier tecla)",
                badge = "NEGRO PURO",
                badgeColor = Color.parseColor("#38BDF8"),
                iconTint = Color.parseColor("#38BDF8")
            ) {
                startBuiltinScreensaver(forcePureBlack = true)
            }
        )
        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_palette,
                title = if (currentStyle == 0) "Estilo: Reloj + Clima Flotante OLED" else "Estilo: Pantalla 100% Negra Total",
                subtitle = "Pulsa OK para alternar el diseño cuando se activa automáticamente",
                badge = if (currentStyle == 0) "RELOJ OLED" else "NEGRO 100%",
                badgeColor = Color.parseColor("#FBBF24"),
                iconTint = Color.parseColor("#FBBF24")
            ) {
                val nextStyle = if (currentStyle == 0) 1 else 0
                prefs.edit().putInt("screensaver_style", nextStyle).apply()
                showScreensaverDrawer()
            }
        )

        val timeouts = listOf(
            Triple(3, "Activar automáticamente a los 3 minutos", "Protección rápida contra quemado de pantalla"),
            Triple(5, "Activar automáticamente a los 5 minutos (Recomendado)", "Viene activo por defecto por si el usuario se olvida"),
            Triple(10, "Activar automáticamente a los 10 minutos", "Ideal para uso cotidiano en sala de estar"),
            Triple(15, "Activar automáticamente a los 15 minutos", "Espera media antes de oscurecer la pantalla"),
            Triple(30, "Activar automáticamente a los 30 minutos", "Espera prolongada"),
            Triple(0, "Desactivar inicio automático", "Solo se iniciará cuando lo actives manualmente")
        )

        for ((minutes, title, desc) in timeouts) {
            val active = currentTimeout == minutes
            options.add(
                DrawerOption(
                    iconRes = if (active) R.drawable.ic_check else R.drawable.ic_moon,
                    title = title,
                    subtitle = desc,
                    badge = if (active) "ACTIVO" else null,
                    badgeColor = Color.parseColor("#4ADE80"),
                    iconTint = if (active) Color.parseColor("#4ADE80") else Color.parseColor("#94A3B8")
                ) {
                    prefs.edit().putInt("screensaver_timeout_min", minutes).apply()
                    resetScreensaverIdleTimer()
                    showScreensaverDrawer()
                }
            )
        }

        openSideDrawer(
            title = "Salvapantallas",
            subtitle = "Salvapantallas propio integrado en FastHomeTv (consume 0% extra de RAM)",
            options = options
        )
    }

    /**
     * Panel de Rendimiento, Memoria RAM, Caché y Cierre de Aplicaciones en Segundo Plano.
     */
    private fun showPerformanceDrawer() {
        val liveSummary = SystemCommandRunner.getRamAndStorageSummary(this)
        val allPkgs = getAppsCached().map { it.pkg }

        val options = listOf(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Volver a Configuración",
                subtitle = "Regresar al menú principal de ajustes"
            ) {
                showUnifiedSettingsDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_check,
                title = "⚡ Optimización Total Turbo (RAM + Caché + Cerrar 2do Plano)",
                subtitle = "Cierra apps ocultas en 2do plano, vacía el caché de todas las apps y libera RAM al instante",
                badge = "TURBO",
                badgeColor = Color.parseColor("#4ADE80"),
                iconTint = Color.parseColor("#4ADE80")
            ) {
                drawerHeaderTitle.text = "Optimizando Sistema..."
                drawerHeaderSubtitle.text = "Iniciando limpieza profunda de RAM y caché..."
                bgExecutor.execute {
                    val result = SystemCommandRunner.runTurboBoostAll(this, allPkgs) { stepMsg ->
                        mainHandler.post {
                            drawerHeaderSubtitle.text = stepMsg
                        }
                    }
                    mainHandler.post {
                        drawerHeaderTitle.text = "Rendimiento y RAM"
                        drawerHeaderSubtitle.text = result
                        Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                    }
                }
            },
            DrawerOption(
                iconRes = R.drawable.ic_refresh,
                title = "🚀 Optimizar Memoria RAM y Cerrar Apps en Segundo Plano",
                subtitle = "Fuerza el cierre de apps que quedaron consumiendo memoria (protegiendo FastHomeTv)",
                badge = "RAM",
                badgeColor = Color.parseColor("#38BDF8"),
                iconTint = Color.parseColor("#38BDF8")
            ) {
                drawerHeaderSubtitle.text = "Cerrando aplicaciones en segundo plano y liberando RAM..."
                bgExecutor.execute {
                    val msg = SystemCommandRunner.optimizeRamAndCloseBackgroundApps(this, allPkgs)
                    mainHandler.post {
                        drawerHeaderSubtitle.text = msg
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            DrawerOption(
                iconRes = R.drawable.ic_hide,
                title = "🧹 Eliminar Caché de Todas las Aplicaciones",
                subtitle = "Vacía archivos temporales de YouTube, Netflix, Disney+, Mundo GO y todo el sistema",
                badge = "CACHÉ",
                badgeColor = Color.parseColor("#FBBF24"),
                iconTint = Color.parseColor("#FBBF24")
            ) {
                drawerHeaderSubtitle.text = "Eliminando caché de todas las aplicaciones instaladas..."
                bgExecutor.execute {
                    val msg = SystemCommandRunner.clearAllAppsCache(this)
                    mainHandler.post {
                        drawerHeaderSubtitle.text = msg
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            DrawerOption(
                iconRes = R.drawable.ic_apps,
                title = "📋 Cerrar Aplicaciones Individuales (Forzar Detención)",
                subtitle = "Elige una aplicación específica para cerrarla por completo y limpiar su memoria",
                badge = "APPS",
                badgeColor = Color.parseColor("#FCA5A5"),
                iconTint = Color.parseColor("#F87171")
            ) {
                showForceStopAppsDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_star,
                title = "🎞️ Velocidad de Animaciones: Ultra Rápido (0.5x)",
                subtitle = "Acelera las transiciones del sistema a 0.5x para respuesta inmediata del control",
                badge = "0.5x",
                badgeColor = Color.parseColor("#C084FC"),
                iconTint = Color.parseColor("#C084FC")
            ) {
                bgExecutor.execute {
                    try {
                        Settings.Global.putFloat(contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 0.5f)
                        Settings.Global.putFloat(contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0.5f)
                        Settings.Global.putFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0.5f)
                    } catch (_: Exception) {
                    }
                    SystemCommandRunner.execShellCommand(
                        "settings put global window_animation_scale 0.5; " +
                            "settings put global transition_animation_scale 0.5; " +
                            "settings put global animator_duration_scale 0.5"
                    )
                    mainHandler.post {
                        drawerHeaderSubtitle.text = "Animaciones del sistema ajustadas a 0.5x (Ultra Fluido)."
                    }
                }
            },
            DrawerOption(
                iconRes = R.drawable.ic_star,
                title = "⚡ Velocidad Instantánea: Sin Animaciones (0.0x • Máximo Rendimiento)",
                subtitle = "Desactiva por completo las animaciones de Android TV para liberar CPU y GPU",
                badge = "0.0x",
                badgeColor = Color.parseColor("#34D399"),
                iconTint = Color.parseColor("#34D399")
            ) {
                bgExecutor.execute {
                    try {
                        Settings.Global.putFloat(contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 0.0f)
                        Settings.Global.putFloat(contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0.0f)
                        Settings.Global.putFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0.0f)
                    } catch (_: Exception) {
                    }
                    SystemCommandRunner.execShellCommand(
                        "settings put global window_animation_scale 0.0; " +
                            "settings put global transition_animation_scale 0.0; " +
                            "settings put global animator_duration_scale 0.0"
                    )
                    mainHandler.post {
                        drawerHeaderSubtitle.text = "Modo Instantáneo 0.0x activado (cero consumo de GPU en transiciones)."
                    }
                }
            },
            DrawerOption(
                iconRes = R.drawable.ic_settings,
                title = "🔧 Compilar Paquetes en Modo Turbo (ART speed-profile)",
                subtitle = "Optimiza el código binario de todas las apps para que abran más rápido",
                badge = "ART",
                badgeColor = Color.parseColor("#60A5FA"),
                iconTint = Color.parseColor("#60A5FA")
            ) {
                drawerHeaderSubtitle.text = "Compilando aplicaciones en modo speed-profile (puede tardar unos segundos)..."
                bgExecutor.execute {
                    SystemCommandRunner.execShellCommand("cmd package compile -m speed-profile -a", timeoutMs = 15000)
                    mainHandler.post {
                        drawerHeaderSubtitle.text = "¡Todas las aplicaciones fueron optimizadas con speed-profile!"
                    }
                }
            }
        )

        openSideDrawer(
            title = "Rendimiento y RAM",
            subtitle = liveSummary,
            options = options
        )
    }

    private fun showForceStopAppsDrawer() {
        val apps = getAppsCached()
        val options = mutableListOf<DrawerOption>()
        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Volver a Rendimiento y RAM",
                subtitle = "Regresar al panel de optimización"
            ) {
                showPerformanceDrawer()
            }
        )

        for (app in apps) {
            options.add(
                DrawerOption(
                    iconRes = R.drawable.ic_power,
                    title = "Cerrar ${app.label}",
                    subtitle = "Forzar detención inmediata de ${app.pkg}",
                    badge = "CERRAR",
                    badgeColor = Color.parseColor("#FCA5A5"),
                    iconTint = Color.parseColor("#F87171")
                ) {
                    drawerHeaderSubtitle.text = "Cerrando ${app.label} y liberando su memoria..."
                    bgExecutor.execute {
                        try {
                            val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                            am?.killBackgroundProcesses(app.pkg)
                        } catch (_: Exception) {
                        }
                        SystemCommandRunner.execShellCommand("am force-stop ${app.pkg}; pm trim-caches 999G")
                        val freeMb = SystemCommandRunner.getFreeRamMb(this)
                        mainHandler.post {
                            drawerHeaderSubtitle.text = "✓ ${app.label} cerrada por completo • RAM Libre: ${freeMb} MB"
                        }
                    }
                }
            )
        }

        openSideDrawer(
            title = "Cerrar Aplicaciones",
            subtitle = "Pulsa OK sobre cualquier aplicación para forzar su cierre en segundo plano",
            options = options
        )
    }

    /**
     * Panel unificado para controlar dispositivos Nexxt Home (Ampolletas, Interruptores Inteligentes
     * y Cámaras Wi-Fi) a través de Google Home / Google Assistant en el TV, además de Home Assistant.
     */
    private fun showHomeAssistantDrawer() {
        val lightName = prefs.getString("nexxt_light_name", "las luces") ?: "las luces"
        val switchName = prefs.getString("nexxt_switch_name", "el interruptor") ?: "el interruptor"
        val cameraName = prefs.getString("nexxt_camera_name", "la cámara") ?: "la cámara"
        val haUrl = prefs.getString("ha_url", "http://homeassistant.local:8123") ?: "http://homeassistant.local:8123"
        val autoEvents = prefs.getBoolean("ha_auto_events", true)

        val options = mutableListOf(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Volver a Configuración",
                subtitle = "Regresar al menú principal de ajustes"
            ) {
                showUnifiedSettingsDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_star,
                title = "💡 Encender Ampolleta Nexxt Home ($lightName)",
                subtitle = "Envía a Google Home: \"Encender $lightName\"",
                badge = "ON",
                badgeColor = Color.parseColor("#4ADE80"),
                iconTint = Color.parseColor("#FBBF24")
            ) {
                closeSideDrawer()
                SystemCommandRunner.sendGoogleHomeAssistantCommand(this, "Encender $lightName")
            },
            DrawerOption(
                iconRes = R.drawable.ic_moon,
                title = "🌑 Apagar Ampolleta Nexxt Home ($lightName)",
                subtitle = "Envía a Google Home: \"Apagar $lightName\"",
                badge = "OFF",
                badgeColor = Color.parseColor("#FCA5A5"),
                iconTint = Color.parseColor("#94A3B8")
            ) {
                closeSideDrawer()
                SystemCommandRunner.sendGoogleHomeAssistantCommand(this, "Apagar $lightName")
            },
            DrawerOption(
                iconRes = R.drawable.ic_play,
                title = "🎬 Modo Cine Nexxt (Atenuar $lightName al 20%)",
                subtitle = "Baja el brillo de tus ampolletas Nexxt al 20% para ver películas",
                badge = "20%",
                badgeColor = Color.parseColor("#C084FC"),
                iconTint = Color.parseColor("#C084FC")
            ) {
                closeSideDrawer()
                SystemCommandRunner.sendGoogleHomeAssistantCommand(this, "Poner $lightName al 20 por ciento")
                triggerHomeAssistantWebhook("fasthometv_modo_cine", "Modo Cine")
            },
            DrawerOption(
                iconRes = R.drawable.ic_palette,
                title = "🌈 Cambiar Color de Ampolleta Nexxt (Blanco Cálido / Azul Cine)",
                subtitle = "Alterna entre luz cálida relajante y azul cine en tus ampolletas Nexxt RGB",
                badge = "COLOR",
                badgeColor = Color.parseColor("#38BDF8"),
                iconTint = Color.parseColor("#38BDF8")
            ) {
                val useBlue = prefs.getBoolean("nexxt_color_toggle", false)
                prefs.edit().putBoolean("nexxt_color_toggle", !useBlue).apply()
                val colorCmd = if (useBlue) "Poner $lightName en color azul" else "Poner $lightName en blanco cálido"
                closeSideDrawer()
                SystemCommandRunner.sendGoogleHomeAssistantCommand(this, colorCmd)
            },
            DrawerOption(
                iconRes = R.drawable.ic_power,
                title = "🔌 Encender Interruptor Inteligente Nexxt ($switchName)",
                subtitle = "Envía a Google Home: \"Encender $switchName\"",
                badge = "ENCENDER",
                badgeColor = Color.parseColor("#4ADE80"),
                iconTint = Color.parseColor("#4ADE80")
            ) {
                closeSideDrawer()
                SystemCommandRunner.sendGoogleHomeAssistantCommand(this, "Encender $switchName")
            },
            DrawerOption(
                iconRes = R.drawable.ic_power,
                title = "🔌 Apagar Interruptor Inteligente Nexxt ($switchName)",
                subtitle = "Envía a Google Home: \"Apagar $switchName\"",
                badge = "APAGAR",
                badgeColor = Color.parseColor("#F87171"),
                iconTint = Color.parseColor("#F87171")
            ) {
                closeSideDrawer()
                SystemCommandRunner.sendGoogleHomeAssistantCommand(this, "Apagar $switchName")
            },
            DrawerOption(
                iconRes = R.drawable.ic_tv,
                title = "📷 Ver Cámara Wi-Fi Nexxt en la TV ($cameraName)",
                subtitle = "Pide a Google Home transmitir en vivo \"$cameraName\" en la pantalla del televisor",
                badge = "CÁMARA",
                badgeColor = Color.parseColor("#38BDF8"),
                iconTint = Color.parseColor("#38BDF8")
            ) {
                closeSideDrawer()
                SystemCommandRunner.sendGoogleHomeAssistantCommand(this, "Mostrar $cameraName")
            },
            DrawerOption(
                iconRes = R.drawable.ic_settings,
                title = "✏️ Configurar Nombres de tus Dispositivos Nexxt / Google Home",
                subtitle = "Luz: \"$lightName\" • Interruptor: \"$switchName\" • Cámara: \"$cameraName\"",
                badge = "NOMBRES",
                badgeColor = Color.parseColor("#FBBF24"),
                iconTint = Color.parseColor("#FBBF24")
            ) {
                showNexxtDeviceNamesDialog()
            },
            DrawerOption(
                iconRes = R.drawable.ic_play,
                title = "🗣️ Enviar Otro Comando a Google Home",
                subtitle = "Escribe cualquier orden para tus dispositivos de Google Home / Nexxt",
                badge = "COMANDO",
                badgeColor = Color.parseColor("#A7F3D0"),
                iconTint = Color.parseColor("#34D399")
            ) {
                showCustomGoogleHomeCommandDialog()
            },
            DrawerOption(
                iconRes = R.drawable.ic_check,
                title = if (autoEvents) "🤖 Eventos Automáticos TV → Home Assistant: SÍ" else "🤖 Eventos Automáticos TV → Home Assistant: NO",
                subtitle = "Servidor opcional Home Assistant: $haUrl",
                badge = if (autoEvents) "AUTO ON" else "AUTO OFF",
                badgeColor = if (autoEvents) Color.parseColor("#4ADE80") else Color.parseColor("#F87171"),
                iconTint = if (autoEvents) Color.parseColor("#4ADE80") else Color.parseColor("#F87171")
            ) {
                prefs.edit().putBoolean("ha_auto_events", !autoEvents).apply()
                showHomeAssistantDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_settings,
                title = "Configurar Servidor Home Assistant (Opcional)",
                subtitle = "Para usuarios avanzados con servidor local Home Assistant ($haUrl)",
                badge = "WEBHOOK",
                iconTint = Color.parseColor("#94A3B8")
            ) {
                showHomeAssistantConfigDialog()
            }
        )

        openSideDrawer(
            title = "Google Home y Nexxt Home",
            subtitle = "Controla tus ampolletas, interruptores y cámaras Wi-Fi Nexxt desde el Launcher",
            options = options
        )
    }

    private fun showNexxtDeviceNamesDialog() {
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        val lightInput = EditText(this).apply {
            hint = "Ej: las luces / luz del living / lámpara"
            setText(prefs.getString("nexxt_light_name", "las luces"))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }

        val switchInput = EditText(this).apply {
            hint = "Ej: el interruptor / luz pasillo / enchufe"
            setText(prefs.getString("nexxt_switch_name", "el interruptor"))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }

        val cameraInput = EditText(this).apply {
            hint = "Ej: la cámara / cámara del patio / cámara entrada"
            setText(prefs.getString("nexxt_camera_name", "la cámara"))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }

        container.addView(TextView(this).apply {
            text = "Nombre de tu Ampolleta o Grupo de Luces en Google Home / Nexxt:"
            textSize = 13f
        })
        container.addView(lightInput)

        container.addView(TextView(this).apply {
            text = "Nombre de tu Interruptor Inteligente Nexxt:"
            textSize = 13f
            setPadding(0, (8 * density).toInt(), 0, 0)
        })
        container.addView(switchInput)

        container.addView(TextView(this).apply {
            text = "Nombre de tu Cámara Wi-Fi Nexxt:"
            textSize = 13f
            setPadding(0, (8 * density).toInt(), 0, 0)
        })
        container.addView(cameraInput)

        AlertDialog.Builder(this)
            .setTitle("Dispositivos Nexxt Home / Google Home")
            .setView(container)
            .setPositiveButton("Guardar") { _, _ ->
                val l = lightInput.text?.toString()?.trim()?.ifBlank { "las luces" } ?: "las luces"
                val s = switchInput.text?.toString()?.trim()?.ifBlank { "el interruptor" } ?: "el interruptor"
                val c = cameraInput.text?.toString()?.trim()?.ifBlank { "la cámara" } ?: "la cámara"
                prefs.edit()
                    .putString("nexxt_light_name", l)
                    .putString("nexxt_switch_name", s)
                    .putString("nexxt_camera_name", c)
                    .apply()
                showHomeAssistantDrawer()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showCustomGoogleHomeCommandDialog() {
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        val cmdInput = EditText(this).apply {
            hint = "Ej: Apagar luz del dormitorio"
            setText(prefs.getString("last_custom_gh_cmd", "Apagar las luces"))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }

        container.addView(TextView(this).apply {
            text = "Escribe la orden tal como se la pedirías a Google Home:"
            textSize = 13f
        })
        container.addView(cmdInput)

        AlertDialog.Builder(this)
            .setTitle("Enviar Orden a Google Home / Nexxt")
            .setView(container)
            .setPositiveButton("Ejecutar ahora") { _, _ ->
                val cmd = cmdInput.text?.toString()?.trim() ?: ""
                if (cmd.isNotBlank()) {
                    prefs.edit().putString("last_custom_gh_cmd", cmd).apply()
                    closeSideDrawer()
                    SystemCommandRunner.sendGoogleHomeAssistantCommand(this, cmd)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun triggerHomeAssistantWebhook(webhookId: String, friendlyName: String) {
        val baseUrl = (prefs.getString("ha_url", "http://homeassistant.local:8123") ?: "http://homeassistant.local:8123")
            .trim()
            .trimEnd('/')
        val customWebhook = prefs.getString("ha_custom_webhook", "")?.trim() ?: ""
        val targetHook = if (customWebhook.isNotBlank() && webhookId == "fasthometv_modo_cine") customWebhook else webhookId

        if (isDrawerOpen) {
            drawerHeaderSubtitle.text = "Enviando acción '$friendlyName' a $baseUrl..."
        }
        bgExecutor.execute {
            val ok = postHomeAssistantPayload(
                baseUrl = baseUrl,
                webhookId = targetHook,
                payload = JSONObject().apply {
                    put("action", webhookId)
                    put("label", friendlyName)
                    put("device", "FastHomeTv")
                    put("weather", weatherSummary)
                    put("timestamp", System.currentTimeMillis())
                }
            )
            mainHandler.post {
                val msg = if (ok) {
                    "✓ Home Assistant ejecutó: $friendlyName"
                } else {
                    "No se pudo conectar a $baseUrl/api/webhook/$targetHook (verifica la IP en Configurar URL)"
                }
                if (isDrawerOpen) {
                    drawerHeaderSubtitle.text = msg
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun notifyHomeAssistantAutoEvent(eventType: String, targetName: String) {
        if (!prefs.getBoolean("ha_auto_events", true)) return
        val baseUrl = (prefs.getString("ha_url", "") ?: "").trim().trimEnd('/')
        if (baseUrl.isBlank()) return
        bgExecutor.execute {
            postHomeAssistantPayload(
                baseUrl = baseUrl,
                webhookId = "fasthometv_evento",
                payload = JSONObject().apply {
                    put("event", eventType)
                    put("target", targetName)
                    put("device", "FastHomeTv")
                    put("timestamp", System.currentTimeMillis())
                }
            )
        }
    }

    private fun postHomeAssistantPayload(baseUrl: String, webhookId: String, payload: JSONObject): Boolean {
        return try {
            val endpoint = "$baseUrl/api/webhook/$webhookId"
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            val token = prefs.getString("ha_token", "")?.trim() ?: ""
            if (token.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
            conn.outputStream.use { out ->
                out.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun showHomeAssistantConfigDialog() {
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        val urlInput = EditText(this).apply {
            hint = "Ej: http://192.168.1.100:8123"
            setText(prefs.getString("ha_url", "http://homeassistant.local:8123"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }

        val hookInput = EditText(this).apply {
            hint = "Webhook ID opcional (por defecto: fasthometv_modo_cine)"
            setText(prefs.getString("ha_custom_webhook", ""))
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }

        container.addView(TextView(this).apply {
            text = "Dirección IP o URL de tu servidor Home Assistant:"
            textSize = 13f
        })
        container.addView(urlInput)
        container.addView(TextView(this).apply {
            text = "Webhook ID personalizado (dejar vacío para usar los predeterminados):"
            textSize = 13f
            setPadding(0, (10 * density).toInt(), 0, 0)
        })
        container.addView(hookInput)

        AlertDialog.Builder(this)
            .setTitle("Configurar Home Assistant")
            .setView(container)
            .setPositiveButton("Guardar") { _, _ ->
                val cleanUrl = urlInput.text?.toString()?.trim()?.ifBlank { "http://homeassistant.local:8123" }
                    ?: "http://homeassistant.local:8123"
                val cleanHook = hookInput.text?.toString()?.trim() ?: ""
                prefs.edit()
                    .putString("ha_url", cleanUrl)
                    .putString("ha_custom_webhook", cleanHook)
                    .apply()
                showHomeAssistantDrawer()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showPowerDrawer() {
        val options = listOf(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Volver a Configuración",
                subtitle = "Regresar al menú principal de ajustes"
            ) {
                showUnifiedSettingsDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_power,
                title = "Suspender / Modo Reposo",
                subtitle = "Apagar pantalla y poner el Decodificador / TV Box en suspensión",
                badge = "SLEEP",
                badgeColor = Color.parseColor("#FCA5A5"),
                iconTint = Color.parseColor("#F87171")
            ) {
                closeSideDrawer()
                mainHandler.postDelayed({
                    SystemCommandRunner.sleepDevice(this)
                }, 100L)
            },
            DrawerOption(
                iconRes = R.drawable.ic_refresh,
                title = "Reiniciar Decodificador / Android TV",
                subtitle = "Reiniciar el sistema completo del dispositivo ahora",
                badge = "REBOOT",
                badgeColor = Color.parseColor("#FBBF24"),
                iconTint = Color.parseColor("#FBBF24")
            ) {
                showRebootConfirmationDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_power,
                title = "Menú de Apagado Nativo (Android TV)",
                subtitle = "Abrir el panel oficial de energía del sistema Android TV",
                badge = "SISTEMA",
                badgeColor = Color.parseColor("#38BDF8"),
                iconTint = Color.parseColor("#38BDF8")
            ) {
                closeSideDrawer()
                mainHandler.postDelayed({
                    SystemCommandRunner.showSystemPowerMenuOrShutdown(this, directShutdown = false)
                }, 100L)
            },
            DrawerOption(
                iconRes = R.drawable.ic_power,
                title = "Apagar Dispositivo Directamente",
                subtitle = "Enviar orden de apagado total al sistema",
                badge = "OFF",
                badgeColor = Color.parseColor("#F87171"),
                iconTint = Color.parseColor("#F87171")
            ) {
                closeSideDrawer()
                mainHandler.postDelayed({
                    SystemCommandRunner.showSystemPowerMenuOrShutdown(this, directShutdown = true)
                }, 100L)
            },
            DrawerOption(
                iconRes = R.drawable.ic_moon,
                title = "Apagar Pantalla (Modo Reloj OLED Propio)",
                subtitle = "Activa el salvapantallas OLED integrado de FastHomeTv (despierta con cualquier tecla)",
                badge = "OLED",
                badgeColor = Color.parseColor("#C084FC"),
                iconTint = Color.parseColor("#C084FC")
            ) {
                startBuiltinScreensaver(forcePureBlack = false)
            },
            DrawerOption(
                iconRes = R.drawable.ic_moon,
                title = "Pantalla 100% Negra Total (Cero Luz)",
                subtitle = "Oscurece la pantalla por completo sin reloj (despierta con cualquier tecla)",
                badge = "NEGRO 100%",
                badgeColor = Color.parseColor("#38BDF8"),
                iconTint = Color.parseColor("#38BDF8")
            ) {
                startBuiltinScreensaver(forcePureBlack = true)
            },
            DrawerOption(
                iconRes = R.drawable.ic_refresh,
                title = "Reiniciar Interfaz de FastHomeTv",
                subtitle = "Recargar el Launcher limpiando la memoria gráfica",
                badge = "UI",
                badgeColor = Color.parseColor("#4ADE80"),
                iconTint = Color.parseColor("#4ADE80")
            ) {
                closeSideDrawer()
                recreate()
            }
        )

        openSideDrawer(
            title = "Apagado y Encendido",
            subtitle = "Control universal de energía para Decos, TV Box y Android TV",
            options = options
        )
    }

    private fun showRebootConfirmationDrawer() {
        val options = listOf(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Cancelar y volver",
                subtitle = "No reiniciar el dispositivo"
            ) {
                showPowerDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_refresh,
                title = "Confirmar: Reiniciar Decodificador / TV ahora",
                subtitle = "El dispositivo se reiniciará inmediatamente",
                badge = "REINICIAR",
                badgeColor = Color.parseColor("#F87171"),
                iconTint = Color.parseColor("#F87171")
            ) {
                drawerHeaderSubtitle.text = "Reiniciando dispositivo..."
                SystemCommandRunner.rebootDevice(this)
            }
        )

        openSideDrawer(
            title = "¿Reiniciar Dispositivo?",
            subtitle = "Confirma si deseas reiniciar tu Android TV / Decodificador",
            options = options
        )
    }

    /**
     * Asistente de Permisos para personas que descarguen FastHomeTv desde un link o código Downloader,
     * permitiendo aceptar cada permiso con el control remoto y blindando la app contra desinstalación.
     */
    private fun showPermissionsAndProtectionDrawer() {
        val isAccActive = LauncherAccessibilityService.isEnabled(this)
        val isBatteryOk = SystemCommandRunner.isBatteryOptimizationIgnored(this)
        val isOverlayOk = SystemCommandRunner.isOverlayPermissionGranted(this)
        val isUsageOk = SystemCommandRunner.isUsageStatsGranted(this)
        val isHomeOk = SystemCommandRunner.isDefaultHomeLauncher(this)

        val options = listOf(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Volver a Configuración",
                subtitle = "Regresar al menú principal"
            ) {
                showUnifiedSettingsDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_check,
                title = "⚡ Activar Todos los Permisos Automáticamente (ADB + Diálogo)",
                subtitle = "Intenta conceder todos los permisos y abre los diálogos que requieran tu aceptación",
                badge = "AUTO",
                badgeColor = Color.parseColor("#4ADE80"),
                iconTint = Color.parseColor("#4ADE80")
            ) {
                drawerHeaderSubtitle.text = "Configurando permisos y blindando FastHomeTv..."
                bgExecutor.execute {
                    SystemCommandRunner.enableAccessibilityAndDefaultHome(this)
                    mainHandler.post {
                        if (!SystemCommandRunner.isBatteryOptimizationIgnored(this)) {
                            requestIgnoreBatteryOptimizationsDialog()
                        } else if (!LauncherAccessibilityService.isEnabled(this)) {
                            Toast.makeText(
                                this,
                                "Activa 'FastHomeTv Override' en la lista de Accesibilidad",
                                Toast.LENGTH_LONG
                            ).show()
                            openAccessibilitySettingsScreen()
                        } else {
                            showPermissionsAndProtectionDrawer()
                            drawerHeaderSubtitle.text = "¡Todos los permisos principales y el blindaje están activos!"
                        }
                    }
                }
            },
            DrawerOption(
                iconRes = if (isAccActive) R.drawable.ic_check else R.drawable.ic_star,
                title = "1. Servicio de Accesibilidad (FastHomeTv Override)",
                subtitle = if (isAccActive) {
                    "Activo: Intercepta el botón HOME y evita cierres"
                } else {
                    "Falta activar: Pulsa OK y activa 'FastHomeTv Override' en la pantalla de Android"
                },
                badge = if (isAccActive) "ACTIVO" else "ACEPTAR",
                badgeColor = if (isAccActive) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24"),
                iconTint = if (isAccActive) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24")
            ) {
                closeSideDrawer()
                Toast.makeText(
                    this,
                    "Busca 'FastHomeTv Override' en Accesibilidad y pulsa Activar",
                    Toast.LENGTH_LONG
                ).show()
                openAccessibilitySettingsScreen()
            },
            DrawerOption(
                iconRes = if (isBatteryOk) R.drawable.ic_check else R.drawable.ic_power,
                title = "2. Sin Restricción de Batería / No Cerrar en 2do Plano",
                subtitle = if (isBatteryOk) {
                    "Activo: Android TV jamás cerrará FastHomeTv por ahorro de memoria"
                } else {
                    "Pulsa OK y selecciona 'Permitir' en el cuadro de diálogo de Android"
                },
                badge = if (isBatteryOk) "ACTIVO" else "ACEPTAR",
                badgeColor = if (isBatteryOk) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24"),
                iconTint = if (isBatteryOk) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24")
            ) {
                closeSideDrawer()
                requestIgnoreBatteryOptimizationsDialog()
            },
            DrawerOption(
                iconRes = if (isOverlayOk) R.drawable.ic_check else R.drawable.ic_tv,
                title = "3. Mostrar Sobre Otras Aplicaciones (Superposición)",
                subtitle = if (isOverlayOk) {
                    "Activo: Permite iniciar FastHomeTv desde cualquier pantalla"
                } else {
                    "Pulsa OK y activa el permiso para FastHomeTv en la lista"
                },
                badge = if (isOverlayOk) "ACTIVO" else "ACEPTAR",
                badgeColor = if (isOverlayOk) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24"),
                iconTint = if (isOverlayOk) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24")
            ) {
                closeSideDrawer()
                openOverlayPermissionScreen()
            },
            DrawerOption(
                iconRes = if (isUsageOk) R.drawable.ic_check else R.drawable.ic_apps,
                title = "4. Acceso a Uso de Aplicaciones",
                subtitle = if (isUsageOk) {
                    "Activo: Detección precisa de apps recientes y protección del Home"
                } else {
                    "Pulsa OK y concede acceso de uso a FastHomeTv"
                },
                badge = if (isUsageOk) "ACTIVO" else "ACEPTAR",
                badgeColor = if (isUsageOk) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24"),
                iconTint = if (isUsageOk) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24")
            ) {
                closeSideDrawer()
                openUsageAccessScreen()
            },
            DrawerOption(
                iconRes = R.drawable.ic_check,
                title = "5. Blindaje Anti-Desinstalación ADB ($packageName)",
                subtitle = if (isHomeOk) {
                    "Blindado: FastHomeTv es el Home oficial y está excluido de cualquier borrado ADB"
                } else {
                    "Blindado contra borrado ADB • Pulsa OK para fijar como Launcher Predeterminado"
                },
                badge = "BLINDADO",
                badgeColor = Color.parseColor("#38BDF8"),
                iconTint = Color.parseColor("#38BDF8")
            ) {
                bgExecutor.execute {
                    SystemCommandRunner.enableAccessibilityAndDefaultHome(this)
                    mainHandler.post {
                        showPermissionsAndProtectionDrawer()
                        Toast.makeText(
                            this,
                            "FastHomeTv ($packageName) blindado y fijado como Home",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )

        openSideDrawer(
            title = "Permisos y Blindaje",
            subtitle = "Ideal si instalaste FastHomeTv desde un link o código: acepta aquí los permisos",
            options = options
        )
    }

    private fun requestIgnoreBatteryOptimizationsDialog() {
        try {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return
        } catch (_: Exception) {
        }
        openFirstAvailableIntent(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun openAccessibilitySettingsScreen() {
        openFirstAvailableIntent(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.oemlink.AccessibilitySettingsActivity"),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun openOverlayPermissionScreen() {
        openFirstAvailableIntent(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun openUsageAccessScreen() {
        openFirstAvailableIntent(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun showDefaultLauncherDrawer() {
        val isHomeDefault = SystemCommandRunner.isDefaultHomeLauncher(this)
        val isAccActive = LauncherAccessibilityService.isEnabled(this)

        val options = listOf(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Volver a Configuración",
                subtitle = "Regresar al menú principal de ajustes"
            ) {
                showUnifiedSettingsDrawer()
            },
            DrawerOption(
                iconRes = if (isHomeDefault) R.drawable.ic_check else R.drawable.ic_tv,
                title = "⚡ Dejar FastHomeTv como Launcher Predeterminado Ahora",
                subtitle = if (isHomeDefault) {
                    "Activo: FastHomeTv ya es el Launcher predeterminado del sistema"
                } else {
                    "Fija automáticamente FastHomeTv por ADB o abre el selector para elegir 'Siempre'"
                },
                badge = if (isHomeDefault) "ACTIVO" else "FIJAR",
                badgeColor = Color.parseColor("#4ADE80"),
                iconTint = Color.parseColor("#4ADE80")
            ) {
                drawerHeaderSubtitle.text = "Fijando FastHomeTv como Launcher predeterminado..."
                bgExecutor.execute {
                    SystemCommandRunner.enableAccessibilityAndDefaultHome(this)
                    val nowDefault = SystemCommandRunner.isDefaultHomeLauncher(this)
                    mainHandler.post {
                        if (nowDefault) {
                            showDefaultLauncherDrawer()
                            Toast.makeText(
                                this,
                                "¡FastHomeTv quedó fijado como Launcher Predeterminado!",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            closeSideDrawer()
                            Toast.makeText(
                                this,
                                "Selecciona 'FastHomeTv' y pulsa 'Siempre' con el control",
                                Toast.LENGTH_LONG
                            ).show()
                            openDefaultHomeSettingsOrChooser()
                        }
                    }
                }
            },
            DrawerOption(
                iconRes = R.drawable.ic_settings,
                title = "🏠 Abrir Selector de Aplicación de Inicio (Android TV)",
                subtitle = "Elegir manualmente 'FastHomeTv' como aplicación de inicio en Ajustes de Android",
                badge = "SELECTOR",
                badgeColor = Color.parseColor("#38BDF8"),
                iconTint = Color.parseColor("#38BDF8")
            ) {
                closeSideDrawer()
                openDefaultHomeSettingsOrChooser()
            },
            DrawerOption(
                iconRes = R.drawable.ic_hide,
                title = "🔒 Desactivar Launchers de Fábrica (Google TV / Leanback / Operadora)",
                subtitle = "Deshabilita el launcher original para que solo exista FastHomeTv como inicio",
                badge = "ADB",
                badgeColor = Color.parseColor("#FBBF24"),
                iconTint = Color.parseColor("#FBBF24")
            ) {
                drawerHeaderSubtitle.text = "Desactivando launchers secundarios y fijando FastHomeTv..."
                bgExecutor.execute {
                    SystemCommandRunner.execShellCommand(
                        "cmd package set-home-activity $packageName/.MainActivity; " +
                            "pm disable-user --user 0 com.google.android.tvlauncher; " +
                            "pm disable-user --user 0 com.google.android.leanbacklauncher; " +
                            "pm disable-user --user 0 com.android.tv.Nowadaystelevision"
                    )
                    mainHandler.post {
                        showDefaultLauncherDrawer()
                        drawerHeaderSubtitle.text = "¡FastHomeTv fijado como único Launcher del dispositivo!"
                    }
                }
            },
            DrawerOption(
                iconRes = if (isAccActive) R.drawable.ic_check else R.drawable.ic_star,
                title = "🛡️ Forzar Botón HOME con Accesibilidad (FastHomeTv Override)",
                subtitle = if (isAccActive) {
                    "Activo: El botón HOME del control siempre abrirá FastHomeTv"
                } else {
                    "Activar protección adicional para decodificadores que bloquean el cambio de Home"
                },
                badge = if (isAccActive) "ACTIVO" else "ACTIVAR",
                badgeColor = if (isAccActive) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24"),
                iconTint = if (isAccActive) Color.parseColor("#4ADE80") else Color.parseColor("#FBBF24")
            ) {
                showAccessibilityForceDrawer()
            }
        )

        openSideDrawer(
            title = "Launcher Predeterminado",
            subtitle = if (isHomeDefault) {
                "Estado actual: FastHomeTv es el Launcher Predeterminado oficial"
            } else {
                "Configura FastHomeTv para que inicie siempre al encender o presionar HOME"
            },
            options = options
        )
    }

    private fun openDefaultHomeSettingsOrChooser() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val roleManager = getSystemService(android.app.role.RoleManager::class.java)
                if (roleManager != null &&
                    roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) &&
                    !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
                ) {
                    val roleIntent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
                    startActivityForResult(roleIntent, 901)
                    return
                }
            } catch (_: Exception) {
            }
        }
        openFirstAvailableIntent(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.apps.specialaccess.HomeSettingsActivity"),
            Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun showAccessibilityForceDrawer() {
        val isAccActive = LauncherAccessibilityService.isEnabled(this)
        val interceptEnabled = prefs.getBoolean("force_launcher_override", true)

        val options = listOf(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Volver a Configuración",
                subtitle = "Regresar al menú principal"
            ) {
                showUnifiedSettingsDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_check,
                title = if (isAccActive) "Servicio de Accesibilidad Activo" else "Activar Accesibilidad Automáticamente",
                subtitle = "Habilita FastHomeTv Override y fija FastHomeTv como predeterminado",
                badge = if (isAccActive) "ACTIVO" else "ACTIVAR",
                badgeColor = Color.parseColor("#4ADE80"),
                iconTint = Color.parseColor("#4ADE80")
            ) {
                drawerHeaderSubtitle.text = "Activando servicio de accesibilidad y Home..."
                bgExecutor.execute {
                    val ok = SystemCommandRunner.enableAccessibilityAndDefaultHome(this)
                    mainHandler.post {
                        showAccessibilityForceDrawer()
                        if (ok) {
                            drawerHeaderSubtitle.text = "¡Servicio de Accesibilidad y FastHomeTv Predeterminado activados!"
                        } else {
                            Toast.makeText(
                                this,
                                "Selecciona 'FastHomeTv Override' y actívalo con OK",
                                Toast.LENGTH_LONG
                            ).show()
                            openAccessibilitySettingsScreen()
                        }
                    }
                }
            },
            DrawerOption(
                iconRes = R.drawable.ic_star,
                title = if (interceptEnabled) "Forzar botón HOME y Bloquear Stock Launcher: SÍ" else "Forzar botón HOME y Bloquear Stock Launcher: NO",
                subtitle = "Intercepta el botón HOME y redirige si el launcher original intenta abrirse (estilo Projectivy)",
                badge = if (interceptEnabled) "ON" else "OFF",
                badgeColor = if (interceptEnabled) Color.parseColor("#4ADE80") else Color.parseColor("#F87171"),
                iconTint = if (interceptEnabled) Color.parseColor("#4ADE80") else Color.parseColor("#F87171")
            ) {
                prefs.edit().putBoolean("force_launcher_override", !interceptEnabled).apply()
                showAccessibilityForceDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_settings,
                title = "Abrir Ajustes de Accesibilidad de Android TV",
                subtitle = "Para aceptar manualmente 'FastHomeTv Override' si instalaste por link o código",
                badge = "ACEPTAR",
                iconTint = Color.parseColor("#38BDF8")
            ) {
                closeSideDrawer()
                openAccessibilitySettingsScreen()
            },
            DrawerOption(
                iconRes = R.drawable.ic_star,
                title = "Revisar Todos los Permisos de Instalación",
                subtitle = "Batería, superposición, uso de apps y blindaje anti-desinstalación",
                badge = "PERMISOS",
                iconTint = Color.parseColor("#FBBF24")
            ) {
                showPermissionsAndProtectionDrawer()
            }
        )

        openSideDrawer(
            title = "Forzar Launcher (Projectivy)",
            subtitle = if (isAccActive) "Protección activa: FastHomeTv siempre iniciará por defecto" else "Activa la accesibilidad para forzar FastHomeTv en cualquier dispositivo",
            options = options
        )
    }

    private fun showAdbOptimizationDrawer() {
        val options = listOf(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Volver a Configuración",
                subtitle = "Regresar al menú principal de ajustes"
            ) {
                showUnifiedSettingsDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_check,
                title = "Ejecutar TODOS los Comandos ADB Ahora",
                subtitle = "Debloat Mundo GO + quitar TVLauncher + bloquear OTA + velocidad 0.5x (FastHomeTv blindado)",
                badge = "EJECUTAR",
                badgeColor = Color.parseColor("#4ADE80"),
                iconTint = Color.parseColor("#4ADE80")
            ) {
                drawerHeaderTitle.text = "Ejecutando ADB..."
                drawerHeaderSubtitle.text = "Blindando FastHomeTv e iniciando secuencia ADB..."
                bgExecutor.execute {
                    val summary = SystemCommandRunner.runFullAdbOptimizationSuite(this) { stepMsg ->
                        mainHandler.post {
                            drawerHeaderSubtitle.text = stepMsg
                        }
                    }
                    mainHandler.post {
                        cachedRawApps = null
                        refresh()
                        drawerHeaderTitle.text = "¡Comandos Aplicados!"
                        drawerHeaderSubtitle.text = summary
                    }
                }
            },
            DrawerOption(
                iconRes = R.drawable.ic_star,
                title = "Asistente de Permisos Manuales (Si instalaste por Link)",
                subtitle = "Aceptar permisos de Accesibilidad, Batería y Superposición con el control remoto",
                badge = "PERMISOS",
                badgeColor = Color.parseColor("#FBBF24"),
                iconTint = Color.parseColor("#FBBF24")
            ) {
                showPermissionsAndProtectionDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_hide,
                title = "1. Desactivar y Desinstalar Apps Operadora / Bloat",
                subtitle = "Elimina bloatware protegiendo siempre a FastHomeTv ($packageName)",
                badge = "DEBLOAT",
                iconTint = Color.parseColor("#F87171")
            ) {
                drawerHeaderSubtitle.text = "Desactivando y desinstalando bloatware (FastHomeTv protegido)..."
                bgExecutor.execute {
                    for (pkg in SystemCommandRunner.DEBLOAT_DISABLE_PACKAGES) {
                        if (pkg == packageName || pkg in SystemCommandRunner.PROTECTED_PACKAGES) continue
                        SystemCommandRunner.execShellCommand("pm disable-user --user 0 $pkg")
                    }
                    for (pkg in SystemCommandRunner.DEBLOAT_UNINSTALL_PACKAGES) {
                        if (pkg == packageName || pkg in SystemCommandRunner.PROTECTED_PACKAGES) continue
                        SystemCommandRunner.execShellCommand("pm uninstall --user 0 $pkg")
                    }
                    mainHandler.post {
                        cachedRawApps = null
                        refresh()
                        drawerHeaderSubtitle.text = "Bloatware desinstalado (FastHomeTv blindado intacto)."
                    }
                }
            },
            DrawerOption(
                iconRes = R.drawable.ic_settings,
                title = "2. Bloquear Actualizaciones del Sistema (OTA)",
                subtitle = "ota_disable_automatic_update = 1 • auto_update_apps = 0",
                badge = "ANTI-OTA",
                iconTint = Color.parseColor("#FBBF24")
            ) {
                bgExecutor.execute {
                    try {
                        Settings.Global.putInt(contentResolver, "ota_disable_automatic_update", 1)
                        Settings.Global.putInt(contentResolver, "auto_update_apps", 0)
                    } catch (_: Exception) {
                    }
                    SystemCommandRunner.execShellCommand(
                        "settings put global ota_disable_automatic_update 1; settings put global auto_update_apps 0"
                    )
                    mainHandler.post {
                        drawerHeaderSubtitle.text = "Actualizaciones automáticas OTA bloqueadas (1 / 0)."
                    }
                }
            },
            DrawerOption(
                iconRes = R.drawable.ic_refresh,
                title = "3. Mejora de Velocidad (0.5x + Trim Caches 2G)",
                subtitle = "Animaciones a 0.5x, pm trim-caches 2G y compile speed-profile",
                badge = "TURBO",
                iconTint = Color.parseColor("#38BDF8")
            ) {
                drawerHeaderSubtitle.text = "Aplicando escalas 0.5x y optimizando paquetes..."
                bgExecutor.execute {
                    try {
                        Settings.Global.putFloat(contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 0.5f)
                        Settings.Global.putFloat(contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0.5f)
                        Settings.Global.putFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0.5f)
                    } catch (_: Exception) {
                    }
                    SystemCommandRunner.execShellCommand(
                        "settings put global window_animation_scale 0.5; " +
                            "settings put global transition_animation_scale 0.5; " +
                            "settings put global animator_duration_scale 0.5; " +
                            "pm trim-caches 2G; " +
                            "cmd package compile -m speed-profile -a",
                        timeoutMs = 15000
                    )
                    mainHandler.post {
                        drawerHeaderSubtitle.text = "¡Velocidad 0.5x, caché 2G y compilación speed-profile aplicados!"
                    }
                }
            }
        )

        openSideDrawer(
            title = "Comandos ADB y Optimización",
            subtitle = "FastHomeTv ($packageName) está blindado para no eliminarse jamás",
            options = options
        )
    }

    private fun showVisibilityDrawer() {
        val allInstalled = getAppsCached()
        val hiddenSet = HashSet(prefs.getStringSet("hidden", emptySet()) ?: emptySet())

        val options = mutableListOf<DrawerOption>()
        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Volver a Configuración",
                subtitle = "Guardar y regresar al menú principal"
            ) {
                showUnifiedSettingsDrawer()
            }
        )
        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_check,
                title = "Mostrar todas las aplicaciones",
                subtitle = "Restablecer todas las apps ocultas",
                badge = "RESET"
            ) {
                prefs.edit()
                    .putStringSet("hidden", HashSet())
                    .putBoolean("hidden_initialized", true)
                    .apply()
                refresh()
                showVisibilityDrawer()
            }
        )

        for (app in allInstalled) {
            val isVisible = app.pkg !in hiddenSet
            options.add(
                DrawerOption(
                    iconRes = if (isVisible) R.drawable.ic_eye else R.drawable.ic_hide,
                    title = app.label,
                    subtitle = app.pkg,
                    badge = if (isVisible) "VISIBLE" else "OCULTA",
                    badgeColor = if (isVisible) Color.parseColor("#4ADE80") else Color.parseColor("#F87171"),
                    iconTint = if (isVisible) Color.parseColor("#4ADE80") else Color.parseColor("#F87171")
                ) {
                    if (isVisible) {
                        hideApp(app.pkg)
                    } else {
                        unhideApp(app.pkg)
                    }
                    showVisibilityDrawer()
                }
            )
        }

        openSideDrawer(
            title = "Visibilidad de Apps",
            subtitle = "Las apps ocultas se quitan de Todas, del Dock y de Recientes",
            options = options
        )
    }

    private fun showThemeDrawer() {
        val current = prefs.getInt("theme_mode", 1)
        val themes = listOf(
            Triple(0, "Tema Claro (Blanco Perla • Free My TV)", "Interfaz completa clara (fondo, Dock y menús) con alto contraste"),
            Triple(1, "Auto Día / Noche (Inteligente)", "Tema Claro durante el día y Obsidiana OLED de noche"),
            Triple(2, "Obsidiana Puro (Negro OLED • CleanYourTV)", "Negro absoluto en toda la interfaz y menús"),
            Triple(3, "Azul Zafiro Intenso (Cinema Blue)", "Atmósfera azul cine en pantalla principal y paneles"),
            Triple(4, "Púrpura Nébula (Violeta Real)", "Degradado púrpura espacial en toda la interfaz"),
            Triple(5, "Esmeralda Aurora (Verde Boreal)", "Tonos cristal esmeralda profundo en todo el sistema"),
            Triple(6, "Rojo Carmesí (Crimson Cinema • Estilo Netflix)", "Tonos rojo cine intenso y carbón profundo en todo el Launcher"),
            Triple(7, "Dorado Ámbar (Sunset Gold • Cálido)", "Atmósfera dorada cálida y bronce elegante en pantalla y paneles"),
            Triple(8, "Cian Ártico (Cyber Titanium • Azul Hielo)", "Tonos turquesa ártico y azul titanio moderno"),
            Triple(9, "Gris Titanio (Graphite Studio • Apple Pro)", "Acabado grafito metálico neutro de alto contraste")
        )

        val options = mutableListOf<DrawerOption>()
        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Volver a Configuración",
                subtitle = "Regresar al menú anterior"
            ) {
                showUnifiedSettingsDrawer()
            }
        )

        for ((id, name, desc) in themes) {
            val active = current == id
            options.add(
                DrawerOption(
                    iconRes = if (active) R.drawable.ic_check else R.drawable.ic_palette,
                    title = name,
                    subtitle = desc,
                    badge = if (active) "ACTIVO" else null,
                    badgeColor = Color.parseColor("#FBBF24"),
                    iconTint = if (active) Color.parseColor("#FBBF24") else Color.parseColor("#94A3B8")
                ) {
                    prefs.edit().putInt("theme_mode", id).apply()
                    applyThemeBackground()
                    val c = lastFocusedColor.takeIf { it != 0 } ?: Color.parseColor("#3B82F6")
                    applyAmbientGlow(c, force = true)
                    showThemeDrawer()
                }
            )
        }

        openSideDrawer(
            title = "Tema y Color",
            subtitle = "Toda la interfaz y este panel se adaptan en vivo al tema elegido",
            options = options
        )
    }

    private fun showWeatherDrawer() {
        val options = listOf(
            DrawerOption(
                iconRes = R.drawable.ic_refresh,
                title = "Actualizar pronóstico ahora",
                subtitle = "Consultar datos meteorológicos en vivo desde Open-Meteo",
                badge = "LIVE",
                iconTint = Color.parseColor("#38BDF8")
            ) {
                weatherPill.text = "⛅ Actualizando..."
                fetchLiveWeather()
                closeSideDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_settings,
                title = "Ir a Configuración de FastHomeTv",
                subtitle = "Temas, visibilidad de apps y ajustes de Android TV"
            ) {
                showUnifiedSettingsDrawer()
            },
            DrawerOption(
                iconRes = R.drawable.ic_arrow_left,
                title = "Cerrar panel",
                subtitle = "Volver a la pantalla de inicio"
            ) {
                closeSideDrawer()
            }
        )

        openSideDrawer(
            title = weatherSummary,
            subtitle = weatherDetails,
            options = options
        )
    }

    private fun showAppOptionsDrawer(app: AppEntry) {
        val favSet = prefs.getStringSet("fav", emptySet()) ?: emptySet()
        val isFav = app.pkg in favSet

        val options = mutableListOf<DrawerOption>()
        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_play,
                title = "Abrir ${app.label}",
                subtitle = "Iniciar aplicación ahora",
                badge = "OK",
                iconTint = Color.parseColor("#4ADE80")
            ) {
                closeSideDrawer()
                open(app)
            }
        )
        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_star,
                title = if (isFav) "Quitar del Dock" else "Fijar en el Dock",
                subtitle = if (isFav) "Mover de regreso a la bandeja inferior" else "Añadir a la barra principal del Dock",
                badge = if (isFav) "EN DOCK" else "FIJAR",
                badgeColor = Color.parseColor("#FBBF24"),
                iconTint = Color.parseColor("#FBBF24")
            ) {
                toggleFav(app)
                closeSideDrawer()
            }
        )

        if (isFav) {
            options.add(
                DrawerOption(
                    iconRes = R.drawable.ic_arrow_left,
                    title = "Mover a la izquierda en el Dock",
                    subtitle = "Adelantar posición en la barra principal"
                ) {
                    moveFav(app, -1)
                }
            )
            options.add(
                DrawerOption(
                    iconRes = R.drawable.ic_arrow_right,
                    title = "Mover a la derecha en el Dock",
                    subtitle = "Retrasar posición en la barra principal"
                ) {
                    moveFav(app, 1)
                }
            )
        }

        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_hide,
                title = "Ocultar \"${app.label}\"",
                subtitle = "No mostrar esta app en FastHomeTv (reversible en Visibilidad)",
                badge = "OCULTAR",
                badgeColor = Color.parseColor("#F87171"),
                iconTint = Color.parseColor("#F87171")
            ) {
                hideApp(app.pkg)
                closeSideDrawer()
            }
        )
        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_eye,
                title = "Administrar visibilidad de todas las apps",
                subtitle = "Mostrar u ocultar aplicaciones instaladas"
            ) {
                showVisibilityDrawer()
            }
        )
        options.add(
            DrawerOption(
                iconRes = R.drawable.ic_info,
                title = "Información de la aplicación (Android)",
                subtitle = "Forzar detención, borrar caché o desinstalar en el sistema"
            ) {
                closeSideDrawer()
                openAppDetails(app.pkg)
            }
        )

        openSideDrawer(
            title = app.label,
            subtitle = "Paquete: ${app.pkg}",
            options = options
        )
    }

    private fun applyThemeBackground() {
        val mode = prefs.getInt("theme_mode", 1)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isDay = hour in 7..18 && isDaytimeWeather

        val useLight = (mode == 0) || (mode == 1 && isDay)
        isLightThemeActive = useLight

        val bgColors = when (mode) {
            0 -> intArrayOf(
                Color.parseColor("#F1F5F9"),
                Color.parseColor("#E2E8F0"),
                Color.parseColor("#CBD5E1")
            )
            1 -> if (isDay) {
                intArrayOf(
                    Color.parseColor("#F1F5F9"),
                    Color.parseColor("#E2E8F0"),
                    Color.parseColor("#CBD5E1")
                )
            } else {
                intArrayOf(
                    Color.parseColor("#0A0F1D"),
                    Color.parseColor("#050811"),
                    Color.parseColor("#020307")
                )
            }
            2 -> intArrayOf(
                Color.parseColor("#040406"),
                Color.parseColor("#010102"),
                Color.parseColor("#000000")
            )
            3 -> intArrayOf(
                Color.parseColor("#1D4ED8"),
                Color.parseColor("#0F172A"),
                Color.parseColor("#050814")
            )
            4 -> intArrayOf(
                Color.parseColor("#6D28D9"),
                Color.parseColor("#1E1B4B"),
                Color.parseColor("#080612")
            )
            5 -> intArrayOf(
                Color.parseColor("#047857"),
                Color.parseColor("#064E3B"),
                Color.parseColor("#03120E")
            )
            6 -> intArrayOf(
                Color.parseColor("#991B1B"),
                Color.parseColor("#2A0A10"),
                Color.parseColor("#090406")
            )
            7 -> intArrayOf(
                Color.parseColor("#92400E"),
                Color.parseColor("#271507"),
                Color.parseColor("#0A0604")
            )
            8 -> intArrayOf(
                Color.parseColor("#0E7490"),
                Color.parseColor("#0F172A"),
                Color.parseColor("#030712")
            )
            9 -> intArrayOf(
                Color.parseColor("#334155"),
                Color.parseColor("#1E293B"),
                Color.parseColor("#090D16")
            )
            else -> intArrayOf(
                Color.parseColor("#047857"),
                Color.parseColor("#064E3B"),
                Color.parseColor("#03120E")
            )
        }

        rootContainer.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bgColors)

        val drawerColors = when {
            useLight -> intArrayOf(
                Color.parseColor("#F8FAFC"),
                Color.parseColor("#E2E8F0"),
                Color.parseColor("#CBD5E1")
            )
            mode == 2 -> intArrayOf(
                Color.parseColor("#08080C"),
                Color.parseColor("#030305"),
                Color.parseColor("#000000")
            )
            mode == 3 -> intArrayOf(
                Color.parseColor("#1E3A8A"),
                Color.parseColor("#0F172A"),
                Color.parseColor("#070B16")
            )
            mode == 4 -> intArrayOf(
                Color.parseColor("#4C1D95"),
                Color.parseColor("#1E1B4B"),
                Color.parseColor("#090714")
            )
            mode == 5 -> intArrayOf(
                Color.parseColor("#065F46"),
                Color.parseColor("#064E3B"),
                Color.parseColor("#021410")
            )
            mode == 6 -> intArrayOf(
                Color.parseColor("#7F1D1D"),
                Color.parseColor("#2A0A10"),
                Color.parseColor("#090406")
            )
            mode == 7 -> intArrayOf(
                Color.parseColor("#78350F"),
                Color.parseColor("#271507"),
                Color.parseColor("#0A0604")
            )
            mode == 8 -> intArrayOf(
                Color.parseColor("#155E75"),
                Color.parseColor("#0F172A"),
                Color.parseColor("#040814")
            )
            mode == 9 -> intArrayOf(
                Color.parseColor("#334155"),
                Color.parseColor("#1E293B"),
                Color.parseColor("#090D16")
            )
            else -> intArrayOf(
                Color.parseColor("#111827"),
                Color.parseColor("#0B111E"),
                Color.parseColor("#060911")
            )
        }

        val density = resources.displayMetrics.density
        val cornerRadiusPx = 22f * density
        val drawerBg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, drawerColors).apply {
            cornerRadii = floatArrayOf(
                cornerRadiusPx, cornerRadiusPx,
                0f, 0f,
                0f, 0f,
                cornerRadiusPx, cornerRadiusPx
            )
            setStroke(
                (1.5f * density).toInt().coerceAtLeast(1),
                if (useLight) Color.parseColor("#2E0F172A") else Color.parseColor("#33FFFFFF")
            )
        }
        sideDrawerPanel.background = drawerBg
        sideDrawerPanel.clipToOutline = true

        val primaryText = if (useLight) Color.parseColor("#0F172A") else Color.WHITE
        val secondaryText = if (useLight) Color.parseColor("#334155") else Color.parseColor("#CBD5E1")
        val mutedText = if (useLight) Color.parseColor("#475569") else Color.parseColor("#94A3B8")
        val dividerColor = if (useLight) Color.parseColor("#240F172A") else Color.parseColor("#26FFFFFF")

        clockView.setTextColor(primaryText)
        clockDivider.setBackgroundColor(dividerColor)
        greeting.setTextColor(primaryText)
        dateClockView.setTextColor(mutedText)
        spotlightStatus.setTextColor(secondaryText)
        pullDownHint.setTextColor(mutedText)
        hint.setTextColor(mutedText)
        allAppsLabel.setTextColor(secondaryText)

        drawerHeaderTitle.setTextColor(primaryText)
        drawerHeaderSubtitle.setTextColor(mutedText)
        drawerHeaderDivider.setBackgroundColor(dividerColor)

        dockContainer.setBackgroundResource(if (useLight) R.drawable.bg_dock_light else R.drawable.bg_dock)

        val pillBgRes = if (useLight) R.drawable.bg_pill_light else R.drawable.bg_pill
        val pillTextCsl: ColorStateList = resources.getColorStateList(
            if (useLight) R.color.pill_text_color_light else R.color.pill_text_color,
            theme
        )

        val allPills = listOf(
            weatherPill, networkPill, inputsPill, settingsPill,
            tabExplore, tabMovies, tabShows, tabSeries,
            catAll, catRecent, catStreaming, catMedia, catTools
        )
        for (pill in allPills) {
            pill.setBackgroundResource(pillBgRes)
            pill.setTextColor(pillTextCsl)
            pill.compoundDrawableTintList = pillTextCsl
        }

        updateMediaTabStyles()
        updateCategoryTabStyles()

        mediaAdapter.setLightMode(useLight)
        favAdapter.setLightMode(useLight)
        allAdapter.setLightMode(useLight)
        drawerAdapter.setLightMode(useLight)
    }

    private fun fetchLiveWeather() {
        bgExecutor.execute {
            try {
                var lat = -33.45
                var lon = -70.66
                var city = "Santiago"

                try {
                    val geoConn = URL("https://get.geojs.io/v1/ip/geo.json").openConnection() as HttpURLConnection
                    geoConn.connectTimeout = 3000
                    geoConn.readTimeout = 3000
                    if (geoConn.responseCode == 200) {
                        val geoJson = JSONObject(geoConn.inputStream.bufferedReader().readText())
                        lat = geoJson.optString("latitude", "-33.45").toDoubleOrNull() ?: -33.45
                        lon = geoJson.optString("longitude", "-70.66").toDoubleOrNull() ?: -70.66
                        val detectedCity = geoJson.optString("city", "")
                        if (detectedCity.isNotBlank()) city = detectedCity
                    }
                    geoConn.disconnect()
                } catch (_: Exception) {
                }

                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,relative_humidity_2m,is_day,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min&timezone=auto"
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(body)
                    val current = json.getJSONObject("current")
                    val temp = Math.round(current.optDouble("temperature_2m", 18.0))
                    val feels = Math.round(current.optDouble("apparent_temperature", temp.toDouble()))
                    val humidity = current.optInt("relative_humidity_2m", 60)
                    val wind = Math.round(current.optDouble("wind_speed_10m", 10.0))
                    val code = current.optInt("weather_code", 0)
                    val isDay = current.optInt("is_day", 1) == 1

                    var minTemp = temp
                    var maxTemp = temp
                    val daily = json.optJSONObject("daily")
                    if (daily != null) {
                        val maxArr = daily.optJSONArray("temperature_2m_max")
                        val minArr = daily.optJSONArray("temperature_2m_min")
                        if (maxArr != null && maxArr.length() > 0) maxTemp = Math.round(maxArr.optDouble(0, temp.toDouble()))
                        if (minArr != null && minArr.length() > 0) minTemp = Math.round(minArr.optDouble(0, temp.toDouble()))
                    }

                    val (icon, desc) = weatherCodeToLabel(code, isDay)
                    val pillText = "$icon ${temp}°C • $desc"
                    val detailsText = "Ubicación: $city  |  Sensación: ${feels}°C\n" +
                        "Mín / Máx hoy: ${minTemp}°C / ${maxTemp}°C  |  Humedad: $humidity%  |  Viento: $wind km/h"

                    prefs.edit().putString("cached_weather", pillText).apply()

                    mainHandler.post {
                        isDaytimeWeather = isDay
                        weatherSummary = pillText
                        weatherDetails = detailsText
                        weatherPill.text = pillText
                        applyThemeBackground()
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun weatherCodeToLabel(code: Int, isDay: Boolean): Pair<String, String> {
        return when (code) {
            0 -> (if (isDay) "☀" else "🌙") to "Despejado"
            1, 2 -> (if (isDay) "⛅" else "☁") to "Parcial nublado"
            3 -> "☁" to "Nublado"
            45, 48 -> "🌫" to "Niebla"
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> "🌧" to "Lluvia"
            71, 73, 75 -> "❄" to "Nieve"
            95, 96, 99 -> "⛈" to "Tormenta"
            else -> "⛅" to "Templado"
        }
    }

    /**
     * Lanza el primer Intent válido disponible, filtrando EmptyStubActivity de Android TV
     * y nuestro propio paquete para que jamás falle silenciosamente.
     */
    private fun openFirstAvailableIntent(vararg intents: Intent) {
        val pm = packageManager
        for (intent in intents) {
            try {
                val resolved = pm.resolveActivity(intent, 0)
                val targetPkg = resolved?.activityInfo?.packageName ?: ""
                val targetCls = resolved?.activityInfo?.name ?: ""
                if (targetPkg == packageName || targetCls.contains("EmptyStubActivity", ignoreCase = true)) {
                    continue
                }
                if (resolved != null || intent.component != null) {
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                }
            } catch (_: Exception) {
            }
        }
        // Último recurso: abrir Ajustes principales de Android TV
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isDrawerOpen) {
            mainContentColumn.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        updateGreeting()
        applyThemeBackground()
        refresh()
        resetScreensaverIdleTimer()
        mainHandler.removeCallbacks(mediaAutoRotateRunnable)
        mainHandler.postDelayed(mediaAutoRotateRunnable, 180_000L)

        val now = System.currentTimeMillis()
        val lastRot = prefs.getLong("last_media_rotate_ms", 0L)
        if (lastRot == 0L) {
            prefs.edit().putLong("last_media_rotate_ms", now).apply()
        } else if (now - lastRot > 2 * 60_000L) {
            prefs.edit().putLong("last_media_rotate_ms", now).apply()
            rotateMediaShowcaseNow()
        }
    }

    private fun updateGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greeting.text = when (hour) {
            in 5..11 -> "Buenos días"
            in 12..19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isScreensaverActive) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                stopBuiltinScreensaver()
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            resetScreensaverIdleTimer()
        }

        val drawerVisible = isDrawerOpen && sideDrawerOverlay.visibility == View.VISIBLE
        if (drawerVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        closeSideDrawer()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action == KeyEvent.ACTION_DOWN && isDrawerOpen) {
                        closeSideDrawer()
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!isDrawerOpen) return true
                    val current = window.currentFocus
                    if (current == null || !isDescendantOf(current, drawerList)) {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            drawerList.getChildAt(0)?.requestFocus()
                        }
                        return true
                    }
                    val holder = drawerList.findContainingViewHolder(current)
                    val pos = holder?.bindingAdapterPosition ?: -1
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && pos == 0) {
                        return true
                    }
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && pos >= drawerAdapter.itemCount - 1) {
                        return true
                    }
                }
            }
            return super.dispatchKeyEvent(event)
        }

        // Asegurar siempre que el contenedor principal permita foco en sus hijos cuando el panel lateral está cerrado
        mainContentColumn.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                handleBackNavigation()
                return true
            }

            val focused = window.currentFocus
            val isFocusedInHeaderPills = focused === weatherPill || focused === networkPill || focused === inputsPill || focused === settingsPill
            val isFocusedInMediaTabs = focused === tabExplore || focused === tabMovies || focused === tabShows || focused === tabSeries
            val isFocusedInMediaRow = focused != null && isDescendantOf(focused, mediaRow)
            val isFocusedInDock = focused != null && isDescendantOf(focused, favRow)
            val isFocusedInCategoryTabs = focused === catAll || focused === catRecent || focused === catStreaming || focused === catMedia || focused === catTools
            val isFocusedInAllGrid = focused != null && isDescendantOf(focused, allGrid)

            // 0. Navegación desde los botones superiores (Clima, Red, Entradas, Ajustes)
            if (isFocusedInHeaderPills) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> return true
                    KeyEvent.KEYCODE_DPAD_LEFT -> if (focused === weatherPill) return true
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (focused === settingsPill) return true
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (isAppsExpanded) setAppsExpanded(false)
                        getActiveMediaTabView().requestFocus()
                        return true
                    }
                }
            }

            // 1. Navegación controlada en las pestañas (EXPLORA, PELÍCULAS, SHOWS, SERIES)
            if (isFocusedInMediaTabs) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (focused === tabExplore) return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (focused === tabSeries) return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        weatherPill.requestFocus()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusMediaRowOrDock()
                        return true
                    }
                }
            }

            // 2. Navegación controlada en las tarjetas de EXPLORA / PELÍCULAS / SHOWS / SERIES
            if (isFocusedInMediaRow && focused != null) {
                val holder = mediaRow.findContainingViewHolder(focused)
                val pos = holder?.bindingAdapterPosition ?: -1
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (pos == 0) return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (pos >= mediaAdapter.itemCount - 1) return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        // Al subir desde cualquier póster, vuelve SIEMPRE a la pestaña activa (sin reiniciar a EXPLORA)
                        getActiveMediaTabView().requestFocus()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusDockFirstItem()
                        return true
                    }
                }
            }

            // 3. Navegación controlada en el Dock
            if (isFocusedInDock && focused != null) {
                val holder = favRow.findContainingViewHolder(focused)
                val pos = holder?.bindingAdapterPosition ?: -1
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (pos == 0) return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (pos >= favAdapter.itemCount - 1) return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusAllGridFirstItem()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusMediaRowOrDock()
                        return true
                    }
                }
            }

            // 4. Navegación controlada en las pestañas de Categorías (Todas, Recientes, Streaming, Reproductores, Herramientas)
            if (isFocusedInCategoryTabs) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (focused === catAll) return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (focused === catTools) return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focusDockFirstItem()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focusAllGridFirstItem()
                        return true
                    }
                }
            }

            // 5. Navegación controlada en la cuadrícula de Todas las Aplicaciones (allGrid)
            if (isFocusedInAllGrid && focused != null) {
                val holder = allGrid.findContainingViewHolder(focused)
                val pos = holder?.bindingAdapterPosition ?: -1
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (pos in 0..5) {
                            getActiveCategoryTabView().requestFocus()
                            return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isDescendantOf(view: View, parent: ViewGroup): Boolean {
        var current: Any? = view
        while (current is View) {
            if (current === parent) return true
            current = current.parent
        }
        return false
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackNavigation()
    }

    private fun handleBackNavigation() {
        if (isScreensaverActive) {
            stopBuiltinScreensaver()
            return
        }
        if (isDrawerOpen) {
            closeSideDrawer()
            return
        }
        if (selectedCategory != "ALL") {
            selectedCategory = "ALL"
            updateCategoryTabStyles()
            refresh()
            return
        }
        if (isAppsExpanded) {
            setAppsExpanded(false)
            focusDockFirstItem()
            return
        }
        focusDockFirstItem()
    }

    private fun open(app: AppEntry) {
        try {
            prefs.edit()
                .putString("last_opened_pkg", app.pkg)
                .putLong("last_used_${app.pkg}", System.currentTimeMillis())
                .apply()
            notifyHomeAssistantAutoEvent("app_opened", app.label)
            startActivity(app.launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    private fun getOrderedFavList(defaultSet: Set<String>): MutableList<String> {
        val orderStr = prefs.getString("fav_order", null)
        if (!orderStr.isNullOrBlank()) {
            val list = orderStr.split(",").filter { it.isNotBlank() && it in defaultSet }.toMutableList()
            for (pkg in defaultSet) {
                if (pkg !in list) list.add(pkg)
            }
            return list
        }
        return defaultSet.toMutableList()
    }

    private fun saveFavList(list: List<String>) {
        prefs.edit()
            .putStringSet("fav", HashSet(list))
            .putString("fav_order", list.joinToString(","))
            .putBoolean("fav_initialized", true)
            .apply()
    }

    private fun toggleFav(app: AppEntry) {
        val set = HashSet(prefs.getStringSet("fav", emptySet()) ?: emptySet())
        val ordered = getOrderedFavList(set)
        if (app.pkg in ordered) {
            ordered.remove(app.pkg)
        } else {
            ordered.add(app.pkg)
        }
        saveFavList(ordered)
        refresh()
    }

    private fun moveFav(app: AppEntry, delta: Int) {
        val set = HashSet(prefs.getStringSet("fav", emptySet()) ?: emptySet())
        val ordered = getOrderedFavList(set)
        val idx = ordered.indexOf(app.pkg)
        if (idx == -1) return
        val newIdx = (idx + delta).coerceIn(0, ordered.size - 1)
        if (newIdx != idx) {
            ordered.removeAt(idx)
            ordered.add(newIdx, app.pkg)
            saveFavList(ordered)
            refresh()
            showAppOptionsDrawer(app)
        }
    }

    private fun hideApp(pkg: String) {
        val hidden = HashSet(prefs.getStringSet("hidden", emptySet()) ?: emptySet())
        hidden.add(pkg)
        val favs = getOrderedFavList(HashSet(prefs.getStringSet("fav", emptySet()) ?: emptySet()))
        favs.remove(pkg)
        saveFavList(favs)
        prefs.edit()
            .putStringSet("hidden", HashSet(hidden))
            .remove("last_used_$pkg")
            .putBoolean("hidden_initialized", true)
            .apply()
        refresh()
    }

    private fun unhideApp(pkg: String) {
        val hidden = HashSet(prefs.getStringSet("hidden", emptySet()) ?: emptySet())
        hidden.remove(pkg)
        prefs.edit()
            .putStringSet("hidden", HashSet(hidden))
            .putBoolean("hidden_initialized", true)
            .apply()
        refresh()
    }

    private fun openAppDetails(pkg: String) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$pkg")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun classifyAppCategory(pkg: String, label: String): String {
        val p = pkg.lowercase()
        val l = label.lowercase()
        return when {
            listOf("netflix", "disney", "youtube", "mundogo", "mxliptv", "xuper", "mgstx", "amazon", "hbo", "prime", "twitch", "pluto", "vix").any { p.contains(it) || l.contains(it) } -> "STREAMING"
            listOf("vlc", "nova", "spotify", "kodi", "plex", "music", "player", "video").any { p.contains(it) || l.contains(it) } -> "MEDIA"
            else -> "TOOLS"
        }
    }

    private fun onAppFocused(app: AppEntry, fromGrid: Boolean) {
        if (isDrawerOpen) return
        if (fromGrid && !isAppsExpanded) {
            setAppsExpanded(true)
        }
        val locationTag = if (app.isFav) "★ Dock" else "App"
        spotlightStatus.text = "${app.label} ($locationTag) • OK: Abrir • Mantén OK: Opciones"
        applyAmbientGlow(app.accentColor)
    }

    private fun applyAmbientGlow(accentColor: Int, force: Boolean = false) {
        if (!force && accentColor == lastFocusedColor) return
        lastFocusedColor = accentColor

        val mode = prefs.getInt("theme_mode", 1)
        val topAlpha = when {
            isLightThemeActive -> 28
            mode == 2 -> 35
            else -> 52
        }
        val midAlpha = when {
            isLightThemeActive -> 10
            mode == 2 -> 12
            else -> 18
        }

        val topAlphaColor = Color.argb(
            topAlpha,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor)
        )
        val midAlphaColor = Color.argb(
            midAlpha,
            Color.red(accentColor),
            Color.green(accentColor),
            Color.blue(accentColor)
        )
        val gd = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topAlphaColor, midAlphaColor, Color.TRANSPARENT)
        )
        ambientGlow.background = gd
    }

    private fun refresh() {
        val rawApps = getAppsCached()

        var hiddenSet = HashSet(prefs.getStringSet("hidden", emptySet()) ?: emptySet())
        if (!prefs.getBoolean("hidden_initialized", false)) {
            val defaultHidden = rawApps
                .filter { it.pkg.contains("jzatehardware", ignoreCase = true) || it.label.contains("ATEHardware", ignoreCase = true) }
                .map { it.pkg }
                .toSet()
            hiddenSet = HashSet(defaultHidden)
            prefs.edit()
                .putStringSet("hidden", HashSet(hiddenSet))
                .putBoolean("hidden_initialized", true)
                .apply()
        }

        // Ninguna aplicación oculta debe aparecer jamás en visibleApps, en el Dock ni en Recientes
        val visibleApps = rawApps.filter { it.pkg !in hiddenSet }
        var favSet = HashSet(prefs.getStringSet("fav", emptySet()) ?: emptySet())
            .filter { it !in hiddenSet }
            .toSet()

        if (!prefs.getBoolean("fav_initialized", false) && favSet.isEmpty() && visibleApps.isNotEmpty()) {
            val preferredOrder = listOf(
                "netflix", "youtube.tv", "disney", "mundogo", "mxliptv", "xuper",
                "mgstx", "spotify", "vlc"
            )
            val initialFavs = mutableListOf<String>()
            for (kw in preferredOrder) {
                val match = visibleApps.firstOrNull { app ->
                    (app.pkg.lowercase().contains(kw) || app.label.lowercase().contains(kw)) &&
                        !app.pkg.lowercase().contains("tvmusic") &&
                        app.pkg !in initialFavs
                }
                if (match != null && initialFavs.size < 6) {
                    initialFavs.add(match.pkg)
                }
            }

            if (initialFavs.isEmpty()) {
                initialFavs.addAll(visibleApps.take(5).map { it.pkg })
            }
            saveFavList(initialFavs)
            favSet = initialFavs.toSet()
        }

        val orderedFavPkgs = getOrderedFavList(favSet).filter { it !in hiddenSet }
        val apps = visibleApps.map { it.copy(isFav = it.pkg in favSet) }
        val appByPkg = apps.associateBy { it.pkg }
        val favs = orderedFavPkgs.mapNotNull { appByPkg[it] }.filter { it.pkg !in hiddenSet }

        val baseTrayApps = when (selectedCategory) {
            "ALL" -> apps.filter { !it.isFav && it.pkg !in hiddenSet }
            "RECENT" -> {
                val usedVisible = apps.filter {
                    it.pkg !in hiddenSet && prefs.getLong("last_used_${it.pkg}", 0L) > 0L
                }.sortedByDescending { prefs.getLong("last_used_${it.pkg}", 0L) }
                if (usedVisible.isNotEmpty()) {
                    usedVisible
                } else {
                    apps.filter { !it.isFav && it.pkg !in hiddenSet }
                }
            }
            else -> apps.filter { it.pkg !in hiddenSet && classifyAppCategory(it.pkg, it.label) == selectedCategory }
        }

        favAdapter.submit(favs)
        allAdapter.submit(baseTrayApps)

        appCount.visibility = View.GONE

        hint.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE
        favRow.visibility = if (favs.isEmpty()) View.GONE else View.VISIBLE

        allGrid.post {
            if (!isDrawerOpen && (forceDockFocusOnNextRefresh || window.currentFocus == null)) {
                forceDockFocusOnNextRefresh = false
                val target = if (favs.isNotEmpty()) favRow else allGrid
                target.getChildAt(0)?.findViewById<View>(R.id.card)?.requestFocus()
            }
        }
    }

    private fun getAppsCached(): List<AppEntry> {
        val existing = cachedRawApps
        if (existing != null) return existing
        val loaded = loadAppsFromPackageManager()
        cachedRawApps = loaded
        return loaded
    }

    private fun loadAppsFromPackageManager(): List<AppEntry> {
        val pm = packageManager
        val seen = HashSet<String>()
        val list = ArrayList<AppEntry>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || pkg == "com.italo.launcher" || !seen.add(pkg)) continue
                val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
                    ?: pm.getLaunchIntentForPackage(pkg)
                    ?: continue
                val label = ri.loadLabel(pm).toString()
                val icon = ri.loadIcon(pm)
                val banner = try {
                    ri.activityInfo.loadBanner(pm) ?: pm.getApplicationBanner(pkg)
                } catch (_: Exception) {
                    null
                }
                val accent = colorCache.getOrPut(pkg) {
                    extractAccentColor(banner ?: icon)
                }
                list.add(AppEntry(pkg, label, icon, banner, launch, accent))
            }
        }
        return list.sortedBy { it.label.lowercase() }
    }

    private fun extractAccentColor(drawable: Drawable): Int {
        return try {
            val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, 24, 24)
            drawable.draw(canvas)

            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var weightSum = 0L

            for (x in 0 until 24) {
                for (y in 0 until 24) {
                    val c = bmp.getPixel(x, y)
                    val a = Color.alpha(c)
                    if (a < 128) continue
                    val r = Color.red(c)
                    val g = Color.green(c)
                    val b = Color.blue(c)
                    val max = maxOf(r, g, b)
                    val min = minOf(r, g, b)
                    val saturation = max - min
                    if (max > 35 && min < 235) {
                        val w = (saturation + 25).toLong()
                        rSum += r * w
                        gSum += g * w
                        bSum += b * w
                        weightSum += w
                    }
                }
            }
            bmp.recycle()
            if (weightSum > 0) {
                Color.rgb(
                    (rSum / weightSum).toInt().coerceIn(40, 230),
                    (gSum / weightSum).toInt().coerceIn(40, 230),
                    (bSum / weightSum).toInt().coerceIn(40, 230)
                )
            } else {
                Color.parseColor("#3B82F6")
            }
        } catch (_: Exception) {
            Color.parseColor("#3B82F6")
        }
    }
}
