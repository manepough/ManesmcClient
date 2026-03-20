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

        val density = resources.displayMetrics.density
        val btnPx = (44 * density).toInt()

        val lp = WindowManager.LayoutParams(
            btnPx, btnPx, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = (200 * density).toInt() }

        val btn = object : TextView(this) {
            private var ox = 0f; private var oy = 0f
            private var lx = 0f; private var ly = 0f
            private var moved = false
            init {
                text = "⚡"; textSize = 18f; gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#CC0D1117"))
                    setStroke((2 * density).toInt(), Color.parseColor("#2563EB"))
                }
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        ox = lp.x - e.rawX; oy = lp.y - e.rawY
                        lx = e.rawX; ly = e.rawY; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (Math.abs(e.rawX - lx) > 8 || Math.abs(e.rawY - ly) > 8) {
                            moved = true
                            val dm = resources.displayMetrics
                            lp.x = (e.rawX + ox).toInt().coerceIn(0, dm.widthPixels - btnPx)
                            lp.y = (e.rawY + oy).toInt().coerceIn(0, dm.heightPixels - btnPx)
                            try { wm?.updateViewLayout(this, lp) } catch (_: Exception) {}
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
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val dm = resources.displayMetrics
        val density = dm.density

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#BB0A0A0F"))
        }

        val panelW = (dm.widthPixels * 0.70f).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(panelW, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val density2 = density
        val leftColW = (52 * density2).toInt()

        // LEFT: accent bar + category icons
        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1117"))
            layoutParams = LinearLayout.LayoutParams(leftColW, LinearLayout.LayoutParams.MATCH_PARENT)
            setPadding(0, (52 * density2).toInt(), 0, 0)
        }

        // RIGHT: header + modules
        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111827"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        val hdr = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16*density2).toInt(), (48*density2).toInt(), (16*density2).toInt(), (12*density2).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val ttl = TextView(this).apply {
            text = currentTab; textSize = 15f
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val cls = TextView(this).apply {
            text = "✕"; textSize = 17f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#6B7280"))
            setPadding((8*density2).toInt(), 0, (8*density2).toInt(), 0)
            setOnClickListener { closePanel() }
        }
        hdr.addView(ttl); hdr.addView(cls)
        rightCol.addView(hdr)
        rightCol.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#1F2937"))
        })

        val screenH = dm.heightPixels
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            isScrollbarFadingEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10*density2).toInt(), (8*density2).toInt(), (10*density2).toInt(), (32*density2).toInt())
        }
        scroll.addView(content)
        rightCol.addView(scroll)

        val categories = listOf("Combat","Visual","Motion","World","Misc")
        val catIcons  = mapOf("Combat" to "⚔","Visual" to "👁","Motion" to "💨","World" to "🌍","Misc" to "⚙")
        val tabBtns   = mutableMapOf<String, TextView>()

        fun refreshModules(cat: String) {
            currentTab = cat; ttl.text = cat
            tabBtns.forEach { (name, tv) ->
                val on  = name == cat
                val col = tabColor(name)
                tv.setTextColor(if(on) Color.parseColor(col) else Color.parseColor("#4B5563"))
                tv.setBackgroundColor(if(on) Color.parseColor(col.replace("#","#22")) else Color.TRANSPARENT)
            }
            content.removeAllViews()
            Modules.all.filter { it.category == cat }
                .forEach { mod -> content.addView(moduleRow(mod, density2)) }
        }

        categories.forEach { cat ->
            val tv = TextView(this).apply {
                text = "${catIcons[cat]}\n${cat.take(3)}"
                textSize = 9f; gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#4B5563"))
                setPadding(0, (10*density2).toInt(), 0, (10*density2).toInt())
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (2*density2).toInt() }
                setOnClickListener { refreshModules(cat) }
            }
            tabBtns[cat] = tv
            leftCol.addView(tv)
        }

        // 3px accent line on far left
        val accentLine = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((3*density2).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#2563EB"))
        }
        val divLine = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((1*density2).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#1F2937"))
        }

        panel.addView(accentLine)
        panel.addView(leftCol)
        panel.addView(divLine)
        panel.addView(rightCol)
        root.addView(panel)

        // tap outside = close
        root.setOnClickListener { closePanel() }
        panel.setOnClickListener { }

        refreshModules(currentTab)
        panelView = root
        try { wm?.addView(root, lp) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun moduleRow(mod: Module, density: Float): LinearLayout {
        val cc = tabColor(mod.category)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12*density).toInt(),(10*density).toInt(),(12*density).toInt(),(10*density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 10f * density
                setColor(if(mod.enabled) Color.parseColor(cc.replace("#","#1A")) else Color.parseColor("#161C2A"))
                setStroke((1*density).toInt(), if(mod.enabled) Color.parseColor(cc) else Color.parseColor("#1F2937"))
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = (6*density).toInt() }
        }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val nameCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nm = TextView(this).apply { text = mod.name; textSize = 12f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD }
        val ds = TextView(this).apply { text = mod.desc; textSize = 10f; setTextColor(Color.parseColor("#6B7280")) }
        nameCol.addView(nm); nameCol.addView(ds)

        val sw = Switch(this).apply {
            isChecked = mod.enabled
            setOnCheckedChangeListener { _, v ->
                mod.enabled = v
                row.background = GradientDrawable().apply {
                    cornerRadius = 10f * density
                    setColor(if(v) Color.parseColor(cc.replace("#","#1A")) else Color.parseColor("#161C2A"))
                    setStroke((1*density).toInt(), if(v) Color.parseColor(cc) else Color.parseColor("#1F2937"))
                }
            }
        }
        top.addView(nameCol); top.addView(sw)
        row.addView(top)

        when (mod) {
            is HitboxModule   -> row.addView(sliderRow("Scale",  1f, 4f, mod.scale,           density) { mod.scale   = it })
            is ReachModule    -> row.addView(sliderRow("Reach",  3f, 8f, mod.reach,            density) { mod.reach   = it })
            is KillAuraModule -> { row.addView(sliderRow("Range", 2f, 6f, mod.range,           density) { mod.range   = it })
                                   row.addView(sliderRow("Delay", 50f,500f,mod.delayMs.toFloat(),density) { mod.delayMs = it.toInt() }) }
            is TimerModule    -> row.addView(sliderRow("Speed",  0.5f,3f, mod.speed,           density) { mod.speed   = it })
        }
        return row
    }

    private fun sliderRow(label: String, min: Float, max: Float, init: Float, density: Float, onChange: (Float)->Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (6*density).toInt() }
        }
        val lbl = TextView(this).apply {
            text = "$label: ${"%.1f".format(init)}"; textSize = 10f
            setTextColor(Color.parseColor("#60A5FA")); minWidth = (130*density).toInt()
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = (8*density).toInt() }
        }
        val sb = SeekBar(this).apply {
            this.max = 100
            progress = ((init-min)/(max-min)*100).toInt().coerceIn(0,100)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) {
                    val v = min + (max-min)*p/100f; lbl.text = "$label: ${"%.1f".format(v)}"; onChange(v)
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

    private fun tabColor(cat: String) = when(cat) {
        "Combat" -> "#F87171"; "Visual" -> "#818CF8"; "Motion" -> "#FBBF24"
        "World"  -> "#34D399"; else     -> "#22D3EE"
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "Manes Overlay", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNote() = NotificationCompat.Builder(this, CH)
        .setContentTitle("Manes active")
        .setContentText("Tap ⚡ in-game to open modules")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
}
