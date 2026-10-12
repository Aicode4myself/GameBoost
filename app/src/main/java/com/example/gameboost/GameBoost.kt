package com.example.gameboost

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import java.util.Locale
import rikka.shizuku.Shizuku

class BoostActivity : Activity() {

    private val labels = ArrayList<String>()
    private val pkgs = ArrayList<String>()
    private val fpsOptions = listOf("30", "45", "60", "90", "120")
    private lateinit var appSpinner: Spinner
    private lateinit var fpsSpinner: Spinner
    private lateinit var scaleBar: SeekBar
    private lateinit var scaleText: TextView
    private lateinit var status: TextView

    private fun fmt(p: Int): String = String.format(Locale.US, "%.2f", 0.30 + 0.05 * p)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pm = packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launch, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
            .forEach { labels.add(it.first); pkgs.add(it.second) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        fun title(t: String) = TextView(this).apply {
            text = t
            textSize = 16f
            setPadding(0, 32, 0, 8)
            root.addView(this)
        }

        title("Game")
        appSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@BoostActivity, android.R.layout.simple_spinner_dropdown_item, labels)
            root.addView(this)
        }

        title("Presets")
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            root.addView(this)
        }
        fun preset(name: String, p: Int, f: Int) = Button(this).apply {
            text = name
            setOnClickListener {
                scaleBar.progress = p
                fpsSpinner.setSelection(f)
            }
            row.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        preset("Save power", 4, 0)
        preset("Balanced", 8, 1)
        preset("High", 12, 2)

        scaleText = TextView(this).apply {
            textSize = 16f
            setPadding(0, 32, 0, 8)
            text = "Resolution scale: " + fmt(8)
            root.addView(this)
        }
        scaleBar = SeekBar(this).apply {
            max = 12
            progress = 8
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {
                    scaleText.text = "Resolution scale: " + fmt(p)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            root.addView(this)
        }

        title("FPS cap")
        fpsSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@BoostActivity, android.R.layout.simple_spinner_dropdown_item, fpsOptions)
            setSelection(1)
            root.addView(this)
        }

        fun action(t: String, f: () -> Unit) = Button(this).apply {
            text = t
            setOnClickListener { f() }
            root.addView(this)
        }
        action("Apply") { applySettings() }
        action("Reset this game") { resetSettings() }
        action("Launch game") { launchGame() }

        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, 32, 0, 0)
            setTextIsSelectable(true)
            text = "Start Shizuku first, then pick a game and tap Apply."
            root.addView(this)
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun currentPkg(): String? = pkgs.getOrNull(appSpinner.selectedItemPosition)

    private fun applySettings() {
        val pkg = currentPkg() ?: return
        val scale = fmt(scaleBar.progress)
        val fps = fpsOptions[fpsSpinner.selectedItemPosition]
        runShell(
            "cmd game set --mode performance --downscale $scale --fps $fps $pkg && cmd game mode performance $pkg",
            "Applied: scale $scale, $fps FPS"
        )
    }

    private fun resetSettings() {
        val pkg = currentPkg() ?: return
        runShell("cmd game reset $pkg; cmd game mode standard $pkg", "Reset to default")
    }

    private fun launchGame() {
        val pkg = currentPkg() ?: return
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
    }

    private fun setStatus(s: String) { status.text = s }

    private fun runShell(cmd: String, okMsg: String) {
        try {
            if (!Shizuku.pingBinder()) {
                setStatus("Shizuku isn't running. Open Shizuku and start it first.")
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(100)
                setStatus("Allow the Shizuku permission, then tap again.")
                return
            }
        } catch (e: Throwable) {
            setStatus("Shizuku error: " + e.message)
            return
        }
        setStatus("Working...")
        Thread {
            val msg = try {
                val m = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                m.isAccessible = true
                val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
                val out = p.inputStream.bufferedReader().readText()
                val err = p.errorStream.bufferedReader().readText()
                val code = p.waitFor()
                if (code == 0) okMsg + (if (out.isNotBlank()) "\n" + out else "")
                else "Failed ($code): $err $out"
            } catch (e: Throwable) {
                "Error: " + (e.cause?.message ?: e.message)
            }
            runOnUiThread { setStatus(msg) }
        }.start()
    }
}
