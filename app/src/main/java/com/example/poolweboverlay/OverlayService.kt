package com.example.poolweboverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale

class OverlayService : Service() {

    companion object {
        const val ACTION_START =
            "com.example.poolweboverlay.START"

        const val ACTION_UPDATE =
            "com.example.poolweboverlay.UPDATE"

        const val ACTION_TOGGLE_PANEL =
            "com.example.poolweboverlay.TOGGLE_PANEL"

        const val ACTION_TOGGLE_GUIDELINES =
            "com.example.poolweboverlay.TOGGLE_GUIDELINES"

        private const val CHANNEL_ID = "pool_web_overlay"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS = "pool_web_settings"

        private const val NAVY = 0xFF07111F.toInt()
        private const val CARD = 0xFF101E30.toInt()
        private const val BLUE = 0xFF087EFF.toInt()
        private const val CYAN = 0xFF00D9C0.toInt()
        private const val WHITE = 0xFFF2F7FF.toInt()
        private const val MUTED = 0xFF9AAEC4.toInt()
        private const val BORDER = 0xFF263A52.toInt()

        private const val MIN_THICKNESS = 5
        private const val MAX_THICKNESS = 50
        private const val DEFAULT_THICKNESS = 15
    }

    private lateinit var windowManager: WindowManager

    private var webView: GuidelineView? = null

    // The same floating panel is used for both control screens.
    private var alignmentPanel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var isAlignmentScreen = false

    private var bubbleView: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private val prefs by lazy {
        getSharedPreferences(PREFS, MODE_PRIVATE)
    }

    private val guidelinesVisible: Boolean
        get() = prefs.getBoolean("guidelines_visible", true)

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
            ACTION_UPDATE -> {
                webView?.invalidate()
            }

            ACTION_TOGGLE_PANEL -> {
                if (Settings.canDrawOverlays(this)) {
                    startWeb()
                    hideBubble()
                    if (alignmentPanel == null) {
                        showMainPanel()
                    }
                }
            }

            ACTION_TOGGLE_GUIDELINES -> {
                setGuidelinesVisible(!guidelinesVisible)
            }

            ACTION_START, null -> {
                startWeb()
            }

            else -> {
                startWeb()
            }
        }

        return START_STICKY
    }

    // ---------------------------------------------------------
    // GUIDELINE OVERLAY
    // ---------------------------------------------------------

    private fun startWeb() {
        if (!Settings.canDrawOverlays(this)) return

        if (webView != null) {
            webView?.invalidate()
            return
        }

        val view = GuidelineView()
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
        } catch (_: Exception) {
            webView = null
        }
    }

    private fun setGuidelinesVisible(visible: Boolean) {
        prefs.edit()
            .putBoolean("guidelines_visible", visible)
            .apply()

        webView?.visibility =
            if (visible) View.VISIBLE else View.INVISIBLE

        webView?.invalidate()

        updateNotification()
        updateGridButton()
    }

    // ---------------------------------------------------------
    // NOTIFICATION
    // ---------------------------------------------------------

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

        val openIntent = Intent(
            this,
            OverlayService::class.java
        ).apply {
            action = ACTION_TOGGLE_PANEL
        }

        val openPendingIntent = PendingIntent.getService(
            this,
            1002,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(
            this,
            OverlayService::class.java
        ).apply {
            action = ACTION_TOGGLE_GUIDELINES
        }

        val togglePendingIntent = PendingIntent.getService(
            this,
            1003,
            toggleIntent,
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
                if (guidelinesVisible) {
                    "Gridlines ON • Tap to open controls"
                } else {
                    "Gridlines OFF • Tap to open controls"
                }
            )
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setColor(BLUE)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_edit,
                "Open Controls",
                openPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                if (guidelinesVisible) "Hide Gridlines"
                else "Show Gridlines",
                togglePendingIntent
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun updateNotification() {
        showPersistentNotification()
    }

    // ---------------------------------------------------------
    // PANEL UI HELPERS
    // ---------------------------------------------------------

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
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = roundedBackground(color, 10)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun makeSection(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(10), dp(11), dp(8))
            background = roundedBackground(CARD, 12)
        }

    private fun addSection(
        parent: LinearLayout,
        section: View,
        bottom: Int = 9
    ) {
        parent.addView(
            section,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(bottom)
            }
        )
    }

    private fun styleSeekBar(seekBar: SeekBar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.progressTintList =
                ColorStateList.valueOf(BLUE)

            seekBar.thumbTintList =
                ColorStateList.valueOf(CYAN)

            seekBar.progressBackgroundTintList =
                ColorStateList.valueOf(BORDER)
        }
    }

    // ---------------------------------------------------------
    // OPEN MAIN PANEL
    // ---------------------------------------------------------

    private fun showMainPanel() {
        showPanel(alignmentScreen = false)
    }

    // ---------------------------------------------------------
    // OPEN ALIGNMENT SETUP
    // ---------------------------------------------------------

    private fun showAlignmentSetup() {
        showPanel(alignmentScreen = true)
    }

    private fun showPanel(alignmentScreen: Boolean) {
        if (!Settings.canDrawOverlays(this)) return

        removeCurrentPanel(keepPosition = true)
        hideBubble()

        isAlignmentScreen = alignmentScreen

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = roundedBackground(NAVY, 18, BLUE)
            elevation = dp(12).toFloat()
        }

        // Header: drag by the title area.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        heading.addView(
            makeText("CUE AIM PRO", 16f, WHITE, true)
        )

        heading.addView(
            makeText(
                if (alignmentScreen) {
                    "ALIGNMENT SETUP"
                } else {
                    "QUICK CONTROLS"
                },
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
            makeAction("−", CARD, CYAN) {
                collapsePanel()
            }.apply {
                textSize = 21f
                setPadding(dp(12), dp(5), dp(12), dp(5))
            }
        )

        header.addView(
            makeAction("✕", CARD, CYAN) {
                hidePanel()
            }.apply {
                textSize = 16f
                setPadding(dp(11), dp(7), dp(11), dp(7))
            }
        )

        panel.addView(header)

        // Everything under the header can scroll on small screens.
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }

        scroll.addView(content)

        val viewportHeight = (resources.displayMetrics.heightPixels * 0.62f)
            .toInt()
            .coerceAtLeast(dp(300))

        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                viewportHeight
            )
        )

        if (alignmentScreen) {
            buildAlignmentContent(content)
        } else {
            buildQuickControls(content)
        }

        val existing = panelParams

        val params = WindowManager.LayoutParams(
            dp(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = existing?.x ?: dp(12)
            y = existing?.y ?: dp(45)
        }

        attachDragHandle(header, panel, params)

        alignmentPanel = panel
        panelParams = params

        try {
            windowManager.addView(panel, params)
        } catch (_: Exception) {
            alignmentPanel = null
            panelParams = null
        }
    }

    // ---------------------------------------------------------
    // QUICK CONTROLS: GRIDLINES + THICKNESS + ALIGNMENT BUTTON
    // ---------------------------------------------------------

    private fun buildQuickControls(content: LinearLayout) {
        val status = makeText(
            if (guidelinesVisible) "●  GRIDLINES ACTIVE" else "○  GRIDLINES HIDDEN",
            11f,
            CYAN,
            true
        ).apply {
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = roundedBackground(CARD, 9)
        }
        addSection(content, status, 10)

        // Gridline visibility toggle.
        val gridSection = makeSection()
        gridSection.addView(makeText("GRIDLINES", 11f, MUTED, true))
        val gridButton = makeAction(
            if (guidelinesVisible) "✓  GRIDLINES ON  •  TAP TO HIDE" else "GRIDLINES OFF  •  TAP TO SHOW",
            if (guidelinesVisible) CARD else BLUE,
            if (guidelinesVisible) CYAN else WHITE
        ) {
            setGuidelinesVisible(!guidelinesVisible)
        }
        gridButton.tag = "grid_toggle"
        gridSection.addView(
            gridButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )
        addSection(content, gridSection, 10)

        // Put thickness near the top so it is visible without scrolling far.
        val thicknessSection = makeSection()
        thicknessSection.addView(makeText("LINE THICKNESS", 11f, MUTED, true))
        val thicknessLabel = makeText(
            thicknessText(prefs.getInt("line_thickness", DEFAULT_THICKNESS)
                .coerceIn(MIN_THICKNESS, MAX_THICKNESS)),
            12f,
            WHITE,
            true
        )
        thicknessSection.addView(thicknessLabel)

        val currentThickness = prefs.getInt("line_thickness", DEFAULT_THICKNESS)
            .coerceIn(MIN_THICKNESS, MAX_THICKNESS)
        val thicknessSlider = SeekBar(this).apply {
            max = MAX_THICKNESS - MIN_THICKNESS
            progress = currentThickness - MIN_THICKNESS
            minimumHeight = dp(48)
            setPadding(dp(2), dp(6), dp(2), dp(6))
            isEnabled = true
            styleSeekBar(this)
        }
        thicknessSlider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + MIN_THICKNESS
                    prefs.edit().putInt("line_thickness", value).apply()
                    thicknessLabel.text = thicknessText(value)
                    webView?.invalidate()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )
        thicknessSection.addView(thicknessSlider)
        thicknessSection.addView(
            makeText("0.5 dp                                      5.0 dp", 10f, MUTED)
        )
        addSection(content, thicknessSection, 10)

        // Select the drawing pattern. The choice is persisted.
        val gridStyleSection = makeSection()
        gridStyleSection.addView(makeText("GRID STYLE", 11f, MUTED, true))
        gridStyleSection.addView(
            makeText("Choose the line pattern shown over the table.", 11f, WHITE).apply {
                setPadding(0, dp(5), 0, dp(8))
            }
        )
        val selectedGridStyle = prefs.getInt("grid_style", 0)
        val classicGridButton = makeAction(
            if (selectedGridStyle == 0) "✓  CLASSIC GRID" else "CLASSIC GRID",
            if (selectedGridStyle == 0) BLUE else BORDER,
            if (selectedGridStyle == 0) WHITE else MUTED
        ) {
            prefs.edit().putInt("grid_style", 0).apply()
            webView?.invalidate()
            showMainPanel()
        }
        val trickshotGridButton = makeAction(
            if (selectedGridStyle == 1) "✓  TRICKSHOT GRID" else "TRICKSHOT GRID",
            if (selectedGridStyle == 1) BLUE else BORDER,
            if (selectedGridStyle == 1) WHITE else MUTED
        ) {
            prefs.edit().putInt("grid_style", 1).apply()
            webView?.invalidate()
            showMainPanel()
        }
        gridStyleSection.addView(classicGridButton)
        gridStyleSection.addView(
            trickshotGridButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(7) }
        )
        addSection(content, gridStyleSection, 10)

        // Keep alignment controls separate from everyday toggles.
        val alignmentSection = makeSection()
        alignmentSection.addView(makeText("SCREEN ALIGNMENT", 11f, MUTED, true))
        alignmentSection.addView(
            makeText("Set the playing-area boundaries when needed.", 11f, WHITE).apply {
                setPadding(0, dp(5), 0, dp(9))
            }
        )
        alignmentSection.addView(makeAction("⚙  ALIGNMENT SETUP", BLUE) {
            showAlignmentSetup()
        })
        addSection(content, alignmentSection, 10)

        content.addView(
            makeText("Drag the title to move this panel.", 10f, MUTED).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, dp(4))
            }
        )
    }

    private fun thicknessText(value: Int): String {
        val dpValue = value / 10f
        return String.format(
            Locale.US,
            "LINE THICKNESS     %.1f dp",
            dpValue
        )
    }

    private fun updateGridButton() {
        val panel = alignmentPanel ?: return
        if (isAlignmentScreen) return

        val gridButton = findViewWithTag(
            panel,
            "grid_toggle"
        ) as? TextView ?: return

        gridButton.text = if (guidelinesVisible) {
            "✓  GRIDLINES ON  •  TAP TO HIDE"
        } else {
            "GRIDLINES OFF  •  TAP TO SHOW"
        }

        gridButton.background = roundedBackground(
            if (guidelinesVisible) CARD else BLUE,
            10
        )

        gridButton.setTextColor(
            if (guidelinesVisible) CYAN else WHITE
        )
    }

    private fun findViewWithTag(
        view: View,
        tag: Any
    ): View? {
        if (view.tag == tag) return view

        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val result = findViewWithTag(
                    view.getChildAt(i),
                    tag
                )

                if (result != null) return result
            }
        }

        return null
    }

    // ---------------------------------------------------------
    // ALIGNMENT SETUP SCREEN
    // ---------------------------------------------------------

    private fun buildAlignmentContent(content: LinearLayout) {
        content.addView(
            makeText(
                "Adjust the four boundaries to fit your screen.",
                11f,
                MUTED
            ).apply {
                setPadding(dp(2), 0, dp(2), dp(10))
            }
        )

        addAlignmentSlider(
            content,
            key = "left",
            title = "LEFT BOUNDARY",
            defaultValue = 221
        )

        addAlignmentSlider(
            content,
            key = "right",
            title = "RIGHT BOUNDARY",
            defaultValue = 814
        )

        addAlignmentSlider(
            content,
            key = "top",
            title = "TOP BOUNDARY",
            defaultValue = 239
        )

        addAlignmentSlider(
            content,
            key = "bottom",
            title = "BOTTOM BOUNDARY",
            defaultValue = 887
        )

        content.addView(
            makeAction("DONE  •  BACK TO QUICK CONTROLS", BLUE) {
                showMainPanel()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(8)
            }
        )

        content.addView(
            makeText(
                "Your boundary settings are saved automatically.",
                10f,
                MUTED
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, dp(5))
            }
        )
    }

    private fun addAlignmentSlider(
        content: LinearLayout,
        key: String,
        title: String,
        defaultValue: Int
    ) {
        val section = makeSection()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val label = makeText(
            "",
            11f,
            WHITE,
            true
        )

        row.addView(
            label,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val slider = SeekBar(this).apply {
            max = 950
            progress = prefs.getInt(key, defaultValue)
                .coerceIn(0, 950)
            styleSeekBar(this)
        }

        fun updateLabel(value: Int) {
            label.text = String.format(
                Locale.US,
                "%s  %.1f%%",
                title,
                value / 10f
            )
        }

        fun saveValue(value: Int) {
            val safeValue = value.coerceIn(0, 950)

            prefs.edit()
                .putInt(key, safeValue)
                .apply()

            if (slider.progress != safeValue) {
                slider.progress = safeValue
            }

            updateLabel(safeValue)
            webView?.invalidate()
        }

        updateLabel(slider.progress)

        val minus = makeAction("−", BORDER, WHITE) {
            saveValue(slider.progress - 1)
        }.apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        val plus = makeAction("+", BORDER, CYAN) {
            saveValue(slider.progress + 1)
        }.apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        row.addView(
            minus,
            LinearLayout.LayoutParams(
                dp(38),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(5)
            }
        )

        row.addView(
            plus,
            LinearLayout.LayoutParams(
                dp(38),
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(5)
            }
        )

        section.addView(row)

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

        section.addView(slider)

        addSection(content, section, 9)
    }

    // ---------------------------------------------------------
    // PANEL DRAGGING
    // ---------------------------------------------------------

    private fun attachDragHandle(
        handle: View,
        panel: View,
        params: WindowManager.LayoutParams
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()

                    val screenWidth =
                        resources.displayMetrics.widthPixels

                    val screenHeight =
                        resources.displayMetrics.heightPixels

                    params.x = (startX + dx).coerceIn(
                        0,
                        (screenWidth - panel.width).coerceAtLeast(0)
                    )

                    params.y = (startY + dy).coerceIn(
                        0,
                        (screenHeight - panel.height).coerceAtLeast(0)
                    )

                    try {
                        windowManager.updateViewLayout(panel, params)
                    } catch (_: Exception) {
                        // Panel might have been detached.
                    }

                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> true

                else -> false
            }
        }
    }

    // ---------------------------------------------------------
    // COLLAPSE TO THE FLOATING CA BUBBLE
    // ---------------------------------------------------------

    private fun collapsePanel() {
        val oldParams = panelParams

        val x = oldParams?.x ?: dp(12)
        val y = oldParams?.y ?: dp(45)

        removeCurrentPanel(keepPosition = true)
        showBubble(x, y)
    }

    private fun showBubble(x: Int, y: Int) {
        if (bubbleView != null) return

        val bubble = TextView(this).apply {
            text = "CA"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WHITE)
            gravity = Gravity.CENTER
            background = roundedBackground(BLUE, 50, CYAN)
            elevation = dp(10).toFloat()
            isClickable = true
        }

        val params = WindowManager.LayoutParams(
            dp(54),
            dp(54),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x.coerceAtLeast(0)
            this.y = y.coerceAtLeast(0)
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()

                    if (kotlin.math.abs(dx) > dp(4) ||
                        kotlin.math.abs(dy) > dp(4)
                    ) {
                        moved = true
                    }

                    params.x = (startX + dx).coerceIn(
                        0,
                        (resources.displayMetrics.widthPixels -
                            bubble.width).coerceAtLeast(0)
                    )

                    params.y = (startY + dy).coerceIn(
                        0,
                        (resources.displayMetrics.heightPixels -
                            bubble.height).coerceAtLeast(0)
                    )

                    try {
                        windowManager.updateViewLayout(
                            bubble,
                            params
                        )
                    } catch (_: Exception) {
                        // Bubble might have been detached.
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        hideBubble()
                        showMainPanel()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> true

                else -> false
            }
        }

        bubbleView = bubble
        bubbleParams = params

        try {
            windowManager.addView(bubble, params)
        } catch (_: Exception) {
            bubbleView = null
            bubbleParams = null
        }
    }

    private fun hideBubble() {
        bubbleView?.let {
            try {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            } catch (_: Exception) {
                // Ignore if already detached.
            }
        }

        bubbleView = null
        bubbleParams = null
    }

    // ---------------------------------------------------------
    // PANEL CLEANUP
    // ---------------------------------------------------------

    private fun removeCurrentPanel(keepPosition: Boolean) {
        val panel = alignmentPanel

        if (panel != null) {
            try {
                if (panel.isAttachedToWindow) {
                    windowManager.removeView(panel)
                }
            } catch (_: Exception) {
                // Ignore if already detached.
            }
        }

        alignmentPanel = null

        if (!keepPosition) {
            panelParams = null
        }
    }

    private fun hidePanel() {
        removeCurrentPanel(keepPosition = false)
    }

    override fun onDestroy() {
        hidePanel()
        hideBubble()

        webView?.let {
            try {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            } catch (_: Exception) {
                // Ignore if already detached.
            }
        }

        webView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------
    // GRID DRAWING
    // ---------------------------------------------------------

    private inner class GuidelineView : View(this@OverlayService) {

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

        private fun drawClassicGrid(
            canvas: Canvas,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            midX: Float,
            midY: Float
        ) {
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
        }

        /*
         * Trickshot layout based on the reference image:
         * - six internal points along the top and bottom edges
         * - three internal points along each side edge
         * - all six pockets are also origins
         * Every origin fans toward the other table edges to create the dense
         * crossing pattern. All coordinates follow the saved table bounds.
         */
        /*
         * Reference-style trickshot grid.
         *
         * Use actual edge anchor points rather than drawing 12 arbitrary
         * subdivisions from every origin. This keeps the pattern structured:
         *  - 6 internal anchors on top and bottom (plus the corner pockets)
         *  - 3 internal anchors on left and right (plus corner and side pockets)
         *  - connect anchors that belong to different edges only
         *
         * Shared corner anchors belong to two edges, so lines are not drawn
         * between points on the same rail. Each pair is drawn only once.
         */
        /*
         * Reference-style trickshot grid.
         * - Six diamond anchors along the top and bottom rails.
         * - One additional pocket origin at the midpoint of the top and bottom.
         * - Three additional anchors on each left/right edge.
         * - Four corner pockets.
         * Points on the same rail are not connected to each other.
         */
        private fun drawTrickshotGrid(
            canvas: Canvas,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            midX: Float,
            midY: Float
        ) {
            data class Anchor(
                val x: Float,
                val y: Float,
                val edges: Set<Int>
            )

            // Edge IDs: 0 = top, 1 = bottom, 2 = left, 3 = right.
            val anchors = mutableListOf<Anchor>()

            // Four corner pockets.
            anchors += Anchor(left, top, setOf(0, 2))
            anchors += Anchor(right, top, setOf(0, 3))
            anchors += Anchor(left, bottom, setOf(1, 2))
            anchors += Anchor(right, bottom, setOf(1, 3))

            // Six diamond points on top and bottom, excluding the midpoint.
            // The midpoint is added separately as the pocket origin.
            val diamondFractions = floatArrayOf(
                1f / 8f, 2f / 8f, 3f / 8f,
                5f / 8f, 6f / 8f, 7f / 8f
            )
            for (fraction in diamondFractions) {
                val x = left + (right - left) * fraction
                anchors += Anchor(x, top, setOf(0))
                anchors += Anchor(x, bottom, setOf(1))
            }

            // The two middle pockets are on the top and bottom rails.
            anchors += Anchor(midX, top, setOf(0))
            anchors += Anchor(midX, bottom, setOf(1))

            // Three additional points on each side edge. These are not pockets.
            for (fraction in floatArrayOf(0.25f, 0.5f, 0.75f)) {
                val y = top + (bottom - top) * fraction
                anchors += Anchor(left, y, setOf(2))
                anchors += Anchor(right, y, setOf(3))
            }

            // Draw each connection once, only between different edges.
            for (i in anchors.indices) {
                val a = anchors[i]
                for (j in i + 1 until anchors.size) {
                    val b = anchors[j]
                    if (a.edges.intersect(b.edges).isEmpty()) {
                        canvas.drawLine(a.x, a.y, b.x, b.y, linePaint)
                    }
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (!guidelinesVisible) return
            if (width < height) return

            val thickness = prefs.getInt(
                "line_thickness",
                DEFAULT_THICKNESS
            ).coerceIn(MIN_THICKNESS, MAX_THICKNESS)

            linePaint.strokeWidth =
                resources.displayMetrics.density *
                    (thickness / 10f)

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

            if (prefs.getInt("grid_style", 0) == 1) {
                drawTrickshotGrid(
                    canvas, left, top, right, bottom, midX, midY
                )
            } else {
                drawClassicGrid(
                    canvas, left, top, right, bottom, midX, midY
                )
            }

            canvas.restoreToCount(save)
        }
    }
}