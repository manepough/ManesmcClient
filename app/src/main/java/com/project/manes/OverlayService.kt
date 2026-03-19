package com.project.manes

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.widget.*
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var panelView: View? = null
    private var panelVisible = false

    companion object {
        const val CHANNEL_ID = "manes_overlay"
        fun hasPermission(ctx: Context) = Settings.canDrawOverlays(ctx)
        fun start(ctx: Context) { ctx.startForegroundService(Intent(ctx, OverlayService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, OverlayService::class.java)) }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showYinYangButton()
    }

    private fun showYinYangButton() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 16; y = 200
        }

        val btn = object : android.widget.TextView(this) {
            private var dx = 0f; private var dy = 0f
            private var lx = 0f; private var ly = 0f
            private var moved = false

            init {
                text = "☯"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(4, 4, 4, 4)
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#CC7C6EF7"))
                    setStroke(2, Color.parseColor("#A78BFA"))
                }
                background = bg
                layoutParams = FrameLayout.LayoutParams(120, 120)
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { dx = params.x - e.rawX; dy = params.y - e.rawY; lx = e.rawX; ly = e.rawY; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val nx = (e.rawX + dx).toInt(); val ny = (e.rawY + dy).toInt()
                        if (Math.abs(e.rawX - lx) > 8 || Math.abs(e.rawY - ly) > 8) { moved = true; params.x = nx; params.y = ny; windowManager?.updateViewLayout(this, params) }
                    }
                    MotionEvent.ACTION_UP -> { if (!moved) togglePanel() }
                }
                return true
            }
        }

        overlayView = btn
        try { windowManager?.addView(btn, params) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun togglePanel() {
        if (panelVisible) hidePanel() else showPanel()
    }

    private fun showPanel() {
        panelVisible = true
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            700, WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            val bg = GradientDrawable().apply { setColor(Color.parseColor("#F018181C")); cornerRadius = 40f; setStroke(2, Color.parseColor("#7C6EF7")) }
            background = bg
        }

        // Header row
        val header = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.HORIZONTAL }
        val title = android.widget.TextView(this).apply { text = "☯  Modules"; textSize = 18f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val close = android.widget.TextView(this).apply { text = "✕"; textSize = 18f; setTextColor(Color.parseColor("#8B8A9B")); setPadding(16, 0, 0, 0)
            setOnClickListener { hidePanel() } }
        header.addView(title); header.addView(close)
        layout.addView(header)

        // Divider
        val div = android.view.View(this).apply { layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 2).apply { topMargin=16; bottomMargin=16 }; setBackgroundColor(Color.parseColor("#222228")) }
        layout.addView(div)

        // Scroll
        val scroll = android.widget.ScrollView(this).apply { layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 900) }
        val inner = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }

        val cats = Modules.all.groupBy { it.category }
        val catColors = mapOf("Combat" to "#F87171","Visual" to "#A78BFA","World" to "#4ADE80","Motion" to "#FBBF24","Misc" to "#22D3EE")

        cats.forEach { (cat, mods) ->
            val catLabel = android.widget.TextView(this).apply {
                text = cat.uppercase(); textSize = 10f
                setTextColor(Color.parseColor(catColors[cat] ?: "#8B8A9B"))
                setPadding(0, 24, 0, 8)
                letterSpacing = 0.1f
            }
            inner.addView(catLabel)
            mods.forEach { mod ->
                val row = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setPadding(20, 16, 20, 16)
                    val bg2 = GradientDrawable().apply {
                        val c = Color.parseColor(catColors[mod.category] ?: "#8B8A9B")
                        setColor(if (mod.enabled) Color.argb(30, Color.red(c), Color.green(c), Color.blue(c)) else Color.parseColor("#18181C"))
                        cornerRadius = 24f
                        setStroke(1, if (mod.enabled) c else Color.parseColor("#222228"))
                    }
                    background = bg2
                    layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 }
                }
                val col = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL; layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
                val nm = android.widget.TextView(this).apply { text = mod.name; textSize = 14f; setTextColor(Color.WHITE) }
                val ds = android.widget.TextView(this).apply { text = mod.desc; textSize = 11f; setTextColor(Color.parseColor("#8B8A9B")) }
                col.addView(nm); col.addView(ds)
                val sw = android.widget.Switch(this).apply {
                    isChecked = mod.enabled
                    setOnCheckedChangeListener { _, v ->
                        mod.enabled = v
                        // Refresh background
                        val c = Color.parseColor(catColors[mod.category] ?: "#8B8A9B")
                        val bg3 = GradientDrawable().apply {
                            setColor(if (v) Color.argb(30, Color.red(c), Color.green(c), Color.blue(c)) else Color.parseColor("#18181C"))
                            cornerRadius = 24f; setStroke(1, if (v) c else Color.parseColor("#222228"))
                        }
                        row.background = bg3
                    }
                }
                row.addView(col); row.addView(sw)
                inner.addView(row)
            }
        }

        scroll.addView(inner); layout.addView(scroll)
        panelView = layout
        try { windowManager?.addView(layout, params) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun hidePanel() {
        panelVisible = false
        panelView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        panelView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        hidePanel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Manes Overlay", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Manes is active")
            .setContentText("Tap ☯ button in-game to toggle modules")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
