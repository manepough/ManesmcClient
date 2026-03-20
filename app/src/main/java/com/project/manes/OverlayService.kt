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
        val d = resources.displayMetrics.density
        val btnPx = (44 * d).toInt()
        val lp = WindowManager.LayoutParams(btnPx, btnPx, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = (200*d).toInt() }

        val btn = object : TextView(this) {
            private var ox=0f; private var oy=0f; private var lx=0f; private var ly=0f; private var moved=false
            init {
                text="⚡"; textSize=18f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape=GradientDrawable.OVAL
                    setColor(Color.parseColor("#CC1A56F0"))
                    setStroke((2*d).toInt(), Color.parseColor("#4A7FFF"))
                }
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                when(e.action) {
                    MotionEvent.ACTION_DOWN -> { ox=lp.x-e.rawX; oy=lp.y-e.rawY; lx=e.rawX; ly=e.rawY; moved=false }
                    MotionEvent.ACTION_MOVE -> {
                        if(Math.abs(e.rawX-lx)>8||Math.abs(e.rawY-ly)>8) {
                            moved=true
                            val dm=resources.displayMetrics
                            lp.x=(e.rawX+ox).toInt().coerceIn(0,dm.widthPixels-btnPx)
                            lp.y=(e.rawY+oy).toInt().coerceIn(0,dm.heightPixels-btnPx)
                            try { wm?.updateViewLayout(this,lp) } catch(_:Exception){}
                        }
                    }
                    MotionEvent.ACTION_UP -> if(!moved) toggle()
                }
                return true
            }
        }
        btnView=btn
        try { wm?.addView(btn,lp) } catch(e:Exception){ e.printStackTrace() }
    }

    private fun toggle() { if(panelOpen) closePanel() else openPanel() }

    private fun openPanel() {
        panelOpen=true
        val type = if(Build.VERSION.SDK_INT>=26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val dm=resources.displayMetrics; val d=dm.density
        val panelW=(dm.widthPixels*0.72f).toInt()
        val panelH=(dm.heightPixels*0.82f).toInt()

        val lp=WindowManager.LayoutParams(panelW,panelH,type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT)
            .apply { gravity=Gravity.CENTER }

        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            background=GradientDrawable().apply {
                setColor(Color.parseColor("#F0111827")); cornerRadius=16*d
                setStroke((1*d).toInt(),Color.parseColor("#1F2937"))
            }
        }

        // Header
        val hdr=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding((14*d).toInt(),(10*d).toInt(),(10*d).toInt(),(10*d).toInt())
            layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT)
            background=GradientDrawable().apply {
                setColor(Color.parseColor("#1A56F0"))
                cornerRadii=floatArrayOf(16*d,16*d,16*d,16*d,0f,0f,0f,0f)
            }
        }
        val logo=TextView(this).apply {
            text="M"; textSize=13f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
            background=GradientDrawable().apply { shape=GradientDrawable.OVAL; setColor(Color.parseColor("#FFFFFF33")) }
            val s=(28*d).toInt()
            layoutParams=LinearLayout.LayoutParams(s,s).apply { marginEnd=(8*d).toInt() }
        }
        val appName=TextView(this).apply {
            text="MANES"; textSize=14f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f)
        }
        val catTitle=TextView(this).apply {
            text=currentTab; textSize=16f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity=Gravity.CENTER_VERTICAL
            layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f)
        }
        val closeBtn=TextView(this).apply {
            text="✕"; textSize=13f; gravity=Gravity.CENTER; setTextColor(Color.WHITE)
            background=GradientDrawable().apply { shape=GradientDrawable.OVAL; setColor(Color.parseColor("#EF4444")) }
            val s=(26*d).toInt(); layoutParams=LinearLayout.LayoutParams(s,s)
            setOnClickListener { closePanel() }
        }
        hdr.addView(logo); hdr.addView(appName); hdr.addView(catTitle); hdr.addView(closeBtn)
        root.addView(hdr)

        // Body
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f)
        }

        // Sidebar — NO Chat tab
        val sidebar=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D1117"))
            layoutParams=LinearLayout.LayoutParams((110*d).toInt(),LinearLayout.LayoutParams.MATCH_PARENT)
            setPadding(0,(8*d).toInt(),0,(8*d).toInt())
        }

        // Right col
        val rightCol=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111827"))
            layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT,1f)
        }
        val scroll=ScrollView(this).apply {
            layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f)
            isScrollbarFadingEnabled=false
        }
        val content=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding((10*d).toInt(),(8*d).toInt(),(10*d).toInt(),(20*d).toInt())
        }
        scroll.addView(content); rightCol.addView(scroll)

        // Categories — NO Chat
        val categories=listOf(
            Triple("Combat",  "✕",  "#F87171"),
            Triple("Movement","🏃", "#60A5FA"),
            Triple("World",   "🌍", "#34D399"),
            Triple("Render",  "👁", "#818CF8"),
            Triple("Misc",    "⚙", "#22D3EE"),
            Triple("Config",  "🔧", "#A78BFA")
        )
        val tabBtns=mutableMapOf<String,LinearLayout>()

        fun refreshModules(cat:String) {
            currentTab=cat; catTitle.text=cat
            tabBtns.forEach { (name,view) ->
                val on=name==cat
                val col=categories.find{it.first==name}?.third?:"#6B7280"
                view.setBackgroundColor(if(on) Color.parseColor(col.replace("#","#22")) else Color.TRANSPARENT)
                (view.getChildAt(1) as? TextView)?.setTextColor(
                    if(on) Color.parseColor(col) else Color.parseColor("#9CA3AF")
                )
            }
            content.removeAllViews()
            val modCat=when(cat){"Movement"->"Motion";"Render"->"Visual";else->cat}
            Modules.all.filter{it.category==modCat||it.category==cat}
                .forEach { mod -> content.addView(moduleRow(mod,d,content)) }
        }

        categories.forEach { (name,icon,color) ->
            val row=LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                setPadding((12*d).toInt(),(10*d).toInt(),(12*d).toInt(),(10*d).toInt())
                layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT)
                isClickable=true
            }
            row.addView(TextView(this).apply {
                text=icon; textSize=14f; gravity=Gravity.CENTER
                layoutParams=LinearLayout.LayoutParams((24*d).toInt(),LinearLayout.LayoutParams.WRAP_CONTENT).apply{marginEnd=(8*d).toInt()}
            })
            row.addView(TextView(this).apply {
                text=name; textSize=12f; setTextColor(Color.parseColor("#9CA3AF"))
            })
            row.setOnClickListener { refreshModules(name) }
            tabBtns[name]=row; sidebar.addView(row)
        }

        body.addView(sidebar)
        body.addView(View(this).apply {
            layoutParams=LinearLayout.LayoutParams((1*d).toInt(),LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#1F2937"))
        })
        body.addView(rightCol)
        root.addView(body)
        refreshModules(currentTab)
        panelView=root
        try { wm?.addView(root,lp) } catch(e:Exception){ e.printStackTrace() }
    }

    private fun moduleRow(mod: Module, d: Float, parent: LinearLayout): LinearLayout {
        val cc=when(mod.category){
            "Combat"->"#F87171";"Visual"->"#818CF8";"Motion"->"#60A5FA"
            "World"->"#34D399";"Misc"->"#22D3EE";else->"#9CA3AF"
        }
        val hasSettings=mod is HitboxModule||mod is ReachModule||mod is KillAuraModule||mod is TimerModule

        val wrapper=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply{bottomMargin=(6*d).toInt()}
        }

        fun updateBg(enabled:Boolean)=GradientDrawable().apply {
            cornerRadius=10f*d
            setColor(if(enabled) Color.parseColor(cc.replace("#","#1A")) else Color.parseColor("#1A1E2A"))
            setStroke((1*d).toInt(),if(enabled) Color.parseColor(cc) else Color.parseColor("#1F2937"))
        }

        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding((14*d).toInt(),(12*d).toInt(),(12*d).toInt(),(12*d).toInt())
            background=updateBg(mod.enabled)
            layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable=true
        }

        val nameCol=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f)
        }
        nameCol.addView(TextView(this).apply { text=mod.name; textSize=13f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD })
        nameCol.addView(TextView(this).apply { text=mod.desc; textSize=10f; setTextColor(Color.parseColor("#6B7280")) })

        // Slider area — hidden by default, shown when settings tapped
        val sliderArea=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding((14*d).toInt(),(4*d).toInt(),(14*d).toInt(),(10*d).toInt())
            visibility=View.GONE
            layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        when(mod) {
            is HitboxModule -> sliderArea.addView(sliderRow("Scale",1f,4f,mod.scale,d,cc){mod.scale=it})
            is ReachModule -> sliderArea.addView(sliderRow("Reach",3f,8f,mod.reach,d,cc){mod.reach=it})
            is KillAuraModule -> {
                sliderArea.addView(sliderRow("Range",2f,6f,mod.range,d,cc){mod.range=it})
                sliderArea.addView(sliderRow("Delay ms",50f,500f,mod.delayMs.toFloat(),d,cc){mod.delayMs=it.toInt()})
            }
            is TimerModule -> sliderArea.addView(sliderRow("Speed",0.5f,3f,mod.speed,d,cc){mod.speed=it})
        }

        row.addView(nameCol)

        // Only show gear if module has settings
        if(hasSettings) {
            val gear=TextView(this).apply {
                text="⚙"; textSize=14f; gravity=Gravity.CENTER
                setTextColor(if(mod.enabled) Color.parseColor(cc) else Color.parseColor("#4B5563"))
                setPadding((8*d).toInt(),0,0,0)
            }
            gear.setOnClickListener {
                sliderArea.visibility=if(sliderArea.visibility==View.GONE) View.VISIBLE else View.GONE
            }
            row.addView(gear)
        }

        row.setOnClickListener {
            mod.enabled=!mod.enabled
            row.background=updateBg(mod.enabled)
        }

        wrapper.addView(row)
        if(hasSettings) wrapper.addView(sliderArea)
        return wrapper
    }

    private fun sliderRow(label:String,min:Float,max:Float,init:Float,d:Float,color:String,onChange:(Float)->Unit):LinearLayout {
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply{topMargin=(6*d).toInt()}
        }
        val lbl=TextView(this).apply {
            text="$label: ${"%.1f".format(init)}"; textSize=11f
            setTextColor(Color.parseColor(color)); minWidth=(130*d).toInt()
            layoutParams=LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply{marginEnd=(8*d).toInt()}
        }
        val sb=SeekBar(this).apply {
            this.max=100; progress=((init-min)/(max-min)*100).toInt().coerceIn(0,100)
            layoutParams=LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onProgressChanged(sb:SeekBar,p:Int,u:Boolean){
                    val v=min+(max-min)*p/100f; lbl.text="$label: ${"%.1f".format(v)}"; onChange(v)
                }
                override fun onStartTrackingTouch(sb:SeekBar){}
                override fun onStopTrackingTouch(sb:SeekBar){}
            })
        }
        row.addView(lbl); row.addView(sb)
        return row
    }

    private fun closePanel() {
        panelOpen=false
        panelView?.let { try { wm?.removeView(it) } catch(_:Exception){} }
        panelView=null
    }
    override fun onDestroy() { super.onDestroy(); closePanel(); btnView?.let{try{wm?.removeView(it)}catch(_:Exception){}} }
    override fun onBind(i:Intent?):IBinder?=null

    private fun createChannel() {
        if(Build.VERSION.SDK_INT>=26){
            val ch=NotificationChannel(CH,"Manes Overlay",NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }
    private fun buildNote()=NotificationCompat.Builder(this,CH)
        .setContentTitle("Manes active").setContentText("Tap ⚡ to open modules")
        .setSmallIcon(android.R.drawable.ic_dialog_info).setPriority(NotificationCompat.PRIORITY_LOW).build()
}
