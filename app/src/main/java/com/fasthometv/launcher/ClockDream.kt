package com.fasthometv.launcher

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import kotlin.random.Random

/**
 * Salvapantallas minimalista estilo CleanYourTV (ClockDream)
 * Reemplaza a com.google.android.backdrop con un reloj flotante que se desplaza
 * suavemente cada 45 segundos para proteger el panel y no consumir RAM.
 */
class ClockDream : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clockContainer: LinearLayout
    private lateinit var root: FrameLayout

    private val moveRunnable = object : Runnable {
        override fun run() {
            driftClock()
            handler.postDelayed(this, 45_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false

        val density = resources.displayMetrics.density

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        clockContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            alpha = 0.78f
        }

        val timeClock = TextClock(this).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            setTextColor(Color.WHITE)
            textSize = 64f
            gravity = Gravity.CENTER
        }

        val dateClock = TextClock(this).apply {
            format12Hour = "EEEE, d 'de' MMMM"
            format24Hour = "EEEE, d 'de' MMMM"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, (4 * density).toInt(), 0, 0)
        }

        clockContainer.addView(timeClock)
        clockContainer.addView(dateClock)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        root.addView(clockContainer, lp)
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.postDelayed(moveRunnable, 45_000L)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(moveRunnable)
        super.onDreamingStopped()
    }

    private fun driftClock() {
        val maxDx = (root.width * 0.25f).coerceAtLeast(1f)
        val maxDy = (root.height * 0.25f).coerceAtLeast(1f)
        val nextX = Random.nextFloat() * (2 * maxDx) - maxDx
        val nextY = Random.nextFloat() * (2 * maxDy) - maxDy

        clockContainer.animate()
            . alpha(0f)
            .setDuration(600)
            .withEndAction {
                clockContainer.translationX = nextX
                clockContainer.translationY = nextY
                clockContainer.animate()
                    .alpha(0.78f)
                    .setDuration(600)
                    .start()
            }
            .start()
    }
}

