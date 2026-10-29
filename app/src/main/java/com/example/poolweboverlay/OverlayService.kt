
package com.example.poolweboverlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class OverlayService : Service() {

    companion object {
        const val ACTION_START = "com.example.poolweboverlay.START"
        const val ACTION_UPDATE = "com.example.poolweboverlay.UPDATE"
        const val ACTION_TOGGLE_PANEL = "com.example.poolweboverlay.TOGGLE_PANEL"
        private const val CHANNEL_ID = "pool_web_overlay"
        private const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var webView: WebView? = null
    private var alignmentPanel: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        showPersistentNotification()

        when (intent?.action) {
            ACTION_UPDATE -> webView?.invalidate()

            ACTION_TOGGLE_PANEL -> {
                if (alignmentPanel == null) showAlignmentPanel()
                else hideAlignmentPanel()
            }

            ACTION_START, null -> startWeb()

            else -> startWeb()
        }

        return START_STICKY
    }

    private fun startWeb() {
        if (webView != null) {
            webView?.invalidate()
            return
        }

        if (!android.provider.Settings.canDrawOverlays(this)) return

        val view = WebView()
        webView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            webView = null
        }
    }

    private fun showPersistentNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pool Web Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_TOGGLE_PANEL
        }

        val pendingIntent = PendingIntent.getService(
            this,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("Pool Web Overlay")
            .setContentText("Tap Adjust alignment to tune the web")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_edit,
                "Adjust alignment",
                pendingIntent
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun showAlignmentPanel() {
        if (alignmentPanel != null) return

        val prefs = getSharedPreferences("pool_web_settings", MODE_PRIVATE)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(25, 32, 43))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.LTGRAY)
            }
        }

        panel.addView(TextView(this).apply {
            text = "Pool Web — Alignment"
            textSize = 17f
            setTextColor(Color.WHITE)
        })

        fun addSlider(key: String, title: String, default: Int) {
            val label = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 13f
            }

            val slider = SeekBar(this).apply {
                max = 950
                progress = prefs.getInt(key, default).coerceIn(0, 950)
            }

            fun updateLabel(value: Int) {
                label.text = "$title: ${value / 10f}%"
            }

            updateLabel(slider.progress)

            slider.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        prefs.edit().putInt(key, progress).apply()
                        updateLabel(progress)
                        webView?.invalidate()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )

            panel.addView(label)
            panel.addView(slider)
        }

        addSlider("left", "Left", 221)
        addSlider("right", "Right", 814)
        addSlider("top", "Top", 239)
        addSlider("bottom", "Bottom", 887)

        panel.addView(Button(this).apply {
            text = "Close panel"
            setOnClickListener { hideAlignmentPanel() }
        })

        val params = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(45)
        }

        alignmentPanel = panel

        try {
            windowManager.addView(panel, params)
        } catch (e: Exception) {
            alignmentPanel = null
        }
    }

    private fun hideAlignmentPanel() {
        alignmentPanel?.let {
            if (it.isAttachedToWindow) windowManager.removeView(it)
        }
        alignmentPanel = null
    }

    override fun onDestroy() {
        hideAlignmentPanel()

        webView?.let {
            if (it.isAttachedToWindow) windowManager.removeView(it)
        }
        webView = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class WebView : View(this@OverlayService) {

        private val prefs by lazy {
            getSharedPreferences("pool_web_settings", MODE_PRIVATE)
        }

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(135, 25, 25, 25)
            strokeWidth = 1.1f
            style = Paint.Style.STROKE
        }

        private val divisions = 12

        private fun ratio(key: String, defaultValue: Int): Float =
            prefs.getInt(key, defaultValue).coerceIn(0, 1000) / 1000f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (width < height) return

            val w = width.toFloat()
            val h = height.toFloat()

            val left = w * ratio("left", 221)
            val right = w * ratio("right", 814)
            val top = h * ratio("top", 239)
            val bottom = h * ratio("bottom", 887)

            if (right - left < w * 0.05f ||
                bottom - top < h * 0.05f
            ) return

            val save = canvas.save()
            canvas.clipRect(left, top, right, bottom)

            val midX = (left + right) / 2f
            val midY = (top + bottom) / 2f

            val origins = arrayOf(
                left to top,
                midX to top,
                right to top,
                right to midY,
                right to bottom,
                midX to bottom,
                left to bottom,
                left to midY
            )

            // Stable origin-to-edge connections.
            val targetEdges = intArrayOf(0, 0, 0, 2, 1, 1, 1, 3)

            for (index in origins.indices) {
                val (ox, oy) = origins[index]

                for (i in 0..divisions) {
                    val f = i.toFloat() / divisions

                    val (tx, ty) = when (targetEdges[index]) {
                        0 -> (left + (right - left) * f) to bottom
                        1 -> (left + (right - left) * f) to top
                        2 -> left to (top + (bottom - top) * f)
                        else -> right to (top + (bottom - top) * f)
                    }

                    canvas.drawLine(ox, oy, tx, ty, linePaint)
                }
            }

            canvas.restoreToCount(save)
        }
    }
}
