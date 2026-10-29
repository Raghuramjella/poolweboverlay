
package com.example.poolweboverlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class OverlayService : Service() {

    companion object {
        const val ACTION_START =
            "com.example.poolweboverlay.START"

        const val ACTION_UPDATE =
            "com.example.poolweboverlay.UPDATE"

        const val ACTION_TOGGLE_PANEL =
            "com.example.poolweboverlay.TOGGLE_PANEL"

        private const val CHANNEL_ID = "pool_web_overlay"
        private const val NOTIFICATION_ID = 1001

        private const val NAVY = 0xFF07111F.toInt()
        private const val CARD = 0xFF101E30.toInt()
        private const val BLUE = 0xFF087EFF.toInt()
        private const val CYAN = 0xFF00D9C0.toInt()
        private const val WHITE = 0xFFF2F7FF.toInt()
        private const val MUTED = 0xFF9AAEC4.toInt()
    }

    private lateinit var windowManager: WindowManager
    private var webView: WebView? = null
    private var alignmentPanel: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager
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
                if (alignmentPanel == null) {
                    showAlignmentPanel()
                } else {
                    hideAlignmentPanel()
                }
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

        if (!android.provider.Settings.canDrawOverlays(this)) {
            return
        }

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
        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CueAim Pro",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "CueAim Pro overlay controls"
                setShowBadge(false)
            }

            manager.createNotificationChannel(channel)
        }

        val adjustIntent =
            Intent(this, OverlayService::class.java).apply {
                action = ACTION_TOGGLE_PANEL
            }

        val adjustPendingIntent = PendingIntent.getService(
            this,
            1002,
            adjustIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        val notification = builder
            .setContentTitle("CUE AIM PRO")
            .setContentText(
                "Guidelines active • Tap to adjust alignment"
            )
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setColor(BLUE)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_edit,
                "Adjust alignment",
                adjustPendingIntent
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun roundedBackground(
        color: Int,
        radius: Int,
        strokeColor: Int = Color.TRANSPARENT
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()

        if (strokeColor != Color.TRANSPARENT) {
            setStroke(dp(1), strokeColor)
        }
    }

    private fun makeText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)

        if (bold) {
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun makeAction(
        value: String,
        color: Int,
        textColor: Int = WHITE,
        onClick: () -> Unit
    ) = TextView(this).apply {
        text = value
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(11), dp(12), dp(11))
        background = roundedBackground(color, 10)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun showAlignmentPanel() {
        if (alignmentPanel != null) return

        val prefs = getSharedPreferences(
            "pool_web_settings",
            MODE_PRIVATE
        )

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = roundedBackground(NAVY, 18, BLUE)
            elevation = dp(12).toFloat()
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        heading.addView(
            makeText("CUE AIM PRO", 17f, WHITE, true)
        )

        heading.addView(
            makeText(
                "GUIDELINE ALIGNMENT",
                10f,
                CYAN,
                true
            ).apply {
                letterSpacing = 0.08f
                setPadding(0, dp(3), 0, 0)
            }
        )

        header.addView(
            heading,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(
            makeAction("✕", CARD, CYAN) {
                hideAlignmentPanel()
            }.apply {
                textSize = 18f
                setPadding(dp(12), dp(7), dp(12), dp(7))
            }
        )

        panel.addView(header)

        // Status indicator
        val status = TextView(this).apply {
            text = "●  OVERLAY CONTROLS"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(CYAN)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = roundedBackground(CARD, 8)
        }

        val statusParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(14)
            bottomMargin = dp(10)
        }

        panel.addView(status, statusParams)

        fun addSlider(
            key: String,
            title: String,
            default: Int
        ) {
            val section = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(7), dp(10), dp(5))
                background = roundedBackground(CARD, 10)
            }

            val label = makeText(
                "",
                12f,
                WHITE,
                true
            )

            val slider = SeekBar(this).apply {
                max = 950
                progress = prefs.getInt(key, default)
                    .coerceIn(0, 950)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    progressTintList =
                        ColorStateList.valueOf(BLUE)
                    thumbTintList =
                        ColorStateList.valueOf(CYAN)
                    progressBackgroundTintList =
                        ColorStateList.valueOf(
                            0xFF263A52.toInt()
                        )
                }
            }

            fun updateLabel(value: Int) {
                label.text = "$title     ${value / 10f}%"
            }

            updateLabel(slider.progress)

            slider.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        prefs.edit()
                            .putInt(key, progress)
                            .apply()

                        updateLabel(progress)
                        webView?.invalidate()
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {}
                }
            )

            section.addView(label)
            section.addView(slider)

            val sectionParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }

            panel.addView(section, sectionParams)
        }

        addSlider("left", "LEFT BOUNDARY", 221)
        addSlider("right", "RIGHT BOUNDARY", 814)
        addSlider("top", "TOP BOUNDARY", 239)
        addSlider("bottom", "BOTTOM BOUNDARY", 887)

        // Footer actions
        panel.addView(
            makeAction("DONE", BLUE) {
                hideAlignmentPanel()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
            }
        )

        panel.addView(
            makeText(
                "Adjust carefully for your screen",
                10f,
                MUTED
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            }
        )

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

        // Drag the panel by holding the header area.
        // The close button remains independently clickable.
        var dragStartX = 0
        var dragStartY = 0
        var touchStartX = 0f
        var touchStartY = 0f

        header.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dragStartX = params.x
                    dragStartY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    true
                }

                android.view.MotionEvent.ACTION_MOVE -> {
                    val screenWidth =
                        resources.displayMetrics.widthPixels
                    val screenHeight =
                        resources.displayMetrics.heightPixels

                    val newX = dragStartX +
                        (event.rawX - touchStartX).toInt()
                    val newY = dragStartY +
                        (event.rawY - touchStartY).toInt()

                    // Keep the panel within the visible screen area.
                    params.x = newX.coerceIn(
                        0,
                        (screenWidth - panel.width).coerceAtLeast(0)
                    )
                    params.y = newY.coerceIn(
                        0,
                        (screenHeight - panel.height).coerceAtLeast(0)
                    )

                    try {
                        windowManager.updateViewLayout(panel, params)
                    } catch (_: Exception) {
                        // Ignore an update if the panel was detached.
                    }
                    true
                }

                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> true

                else -> false
            }
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
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
        }

        alignmentPanel = null
    }

    override fun onDestroy() {
        hideAlignmentPanel()

        webView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
        }

        webView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private inner class WebView(
    ) : View(this@OverlayService) {

        private val prefs by lazy {
            getSharedPreferences(
                "pool_web_settings",
                MODE_PRIVATE
            )
        }

        private val linePaint = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color = Color.argb(135, 25, 25, 25)
            strokeWidth = 1.1f
            style = Paint.Style.STROKE
        }

        private val divisions = 12

        private fun ratio(
            key: String,
            defaultValue: Int
        ): Float =
            prefs.getInt(key, defaultValue)
                .coerceIn(0, 1000) / 1000f

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (width < height) return

            val w = width.toFloat()
            val h = height.toFloat()

            val left = w * ratio("left", 221)
            val right = w * ratio("right", 814)
            val top = h * ratio("top", 239)
            val bottom = h * ratio("bottom", 887)

            if (
                right - left < w * 0.05f ||
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

            val targetEdges =
                intArrayOf(0, 0, 0, 2, 1, 1, 1, 3)

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
