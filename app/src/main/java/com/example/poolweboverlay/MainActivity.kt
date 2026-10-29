
package com.example.poolweboverlay

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("pool_web_settings", MODE_PRIVATE)
    }

    private val defaults = mapOf(
        "left" to 221,
        "right" to 814,
        "top" to 239,
        "bottom" to 887
    )

    private val bars = mutableMapOf<String, SeekBar>()
    private val labels = mutableMapOf<String, TextView>()

    private val bg = Color.rgb(5, 15, 29)
    private val cardBg = Color.rgb(12, 29, 47)
    private val cardBorder = Color.rgb(29, 58, 79)
    private val cyan = Color.rgb(0, 217, 192)
    private val blue = Color.rgb(8, 126, 255)
    private val white = Color.rgb(241, 247, 255)
    private val muted = Color.rgb(153, 176, 198)
    private val red = Color.rgb(255, 83, 104)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
            setBackgroundColor(bg)
        }

        // Brand header
        content.addView(TextView(this).apply {
            text = "◉  CUE AIM PRO"
            textSize = 27f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(cyan)
            gravity = Gravity.CENTER_VERTICAL
            letterSpacing = 0.02f
        })

        content.addView(TextView(this).apply {
            text = "POOL WEB OVERLAY"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.20f
            setTextColor(muted)
            setPadding(2, dp(4), 0, dp(18))
        })

        // Status card
        val statusCard = makeCard()
        statusCard.addView(TextView(this).apply {
            text = "●  OVERLAY CONTROL"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(cyan)
        })

        statusCard.addView(TextView(this).apply {
            text = "Aim better. Play smarter."
            textSize = 22f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(white)
            setPadding(0, dp(10), 0, dp(4))
        })

        statusCard.addView(TextView(this).apply {
            text = "Adjust your saved guideline boundaries and manage the floating overlay."
            textSize = 14f
            setTextColor(muted)
            setLineSpacing(dp(3).toFloat(), 1f)
        })

        content.addView(statusCard, sectionParams())

        // Main actions
        content.addView(sectionTitle("QUICK ACTIONS"))

        val startButton = makeButton(
            "▶   Start Guideline Web",
            cyan,
            Color.rgb(2, 25, 34)
        ) {
            startGuidelineWeb()
        }
        content.addView(startButton, buttonParams())

        val stopButton = makeButton(
            "■   Stop Guideline Web",
            Color.rgb(61, 24, 39),
            Color.rgb(255, 132, 148)
        ) {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(
                this,
                "Overlay stopped",
                Toast.LENGTH_SHORT
            ).show()
        }
        content.addView(stopButton, buttonParams())

        // Alignment settings card
        content.addView(sectionTitle("ALIGNMENT SETTINGS"))

        val alignmentCard = makeCard()

        alignmentCard.addView(TextView(this).apply {
            text = "Fine-tune the web"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
        })

        alignmentCard.addView(TextView(this).apply {
            text = "Move each slider to adjust a boundary. Changes are saved automatically."
            textSize = 13f
            setTextColor(muted)
            setPadding(0, dp(5), 0, dp(14))
        })

        addSlider(alignmentCard, "left", "Left boundary")
        addSlider(alignmentCard, "right", "Right boundary")
        addSlider(alignmentCard, "top", "Top boundary")
        addSlider(alignmentCard, "bottom", "Bottom boundary")

        content.addView(alignmentCard, sectionParams())

        val resetButton = makeButton(
            "↻   Reset Alignment",
            cardBg,
            cyan
        ) {
            defaults.forEach { (key, value) ->
                prefs.edit().putInt(key, value).apply()
                bars[key]?.progress = value
                updateLabel(key, value)
            }

            sendAlignmentUpdate()

            Toast.makeText(
                this,
                "Alignment reset",
                Toast.LENGTH_SHORT
            ).show()
        }
        content.addView(resetButton, buttonParams())

        // Usage guidance
        val tipCard = makeCard()
        tipCard.addView(TextView(this).apply {
            text = "HOW TO USE"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.10f
            setTextColor(cyan)
        })

        tipCard.addView(TextView(this).apply {
            text = "1. Start the guideline web.\n\n" +
                "2. Open 8 Ball Pool in landscape mode.\n\n" +
                "3. Use the persistent notification's alignment control to fine-tune the web without leaving the game."
            textSize = 14f
            setTextColor(white)
            setPadding(0, dp(10), 0, 0)
            setLineSpacing(dp(3).toFloat(), 1f)
        })

        content.addView(tipCard, sectionParams())

        content.addView(TextView(this).apply {
            text = "CUE AIM PRO  •  VERSION 1.1"
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = 0.10f
            setTextColor(muted)
            setPadding(0, dp(18), 0, dp(4))
        })

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg)
            addView(content)
        }

        setContentView(scrollView)
    }

    private fun addSlider(
        layout: LinearLayout,
        key: String,
        title: String
    ) {
        val label = TextView(this).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(white)
        }

        labels[key] = label
        layout.addView(label)

        val slider = SeekBar(this).apply {
            max = 950
            progress = prefs.getInt(
                key,
                defaults.getValue(key)
            ).coerceIn(0, max)

            progressTintList = ColorStateList.valueOf(cyan)
            thumbTintList = ColorStateList.valueOf(blue)
            progressBackgroundTintList = ColorStateList.valueOf(cardBorder)
        }

        bars[key] = slider
        updateLabel(key, slider.progress)

        slider.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    prefs.edit().putInt(key, progress).apply()
                    updateLabel(key, progress)

                    if (fromUser) {
                        sendAlignmentUpdate()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        layout.addView(
            slider,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            ).apply {
                bottomMargin = dp(10)
            }
        )
    }

    private fun updateLabel(key: String, progress: Int) {
        val title = when (key) {
            "left" -> "Left boundary"
            "right" -> "Right boundary"
            "top" -> "Top boundary"
            else -> "Bottom boundary"
        }

        labels[key]?.text = "$title                         ${progress / 10f}%"
    }

    private fun startGuidelineWeb() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        try {
            startService(
                Intent(this, OverlayService::class.java)
                    .setAction(OverlayService.ACTION_START)
            )

            Toast.makeText(
                this,
                "CueAim Pro started",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to start overlay: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun sendAlignmentUpdate() {
        if (!Settings.canDrawOverlays(this)) return

        try {
            startService(
                Intent(this, OverlayService::class.java)
                    .setAction(OverlayService.ACTION_UPDATE)
            )
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Unable to update overlay: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            background = roundedBackground(cardBg, cardBorder, 18)
        }
    }

    private fun sectionTitle(value: String): TextView {
        return TextView(this).apply {
            text = value
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            setTextColor(muted)
            setPadding(2, dp(10), 0, dp(8))
        }
    }

    private fun makeButton(
        textValue: String,
        backgroundColor: Int,
        textColor: Int,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = textValue
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            isAllCaps = false
            gravity = Gravity.CENTER
            background = roundedBackground(
                backgroundColor,
                if (backgroundColor == cyan) cyan else cardBorder,
                15
            )
            setPadding(dp(12), dp(8), dp(12), dp(8))
            stateListAnimator = null
            setOnClickListener { action() }
        }
    }

    private fun roundedBackground(
        color: Int,
        strokeColor: Int,
        radiusDp: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), strokeColor)
        }
    }

    private fun sectionParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }
    }

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(58)
        ).apply {
            bottomMargin = dp(10)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
