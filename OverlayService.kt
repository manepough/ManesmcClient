package com.project.manes

import android.app.*
import androidx.core.app.NotificationCompat
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.*

// ── Macro System ──────────────────────────────────────────────────────────────
data class Macro(val name: String, val commands: List<String>, var color: Int = Color.parseColor("#4A7FFF"))

object MacroManager {
    val macros = mutableListOf(
        Macro("Sprint+KB Off", listOf("AutoSprint:on","AntiKnockback:on")),
        Macro("PvP Mode",      listOf("KillAura:on","Reach:on","NoFall:on","AutoSprint:on")),
        Macro("Reset",         listOf("KillAura:off","Reach:off","NoFall:off","AutoSprint:off","AntiKnockback:off"))
    )

    fun run(macro: Macro) {
        macro.commands.forEach { cmd ->
            val parts = cmd.split(":")
            if (parts.size == 2) {
                val mod = Modules.all.firstOrNull { it.name.equals(parts[0], true) }
                mod?.enabled = parts[1] == "on"
            }
        }
    }
}

// ── OverlayService ────────────────────────────────────────────────────────────
class OverlayService : Service() {

    private var wm: WindowManager? = null
    private var btnView: View? = null
    private var panelView: View? = null
    private var panelOpen = false
    private var currentTab = "Combat"

    companion object {
        const val CH = "manes_ov"
        fun hasPermission(ctx: Context) = Settings.canDrawOverlays(ctx)
        fun start(ctx: Context) { ctx.startForegroundService(Intent(ctx, OverlayService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, OverlayService::class.java)) }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, buildNote())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addButton()
    }

    // ── Draggable circle button ───────────────────────────────────────────────
    private fun addButton() {
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val d = resources.displayMetrics.density
        val sz = (40 * d).toInt()
        val lp = WindowManager.LayoutParams(sz, sz, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = 16; y = (180*d).toInt() }

        val btn = object : TextView(this) {
            private var ox=0f; private var oy=0f; private var lx=0f; private var ly=0f; private var moved=false
            init {
                text="⚡"; textSize=16f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#CC000000"))
                    setStroke((2*d).toInt(), Color.parseColor("#666666"))
                }
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when(e.action) {
                    MotionEvent.ACTION_DOWN -> { ox=lp.x-e.rawX; oy=lp.y-e.rawY; lx=e.rawX; ly=e.rawY; moved=false }
                    MotionEvent.ACTION_MOVE -> {
                        if(Math.abs(e.rawX-lx)>6||Math.abs(e.rawY-ly)>6) {
                            moved=true
                            val dm=resources.displayMetrics
                            lp.x=(e.rawX+ox).toInt().coerceIn(0,dm.widthPixels-sz)
                            lp.y=(e.rawY+oy).toInt().coerceIn(0,dm.heightPixels-sz)
                            try { wm?.updateViewLayout(this,lp) } catch(_:Exception){}
                        }
                    }
                    MotionEvent.ACTION_UP -> if(!moved) toggle()
                }
                return true
            }
        }
        btnView = btn
        try { wm?.addView(btn,lp) } catch(e:Exception){ e.printStackTrace() }
    }

    private fun toggle() { if(panelOpen) closePanel() else openPanel() }

    // ── Main panel ────────────────────────────────────────────────────────────
    private fun openPanel() {
        panelOpen = true
        val type = if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val dm = resources.displayMetrics; val d = dm.density
        val W = (dm.widthPixels * 0.78f).toInt()
        val H = (dm.heightPixels * 0.84f).toInt()
        val lp = WindowManager.LayoutParams(W, H, type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.CENTER }

        // Root — Meteor-style black rounded panel
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F0101010"))
                cornerRadius = 8 * d
                setStroke((1*d).toInt(), Color.parseColor("#2A2A2A"))
            }
        }

        // ── Tab bar (top) ─────────────────────────────────────────────────────
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val tabs = listOf(
            "Combat" to "⚔",
            "Motion" to "🏃",
            "Visual" to "👁",
            "World"  to "🌍",
            "Misc"   to "⚙",
            "Macro"  to "▶"
        )
        val tabViews = mutableMapOf<String, TextView>()

        val body = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // Close btn in tab bar
        val closeBtn = TextView(this).apply {
            text = "✕"; textSize = 13f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#888888"))
            setPadding((12*d).toInt(), (10*d).toInt(), (12*d).toInt(), (10*d).toInt())
            setOnClickListener { closePanel() }
        }

        fun switchTab(tab: String) {
            currentTab = tab
            tabViews.forEach { (name, tv) ->
                val on = name == tab
                tv.setTextColor(if(on) Color.WHITE else Color.parseColor("#666666"))
                tv.setBackgroundColor(if(on) Color.parseColor("#2A2A2A") else Color.TRANSPARENT)
            }
            body.removeAllViews()
            if (tab == "Macro") {
                body.addView(buildMacroPanel(d))
            } else {
                body.addView(buildModulePanel(tab, d))
            }
        }

        tabs.forEach { (name, icon) ->
            val tv = TextView(this).apply {
                text = "$icon $name"; textSize = 11f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#666666"))
                setPadding((10*d).toInt(), (10*d).toInt(), (10*d).toInt(), (10*d).toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { switchTab(name) }
            }
            tabViews[name] = tv
            tabBar.addView(tv)
        }
        tabBar.addView(closeBtn)
        root.addView(tabBar)
        root.addView(body.also {
            it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })

        switchTab(currentTab)
        panelView = root
        try { wm?.addView(root, lp) } catch(e:Exception){ e.printStackTrace() }
    }

    // ── Module panel — Meteor style ───────────────────────────────────────────
    private fun buildModulePanel(cat: String, d: Float): View {
        val scroll = ScrollView(this).apply {
            isScrollbarFadingEnabled = false
            setBackgroundColor(Color.parseColor("#101010"))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8*d).toInt(), (8*d).toInt(), (8*d).toInt(), (8*d).toInt())
        }

        val modCat = when(cat) { "Motion" -> "Motion"; "Visual" -> "Visual"; else -> cat }
        val mods = Modules.all.filter { it.category == modCat }

        mods.forEach { mod ->
            col.addView(buildModuleCard(mod, d))
        }
        scroll.addView(col)
        return scroll
    }

    private fun buildModuleCard(mod: Module, d: Float): LinearLayout {
        val enabledColor = "#4A7FFF"
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (4*d).toInt() }
        }

        fun cardBg(on: Boolean) = GradientDrawable().apply {
            cornerRadius = 4 * d
            setColor(if(on) Color.parseColor("#1A1A2E") else Color.parseColor("#1A1A1A"))
            setStroke((1*d).toInt(), if(on) Color.parseColor(enabledColor) else Color.parseColor("#2A2A2A"))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = cardBg(mod.enabled)
            setPadding((12*d).toInt(), (10*d).toInt(), (10*d).toInt(), (10*d).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true
        }

        // Left: enabled indicator bar
        val bar = View(this).apply {
            setBackgroundColor(if(mod.enabled) Color.parseColor(enabledColor) else Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams((3*d).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
                .apply { marginEnd = (10*d).toInt() }
        }

        val nameCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameCol.addView(TextView(this).apply {
            text = mod.name; textSize = 13f
            setTextColor(if(mod.enabled) Color.WHITE else Color.parseColor("#AAAAAA"))
            typeface = Typeface.DEFAULT_BOLD
        })
        nameCol.addView(TextView(this).apply {
            text = mod.desc; textSize = 10f
            setTextColor(Color.parseColor("#555555"))
        })

        // Toggle switch — Meteor style
        val toggle = TextView(this).apply {
            text = if(mod.enabled) "ON" else "OFF"
            textSize = 10f; gravity = Gravity.CENTER
            setTextColor(if(mod.enabled) Color.parseColor(enabledColor) else Color.parseColor("#444444"))
            setPadding((8*d).toInt(), (4*d).toInt(), (8*d).toInt(), (4*d).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 3*d
                setColor(Color.parseColor("#0A0A0A"))
                setStroke((1*d).toInt(), if(mod.enabled) Color.parseColor(enabledColor) else Color.parseColor("#333333"))
            }
        }

        // Settings area (sliders)
        val hasSettings = mod is HitboxModule || mod is ReachModule || mod is KillAuraModule || mod is TimerModule
        val settingsArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12*d).toInt(), (6*d).toInt(), (12*d).toInt(), (8*d).toInt())
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#0D0D1A"))
        }
        when(mod) {
            is HitboxModule -> settingsArea.addView(sliderRow("Scale", 1f, 4f, mod.scale, d, enabledColor) { mod.scale = it })
            is ReachModule -> settingsArea.addView(sliderRow("Reach", 3f, 8f, mod.reach, d, enabledColor) { mod.reach = it })
            is KillAuraModule -> {
                settingsArea.addView(sliderRow("Range", 2f, 6f, mod.range, d, enabledColor) { mod.range = it })
                settingsArea.addView(sliderRow("Delay ms", 50f, 500f, mod.delayMs.toFloat(), d, enabledColor) { mod.delayMs = it.toInt() })
            }
            is TimerModule -> settingsArea.addView(sliderRow("Speed", 0.5f, 3f, mod.speed, d, enabledColor) { mod.speed = it })
        }

        row.setOnClickListener {
            mod.enabled = !mod.enabled
            row.background = cardBg(mod.enabled)
            bar.setBackgroundColor(if(mod.enabled) Color.parseColor(enabledColor) else Color.TRANSPARENT)
            toggle.text = if(mod.enabled) "ON" else "OFF"
            toggle.setTextColor(if(mod.enabled) Color.parseColor(enabledColor) else Color.parseColor("#444444"))
            (toggle.background as? GradientDrawable)?.setStroke((1*d).toInt(),
                if(mod.enabled) Color.parseColor(enabledColor) else Color.parseColor("#333333"))
            (nameCol.getChildAt(0) as? TextView)?.setTextColor(
                if(mod.enabled) Color.WHITE else Color.parseColor("#AAAAAA"))
        }

        if (hasSettings) {
            val gearBtn = TextView(this).apply {
                text = "⚙"; textSize = 12f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#555555"))
                setPadding((8*d).toInt(), 0, 0, 0)
                setOnClickListener {
                    settingsArea.visibility = if(settingsArea.visibility == View.GONE) View.VISIBLE else View.GONE
                }
            }
            row.addView(bar); row.addView(nameCol); row.addView(toggle); row.addView(gearBtn)
        } else {
            row.addView(bar); row.addView(nameCol); row.addView(toggle)
        }

        wrapper.addView(row)
        if(hasSettings) wrapper.addView(settingsArea)
        return wrapper
    }

    // ── Macro panel — Mojo Launcher style buttons ─────────────────────────────
    private fun buildMacroPanel(d: Float): View {
        val scroll = ScrollView(this).apply {
            isScrollbarFadingEnabled = false
            setBackgroundColor(Color.parseColor("#101010"))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10*d).toInt(), (10*d).toInt(), (10*d).toInt(), (10*d).toInt())
        }

        // Header
        col.addView(TextView(this).apply {
            text = "MACROS"; textSize = 11f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, 0, 0, (10*d).toInt())
            letterSpacing = 0.2f
        })

        MacroManager.macros.forEach { macro ->
            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply {
                    cornerRadius = 4*d
                    setColor(Color.parseColor("#1A1A1A"))
                    setStroke((1*d).toInt(), Color.parseColor("#2A2A2A"))
                }
                setPadding((14*d).toInt(), (12*d).toInt(), (14*d).toInt(), (12*d).toInt())
                isClickable = true; isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (6*d).toInt() }
            }

            // Color bar
            btn.addView(View(this).apply {
                setBackgroundColor(macro.color)
                layoutParams = LinearLayout.LayoutParams((3*d).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
                    .apply { marginEnd = (12*d).toInt() }
            })

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textCol.addView(TextView(this).apply {
                text = macro.name; textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            })
            textCol.addView(TextView(this).apply {
                text = macro.commands.joinToString(", "); textSize = 10f
                setTextColor(Color.parseColor("#555555"))
            })

            // Run button — Mojo style
            val runBtn = TextView(this).apply {
                text = "▶ RUN"; textSize = 11f; gravity = Gravity.CENTER
                setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    cornerRadius = 3*d; setColor(Color.parseColor("#1E3A1E"))
                    setStroke((1*d).toInt(), Color.parseColor("#2E8B2E"))
                }
                setPadding((10*d).toInt(), (6*d).toInt(), (10*d).toInt(), (6*d).toInt())
                setOnClickListener {
                    MacroManager.run(macro)
                    // Flash green feedback
                    setTextColor(Color.parseColor("#00FF44"))
                    postDelayed({ setTextColor(Color.WHITE) }, 500)
                }
            }

            btn.addView(textCol); btn.addView(runBtn)
            col.addView(btn)
        }
        scroll.addView(col)
        return scroll
    }

    private fun sliderRow(label: String, min: Float, max: Float, init: Float, d: Float, color: String, onChange: (Float)->Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (6*d).toInt() }
        }
        val lbl = TextView(this).apply {
            text = "$label: ${"%.1f".format(init)}"; textSize = 10f
            setTextColor(Color.parseColor(color))
        }
        val sb = SeekBar(this).apply {
            this.max = 100
            progress = ((init-min)/(max-min)*100).toInt().coerceIn(0,100)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) {
                    val v = min+(max-min)*p/100f; lbl.text="$label: ${"%.1f".format(v)}"; onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        row.addView(lbl); row.addView(sb)
        return row
    }

    private fun closePanel() {
        panelOpen = false
        panelView?.let { try { wm?.removeView(it) } catch(_:Exception){} }
        panelView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closePanel()
        btnView?.let { try { wm?.removeView(it) } catch(_:Exception){} }
    }
    override fun onBind(i: Intent?): IBinder? = null

    private fun createChannel() {
        if(Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "Manes Overlay", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }
    private fun buildNote() = NotificationCompat.Builder(this, CH)
        .setContentTitle("Manes active").setContentText("Tap ⚡ to open modules")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_LOW).build()
}
