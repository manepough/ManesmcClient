package com.project.manes

import android.content.Context
import android.graphics.Canvas as ACanvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import org.json.JSONObject
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// VOLUME BUTTON HOOK
// Drop this ONE method into your Minecraft Activity (or wherever you override
// dispatchKeyEvent).  It intercepts Volume Down and opens the panel instead of
// changing the volume.  Everything else passes through normally.
//
//   override fun dispatchKeyEvent(event: KeyEvent): Boolean {
//       if (VolumeHook.handle(event, this)) return true
//       return super.dispatchKeyEvent(event)
//   }
// ─────────────────────────────────────────────────────────────────────────────

object VolumeHook {
    // Change to KEYCODE_VOLUME_UP if you prefer the up button
    private const val TRIGGER = KeyEvent.KEYCODE_VOLUME_DOWN

    /**
     * Call from dispatchKeyEvent in your Activity.
     * Returns true if the event was consumed (panel opened), false otherwise.
     */
    fun handle(event: KeyEvent, activity: androidx.activity.ComponentActivity): Boolean {
        if (event.keyCode == TRIGGER && event.action == KeyEvent.ACTION_DOWN) {
            ManesPanel.show(activity)
            return true   // consume — volume does NOT change
        }
        return false
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODULE BASE
// ─────────────────────────────────────────────────────────────────────────────

abstract class Module(val name: String, val desc: String, val category: String) {
    var enabled = false
    open fun onEnable()  {}
    open fun onDisable() {}
    open fun onToggle()  { if (enabled) onEnable() else onDisable() }
    open fun serializeSettings(obj: JSONObject)   {}
    open fun deserializeSettings(obj: JSONObject) {}
}

// ─────────────────────────────────────────────────────────────────────────────
// MODULES
// ─────────────────────────────────────────────────────────────────────────────

class XRayModule : Module("XRay", "See ores through walls", "World") {
    override fun onEnable()  { ModuleHooks.setXRay(true) }
    override fun onDisable() { ModuleHooks.setXRay(false) }
}

class FullbrightModule : Module("Fullbright", "Remove all fog and darkness", "Visual") {
    override fun onEnable()  { ModuleHooks.setFullbright(true) }
    override fun onDisable() { ModuleHooks.setFullbright(false) }
}

class ESPModule : Module("ESP", "Glow entities through walls", "Visual") {
    override fun onEnable()  { ModuleHooks.setESP(true) }
    override fun onDisable() { ModuleHooks.setESP(false) }
}

class HitboxModule : Module("Hitbox", "Expand enemy hitboxes", "Combat") {
    var scale: Float = 1.8f
    override fun onEnable()  { ModuleHooks.setHitbox(scale) }
    override fun onDisable() { ModuleHooks.setHitbox(1.0f) }
    override fun serializeSettings(obj: JSONObject)   { obj.put("scale", scale) }
    override fun deserializeSettings(obj: JSONObject) { scale = obj.optDouble("scale", 1.8).toFloat() }
}

class AntiKnockbackModule : Module("AntiKnockback", "Cancel knockback", "Combat") {
    var strength: Float = 0f
    override fun onEnable()  { ModuleHooks.setAntiKB(strength) }
    override fun onDisable() { ModuleHooks.setAntiKB(1f) }
    override fun serializeSettings(obj: JSONObject)   { obj.put("strength", strength) }
    override fun deserializeSettings(obj: JSONObject) { strength = obj.optDouble("strength", 0.0).toFloat() }
}

class NoFallModule : Module("NoFall", "Cancel fall damage", "Combat") {
    override fun onEnable()  { ModuleHooks.setNoFall(true) }
    override fun onDisable() { ModuleHooks.setNoFall(false) }
}

class KillAuraModule : Module("KillAura", "Auto attack nearby entities", "Combat") {
    var range:   Float = 3.5f
    var delayMs: Int   = 150
    override fun onEnable()  { ModuleHooks.setKillAura(true,  range, delayMs) }
    override fun onDisable() { ModuleHooks.setKillAura(false, range, delayMs) }
    override fun serializeSettings(obj: JSONObject)   { obj.put("range", range); obj.put("delay", delayMs) }
    override fun deserializeSettings(obj: JSONObject) { range = obj.optDouble("range", 3.5).toFloat(); delayMs = obj.optInt("delay", 150) }
}

class ReachModule : Module("Reach", "Extended attack reach", "Combat") {
    var reach: Float = 4.5f
    override fun onEnable()  { ModuleHooks.setReach(reach) }
    override fun onDisable() { ModuleHooks.setReach(3.0f) }
    override fun serializeSettings(obj: JSONObject)   { obj.put("reach", reach) }
    override fun deserializeSettings(obj: JSONObject) { reach = obj.optDouble("reach", 4.5).toFloat() }
}

class AutoSprintModule : Module("AutoSprint", "Always sprint", "Motion") {
    override fun onEnable()  { ModuleHooks.setAutoSprint(true) }
    override fun onDisable() { ModuleHooks.setAutoSprint(false) }
}

class NoSlowModule : Module("NoSlow", "No slowdown while using items", "Motion") {
    override fun onEnable()  { ModuleHooks.setNoSlow(true) }
    override fun onDisable() { ModuleHooks.setNoSlow(false) }
}

class AntiAFKModule : Module("AntiAFK", "Prevent AFK kick", "Misc") {
    override fun onEnable()  { ModuleHooks.setAntiAFK(true) }
    override fun onDisable() { ModuleHooks.setAntiAFK(false) }
}

class AntiVoidModule : Module("AntiVoid", "Stop falling into void", "World") {
    override fun onEnable()  { ModuleHooks.setAntiVoid(true) }
    override fun onDisable() { ModuleHooks.setAntiVoid(false) }
}

class FastPlaceModule : Module("FastPlace", "Remove block placement cooldown", "World") {
    override fun onEnable()  { ModuleHooks.setFastPlace(true) }
    override fun onDisable() { ModuleHooks.setFastPlace(false) }
}

class NameTagsModule : Module("NameTags", "Always show player name tags", "Visual") {
    override fun onEnable()  { ModuleHooks.setNameTags(true) }
    override fun onDisable() { ModuleHooks.setNameTags(false) }
}

class TracersModule : Module("Tracers", "Draw lines to entities", "Visual") {
    override fun onEnable()  { ModuleHooks.setTracers(true) }
    override fun onDisable() { ModuleHooks.setTracers(false) }
}

class TimerModule : Module("Timer", "Speed up game tick", "Misc") {
    var speed: Float = 1.0f
    override fun onEnable()  { ModuleHooks.setTimer(speed) }
    override fun onDisable() { ModuleHooks.setTimer(1.0f) }
    override fun serializeSettings(obj: JSONObject)   { obj.put("speed", speed) }
    override fun deserializeSettings(obj: JSONObject) { speed = obj.optDouble("speed", 1.0).toFloat() }
}

class AutoClickerModule : Module("AutoClicker", "Auto click repeatedly", "Combat") {
    var cps: Int = 10
    override fun onEnable()  { ModuleHooks.setAutoClicker(true, cps) }
    override fun onDisable() { ModuleHooks.setAutoClicker(false, cps) }
    override fun serializeSettings(obj: JSONObject)   { obj.put("cps", cps) }
    override fun deserializeSettings(obj: JSONObject) { cps = obj.optInt("cps", 10) }
}

class ChestStealerModule : Module("ChestStealer", "Auto loot chests", "Misc") {
    override fun onEnable()  { ModuleHooks.setChestStealer(true) }
    override fun onDisable() { ModuleHooks.setChestStealer(false) }
}

class FreeCamModule : Module("FreeCam", "Detach camera from player", "Visual") {
    override fun onEnable()  { ModuleHooks.setFreeCam(true) }
    override fun onDisable() { ModuleHooks.setFreeCam(false) }
}

// ─────────────────────────────────────────────────────────────────────────────
// CROSSHAIR MODULE
// ─────────────────────────────────────────────────────────────────────────────

class CrosshairModule : Module("Crosshair", "Custom crosshair overlay", "Visual") {
    val styles       = listOf("Cross", "Dot", "Circle", "Plus", "Square")
    val colorOptions = listOf(
        "White"  to AColor.WHITE,
        "Red"    to AColor.RED,
        "Green"  to AColor.GREEN,
        "Cyan"   to AColor.CYAN,
        "Yellow" to AColor.YELLOW
    )
    var styleIdx:    Int   = 0
    var colorIdx:    Int   = 0
    var overlaySize: Float = 12f
    private var appCtx: Context? = null

    fun init(ctx: Context) { appCtx = ctx.applicationContext }

    fun apply() {
        val ctx = appCtx ?: return
        CrosshairOverlay.style  = styleIdx
        CrosshairOverlay.aColor = colorOptions[colorIdx].second
        CrosshairOverlay.size   = overlaySize
        if (enabled) CrosshairOverlay.show(ctx) else CrosshairOverlay.hide()
        CrosshairOverlay.update()
    }

    override fun onEnable()  { apply() }
    override fun onDisable() { CrosshairOverlay.hide() }
    override fun serializeSettings(obj: JSONObject) {
        obj.put("styleIdx", styleIdx); obj.put("colorIdx", colorIdx); obj.put("size", overlaySize)
    }
    override fun deserializeSettings(obj: JSONObject) {
        styleIdx    = obj.optInt("styleIdx", 0)
        colorIdx    = obj.optInt("colorIdx", 0)
        overlaySize = obj.optDouble("size", 12.0).toFloat()
    }
}

class CrosshairView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: ACanvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val d  = resources.displayMetrics.density
        val s  = CrosshairOverlay.size * d
        paint.color       = CrosshairOverlay.aColor
        paint.strokeWidth = 2.5f * d
        paint.style       = Paint.Style.STROKE
        when (CrosshairOverlay.style) {
            0 -> { canvas.drawLine(cx-s,cy,cx+s,cy,paint); canvas.drawLine(cx,cy-s,cx,cy+s,paint) }
            1 -> { paint.style = Paint.Style.FILL; canvas.drawCircle(cx,cy,s*0.3f,paint) }
            2 -> { canvas.drawCircle(cx,cy,s*0.7f,paint) }
            3 -> { val g=s*0.3f; canvas.drawLine(cx-s,cy,cx-g,cy,paint); canvas.drawLine(cx+g,cy,cx+s,cy,paint); canvas.drawLine(cx,cy-s,cx,cy-g,paint); canvas.drawLine(cx,cy+g,cx,cy+s,paint) }
            4 -> { canvas.drawRect(cx-s*0.6f,cy-s*0.6f,cx+s*0.6f,cy+s*0.6f,paint) }
        }
    }
}

object CrosshairOverlay {
    private var wm: WindowManager? = null
    private var view: CrosshairView? = null
    var style:  Int   = 0
    var aColor: Int   = AColor.WHITE
    var size:   Float = 12f

    fun show(ctx: Context) {
        if (view != null) return
        if (!Settings.canDrawOverlays(ctx)) return
        val w = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = w
        val v = CrosshairView(ctx); view = v
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        w.addView(v, p)
    }

    fun hide() { view?.let { runCatching { wm?.removeView(it) } }; view = null; wm = null }
    fun update() { view?.postInvalidate() }
}

// ─────────────────────────────────────────────────────────────────────────────
// KEYBIND MODULE
// ─────────────────────────────────────────────────────────────────────────────

enum class KeyAction(val label: String) {
    SLOT_1("1"), SLOT_2("2"), SLOT_3("3"), SLOT_4("4"), SLOT_5("5"),
    SLOT_6("6"), SLOT_7("7"), SLOT_8("8"), SLOT_9("9"),
    LEFT_CLICK("Left Click"), RIGHT_CLICK("Right Click"), MIDDLE_CLICK("Middle Click"),
    JUMP("Space"), SNEAK("Shift"), SPRINT("Ctrl"),
    MOVE_FORWARD("W"), MOVE_BACK("S"), MOVE_LEFT("A"), MOVE_RIGHT("D"),
    DROP_ITEM("Q"), OPEN_INV("E"), SWAP_OFFHAND("F"),
    ATTACK("Attack"), USE_ITEM("Use Item")
}

data class Keybind(
    val id:      String,
    var label:   String,
    val actions: MutableList<KeyAction> = mutableListOf(),
    var x: Float = 100f,
    var y: Float = 300f
)

object KeybindManager {
    val keybinds = mutableListOf<Keybind>()

    fun execute(kb: Keybind) {
        kb.actions.forEach { action ->
            when (action) {
                KeyAction.SLOT_1       -> ModuleHooks.selectSlot(0)
                KeyAction.SLOT_2       -> ModuleHooks.selectSlot(1)
                KeyAction.SLOT_3       -> ModuleHooks.selectSlot(2)
                KeyAction.SLOT_4       -> ModuleHooks.selectSlot(3)
                KeyAction.SLOT_5       -> ModuleHooks.selectSlot(4)
                KeyAction.SLOT_6       -> ModuleHooks.selectSlot(5)
                KeyAction.SLOT_7       -> ModuleHooks.selectSlot(6)
                KeyAction.SLOT_8       -> ModuleHooks.selectSlot(7)
                KeyAction.SLOT_9       -> ModuleHooks.selectSlot(8)
                KeyAction.LEFT_CLICK,
                KeyAction.ATTACK       -> ModuleHooks.attack()
                KeyAction.RIGHT_CLICK,
                KeyAction.USE_ITEM     -> ModuleHooks.useItem()
                KeyAction.MIDDLE_CLICK -> ModuleHooks.pickBlock()
                KeyAction.JUMP         -> ModuleHooks.jump()
                KeyAction.SNEAK        -> ModuleHooks.sneak()
                KeyAction.SPRINT       -> ModuleHooks.sprint()
                KeyAction.MOVE_FORWARD -> ModuleHooks.move(0)
                KeyAction.MOVE_BACK    -> ModuleHooks.move(1)
                KeyAction.MOVE_LEFT    -> ModuleHooks.move(2)
                KeyAction.MOVE_RIGHT   -> ModuleHooks.move(3)
                KeyAction.DROP_ITEM    -> ModuleHooks.dropItem()
                KeyAction.OPEN_INV     -> ModuleHooks.openInventory()
                KeyAction.SWAP_OFFHAND -> ModuleHooks.swapOffhand()
            }
        }
    }
}

class KeybindModule : Module("Keybinds", "Floating buttons that trigger game actions", "Misc") {
    private var appCtx: Context? = null
    fun init(ctx: Context) { appCtx = ctx.applicationContext }
    fun apply()   { val ctx = appCtx ?: return; if (enabled) KeybindOverlay.show(ctx) else KeybindOverlay.hide() }
    fun refresh() { val ctx = appCtx ?: return; if (enabled) KeybindOverlay.refresh(ctx) }
    override fun onEnable()  { apply() }
    override fun onDisable() { KeybindOverlay.hide() }
}

object KeybindOverlay {
    private var wm: WindowManager? = null
    private val views = mutableListOf<View>()
    var visible = false

    fun show(ctx: Context) {
        if (visible) return
        if (!Settings.canDrawOverlays(ctx)) return
        val w = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = w; visible = true
        KeybindManager.keybinds.forEach { kb -> addButton(ctx, w, kb) }
    }

    fun hide() {
        views.forEach { runCatching { wm?.removeView(it) } }
        views.clear(); visible = false; wm = null
    }

    fun refresh(ctx: Context) { hide(); show(ctx) }

    private fun addButton(ctx: Context, w: WindowManager, kb: Keybind) {
        val d = ctx.resources.displayMetrics.density
        val tv = TextView(ctx).apply {
            text = kb.label
            setTextColor(AColor.WHITE)
            textSize = 11f
            setPadding((8*d).toInt(), (6*d).toInt(), (8*d).toInt(), (6*d).toInt())
            setBackgroundColor(AColor.argb(210, 13, 13, 13))
            isSingleLine = true
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = kb.x.toInt(); y = kb.y.toInt() }

        var downX = 0f; var downY = 0f; var moved = false
        var lastX = kb.x.toInt(); var lastY = kb.y.toInt()

        tv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > 5 || kotlin.math.abs(dy) > 5) moved = true
                    params.x = (lastX + dx).toInt(); params.y = (lastY + dy).toInt()
                    runCatching { w.updateViewLayout(tv, params) }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) { lastX = params.x; lastY = params.y; kb.x = params.x.toFloat(); kb.y = params.y.toFloat() }
                    else KeybindManager.execute(kb)
                    true
                }
                else -> false
            }
        }
        w.addView(tv, params); views.add(tv)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODULE HOOKS  — fill in each TODO with your mod's actual function call
// ─────────────────────────────────────────────────────────────────────────────

object ModuleHooks {
    fun setXRay(on: Boolean)                           { /* TODO */ }
    fun setFullbright(on: Boolean)                     { /* TODO */ }
    fun setESP(on: Boolean)                            { /* TODO */ }
    fun setHitbox(scale: Float)                        { /* TODO */ }
    fun setAntiKB(strength: Float)                     { /* TODO */ }
    fun setNoFall(on: Boolean)                         { /* TODO */ }
    fun setKillAura(on: Boolean, range: Float, delay: Int) { /* TODO */ }
    fun setReach(reach: Float)                         { /* TODO */ }
    fun setAutoSprint(on: Boolean)                     { /* TODO */ }
    fun setNoSlow(on: Boolean)                         { /* TODO */ }
    fun setAntiAFK(on: Boolean)                        { /* TODO */ }
    fun setAntiVoid(on: Boolean)                       { /* TODO */ }
    fun setFastPlace(on: Boolean)                      { /* TODO */ }
    fun setNameTags(on: Boolean)                       { /* TODO */ }
    fun setTracers(on: Boolean)                        { /* TODO */ }
    fun setTimer(speed: Float)                         { /* TODO */ }
    fun setAutoClicker(on: Boolean, cps: Int)          { /* TODO */ }
    fun setChestStealer(on: Boolean)                   { /* TODO */ }
    fun setFreeCam(on: Boolean)                        { /* TODO */ }
    fun selectSlot(slot: Int)                          { /* TODO */ }
    fun attack()                                       { /* TODO */ }
    fun useItem()                                      { /* TODO */ }
    fun pickBlock()                                    { /* TODO */ }
    fun jump()                                         { /* TODO */ }
    fun sneak()                                        { /* TODO */ }
    fun sprint()                                       { /* TODO */ }
    fun move(dir: Int)                                 { /* TODO: 0=fwd 1=back 2=left 3=right */ }
    fun dropItem()                                     { /* TODO */ }
    fun openInventory()                                { /* TODO */ }
    fun swapOffhand()                                  { /* TODO */ }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODULE REGISTRY
// ─────────────────────────────────────────────────────────────────────────────

object ModuleRegistry {
    val crosshair = CrosshairModule()
    val keybind   = KeybindModule()

    val all: List<Module> = listOf(
        crosshair, keybind,
        XRayModule(), FullbrightModule(), ESPModule(),
        HitboxModule(), AntiKnockbackModule(), NoFallModule(),
        KillAuraModule(), ReachModule(), AutoClickerModule(),
        AutoSprintModule(), NoSlowModule(), AntiAFKModule(),
        FastPlaceModule(), ChestStealerModule(), AntiVoidModule(),
        NameTagsModule(), TracersModule(), TimerModule(), FreeCamModule()
    )

    fun init(ctx: Context) {
        crosshair.init(ctx)
        keybind.init(ctx)
    }

    fun saveAll(ctx: Context) {
        val obj = JSONObject()
        all.forEach { mod ->
            val m = JSONObject()
            m.put("enabled", mod.enabled)
            mod.serializeSettings(m)
            obj.put(mod.name, m)
        }
        ctx.getSharedPreferences("manes", 0).edit().putString("modules", obj.toString()).apply()
    }

    fun loadAll(ctx: Context) {
        runCatching {
            val raw = ctx.getSharedPreferences("manes", 0).getString("modules", "{}") ?: return
            val obj = JSONObject(raw)
            all.forEach { mod ->
                if (obj.has(mod.name)) {
                    val m = obj.getJSONObject(mod.name)
                    mod.enabled = m.optBoolean("enabled", false)
                    mod.deserializeSettings(m)
                    if (mod.enabled) mod.onEnable()
                }
            }
        }
    }

    fun saveKeybinds(ctx: Context) {
        val arr = org.json.JSONArray()
        KeybindManager.keybinds.forEach { kb ->
            arr.put(JSONObject().apply {
                put("id", kb.id); put("label", kb.label)
                put("actions", org.json.JSONArray().also { a -> kb.actions.forEach { a.put(it.name) } })
                put("x", kb.x); put("y", kb.y)
            })
        }
        ctx.getSharedPreferences("manes", 0).edit().putString("keybinds", arr.toString()).apply()
    }

    fun loadKeybinds(ctx: Context) {
        runCatching {
            val raw = ctx.getSharedPreferences("manes", 0).getString("keybinds", "[]") ?: return
            val arr = org.json.JSONArray(raw)
            KeybindManager.keybinds.clear()
            for (i in 0 until arr.length()) {
                val o    = arr.getJSONObject(i)
                val acts = org.json.JSONArray(o.optString("actions", "[]"))
                KeybindManager.keybinds.add(Keybind(
                    o.getString("id"), o.getString("label"),
                    (0 until acts.length()).mapNotNull { j ->
                        runCatching { KeyAction.valueOf(acts.getString(j)) }.getOrNull()
                    }.toMutableList(),
                    o.optDouble("x", 100.0).toFloat(),
                    o.optDouble("y", 300.0).toFloat()
                ))
            }
        }
    }
}
