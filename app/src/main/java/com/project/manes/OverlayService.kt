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

    private fun addButton() {
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        // Use density-aware size (~44dp)
        val density = resources.displayMetrics.density
        val btnPx = (44 * density).toInt()

        val lp = WindowManager.LayoutParams(
            btnPx, btnPx, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        // Use TOP|START so rawX/rawY map directly — no direction flip
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = (220 * density).toInt() }

        val btn = object : TextView(this) {
            private var ox = 0f; private var oy = 0f
            private var lx = 0f; private var ly = 0f
            private var moved = false
            init {
                text = "☯"; textSize = 22f; gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = oval("#CC2563EB", "#1D4ED8", 2)
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Save offset from finger to view top-left corner
                        ox = lp.x - e.rawX
                        oy = lp.y - e.rawY
                        lx = e.rawX; ly = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (Math.abs(e.rawX - lx) > 8 || Math.abs(e.rawY - ly) > 8) {
                            moved = true
                            val dm = resources.displayMetrics
                            lp.x = (e.rawX + ox).toInt().coerceIn(0, dm.widthPixels - btnPx)
                            lp.y = (e.rawY + oy).toInt().coerceIn(0, dm.heightPixels - btnPx)
                            wm?.updateViewLayout(this, lp)
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) toggle()
                }
                return true
            }
        }
        btnView = btn
        try { wm?.addView(btn, lp) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun toggle() { if (panelOpen) closePanel() else openPanel() }

    private fun openPanel() {
        panelOpen = true
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM; y = 0 }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect("#F0101014", 48f)
            setPadding(24, 24, 24, 24)
        }

        // Header
        val hdr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val ttl = TextView(this).apply {
            text = "☯  Modules"; textSize = 17f
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val cls = TextView(this).apply {
            text = "✕"; textSize = 20f; setTextColor(Color.parseColor("#8B8A9B"))
            setPadding(16,0,0,0); setOnClickListener { closePanel() }
        }
        hdr.addView(ttl); hdr.addView(cls)
        root.addView(hdr)
        root.addView(divider())

        // Tab row
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin=12; bottomMargin=12 }
        }
        val tabNames = listOf("Combat","Visual","World","Motion","Misc")
        val tabViews = mutableMapOf<String,TextView>()

        tabNames.forEach { tabName ->
            val tv = TextView(this).apply {
                text = tabName; textSize = 12f; gravity = Gravity.CENTER
                setPadding(16, 12, 16, 12)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd=4 }
                isClickable = true
            }
            tabViews[tabName] = tv
            tabs.addView(tv)
        }
        root.addView(tabs)
        root.addView(divider())

        // Content scroll — use 55% of screen height so it doesn't overflow
        val screenH = resources.displayMetrics.heightPixels
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (screenH * 0.55f).toInt())
            isScrollbarFadingEnabled = false
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,8,0,8) }
        scroll.addView(content)
        root.addView(scroll)

        fun refreshTabs(active: String) {
            currentTab = active
            tabViews.forEach { (name, tv) ->
                val on = name == active
                val col = tabColor(name)
                tv.setTextColor(if(on) Color.WHITE else Color.parseColor("#8B8A9B"))
                tv.background = if(on) oval(col, col, 0) else oval("#22222228","#333333",0)
            }
            content.removeAllViews()
            val mods = Modules.all.filter { it.category == active }
            mods.forEach { mod -> content.addView(moduleRow(mod, content)) }
        }

        tabViews.forEach { (name, tv) -> tv.setOnClickListener { refreshTabs(name) } }
        refreshTabs(currentTab)

        panelView = root
        try { wm?.addView(root, lp) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun moduleRow(mod: Module, parent: LinearLayout): LinearLayout {
        val cc = tabColor(mod.category)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20,16,20,16)
            background = if(mod.enabled) roundRectStroke("#1A${cc.removePrefix("#")}", 24f, cc) else roundRectStroke("#18181C", 24f, "#333333")
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin=8 }
        }
        // Top row: name + switch
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val nameCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT,1f) }
        val nm = TextView(this).apply { text = mod.name; textSize = 14f; setTextColor(Color.WHITE) }
        val ds = TextView(this).apply { text = mod.desc; textSize = 11f; setTextColor(Color.parseColor("#8B8A9B")) }
        nameCol.addView(nm); nameCol.addView(ds)
        val sw = Switch(this).apply { isChecked = mod.enabled }
        top.addView(nameCol); top.addView(sw)
        row.addView(top)

        // Sliders for configurable modules
        when (mod) {
            is HitboxModule -> {
                row.addView(sliderRow("Width/Height", 1f, 4f, mod.scale) { v -> mod.scale = v })
            }
            is ReachModule -> {
                row.addView(sliderRow("Reach (blocks)", 3f, 8f, mod.reach) { v -> mod.reach = v })
            }
            is KillAuraModule -> {
                row.addView(sliderRow("Range (blocks)", 2f, 6f, mod.range) { v -> mod.range = v })
                row.addView(sliderRow("Delay (ms)", 50f, 500f, mod.delayMs.toFloat()) { v -> mod.delayMs = v.toInt() })
            }
            is TimerModule -> {
                row.addView(sliderRow("Speed", 0.5f, 3f, mod.speed) { v -> mod.speed = v })
            }
        }

        sw.setOnCheckedChangeListener { _, v ->
            mod.enabled = v
            row.background = if(v) roundRectStroke("#1A${cc.removePrefix("#")}", 24f, cc) else roundRectStroke("#18181C", 24f, "#333333")
        }
        return row
    }

    private fun sliderRow(label: String, min: Float, max: Float, initial: Float, onChange: (Float)->Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin=10 }
        }
        val lbl = TextView(this).apply {
            text = "$label: ${"%.1f".format(initial)}"
            textSize = 11f; setTextColor(Color.parseColor("#A78BFA"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd=8 }
            minWidth = 160
        }
        val sb = SeekBar(this).apply {
            this.max = 100
            progress = ((initial - min) / (max - min) * 100).toInt().coerceIn(0, 100)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) {
                    val v = min + (max - min) * p / 100f
                    lbl.text = "$label: ${"%.1f".format(v)}"
                    onChange(v)
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
        panelView?.let { try { wm?.removeView(it) } catch (_:Exception){} }
        panelView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closePanel()
        btnView?.let { try { wm?.removeView(it) } catch (_:Exception){} }
    }
    override fun onBind(i: Intent?): IBinder? = null

    private fun oval(fill: String, stroke: String, sw: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(fill))
        if (sw > 0) setStroke(sw, Color.parseColor(stroke))
    }
    private fun roundRect(fill: String, r: Float) = GradientDrawable().apply {
        setColor(Color.parseColor(fill)); cornerRadius = r
    }
    private fun roundRectStroke(fill: String, r: Float, stroke: String) = GradientDrawable().apply {
        setColor(Color.parseColor(fill)); cornerRadius = r; setStroke(2, Color.parseColor(stroke))
    }
    private fun divider() = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { topMargin=8; bottomMargin=8 }
        setBackgroundColor(Color.parseColor("#222228"))
    }
    private fun tabColor(cat: String) = when(cat) {
        "Combat" -> "#F87171"; "Visual" -> "#A78BFA"; "World" -> "#4ADE80"
        "Motion" -> "#FBBF24"; else -> "#22D3EE"
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "Manes Overlay", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }
    private fun buildNote() = NotificationCompat.Builder(this, CH)
        .setContentTitle("Manes active")
        .setContentText("Tap ☯ in-game to open modules")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
}
