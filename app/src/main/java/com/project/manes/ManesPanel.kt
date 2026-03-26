package com.project.manes

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// COLOURS
// ─────────────────────────────────────────────────────────────────────────────

private val MBg     = Color(0xFF000000)
private val MPanel  = Color(0xFF0D0D0D)
private val MBorder = Color(0xFF1F1F1F)
private val MHeader = Color(0xFF111111)
private val MSep    = Color(0xFF1A1A1A)
private val MTxtP   = Color(0xFFFFFFFF)
private val MTxtM   = Color(0xFF888888)
private val MTxtD   = Color(0xFF444444)
private val MRed    = Color(0xFFFF4040)
private val MAccent get() = PanelTheme.accent
fun Color.a(v: Float) = Color(red, green, blue, v)

// ─────────────────────────────────────────────────────────────────────────────
// THEME
// ─────────────────────────────────────────────────────────────────────────────

object PanelTheme {
    val themes = listOf(
        "Blue"   to Color(0xFF448AFF),
        "Cyan"   to Color(0xFF00BCD4),
        "Purple" to Color(0xFFAA00FF),
        "Green"  to Color(0xFF00E676),
        "Red"    to Color(0xFFFF4040),
        "Orange" to Color(0xFFFF8C00)
    )
    var currentIndex by mutableStateOf(0)
    val accent get() = themes[currentIndex].second
}

// ─────────────────────────────────────────────────────────────────────────────
// ENTRY POINT
// ─────────────────────────────────────────────────────────────────────────────

object ManesPanel {

    private var ctx: Context? = null

    /**
     * Call ONCE from your mod's onCreate (or equivalent startup hook):
     *
     *   ManesPanel.init(context)
     */
    fun init(context: Context) {
        ctx = context.applicationContext
        ModuleRegistry.init(context)
        ModuleRegistry.loadAll(context)
        ModuleRegistry.loadKeybinds(context)
    }

    /**
     * Call from dispatchKeyEvent to handle volume button.
     * Add this to your Minecraft Activity:
     *
     *   override fun dispatchKeyEvent(event: KeyEvent): Boolean {
     *       if (VolumeHook.handle(event, this)) return true
     *       return super.dispatchKeyEvent(event)
     *   }
     *
     * That is the only thing you need to add to the Activity.
     * Press Volume Down in-game -> panel opens.
     * Press Volume Down while panel is open -> panel closes.
     */
    fun show(activity: ComponentActivity) {
        activity.runOnUiThread {
            activity.setContent {
                MeteorTheme { PanelRoot(activity) }
            }
        }
    }
    
    fun hide(activity: ComponentActivity) {
        // Re-show main UI - handled by activity recreation
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// THEME WRAPPER
// ─────────────────────────────────────────────────────────────────────────────

@Composable fun MeteorTheme(c: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        background = MBg, surface = MPanel,
        primary = MAccent, onPrimary = Color.Black,
        onBackground = MTxtP, onSurface = MTxtP
    ),
    content = c
)

// ─────────────────────────────────────────────────────────────────────────────
// ROOT
// ─────────────────────────────────────────────────────────────────────────────

@Composable fun PanelRoot(activity: ComponentActivity) {
    var page     by remember { mutableStateOf("Home") }
    var showMods by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MBg)) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().background(MHeader)
                    .padding(horizontal = 16.dp).padding(top = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).background(MAccent),
                        contentAlignment = Alignment.Center
                    ) { Text("M", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.Black) }
                    Text("Manes", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MTxtP, letterSpacing = 0.5.sp)
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    listOf("Home", "Settings").forEach { p ->
                        val active = page == p
                        Column(
                            Modifier.clickable { page = p }.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(p, fontSize = 11.sp, color = if (active) MTxtP else MTxtM, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                            Box(Modifier.padding(top = 4.dp).height(2.dp).width(if (active) 20.dp else 0.dp).background(MAccent))
                        }
                    }
                    // Close button — goes back to game (Volume Down again closes too)
                    Box(
                        Modifier.padding(start = 4.dp, bottom = 4.dp).size(32.dp)
                            .clip(CircleShape).background(MPanel).border(1.dp, MBorder, CircleShape)
                            .clickable {
                                // Restore Minecraft's content view
                                activity.setContent {}
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Close, null, tint = MTxtM, modifier = Modifier.size(16.dp)) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MBorder))

            when (page) {
                "Home"     -> HomeScreen(activity) { showMods = true }
                "Settings" -> SettingsScreen()
            }
        }
    }

    if (showMods) {
        ModuleOverlay(
            onClose        = { showMods = false },
            onSaveModules  = { ModuleRegistry.saveAll(activity) },
            onSaveKeybinds = { ModuleRegistry.saveKeybinds(activity) }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HOME SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable fun HomeScreen(activity: ComponentActivity, onOpenModules: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        SLbl("Status")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF00E676)))
            Text("Mod active", fontSize = 13.sp, color = MTxtM)
        }
        val enabledCount = ModuleRegistry.all.count { it.enabled }
        Text("$enabledCount modules enabled", fontSize = 11.sp, color = MTxtD)
        Text("Press Volume Down to close the panel.", fontSize = 11.sp, color = MTxtD)

        Spacer(Modifier.height(8.dp))

        // Modules button
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                .background(MPanel).border(1.dp, MBorder, RoundedCornerShape(3.dp))
                .clickable { onOpenModules() }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.GridView, null, tint = MTxtM, modifier = Modifier.size(15.dp))
                Text("Open Modules", fontSize = 13.sp, color = MTxtM, fontWeight = FontWeight.Medium)
            }
        }

        // Overlay permission warning
        if (!Settings.canDrawOverlays(activity)) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                    .background(MRed.a(0.08f)).border(1.dp, MRed.a(0.3f), RoundedCornerShape(3.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Overlay permission required", fontSize = 12.sp, color = MRed, fontWeight = FontWeight.SemiBold)
                    Text("Crosshair and keybind overlays need Draw over apps permission.", fontSize = 11.sp, color = MRed.a(0.7f), lineHeight = 16.sp)
                    MBtn("Grant Permission", accent = MRed) {
                        activity.startActivity(android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODULE OVERLAY
// ─────────────────────────────────────────────────────────────────────────────

@Composable fun ModuleOverlay(onClose: () -> Unit, onSaveModules: () -> Unit, onSaveKeybinds: () -> Unit) {
    val categories = listOf("Combat", "Visual", "Motion", "World", "Misc")
    var selCat by remember { mutableStateOf("Combat") }

    Box(Modifier.fillMaxSize().background(MBg)) {
        Row(Modifier.fillMaxSize()) {

            // Sidebar
            Column(Modifier.width(80.dp).fillMaxHeight().background(MHeader)) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp).padding(top = 44.dp)) {
                    Text("MODULES", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MTxtD, letterSpacing = 1.2.sp)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MBorder))
                Spacer(Modifier.height(4.dp))
                categories.forEach { cat ->
                    val sel = selCat == cat; val cc = catColor(cat)
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (sel) cc.a(0.08f) else Color.Transparent)
                            .clickable { selCat = cat }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(3.dp).height(28.dp).background(if (sel) cc else Color.Transparent))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(catLabel(cat), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (sel) cc else MTxtD, letterSpacing = 0.5.sp)
                            Text(cat, fontSize = 9.sp, color = if (sel) cc else MTxtD, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, letterSpacing = 0.3.sp)
                        }
                    }
                }
            }

            Box(Modifier.width(1.dp).fillMaxHeight().background(MBorder))

            // Module list
            Column(Modifier.weight(1f).fillMaxHeight().background(MBg)) {
                Row(
                    Modifier.fillMaxWidth().background(MHeader)
                        .padding(horizontal = 16.dp).padding(top = 44.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(selCat, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MTxtP)
                        Text("${ModuleRegistry.all.count { it.category == selCat }} modules", fontSize = 10.sp, color = MTxtD)
                    }
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(MPanel).border(1.dp, MBorder, CircleShape).clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Close, null, tint = MTxtM, modifier = Modifier.size(16.dp)) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(MBorder))

                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val mods = ModuleRegistry.all.filter { it.category == selCat }
                    items(mods, key = { it.name }) { mod ->
                        ModuleCard(mod, onSaveModules, onSaveKeybinds)
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODULE CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable fun ModuleCard(mod: Module, onSaveModules: () -> Unit, onSaveKeybinds: () -> Unit) {
    var on by remember { mutableStateOf(mod.enabled) }
    val cc = catColor(mod.category)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
            .background(MPanel).border(1.dp, if (on) cc.a(0.25f) else MBorder, RoundedCornerShape(3.dp))
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(if (on) cc else MSep))
            Row(
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(mod.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (on) MTxtP else MTxtM)
                        if (on) MTag("ON", cc)
                    }
                    Text(mod.desc, fontSize = 10.sp, color = MTxtD, modifier = Modifier.padding(top = 2.dp))
                }
                Switch(
                    checked = on,
                    onCheckedChange = { v -> on = v; mod.enabled = v; mod.onToggle(); onSaveModules() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor    = Color.White, checkedTrackColor    = cc,
                        uncheckedThumbColor  = MTxtD,       uncheckedTrackColor  = MSep,
                        uncheckedBorderColor = MBorder
                    ),
                    modifier = Modifier.scaleSwitch(0.8f)
                )
            }
        }

        if (on) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(MBorder))
            when (mod) {
                is CrosshairModule     -> CrosshairSettings(mod, cc, onSaveModules)
                is HitboxModule        -> HitboxSettings(mod, cc, onSaveModules)
                is AntiKnockbackModule -> AntiKBSettings(mod, cc, onSaveModules)
                is KillAuraModule      -> KillAuraSettings(mod, cc, onSaveModules)
                is ReachModule         -> ReachSettings(mod, cc, onSaveModules)
                is AutoClickerModule   -> AutoClickerSettings(mod, cc, onSaveModules)
                is TimerModule         -> TimerSettings(mod, cc, onSaveModules)
                is KeybindModule       -> KeybindSettings(mod, cc, onSaveKeybinds)
                else -> {}
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODULE SETTINGS PANELS
// ─────────────────────────────────────────────────────────────────────────────

@Composable fun CrosshairSettings(mod: CrosshairModule, cc: Color, onSave: () -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingLabel("Style")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            mod.styles.forEachIndexed { i, s ->
                ChoiceChip(s, mod.styleIdx == i, cc) { mod.styleIdx = i; mod.apply(); onSave() }
            }
        }
        SettingLabel("Color")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            mod.colorOptions.forEachIndexed { i, (_, aclr) ->
                val sel = mod.colorIdx == i
                Box(Modifier.size(24.dp).clip(CircleShape).background(Color(aclr))
                    .border(if (sel) 2.dp else 1.dp, if (sel) Color.White else MBorder, CircleShape)
                    .clickable { mod.colorIdx = i; mod.apply(); onSave() })
            }
        }
        var v by remember { mutableStateOf(mod.overlaySize) }
        SliderRow("Size", "${v.toInt()} dp") {
            Slider(value = v, onValueChange = { v = it; mod.overlaySize = it; mod.apply(); onSave() },
                valueRange = 6f..30f, colors = sliderColors(cc))
        }
    }
}

@Composable fun HitboxSettings(mod: HitboxModule, cc: Color, onSave: () -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var v by remember { mutableStateOf(mod.scale) }
        SliderRow("Scale", "${String.format("%.1f", v)}x") {
            Slider(value = v, onValueChange = { v = it; mod.scale = it; if (mod.enabled) ModuleHooks.setHitbox(it); onSave() },
                valueRange = 1.0f..4.0f, steps = 29, colors = sliderColors(cc))
        }
        Text("Applies on next entity spawn.", fontSize = 9.sp, color = MTxtD)
    }
}

@Composable fun AntiKBSettings(mod: AntiKnockbackModule, cc: Color, onSave: () -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var v by remember { mutableStateOf(mod.strength) }
        SliderRow("Strength", "${(v * 100).toInt()}%") {
            Slider(value = v, onValueChange = { v = it; mod.strength = it; if (mod.enabled) ModuleHooks.setAntiKB(it); onSave() },
                valueRange = 0f..1f, colors = sliderColors(cc))
        }
        Text("0% = full cancel.  100% = normal knockback.", fontSize = 9.sp, color = MTxtD)
    }
}

@Composable fun KillAuraSettings(mod: KillAuraModule, cc: Color, onSave: () -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var range by remember { mutableStateOf(mod.range) }
        var delay by remember { mutableStateOf(mod.delayMs.toFloat()) }
        SliderRow("Range", "${String.format("%.1f", range)}m") {
            Slider(value = range, onValueChange = { range = it; mod.range = it; if (mod.enabled) ModuleHooks.setKillAura(true, it, mod.delayMs); onSave() },
                valueRange = 1f..8f, colors = sliderColors(cc))
        }
        SliderRow("Delay", "${delay.toInt()} ms") {
            Slider(value = delay, onValueChange = { delay = it; mod.delayMs = it.toInt(); if (mod.enabled) ModuleHooks.setKillAura(true, mod.range, it.toInt()); onSave() },
                valueRange = 50f..500f, colors = sliderColors(cc))
        }
    }
}

@Composable fun ReachSettings(mod: ReachModule, cc: Color, onSave: () -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var v by remember { mutableStateOf(mod.reach) }
        SliderRow("Reach", "${String.format("%.1f", v)}m") {
            Slider(value = v, onValueChange = { v = it; mod.reach = it; if (mod.enabled) ModuleHooks.setReach(it); onSave() },
                valueRange = 3f..10f, colors = sliderColors(cc))
        }
    }
}

@Composable fun AutoClickerSettings(mod: AutoClickerModule, cc: Color, onSave: () -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var v by remember { mutableStateOf(mod.cps.toFloat()) }
        SliderRow("CPS", "${v.toInt()} clicks/sec") {
            Slider(value = v, onValueChange = { v = it; mod.cps = it.toInt(); if (mod.enabled) ModuleHooks.setAutoClicker(true, it.toInt()); onSave() },
                valueRange = 1f..20f, steps = 19, colors = sliderColors(cc))
        }
    }
}

@Composable fun TimerSettings(mod: TimerModule, cc: Color, onSave: () -> Unit) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var v by remember { mutableStateOf(mod.speed) }
        SliderRow("Speed", "${String.format("%.2f", v)}x") {
            Slider(value = v, onValueChange = { v = it; mod.speed = it; if (mod.enabled) ModuleHooks.setTimer(it); onSave() },
                valueRange = 0.1f..3.0f, colors = sliderColors(cc))
        }
        Text("1.0 = normal.  Below = slower.  Above = faster.", fontSize = 9.sp, color = MTxtD)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KEYBIND SETTINGS
// ─────────────────────────────────────────────────────────────────────────────

@Composable fun KeybindSettings(mod: KeybindModule, cc: Color, onSave: () -> Unit) {
    val keybinds               = remember { KeybindManager.keybinds.toMutableStateList() }
    var showAdd  by remember   { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Keybind?>(null) }

    fun syncAndSave() {
        KeybindManager.keybinds.clear()
        KeybindManager.keybinds.addAll(keybinds)
        mod.refresh()
        onSave()
    }

    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("FLOATING BUTTONS", fontSize = 9.sp, color = MTxtD, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text("Tap = fire action.  Hold + drag = move button.", fontSize = 9.sp, color = MTxtD, lineHeight = 14.sp)
        Spacer(Modifier.height(2.dp))

        if (keybinds.isEmpty()) {
            Text("No buttons yet.", fontSize = 11.sp, color = MTxtD)
        } else {
            keybinds.forEachIndexed { idx, kb ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                        .background(MHeader).border(1.dp, MBorder, RoundedCornerShape(3.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(kb.label, fontSize = 12.sp, color = MTxtP, fontWeight = FontWeight.Medium)
                        Text(kb.actions.joinToString(" + ") { it.label }, fontSize = 10.sp, color = MTxtD)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallBtn("Edit", cc)  { editTarget = kb }
                        SmallBtn("Del", MRed) { keybinds.removeAt(idx); syncAndSave() }
                    }
                }
            }
        }

        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                .background(cc.a(0.1f)).border(1.dp, cc.a(0.4f), RoundedCornerShape(3.dp))
                .clickable { showAdd = true }.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) { Text("+ Add Button", fontSize = 12.sp, color = cc, fontWeight = FontWeight.SemiBold) }
    }

    if (showAdd) {
        KeybindEditDialog(existing = null, onDismiss = { showAdd = false }) { kb ->
            keybinds.add(kb); syncAndSave(); showAdd = false
        }
    }
    editTarget?.let { kb ->
        KeybindEditDialog(existing = kb, onDismiss = { editTarget = null }) { updated ->
            val i = keybinds.indexOfFirst { it.id == updated.id }
            if (i >= 0) { keybinds[i] = updated; syncAndSave() }
            editTarget = null
        }
    }
}

@Composable fun KeybindEditDialog(existing: Keybind?, onDismiss: () -> Unit, onConfirm: (Keybind) -> Unit) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    val selected = remember { mutableStateListOf<KeyAction>().also { if (existing != null) it.addAll(existing.actions) } }

    val groups = listOf(
        "Hotbar" to listOf(KeyAction.SLOT_1, KeyAction.SLOT_2, KeyAction.SLOT_3, KeyAction.SLOT_4, KeyAction.SLOT_5, KeyAction.SLOT_6, KeyAction.SLOT_7, KeyAction.SLOT_8, KeyAction.SLOT_9),
        "Mouse"  to listOf(KeyAction.LEFT_CLICK, KeyAction.RIGHT_CLICK, KeyAction.MIDDLE_CLICK, KeyAction.ATTACK, KeyAction.USE_ITEM),
        "Move"   to listOf(KeyAction.MOVE_FORWARD, KeyAction.MOVE_BACK, KeyAction.MOVE_LEFT, KeyAction.MOVE_RIGHT, KeyAction.JUMP, KeyAction.SNEAK, KeyAction.SPRINT),
        "Items"  to listOf(KeyAction.DROP_ITEM, KeyAction.SWAP_OFFHAND, KeyAction.OPEN_INV)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = MPanel,
        shape            = RoundedCornerShape(4.dp),
        title = { Text(if (existing == null) "Add Button" else "Edit Button", color = MTxtP, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text  = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it },
                    label = { Text("Button label", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MTxtP, unfocusedTextColor = MTxtP, focusedBorderColor = MAccent, unfocusedBorderColor = MBorder, focusedLabelColor = MAccent, unfocusedLabelColor = MTxtD, cursorColor = MAccent, focusedContainerColor = MBg, unfocusedContainerColor = MBg))

                Text("ACTIONS  (tap to toggle, multiple allowed)", fontSize = 9.sp, color = MTxtD, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)

                groups.forEach { (groupName, actions) ->
                    Text(groupName.uppercase(), fontSize = 8.sp, color = MTxtD, letterSpacing = 1.sp)
                    actions.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            row.forEach { action ->
                                val sel = selected.contains(action)
                                Box(Modifier.clip(RoundedCornerShape(3.dp))
                                    .background(if (sel) MAccent.a(0.15f) else MHeader)
                                    .border(1.dp, if (sel) MAccent else MBorder, RoundedCornerShape(3.dp))
                                    .clickable { if (sel) selected.remove(action) else selected.add(action) }
                                    .padding(horizontal = 7.dp, vertical = 5.dp)
                                ) { Text(action.label, fontSize = 10.sp, color = if (sel) MAccent else MTxtM) }
                            }
                        }
                    }
                }

                if (selected.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp))
                        .background(MAccent.a(0.05f)).border(1.dp, MAccent.a(0.2f), RoundedCornerShape(3.dp)).padding(8.dp)
                    ) { Text("Will fire: ${selected.joinToString(" + ") { it.label }}", fontSize = 10.sp, color = MAccent) }
                }
            }
        },
        confirmButton = {
            MBtn("Save") {
                if (label.isNotBlank() && selected.isNotEmpty()) {
                    val kb = existing?.also { it.label = label; it.actions.clear(); it.actions.addAll(selected) }
                        ?: Keybind(UUID.randomUUID().toString(), label, selected.toMutableList())
                    onConfirm(kb)
                }
            }
        },
        dismissButton = { Box(Modifier.clickable { onDismiss() }.padding(8.dp)) { Text("Cancel", fontSize = 12.sp, color = MTxtD) } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SETTINGS SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable fun SettingsScreen() {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SLbl("Accent Color") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelTheme.themes.chunked(3).forEachIndexed { ri, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { ci, (name, color) ->
                            val idx = ri * 3 + ci; val sel = PanelTheme.currentIndex == idx
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(3.dp))
                                .background(if (sel) color.a(0.2f) else MPanel)
                                .border(if (sel) 1.5.dp else 1.dp, if (sel) color else MBorder, RoundedCornerShape(3.dp))
                                .clickable { PanelTheme.currentIndex = idx }.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                                    Text(name, fontSize = 11.sp, color = if (sel) color else MTxtM, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Box(Modifier.fillMaxWidth().height(1.dp).background(MBorder)) }
        item { SLbl("Volume Button") }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(MPanel).border(1.dp, MBorder, RoundedCornerShape(3.dp)).padding(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Volume Down", fontSize = 13.sp, color = MTxtP, fontWeight = FontWeight.Bold)
                    Text("Opens and closes this panel.", fontSize = 11.sp, color = MTxtD)
                    Text("Volume Up still controls game volume normally.", fontSize = 11.sp, color = MTxtD)
                }
            }
        }
        item { Box(Modifier.fillMaxWidth().height(1.dp).background(MBorder)) }
        item { SLbl("Build Info") }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)).background(MPanel).border(1.dp, MBorder, RoundedCornerShape(3.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Manes Client", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MTxtP)
                Text("v1.0.0 - inside Minecraft mod", fontSize = 11.sp, color = MTxtD)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// REUSABLE COMPOSABLES
// ─────────────────────────────────────────────────────────────────────────────

fun catColor(cat: String) = when (cat) {
    "Combat" -> Color(0xFFFF4040); "Visual" -> Color(0xFF448AFF)
    "Motion" -> Color(0xFFFFD700); "World"  -> Color(0xFF00E676)
    else     -> Color(0xFF00BCD4)
}
fun catLabel(cat: String) = when (cat) {
    "Combat" -> "CMB"; "Visual" -> "VIS"; "Motion" -> "MOT"; "World" -> "WLD"; else -> "MSC"
}

@Composable fun SLbl(t: String) = Text(t.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MTxtD, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
@Composable fun MBtn(label: String, accent: Color = MAccent, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(accent.a(0.12f)).border(1.dp, accent.a(0.4f), RoundedCornerShape(3.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 12.sp, color = accent, fontWeight = FontWeight.SemiBold)
    }
}
@Composable fun SmallBtn(label: String, accent: Color, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(accent.a(0.1f)).border(1.dp, accent.a(0.3f), RoundedCornerShape(3.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(label, fontSize = 10.sp, color = accent)
    }
}
@Composable fun MTag(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(2.dp)).background(color.a(0.15f)).border(0.5.dp, color.a(0.4f), RoundedCornerShape(2.dp)).padding(horizontal = 5.dp, vertical = 2.dp)) {
        Text(text, fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}
@Composable fun ChoiceChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(3.dp)).background(if (selected) accent.a(0.15f) else MHeader).border(1.dp, if (selected) accent else MBorder, RoundedCornerShape(3.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(label, fontSize = 10.sp, color = if (selected) accent else MTxtM)
    }
}
@Composable fun SettingLabel(t: String) = Text(t.uppercase(), fontSize = 9.sp, color = MTxtD, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
@Composable fun SliderRow(label: String, value: String, slider: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { SettingLabel(label); Text(value, fontSize = 10.sp, color = MTxtM) }
    slider()
}
@Composable fun sliderColors(cc: Color) = SliderDefaults.colors(thumbColor = cc, activeTrackColor = cc, inactiveTrackColor = MSep)
fun Modifier.scaleSwitch(s: Float) = this.layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    layout((p.width * s).toInt(), (p.height * s).toInt()) { p.placeRelative((p.width * (s - 1) / 2).toInt(), (p.height * (s - 1) / 2).toInt()) }
}
private fun mutableStateListOf(vararg elements: Keybind) = androidx.compose.runtime.mutableStateListOf(*elements)
