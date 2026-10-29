
package com.example.poolweboverlay

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(18, 24, 32))
        }

        content.addView(TextView(this).apply {
            text = "POOL WEB OVERLAY"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        content.addView(TextView(this).apply {
            text = "Alignment Settings"
            textSize = 18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        })

        addSlider(content, "left", "Left boundary")
        addSlider(content, "right", "Right boundary")
        addSlider(content, "top", "Top boundary")
        addSlider(content, "bottom", "Bottom boundary")

        content.addView(makeButton("Start guideline web") {
            startGuidelineWeb()
        })

        content.addView(makeButton("Stop guideline web") {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(
                this,
                "Overlay stopped",
                Toast.LENGTH_SHORT
            ).show()
        })

        content.addView(makeButton("Reset alignment") {
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
        })

        content.addView(TextView(this).apply {
            text = "Tip: Start the web, open 8 Ball Pool, then use the persistent notification's Adjust alignment button to tune the box without leaving the game."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 16, 0, 0)
        })

        val scrollView = ScrollView(this).apply {
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
            setTextColor(Color.WHITE)
            textSize = 15f
        }

        labels[key] = label
        layout.addView(label)

        val slider = SeekBar(this).apply {
            max = 950
            progress = prefs.getInt(
                key,
                defaults.getValue(key)
            ).coerceIn(0, max)
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

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {}

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {}
            }
        )

        layout.addView(slider)
    }

    private fun updateLabel(key: String, progress: Int) {
        val title = when (key) {
            "left" -> "Left boundary"
            "right" -> "Right boundary"
            "top" -> "Top boundary"
            else -> "Bottom boundary"
        }

        labels[key]?.text = "$title: ${progress / 10f}%"
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
                "Guideline web started",
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

    private fun makeButton(
        textValue: String,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text = textValue
            setOnClickListener { action() }
        }
    }
}
