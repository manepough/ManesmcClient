package com.project.manes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.util.internal.PlatformDependent
import kotlinx.coroutines.delay
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption
import org.cloudburstmc.protocol.bedrock.BedrockClientSession
import org.cloudburstmc.protocol.bedrock.BedrockPong
import org.cloudburstmc.protocol.bedrock.BedrockServerSession
import org.cloudburstmc.protocol.bedrock.data.entity.EntityData
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockServerInitializer
import org.cloudburstmc.protocol.bedrock.packet.AddEntityPacket
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler
import org.cloudburstmc.protocol.bedrock.packet.LevelChunkPacket
import org.cloudburstmc.protocol.bedrock.packet.PlayerFogPacket
import org.cloudburstmc.protocol.common.PacketSignal
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.UUID

// ── Theme colours ─────────────────────────────────────────────────────────────
private val BG       = Color(0xFF0D0D0F)
private val Surface  = Color(0xFF18181C)
private val Surface2 = Color(0xFF222228)
private val Accent   = Color(0xFF7C6EF7)
private val AccentLt = Color(0xFFA78BFA)
private val TxtPri   = Color(0xFFF1F0FB)
private val TxtMut   = Color(0xFF8B8A9B)
private val Green    = Color(0xFF4ADE80)
private val RedCol   = Color(0xFFF87171)

// ── Data models ───────────────────────────────────────────────────────────────
data class ServerEntry(val id: String, val name: String, val address: String, val port: Int = 19132)
data class WorldEntry(val id: String, val name: String, val info: String = "")

// ── Persistence ───────────────────────────────────────────────────────────────
object Store {
    private const val P = "manes"
    fun saveServers(ctx: Context, list: List<ServerEntry>) {
        val a = JSONArray()
        for (s in list) a.put(JSONObject().apply { put("id",s.id);put("n",s.name);put("a",s.address);put("p",s.port) })
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putString("sv", a.toString()).apply()
    }
    fun loadServers(ctx: Context): MutableList<ServerEntry> = try {
        val a = JSONArray(ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getString("sv","[]")?:"[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { ServerEntry(it.getString("id"),it.getString("n"),it.getString("a"),it.optInt("p",19132)) } }.toMutableList()
    } catch (e: Exception) { mutableListOf() }
    fun saveWorlds(ctx: Context, list: List<WorldEntry>) {
        val a = JSONArray()
        for (w in list) a.put(JSONObject().apply { put("id",w.id);put("n",w.name);put("i",w.info) })
        ctx.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putString("wd", a.toString()).apply()
    }
    fun loadWorlds(ctx: Context): MutableList<WorldEntry> = try {
        val a = JSONArray(ctx.getSharedPreferences(P, Context.MODE_PRIVATE).getString("wd","[]")?:"[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { WorldEntry(it.getString("id"),it.getString("n"),it.optString("i","")) } }.toMutableList()
    } catch (e: Exception) { mutableListOf() }
}

// ── Module base ───────────────────────────────────────────────────────────────
abstract class Module(val name: String, val desc: String, val category: String) {
    var enabled = false
    open fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
    open fun onServerBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// ── XRay ──────────────────────────────────────────────────────────────────────
class XRayModule : Module("XRay", "Show ores through walls", "World") {
    private val keep = setOf(
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
            val defs = ses.serverSession?.blockDefinitions ?: return false
            val nameToId = HashMap<String, Int>()
            defs.forEachEntry { k, id -> nameToId[k.name] = id }
            val allowed = HashSet<Int>()
            for (n in keep) { nameToId[n]?.let { allowed.add(it) } }
            val air = nameToId["minecraft:air"] ?: 0
            val result = rewriteChunk(pkt, allowed, air) ?: return false
            ses.sendToClient(result); true
        } catch (e: Exception) { false }
    }
    private fun rewriteChunk(orig: LevelChunkPacket, allowed: Set<Int>, air: Int): LevelChunkPacket? {
        val raw = orig.data ?: return null
        val buf = Unpooled.wrappedBuffer(raw)
        val out = ByteArrayOutputStream()
        var changed = false
        for (s in 0 until orig.subChunksLength) {
            val ver = buf.readUnsignedByte().toInt()
            val layers = if (ver == 8 || ver == 9) buf.readUnsignedByte().toInt() else 1
            out.write(ver); if (ver == 8 || ver == 9) out.write(layers)
            for (layer in 0 until layers) {
                val bf = buf.readUnsignedByte().toInt()
                val bpb = bf ushr 1
                val bpw = if (bpb > 0) 32 / bpb else 4096
                val wc  = if (bpb > 0) (4096 + bpw - 1) / bpw else 0
                val words = IntArray(wc) { buf.readIntLE() }
                val palSz = buf.readIntLE()
                val pal   = IntArray(palSz) { buf.readIntLE() }
                val newPal = if (layer == 0 && bpb > 0)
                    IntArray(pal.size) { i -> if (pal[i] in allowed) pal[i] else air }
                        .also { if (!it.contentEquals(pal)) changed = true }
                else pal
                out.write(bf)
                for (w in words) wLE(out, w)
                wLE(out, newPal.size)
                for (id in newPal) wLE(out, id)
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
    private fun wLE(o: ByteArrayOutputStream, v: Int) {
        o.write(v and 0xFF); o.write((v ushr 8) and 0xFF)
        o.write((v ushr 16) and 0xFF); o.write((v ushr 24) and 0xFF)
    }
}

// ── Fullbright ────────────────────────────────────────────────────────────────
class FullbrightModule : Module("Fullbright", "Remove darkness and fog", "Visual") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if (!enabled || pkt !is PlayerFogPacket) return false
        pkt.fogStack.clear(); return false
    }
}

// ── ESP ───────────────────────────────────────────────────────────────────────
class ESPModule : Module("ESP", "See entities through walls", "Visual") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if (!enabled) return false
        try {
            when (pkt) {
                is AddPlayerPacket -> pkt.metadata.getFlags().setFlag(EntityFlag.HAS_GLOWING, true)
                is AddEntityPacket -> pkt.metadata.getFlags().setFlag(EntityFlag.HAS_GLOWING, true)
            }
        } catch (e: Exception) { }
        return false
    }
}

// ── Hitbox ────────────────────────────────────────────────────────────────────
class HitboxModule : Module("Hitbox", "Expand enemy hit boxes", "Combat") {
    private val scale = 1.8f
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if (!enabled) return false
        try {
            when (pkt) {
                is AddPlayerPacket -> {
                    pkt.metadata[EntityData.BOUNDING_BOX_WIDTH]  = scale
                    pkt.metadata[EntityData.BOUNDING_BOX_HEIGHT] = scale
                }
                is AddEntityPacket -> {
                    pkt.metadata[EntityData.BOUNDING_BOX_WIDTH]  = scale
                    pkt.metadata[EntityData.BOUNDING_BOX_HEIGHT] = scale
                }
            }
        } catch (e: Exception) { }
        return false
    }
}

// ── Registry ──────────────────────────────────────────────────────────────────
object Modules {
    val all: List<Module> = listOf(XRayModule(), FullbrightModule(), ESPModule(), HitboxModule())
}

// ── Relay session ─────────────────────────────────────────────────────────────
class RelaySession {
    var serverSession: BedrockServerSession? = null
    var clientSession: BedrockClientSession? = null
        set(value) {
            field = value; if (value == null) return
            serverSession?.let { value.codec = it.codec }
            var p = queue.poll(); while (p != null) { value.sendPacket(p); p = queue.poll() }
        }
    private val queue = PlatformDependent.newMpscQueue<BedrockPacket>()
    fun sendToClient(p: BedrockPacket) { serverSession?.sendPacket(p) }
    fun sendToServer(p: BedrockPacket) { val c = clientSession; if (c != null) c.sendPacket(p) else queue.offer(p) }
    fun handleFromServer(pkt: BedrockPacket) {
        for (m in Modules.all) { try { if (m.onClientBound(pkt, this)) return } catch (e: Exception) { } }
        sendToClient(pkt)
    }
    fun handleFromClient(pkt: BedrockPacket) {
        for (m in Modules.all) { try { if (m.onServerBound(pkt, this)) return } catch (e: Exception) { } }
        sendToServer(pkt)
    }
    fun disconnect() { try { clientSession?.disconnect() } catch (e: Exception) { } }
}

// ── Relay server ──────────────────────────────────────────────────────────────
object ManesRelay {
    @Volatile var active: RelaySession? = null

    fun start(remoteIp: String, remotePort: Int) {
        stop()
        val ses = RelaySession().also { active = it }
        val group = NioEventLoopGroup()

        val pong = BedrockPong()
            .edition("MCPE").motd("Manes").subMotd("Manes")
            .playerCount(0).maximumPlayerCount(1)
            .gameType("Survival").protocolVersion(818).version("1.21.80")

        ServerBootstrap()
            .channelFactory(RakChannelFactory.server(NioDatagramChannel::class.java))
            .group(group)
            .option(RakChannelOption.RAK_ADVERTISEMENT, pong.toByteBuf())
            .childHandler(object : BedrockServerInitializer() {
                override fun initSession(srv: BedrockServerSession) {
                    ses.serverSession = srv
                    srv.packetHandler = object : BedrockPacketHandler {
                        override fun handlePacket(pkt: BedrockPacket): PacketSignal {
                            ses.handleFromClient(pkt)
                            return PacketSignal.HANDLED
                        }
                    }
                    Bootstrap()
                        .channelFactory(RakChannelFactory.client(NioDatagramChannel::class.java))
                        .group(group)
                        .handler(object : BedrockClientInitializer() {
                            override fun initSession(cli: BedrockClientSession) {
                                ses.clientSession = cli
                                cli.packetHandler = object : BedrockPacketHandler {
                                    override fun handlePacket(pkt: BedrockPacket): PacketSignal {
                                        ses.handleFromServer(pkt)
                                        return PacketSignal.HANDLED
                                    }
                                }
                            }
                        })
                        .connect(InetSocketAddress(remoteIp, remotePort))
                }
            })
            .bind(InetSocketAddress("0.0.0.0", 19132))
            .syncUninterruptibly()
    }

    fun stop() { active?.disconnect(); active = null }
}

// ── MainActivity ──────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    private val defServers = listOf(
        ServerEntry("hive","The Hive","geo.hivebedrock.network",19132),
        ServerEntry("cube","CubeCraft","mco.cubecraft.net",19132),
        ServerEntry("lbsg","Lifeboat","play.lbsg.net",19132),
        ServerEntry("mnpl","Mineplex","pe.mineplex.com",19132),
        ServerEntry("neth","NetherGames","play.nethergames.org",19132)
    )
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val sv = Store.loadServers(this).ifEmpty { defServers.toMutableList() }
        val wv = Store.loadWorlds(this)
        setContent {
            AppTheme {
                ManesApp(sv, wv,
                    onSaveSrv = { Store.saveServers(this, it) },
                    onSaveWld = { Store.saveWorlds(this, it) },
                    onLaunch  = { addr, port -> doLaunch(addr, port) })
            }
        }
    }
    override fun onDestroy() { super.onDestroy(); ManesRelay.stop() }
    private fun doLaunch(addr: String, port: Int) {
        Thread { try { ManesRelay.start(addr, port) } catch (e: Exception) { e.printStackTrace() } }
            .apply { isDaemon = true }.start()
        Handler(Looper.getMainLooper()).postDelayed({
            try { startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("minecraft://?addExternalServer=Manes%7C127.0.0.1:19132"))) }
            catch (e: Exception) { }
        }, 1500)
    }
}

// ── Theme ─────────────────────────────────────────────────────────────────────
@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(background=BG, surface=Surface, primary=Accent,
        onPrimary=Color.White, onBackground=TxtPri, onSurface=TxtPri),
    content = content
)

// ── Main screen ───────────────────────────────────────────────────────────────
@Composable
fun ManesApp(
    initServers: List<ServerEntry>, initWorlds: List<WorldEntry>,
    onSaveSrv: (List<ServerEntry>) -> Unit, onSaveWld: (List<WorldEntry>) -> Unit,
    onLaunch: (String, Int) -> Unit
) {
    var tab        by remember { mutableStateOf(0) }
    var servers    by remember { mutableStateOf(initServers) }
    var worlds     by remember { mutableStateOf(initWorlds) }
    var selSrv     by remember { mutableStateOf<String?>(null) }
    var selWld     by remember { mutableStateOf<String?>(null) }
    var showAddSrv by remember { mutableStateOf(false) }
    var showAddWld by remember { mutableStateOf(false) }
    var launching  by remember { mutableStateOf(false) }
    var lName      by remember { mutableStateOf("") }
    val curSrv = servers.firstOrNull { it.id == selSrv }
    val curWld = worlds.firstOrNull  { it.id == selWld }
    val ready  = (tab==0&&curSrv!=null)||(tab==1&&curWld!=null)||tab==2

    Column(Modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=52.dp,bottom=8.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Accent),
                    contentAlignment=Alignment.Center) {
                    Text("M", fontSize=18.sp, fontWeight=FontWeight.Bold, color=Color.White) }
                Text("Manes", fontSize=22.sp, fontWeight=FontWeight.Bold, color=TxtPri)
            }
            Box(Modifier.size(8.dp).clip(CircleShape)
                .background(if(ManesRelay.active!=null) Green else TxtMut))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=6.dp),
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Servers","Worlds","Modules").forEachIndexed { i, lbl ->
                val on = tab==i
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                background(if(on) Accent else Surface)
                    .clickable { tab=i; selSrv=null; selWld=null }
                    .padding(vertical=10.dp), contentAlignment=Alignment.Center) {
                    Text(lbl, fontSize=13.sp, fontWeight=FontWeight.Medium,
                        color=if(on) Color.White else TxtMut)
                }
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal=20.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            when (tab) {
                0 -> {
                    item { SLabel("Servers") }
                    items(servers, key={it.id}) { sv ->
                        ECard("🌐",sv.name,"${sv.address}:${sv.port}",sv.id==selSrv,Accent,
                            onClick={ selSrv=if(selSrv==sv.id) null else sv.id },
                            onDelete={ servers=servers.filter{it.id!=sv.id}; onSaveSrv(servers); if(selSrv==sv.id) selSrv=null })
                    }
                    item { ABtn("Add a server") { showAddSrv=true } }
                    item { Spacer(Modifier.height(90.dp)) }
                }
                1 -> {
                    item { SLabel("Worlds") }
                    if(worlds.isEmpty()) item { EMsg("No worlds yet") }
                    items(worlds, key={it.id}) { wl ->
                        ECard("🌲",wl.name,wl.info,wl.id==selWld,Green,
                            onClick={ selWld=if(selWld==wl.id) null else wl.id },
                            onDelete={ worlds=worlds.filter{it.id!=wl.id}; onSaveWld(worlds); if(selWld==wl.id) selWld=null })
                    }
                    item { ABtn("Import a world") { showAddWld=true } }
                    item { Spacer(Modifier.height(90.dp)) }
                }
                2 -> {
                    item { SLabel("Modules") }
                    items(Modules.all, key={it.name}) { mod -> MCard(mod) }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(BG).padding(horizontal=20.dp,vertical=16.dp)) {
            Button(onClick={
                when {
                    tab==0&&curSrv!=null -> { lName=curSrv.name; launching=true; onLaunch(curSrv.address,curSrv.port) }
                    tab==1&&curWld!=null -> { lName=curWld.name; launching=true; onLaunch("127.0.0.1",19132) }
                    tab==2 -> { lName="Minecraft"; launching=true; onLaunch("127.0.0.1",19132) }
                }
            }, modifier=Modifier.fillMaxWidth().height(52.dp), shape=RoundedCornerShape(14.dp),
                enabled=ready,
                colors=ButtonDefaults.buttonColors(
                    containerColor=if(ready) Accent else Surface2,
                    contentColor=if(ready) Color.White else TxtMut,
                    disabledContainerColor=Surface2, disabledContentColor=TxtMut)) {
                Text(when{tab==0&&curSrv!=null->"▶  Launch ${curSrv.name}";tab==1&&curWld!=null->"▶  Open ${curWld.name}";tab==2->"▶  Launch with modules";else->"Select a destination"},
                    fontSize=15.sp, fontWeight=FontWeight.SemiBold)
            }
            Text(when{tab==0&&curSrv!=null->"${curSrv.address}:${curSrv.port}";tab==1&&curWld!=null->"Local world via proxy";tab==2->"Modules active on launch";else->"Tap a server or world to select"},
                fontSize=12.sp, color=TxtMut,
                modifier=Modifier.fillMaxWidth().padding(top=6.dp), textAlign=TextAlign.Center)
        }
    }

    if(launching) LOvr(lName) { launching=false }
    if(showAddSrv) ASrvDlg(onDismiss={showAddSrv=false}, onAdd={ n,a,p ->
        servers=servers+ServerEntry(UUID.randomUUID().toString(),n,a,p); onSaveSrv(servers); showAddSrv=false })
    if(showAddWld) AWldDlg(onDismiss={showAddWld=false}, onAdd={ n ->
        worlds=worlds+WorldEntry(UUID.randomUUID().toString(),n,"Just added"); onSaveWld(worlds); showAddWld=false })
}

// ── Components ────────────────────────────────────────────────────────────────
@Composable
fun ECard(icon:String,title:String,sub:String,selected:Boolean,accent:Color,onClick:()->Unit,onDelete:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(if(selected) accent.copy(alpha=0.1f) else Surface)
        .border(0.5.dp,if(selected) accent else Surface2,RoundedCornerShape(14.dp))
        .clickable(onClick=onClick).padding(14.dp),
        verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha=0.13f)),
            contentAlignment=Alignment.Center) { Text(icon,fontSize=20.sp) }
        Column(Modifier.weight(1f)) {
            Text(title,fontSize=15.sp,fontWeight=FontWeight.Medium,color=TxtPri)
            if(sub.isNotBlank()) Text(sub,fontSize=12.sp,color=TxtMut)
        }
        if(selected) Box(Modifier.size(22.dp).clip(CircleShape).background(accent),contentAlignment=Alignment.Center) {
            Icon(Icons.Default.Check,null,tint=Color.White,modifier=Modifier.size(13.dp)) }
        else IconButton(onClick=onDelete,modifier=Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete,null,tint=TxtMut,modifier=Modifier.size(16.dp)) }
    }
}

@Composable
fun MCard(mod: Module) {
    var on by remember { mutableStateOf(mod.enabled) }
    val cc = when(mod.category) { "Combat"->RedCol; "Visual"->AccentLt; else->Green }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .background(if(on) cc.copy(alpha=0.08f) else Surface)
        .border(0.5.dp,if(on) cc else Surface2,RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(mod.name,fontSize=15.sp,fontWeight=FontWeight.Medium,color=TxtPri)
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(cc.copy(alpha=0.2f))
                    .padding(horizontal=6.dp,vertical=2.dp)) {
                    Text(mod.category,fontSize=10.sp,color=cc,fontWeight=FontWeight.Medium) }
            }
            Text(mod.desc,fontSize=12.sp,color=TxtMut)
        }
        Switch(checked=on,onCheckedChange={v->on=v;mod.enabled=v},
            colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=cc))
    }
}

@Composable
fun ABtn(label:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
        .border(0.5.dp,TxtMut.copy(alpha=0.2f),RoundedCornerShape(14.dp))
        .clickable(onClick=onClick).padding(14.dp),
        verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
            .border(1.5.dp,TxtMut.copy(alpha=0.35f),RoundedCornerShape(10.dp)),
            contentAlignment=Alignment.Center) {
            Icon(Icons.Default.Add,null,tint=TxtMut,modifier=Modifier.size(20.dp)) }
        Text(label,fontSize=14.sp,color=TxtMut)
    }
}

@Composable fun SLabel(t:String) = Text(t.uppercase(),fontSize=11.sp,
    fontWeight=FontWeight.SemiBold,color=TxtMut,letterSpacing=0.8.sp,
    modifier=Modifier.padding(top=8.dp,bottom=4.dp))
@Composable fun EMsg(t:String) = Box(Modifier.fillMaxWidth().padding(24.dp),
    contentAlignment=Alignment.Center) { Text(t,fontSize=13.sp,color=TxtMut) }

@Composable
fun LOvr(name:String,onCancel:()->Unit) {
    var step by remember { mutableStateOf(0) }
    val steps = listOf("Starting relay…","Binding 19132…","Connecting to $name…",
        "Handshaking…","Loading…","Launching Minecraft…")
    LaunchedEffect(Unit) { for(i in steps.indices) { delay(700L); step=i } }
    Box(Modifier.fillMaxSize().background(BG.copy(alpha=0.97f)),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(32.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Accent),
                contentAlignment=Alignment.Center) {
                Text("M",fontSize=36.sp,fontWeight=FontWeight.Bold,color=Color.White) }
            Spacer(Modifier.height(24.dp))
            Text(name,fontSize=22.sp,fontWeight=FontWeight.Bold,color=TxtPri)
            Text("Connecting…",fontSize=14.sp,color=TxtMut,modifier=Modifier.padding(top=4.dp,bottom=28.dp))
            CircularProgressIndicator(color=Accent,modifier=Modifier.size(32.dp),strokeWidth=2.5.dp)
            Spacer(Modifier.height(12.dp))
            Text(steps.getOrElse(step){"Done"},fontSize=13.sp,color=TxtMut)
            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick=onCancel,colors=ButtonDefaults.outlinedButtonColors(contentColor=TxtMut)) {
                Icon(Icons.Default.Close,null,modifier=Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp)); Text("Cancel",fontSize=13.sp) }
        }
    }
}

@Composable
fun ASrvDlg(onDismiss:()->Unit,onAdd:(String,String,Int)->Unit) {
    var n by remember { mutableStateOf("") }; var a by remember { mutableStateOf("") }; var p by remember { mutableStateOf("19132") }
    AlertDialog(onDismissRequest=onDismiss,containerColor=Surface,title={Text("Add server",color=TxtPri)},
        text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){MF("Name",n){n=it};MF("Address",a){a=it};MF("Port",p,KeyboardType.Number){p=it}}},
        confirmButton={Button(onClick={if(n.isNotBlank()&&a.isNotBlank())onAdd(n.trim(),a.trim(),p.toIntOrNull()?:19132)},colors=ButtonDefaults.buttonColors(containerColor=Accent)){Text("Add")}},
        dismissButton={TextButton(onDismiss){Text("Cancel",color=TxtMut)}})
}

@Composable
fun AWldDlg(onDismiss:()->Unit,onAdd:(String)->Unit) {
    var n by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,containerColor=Surface,title={Text("Import world",color=TxtPri)},
        text={MF("World name",n){n=it}},
        confirmButton={Button(onClick={if(n.isNotBlank())onAdd(n.trim())},colors=ButtonDefaults.buttonColors(containerColor=Accent)){Text("Import")}},
        dismissButton={TextButton(onDismiss){Text("Cancel",color=TxtMut)}})
}

@Composable
fun MF(label:String,value:String,kb:KeyboardType=KeyboardType.Text,onChange:(String)->Unit) =
    OutlinedTextField(value,onChange,label={Text(label,fontSize=12.sp)},
        modifier=Modifier.fillMaxWidth(),singleLine=true,
        keyboardOptions=KeyboardOptions(keyboardType=kb),
        colors=OutlinedTextFieldDefaults.colors(
            focusedTextColor=TxtPri,unfocusedTextColor=TxtPri,
            focusedBorderColor=Accent,unfocusedBorderColor=Surface2,
            focusedLabelColor=Accent,unfocusedLabelColor=TxtMut,cursorColor=Accent))
    
