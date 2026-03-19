package com.project.manes

// ═══════════════════════════════════════════════════════════════════════════════
//  Manes Client  –  single-file Android app
//  Modules: XRay · Fullbright · ESP · Hitbox
// ═══════════════════════════════════════════════════════════════════════════════

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.util.internal.PlatformDependent
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption
import org.cloudburstmc.protocol.bedrock.BedrockClientSession
import org.cloudburstmc.protocol.bedrock.BedrockPong
import org.cloudburstmc.protocol.bedrock.BedrockServerSession
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer
import org.cloudburstmc.protocol.bedrock.packet.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 1 · Theme
// ─────────────────────────────────────────────────────────────────────────────

private val BG          = Color(0xFF0D0D0F)
private val Surface     = Color(0xFF18181C)
private val Surface2    = Color(0xFF222228)
private val Accent      = Color(0xFF7C6EF7)
private val AccentLight = Color(0xFFA78BFA)
private val TxtPrimary  = Color(0xFFF1F0FB)
private val TxtMuted    = Color(0xFF8B8A9B)
private val Green       = Color(0xFF4ADE80)
private val Yellow      = Color(0xFFFBBF24)
private val Red         = Color(0xFFF87171)

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 2 · Data models & persistence
// ─────────────────────────────────────────────────────────────────────────────

data class ServerEntry(val id: String, val name: String, val address: String, val port: Int = 19132)
data class WorldEntry (val id: String, val name: String, val info: String = "")

object Store {
    private const val P = "manes"; private const val KS = "sv"; private const val KW = "wd"

    fun saveServers(ctx: Context, list: List<ServerEntry>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id",it.id).put("n",it.name).put("a",it.address).put("p",it.port)) }
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putString(KS, a.toString()).apply()
    }
    fun loadServers(ctx: Context): MutableList<ServerEntry> = try {
        val a = JSONArray(ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getString(KS, "[]")!!)
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            ServerEntry(it.getString("id"), it.getString("n"), it.getString("a"), it.optInt("p", 19132)) }
        }.toMutableList()
    } catch (_: Exception) { mutableListOf() }

    fun saveWorlds(ctx: Context, list: List<WorldEntry>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id",it.id).put("n",it.name).put("i",it.info)) }
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putString(KW, a.toString()).apply()
    }
    fun loadWorlds(ctx: Context): MutableList<WorldEntry> = try {
        val a = JSONArray(ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getString(KW, "[]")!!)
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            WorldEntry(it.getString("id"), it.getString("n"), it.optString("i","")) }
        }.toMutableList()
    } catch (_: Exception) { mutableListOf() }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 3 · Module base + all modules
// ─────────────────────────────────────────────────────────────────────────────

abstract class Module(val name: String, val desc: String, val category: String) {
    var enabled = false
    open fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
    open fun onServerBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// ── XRay ──────────────────────────────────────────────────────────────────────
class XRayModule : Module("XRay", "Show ores through walls", "World") {
    private val oreNames = setOf(
        "minecraft:coal_ore","minecraft:iron_ore","minecraft:gold_ore",
        "minecraft:diamond_ore","minecraft:emerald_ore","minecraft:lapis_ore",
        "minecraft:redstone_ore","minecraft:copper_ore",
        "minecraft:deepslate_coal_ore","minecraft:deepslate_iron_ore",
        "minecraft:deepslate_gold_ore","minecraft:deepslate_diamond_ore",
        "minecraft:deepslate_emerald_ore","minecraft:deepslate_lapis_ore",
        "minecraft:deepslate_redstone_ore","minecraft:deepslate_copper_ore",
        "minecraft:nether_gold_ore","minecraft:ancient_debris","minecraft:quartz_ore",
        "minecraft:air","minecraft:water","minecraft:flowing_water",
        "minecraft:lava","minecraft:flowing_lava","minecraft:bedrock"
    )

    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if (!enabled || pkt !is LevelChunkPacket) return false
        return try {
            val defs = ses.serverSession.blockDefinitions ?: return false
            val nameToId = mutableMapOf<String, Int>()
            defs.forEachEntry { k, id -> nameToId[k.name] = id }
            val allowed = oreNames.mapNotNull { nameToId[it] }.toHashSet()
            val air = nameToId["minecraft:air"] ?: 0
            val modified = rewriteChunk(pkt, allowed, air) ?: return false
            ses.sendToClient(modified)
            true
        } catch (_: Exception) { false }
    }

    private fun rewriteChunk(orig: LevelChunkPacket, allowed: Set<Int>, air: Int): LevelChunkPacket? {
        val raw = orig.data ?: return null
        val buf = Unpooled.wrappedBuffer(raw)
        val out = ByteArrayOutputStream()
        var changed = false

        repeat(orig.subChunksLength) {
            val ver = buf.readUnsignedByte().toInt()
            val layers = if (ver == 8 || ver == 9) buf.readUnsignedByte().toInt() else 1
            out.write(ver)
            if (ver == 8 || ver == 9) out.write(layers)

            for (layer in 0 until layers) {
                val bitsFlag = buf.readUnsignedByte().toInt()
                val bpb = bitsFlag ushr 1
                val bpw = if (bpb > 0) 32 / bpb else 4096
                val wc  = if (bpb > 0) (4096 + bpw - 1) / bpw else 0
                val words = IntArray(wc) { buf.readIntLE() }
                val palSz = buf.readIntLE()
                val pal   = IntArray(palSz) { buf.readIntLE() }

                val newPal = if (layer == 0 && bpb > 0)
                    pal.map { if (it in allowed) it else air }.toIntArray().also {
                        if (!it.contentEquals(pal)) changed = true }
                else pal

                out.write(bitsFlag)
                words.forEach { writeLE(out, it) }
                writeLE(out, newPal.size)
                newPal.forEach { writeLE(out, it) }
            }
        }
        if (!changed) return null
        val rest = ByteArray(buf.readableBytes()); buf.readBytes(rest); out.write(rest)
        return LevelChunkPacket().also {
            it.chunkX = orig.chunkX; it.chunkZ = orig.chunkZ
            it.subChunksLength = orig.subChunksLength
            it.isCachingEnabled = orig.isCachingEnabled
            it.data = out.toByteArray()
        }
    }

    private fun writeLE(o: ByteArrayOutputStream, v: Int) {
        o.write(v and 0xFF); o.write((v ushr 8) and 0xFF)
        o.write((v ushr 16) and 0xFF); o.write((v ushr 24) and 0xFF)
    }
}

// ── Fullbright ────────────────────────────────────────────────────────────────
class FullbrightModule : Module("Fullbright", "Remove fog & darkness", "Visual") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if (!enabled || pkt !is PlayerFogPacket) return false
        pkt.fogStack.clear()
        return false // let modified packet through
    }
}

// ── ESP ───────────────────────────────────────────────────────────────────────
class ESPModule : Module("ESP", "See entities through walls", "Visual") {
    // EntityFlag index 46 = HAS_GLOWING in Bedrock protocol
    private val FLAG_GLOWING = 46

    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if (!enabled) return false
        when (pkt) {
            is AddPlayerPacket -> runCatching { pkt.metadata.putBoolean(FLAG_GLOWING, true) }
            is AddEntityPacket -> runCatching { pkt.metadata.putBoolean(FLAG_GLOWING, true) }
        }
        return false
    }
}

// ── Hitbox ────────────────────────────────────────────────────────────────────
class HitboxModule : Module("Hitbox", "Expand entity hit boxes", "Combat") {
    // metadata keys 39=BOUNDING_BOX_WIDTH, 41=BOUNDING_BOX_HEIGHT
    private val scale = 1.8f

    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if (!enabled) return false
        when (pkt) {
            is AddPlayerPacket -> runCatching {
                pkt.metadata.putFloat(39, scale)
                pkt.metadata.putFloat(41, scale)
            }
            is AddEntityPacket -> runCatching {
                pkt.metadata.putFloat(39, scale)
                pkt.metadata.putFloat(41, scale)
            }
        }
        return false
    }
}

// ── Registry ──────────────────────────────────────────────────────────────────
object Modules {
    val all: List<Module> = listOf(
        XRayModule(),
        FullbrightModule(),
        ESPModule(),
        HitboxModule()
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 4 · Relay session  (packet pipeline)
// ─────────────────────────────────────────────────────────────────────────────

class RelaySession {
    lateinit var serverSession: BedrockServerSession
    var clientSession: BedrockClientSession? = null
        set(value) {
            field = value; value ?: return
            value.codec = serverSession.codec
            while (true) { val (p, imm) = queue.poll() ?: break
                if (imm) value.sendPacketImmediately(p) else value.sendPacket(p) }
        }

    private data class QueuedPacket(val pkt: BedrockPacket, val immediate: Boolean)
    private val queue = PlatformDependent.newMpscQueue<QueuedPacket>()

    fun sendToClient(p: BedrockPacket) = serverSession.sendPacket(p)
    fun sendToServer(p: BedrockPacket) {
        val c = clientSession
        if (c != null) c.sendPacket(p) else queue.offer(QueuedPacket(p, false))
    }

    fun processFromServer(pkt: BedrockPacket) {
        for (m in Modules.all) { runCatching { if (m.onClientBound(pkt, this)) return } }
        sendToClient(pkt)
    }

    fun processFromClient(pkt: BedrockPacket) {
        for (m in Modules.all) { runCatching { if (m.onServerBound(pkt, this)) return } }
        sendToServer(pkt)
    }

    fun disconnect() { runCatching { clientSession?.disconnect() } }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 5 · Relay server  (proxy bootstrap)
// ─────────────────────────────────────────────────────────────────────────────

object ManesRelay {
    @Volatile private var group: NioEventLoopGroup? = null
    var activeSession: RelaySession? = null

    fun start(remoteIp: String, remotePort: Int) {
        stop()
        val eg = NioEventLoopGroup().also { group = it }

        val pong = BedrockPong()
            .edition("MCPE").motd("Manes").subMotd("Manes")
            .playerCount(0).maximumPlayerCount(1)
            .gameType("Survival").protocolVersion(818).version("1.21.80")

        ServerBootstrap()
            .channelFactory(RakChannelFactory.server(eg))
            .group(eg)
            .option(RakChannelOption.RAK_ADVERTISEMENT, pong.toByteBuf())
            .childHandler(object : BedrockChannelInitializer<BedrockServerSession>() {
                override fun initSession(srv: BedrockServerSession) {
                    val relay = RelaySession().also { it.serverSession = srv; activeSession = it }

                    srv.packetHandler = PacketHandler { pkt ->
                        relay.processFromClient(pkt); true
                    }
                    srv.channel().closeFuture().addListener { relay.disconnect() }

                    // connect outward to the real server
                    Bootstrap()
                        .channelFactory(RakChannelFactory.client(NioEventLoopGroup()))
                        .group(eg)
                        .handler(object : BedrockChannelInitializer<BedrockClientSession>() {
                            override fun initSession(cli: BedrockClientSession) {
                                relay.clientSession = cli
                                cli.packetHandler = PacketHandler { pkt ->
                                    relay.processFromServer(pkt); true
                                }
                                cli.channel().closeFuture().addListener { relay.disconnect() }
                            }
                        })
                        .connect(InetSocketAddress(remoteIp, remotePort))
                }
            })
            .bind(InetSocketAddress("0.0.0.0", 19132))
            .syncUninterruptibly()
    }

    fun stop() {
        activeSession?.disconnect(); activeSession = null
        group?.shutdownGracefully(); group = null
    }

    // Minimal functional packet handler
    fun interface PacketHandler : BedrockPacketHandler {
        override fun handle(pkt: BedrockPacket): Boolean = onPacket(pkt)
        fun onPacket(pkt: BedrockPacket): Boolean
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 6 · MainActivity  (Compose UI)
// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val defServers = listOf(
            ServerEntry("hive",  "The Hive",    "geo.hivebedrock.network"),
            ServerEntry("cube",  "CubeCraft",   "mco.cubecraft.net"),
            ServerEntry("lbsg",  "Lifeboat",    "play.lbsg.net"),
            ServerEntry("mnpl",  "Mineplex",    "pe.mineplex.com"),
            ServerEntry("neth",  "Nether Games","play.nethergames.org"),
        )

        val sv = Store.loadServers(this).ifEmpty { defServers.toMutableList() }
        val wv = Store.loadWorlds(this)

        setContent {
            ManesTheme {
                ManesApp(
                    initServers  = sv,
                    initWorlds   = wv,
                    saveServers  = { Store.saveServers(this, it) },
                    saveWorlds   = { Store.saveWorlds(this, it) },
                    doLaunch     = { addr, port -> launchProxy(addr, port) }
                )
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); ManesRelay.stop() }

    private fun launchProxy(addr: String, port: Int) {
        Thread {
            runCatching { ManesRelay.start(addr, port) }
                .onFailure { it.printStackTrace() }
        }.apply { isDaemon = true }.start()

        // give relay a moment then deep-link Minecraft
        android.os.Handler(mainLooper).postDelayed({
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("minecraft://?addExternalServer=Manes%7C127.0.0.1:19132")))
            }
        }, 1200)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 7 · Compose screens
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ManesTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(
        background = BG, surface = Surface,
        primary = Accent, onPrimary = Color.White,
        onBackground = TxtPrimary, onSurface = TxtPrimary
    ), content = content
)

@Composable
fun ManesApp(
    initServers: List<ServerEntry>, initWorlds: List<WorldEntry>,
    saveServers: (List<ServerEntry>) -> Unit, saveWorlds: (List<WorldEntry>) -> Unit,
    doLaunch: (String, Int) -> Unit
) {
    var tab        by remember { mutableStateOf(0) }
    var servers    by remember { mutableStateOf(initServers) }
    var worlds     by remember { mutableStateOf(initWorlds) }
    var selSrvId   by remember { mutableStateOf<String?>(null) }
    var selWldId   by remember { mutableStateOf<String?>(null) }
    var showAddSrv by remember { mutableStateOf(false) }
    var showAddWld by remember { mutableStateOf(false) }
    var launching  by remember { mutableStateOf(false) }
    var launchName by remember { mutableStateOf("") }

    val selSrv = servers.firstOrNull { it.id == selSrvId }
    val selWld = worlds.firstOrNull  { it.id == selWldId }
    val ready  = (tab == 0 && selSrv != null) || (tab == 1 && selWld != null) ||
                 (tab == 2)

    Column(Modifier.fillMaxSize().background(BG)) {

        // Header
        Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=52.dp,bottom=8.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Accent),
                    contentAlignment=Alignment.Center) {
                    Text("M", fontSize=18.sp, fontWeight=FontWeight.Bold, color=Color.White)
                }
                Text("Manes", fontSize=22.sp, fontWeight=FontWeight.Bold, color=TxtPrimary)
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(
                if (ManesRelay.activeSession != null) Green else TxtMuted))
        }

        // Tabs
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp, vertical=8.dp),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Servers","Worlds","Modules").forEachIndexed { i, lbl ->
                val on = tab == i
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(if (on) Accent else Surface)
                    .clickable { tab = i; selSrvId = null; selWldId = null }
                    .padding(vertical=10.dp),
                    contentAlignment=Alignment.Center) {
                    Text(lbl, fontSize=13.sp, fontWeight=FontWeight.Medium,
                        color=if (on) Color.White else TxtMuted)
                }
            }
        }

        LazyColumn(Modifier.weight(1f).padding(horizontal=20.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            when (tab) {
                // ── SERVERS ───────────────────────────────────────────────────
                0 -> {
                    item { SLabel("Featured servers") }
                    items(servers, key={it.id}) { sv ->
                        EntryCard(
                            icon="🌐", title=sv.name, sub="${sv.address}:${sv.port}",
                            selected=sv.id==selSrvId, accentCol=Accent,
                            onClick={ selSrvId = if(selSrvId==sv.id) null else sv.id },
                            onDelete={ servers=servers.filter{it.id!=sv.id}.also{saveServers(it)};
                                if(selSrvId==sv.id) selSrvId=null }
                        )
                    }
                    item { AddBtn("Add a server") { showAddSrv=true } }
                    item { Spacer(Modifier.height(90.dp)) }
                }
                // ── WORLDS ────────────────────────────────────────────────────
                1 -> {
                    item { SLabel("Local worlds") }
                    if (worlds.isEmpty()) item { EmptyHint("No worlds yet — add one below") }
                    items(worlds, key={it.id}) { wl ->
                        EntryCard(
                            icon="🌲", title=wl.name, sub=wl.info,
                            selected=wl.id==selWldId, accentCol=Green,
                            onClick={ selWldId = if(selWldId==wl.id) null else wl.id },
                            onDelete={ worlds=worlds.filter{it.id!=wl.id}.also{saveWorlds(it)};
                                if(selWldId==wl.id) selWldId=null }
                        )
                    }
                    item { AddBtn("Import a world") { showAddWld=true } }
                    item { Spacer(Modifier.height(90.dp)) }
                }
                // ── MODULES ───────────────────────────────────────────────────
                2 -> {
                    item { SLabel("Active modules") }
                    items(Modules.all, key={it.name}) { mod ->
                        ModuleCard(mod)
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }

        // Launch bar
        Column(Modifier.fillMaxWidth().background(BG).padding(horizontal=20.dp, vertical=16.dp)) {
            Button(
                onClick = {
                    when (tab) {
                        0 -> selSrv?.let { launchName=it.name; launching=true; doLaunch(it.address,it.port) }
                        1 -> selWld?.let { launchName=it.name; launching=true; doLaunch("127.0.0.1",19132) }
                        2 -> { launchName="last server"; launching=true }
                    }
                },
                modifier=Modifier.fillMaxWidth().height(52.dp),
                shape=RoundedCornerShape(14.dp),
                enabled=ready,
                colors=ButtonDefaults.buttonColors(
                    containerColor=if(ready) Accent else Surface2,
                    contentColor=if(ready) Color.White else TxtMuted,
                    disabledContainerColor=Surface2, disabledContentColor=TxtMuted
                )
            ) {
                val lbl = when {
                    tab==0 && selSrv!=null -> "▶  Launch ${selSrv.name}"
                    tab==1 && selWld!=null -> "▶  Open ${selWld.name}"
                    tab==2 -> "▶  Launch with modules"
                    else   -> "Select a destination"
                }
                Text(lbl, fontSize=15.sp, fontWeight=FontWeight.SemiBold)
            }
            Text(
                text = when {
                    tab==0&&selSrv!=null -> "${selSrv.address}:${selSrv.port}"
                    tab==1&&selWld!=null -> "Local world via loopback proxy"
                    tab==2 -> "Modules apply to next launch"
                    else   -> "Tap a server or world to select"
                },
                fontSize=12.sp, color=TxtMuted,
                modifier=Modifier.fillMaxWidth().padding(top=6.dp),
                textAlign=TextAlign.Center
            )
        }
    }

    // Launching overlay
    if (launching) {
        LaunchOverlay(name=launchName, onDone={ launching=false })
    }

    // Dialogs
    if (showAddSrv) AddServerDialog(
        onDismiss={showAddSrv=false},
        onAdd={ n,a,p ->
            servers=(servers+ServerEntry(UUID.randomUUID().toString(),n,a,p)).also{saveServers(it)}
            showAddSrv=false
        }
    )
    if (showAddWld) AddWorldDialog(
        onDismiss={showAddWld=false},
        onAdd={ n ->
            worlds=(worlds+WorldEntry(UUID.randomUUID().toString(),n,"Just added")).also{saveWorlds(it)}
            showAddWld=false
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION 8 · Reusable Compose components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EntryCard(icon: String, title: String, sub: String, selected: Boolean, accentCol: Color,
              onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if(selected) accentCol.copy(alpha=0.1f) else Surface)
            .border(0.5.dp, if(selected) accentCol else Surface2, RoundedCornerShape(14.dp))
            .clickable(onClick=onClick).padding(14.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
            .background(accentCol.copy(0.13f)), contentAlignment=Alignment.Center) {
            Text(icon, fontSize=20.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize=15.sp, fontWeight=FontWeight.Medium, color=TxtPrimary)
            if (sub.isNotBlank()) Text(sub, fontSize=12.sp, color=TxtMuted)
        }
        if (selected)
            Box(Modifier.size(22.dp).clip(CircleShape).background(accentCol),
                contentAlignment=Alignment.Center) {
                Icon(Icons.Default.Check, null, tint=Color.White, modifier=Modifier.size(13.dp))
            }
        else
            IconButton(onClick=onDelete, modifier=Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, null, tint=TxtMuted, modifier=Modifier.size(16.dp))
            }
    }
}

@Composable
fun ModuleCard(mod: Module) {
    var on by remember { mutableStateOf(mod.enabled) }
    val cat = when (mod.category) {
        "Combat" -> Red; "Visual" -> AccentLight; else -> Green
    }
    Row(
        modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if(on) cat.copy(0.08f) else Surface)
            .border(0.5.dp, if(on) cat else Surface2, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(mod.name, fontSize=15.sp, fontWeight=FontWeight.Medium, color=TxtPrimary)
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(cat.copy(0.2f))
                    .padding(horizontal=6.dp, vertical=2.dp)) {
                    Text(mod.category, fontSize=10.sp, color=cat, fontWeight=FontWeight.Medium)
                }
            }
            Text(mod.desc, fontSize=12.sp, color=TxtMuted)
        }
        Switch(
            checked=on,
            onCheckedChange={ on=it; mod.enabled=it },
            colors=SwitchDefaults.colors(checkedThumbColor=Color.White, checkedTrackColor=cat)
        )
    }
}

@Composable
fun AddBtn(label: String, onClick: () -> Unit) {
    Row(
        modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(0.5.dp, TxtMuted.copy(0.2f), RoundedCornerShape(14.dp))
            .clickable(onClick=onClick).padding(14.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, TxtMuted.copy(0.35f), RoundedCornerShape(10.dp)),
            contentAlignment=Alignment.Center) {
            Icon(Icons.Default.Add, null, tint=TxtMuted, modifier=Modifier.size(20.dp))
        }
        Text(label, fontSize=14.sp, color=TxtMuted)
    }
}

@Composable
fun SLabel(txt: String) = Text(
    txt.uppercase(), fontSize=11.sp, fontWeight=FontWeight.SemiBold,
    color=TxtMuted, letterSpacing=0.8.sp,
    modifier=Modifier.padding(top=8.dp, bottom=4.dp)
)

@Composable
fun EmptyHint(txt: String) = Box(Modifier.fillMaxWidth().padding(vertical=20.dp),
    contentAlignment=Alignment.Center) { Text(txt, fontSize=13.sp, color=TxtMuted) }

@Composable
fun LaunchOverlay(name: String, onDone: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val steps = listOf(
        "Starting Manes relay…","Binding 0.0.0.0:19132…",
        "Connecting to $name…","Performing handshake…",
        "Authenticated — loading world…","Launching Minecraft…"
    )
    LaunchedEffect(Unit) {
        steps.indices.forEach { i ->
            kotlinx.coroutines.delay(700); step = i
        }
    }

    Box(Modifier.fillMaxSize().background(BG.copy(alpha=0.96f)),
        contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Accent),
                contentAlignment=Alignment.Center) {
                Text("M", fontSize=36.sp, fontWeight=FontWeight.Bold, color=Color.White)
            }
            Spacer(Modifier.height(24.dp))
            Text(name, fontSize=22.sp, fontWeight=FontWeight.Bold, color=TxtPrimary)
            Text("Connecting…", fontSize=14.sp, color=TxtMuted, modifier=Modifier.padding(top=4.dp, bottom=28.dp))
            CircularProgressIndicator(color=Accent, modifier=Modifier.size(32.dp), strokeWidth=2.5.dp)
            Spacer(Modifier.height(12.dp))
            Text(steps.getOrElse(step){"Ready"}, fontSize=13.sp, color=TxtMuted)
            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick=onDone,
                colors=ButtonDefaults.outlinedButtonColors(contentColor=TxtMuted),
                border=ButtonDefaults.outlinedButtonBorder.copy(width=0.5.dp)) {
                Icon(Icons.Default.Close, null, modifier=Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cancel", fontSize=13.sp)
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────
@Composable
fun AddServerDialog(onDismiss: () -> Unit, onAdd: (String,String,Int) -> Unit) {
    var n by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("19132") }
    AlertDialog(onDismissRequest=onDismiss, containerColor=Surface,
        title={ Text("Add server", color=TxtPrimary) },
        text={ Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            MField("Server name", n) { n=it }
            MField("Address", a) { a=it }
            MField("Port", p, KeyboardType.Number) { p=it }
        }},
        confirmButton={ Button(onClick={ if(n.isNotBlank()&&a.isNotBlank())
            onAdd(n.trim(),a.trim(),p.toIntOrNull()?:19132) },
            colors=ButtonDefaults.buttonColors(containerColor=Accent)) { Text("Add") }},
        dismissButton={ TextButton(onDismiss) { Text("Cancel",color=TxtMuted) }}
    )
}

@Composable
fun AddWorldDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var n by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss, containerColor=Surface,
        title={ Text("Import world", color=TxtPrimary) },
        text={ MField("World name", n) { n=it } },
        confirmButton={ Button(onClick={ if(n.isNotBlank()) onAdd(n.trim()) },
            colors=ButtonDefaults.buttonColors(containerColor=Accent)) { Text("Import") }},
        dismissButton={ TextButton(onDismiss) { Text("Cancel",color=TxtMuted) }}
    )
}

@Composable
fun MField(lbl: String, v: String, kb: KeyboardType=KeyboardType.Text, onChange: (String)->Unit) =
    OutlinedTextField(v, onChange, label={Text(lbl,fontSize=12.sp)},
        modifier=Modifier.fillMaxWidth(), singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=kb),
        colors=OutlinedTextFieldDefaults.colors(
            focusedTextColor=TxtPrimary, unfocusedTextColor=TxtPrimary,
            focusedBorderColor=Accent, unfocusedBorderColor=Surface2,
            focusedLabelColor=Accent, unfocusedLabelColor=TxtMuted, cursorColor=Accent
        )
    )
