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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.Canvas
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
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
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockServerInitializer
import org.cloudburstmc.protocol.bedrock.packet.*
import org.cloudburstmc.protocol.common.PacketSignal
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.UUID
import android.provider.Settings

// Colors — Lumina style dark warm palette
private val BG    = Color(0xFF1A1A1E)
private val Surf  = Color(0xFF232328)
private val Surf2 = Color(0xFF2E2E35)
private val TxtP  = Color(0xFFE8E8EC)
private val TxtM  = Color(0xFF8A8A95)
private val Grn   = Color(0xFF4ADE80)
private val RedC  = Color(0xFFF87171)
private val Ylw   = Color(0xFFFBBF24)
private val Cyn   = Color(0xFF22D3EE)
// Lumina's signature cream/beige start button color
private val StartBtn = Color(0xFFF5ECD7)
private val StartBtnTxt = Color(0xFF1A1A1E)
fun Color.a(v: Float) = Color(red, green, blue, v)

// Theme system
object ThemeManager {
    val themes = listOf(
        "Blue"   to Color(0xFF2563EB),
        "Cyan"   to Color(0xFF0891B2),
        "Purple" to Color(0xFF7C3AED),
        "Green"  to Color(0xFF059669),
        "Rose"   to Color(0xFFE11D48),
        "Orange" to Color(0xFFEA580C)
    )
    var currentIndex by mutableStateOf(0)
    val accent get() = themes[currentIndex].second
    val accentLight get() = themes[currentIndex].second.copy(alpha = 0.7f)
}
private val Acc get() = ThemeManager.accent
private val AccLt get() = ThemeManager.accentLight

// Data
data class ServerEntry(val id: String, val name: String, val address: String, val port: Int = 19132)
data class WorldEntry(val id: String, val name: String, val info: String = "")
data class RealmEntry(val id: String, val name: String, val code: String)

// Store
object Store {
    private const val P = "manes"
    fun saveStr(ctx: Context, k: String, v: String) = ctx.getSharedPreferences(P,0).edit().putString(k,v).apply()
    fun loadStr(ctx: Context, k: String, d: String="") = ctx.getSharedPreferences(P,0).getString(k,d)?:d
    fun saveServers(ctx: Context, list: List<ServerEntry>) { val a=JSONArray(); for(s in list)a.put(JSONObject().apply{put("id",s.id);put("n",s.name);put("a",s.address);put("p",s.port)}); saveStr(ctx,"sv",a.toString()) }
    fun loadServers(ctx: Context): MutableList<ServerEntry> = try { val a=JSONArray(loadStr(ctx,"sv","[]")); (0 until a.length()).map{i->a.getJSONObject(i).let{ServerEntry(it.getString("id"),it.getString("n"),it.getString("a"),it.optInt("p",19132))}}.toMutableList() } catch(e:Exception){mutableListOf()}
    fun saveWorlds(ctx: Context, list: List<WorldEntry>) { val a=JSONArray(); for(w in list)a.put(JSONObject().apply{put("id",w.id);put("n",w.name);put("i",w.info)}); saveStr(ctx,"wd",a.toString()) }
    fun loadWorlds(ctx: Context): MutableList<WorldEntry> = try { val a=JSONArray(loadStr(ctx,"wd","[]")); (0 until a.length()).map{i->a.getJSONObject(i).let{WorldEntry(it.getString("id"),it.getString("n"),it.optString("i",""))}}.toMutableList() } catch(e:Exception){mutableListOf()}
    fun saveRealms(ctx: Context, list: List<RealmEntry>) { val a=JSONArray(); for(r in list)a.put(JSONObject().apply{put("id",r.id);put("n",r.name);put("c",r.code)}); saveStr(ctx,"rl",a.toString()) }
    fun loadRealms(ctx: Context): MutableList<RealmEntry> = try { val a=JSONArray(loadStr(ctx,"rl","[]")); (0 until a.length()).map{i->a.getJSONObject(i).let{RealmEntry(it.getString("id"),it.getString("n"),it.getString("c"))}}.toMutableList() } catch(e:Exception){mutableListOf()}
}

// Module base
abstract class Module(val name: String, val desc: String, val category: String) {
    var enabled = false
    open fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
    open fun onServerBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// XRay
class XRayModule : Module("XRay","See ores through walls","World") {
    private val keep = setOf("minecraft:coal_ore","minecraft:iron_ore","minecraft:gold_ore","minecraft:diamond_ore","minecraft:emerald_ore","minecraft:lapis_ore","minecraft:redstone_ore","minecraft:copper_ore","minecraft:deepslate_coal_ore","minecraft:deepslate_iron_ore","minecraft:deepslate_gold_ore","minecraft:deepslate_diamond_ore","minecraft:deepslate_emerald_ore","minecraft:deepslate_lapis_ore","minecraft:deepslate_redstone_ore","minecraft:deepslate_copper_ore","minecraft:nether_gold_ore","minecraft:ancient_debris","minecraft:quartz_ore","minecraft:air","minecraft:water","minecraft:flowing_water","minecraft:lava","minecraft:flowing_lava","minecraft:bedrock")
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if (!enabled || pkt !is LevelChunkPacket) return false
        return try {
            val srv = ses.serverSession ?: return false
            val defs = srv.javaClass.getMethod("getBlockDefinitions").invoke(srv) ?: return false
            val n2i = HashMap<String,Int>()
            defs.javaClass.methods.firstOrNull{it.name=="forEachEntry"}?.invoke(defs, java.util.function.BiConsumer<Any,Int>{k,id->try{n2i[k.javaClass.getMethod("getName").invoke(k) as String]=id}catch(_:Exception){}})
            val allowed=HashSet<Int>(); for(n in keep){n2i[n]?.let{allowed.add(it)}}
            val air=n2i["minecraft:air"]?:0
            val result=rewrite(pkt,allowed,air)?:return false; ses.sendToClient(result); true
        } catch(_:Exception){false}
    }
    private fun rewrite(orig: LevelChunkPacket, allowed: Set<Int>, air: Int): LevelChunkPacket? {
        val rawData=orig.data?:return null
        val buf:ByteBuf=if(rawData is ByteBuf)rawData.duplicate() else Unpooled.wrappedBuffer(rawData as ByteArray)
        val out=ByteArrayOutputStream(); var changed=false
        for(s in 0 until orig.subChunksLength) {
            val ver=buf.readUnsignedByte().toInt(); val layers=if(ver==8||ver==9)buf.readUnsignedByte().toInt() else 1
            out.write(ver); if(ver==8||ver==9)out.write(layers)
            for(layer in 0 until layers) {
                val bf=buf.readUnsignedByte().toInt(); val bpb=bf ushr 1; val bpw=if(bpb>0)32/bpb else 4096; val wc=if(bpb>0)(4096+bpw-1)/bpw else 0
                val words=IntArray(wc){buf.readIntLE()}; val palSz=buf.readIntLE(); val pal=IntArray(palSz){buf.readIntLE()}
                val np=if(layer==0&&bpb>0)IntArray(pal.size){i->if(pal[i] in allowed)pal[i] else air}.also{if(!it.contentEquals(pal))changed=true} else pal
                out.write(bf); for(w in words){out.write(w and 0xFF);out.write((w ushr 8) and 0xFF);out.write((w ushr 16) and 0xFF);out.write((w ushr 24) and 0xFF)}
                for(v in intArrayOf(np.size)+np){out.write(v and 0xFF);out.write((v ushr 8) and 0xFF);out.write((v ushr 16) and 0xFF);out.write((v ushr 24) and 0xFF)}
            }
        }
        if(!changed)return null; val rest=ByteArray(buf.readableBytes()); buf.readBytes(rest); out.write(rest)
        return LevelChunkPacket().also{it.chunkX=orig.chunkX;it.chunkZ=orig.chunkZ;it.subChunksLength=orig.subChunksLength;it.isCachingEnabled=orig.isCachingEnabled;it.data=Unpooled.wrappedBuffer(out.toByteArray())}
    }
}

// Fullbright
class FullbrightModule : Module("Fullbright","Remove all fog and darkness","Visual") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if(!enabled||pkt !is PlayerFogPacket)return false; pkt.fogStack.clear(); return false
    }
}

// ESP
class ESPModule : Module("ESP","See entities through walls","Visual") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if(!enabled)return false
        try {
            val meta=when(pkt){is AddPlayerPacket->pkt.metadata;is AddEntityPacket->pkt.metadata;else->return false}
            val flags=meta.javaClass.methods.firstOrNull{it.name=="getFlags"}?.invoke(meta)?:return false
            val sf=flags.javaClass.methods.firstOrNull{it.name=="setFlag"&&it.parameterCount==2}
            val gl=sf?.parameterTypes?.firstOrNull()?.enumConstants?.firstOrNull{it.toString().contains("GLOW",true)}
            if(sf!=null&&gl!=null)sf.invoke(flags,gl,true)
        }catch(_:Exception){}
        return false
    }
}

// Hitbox
class HitboxModule : Module("Hitbox","Expand enemy hitboxes","Combat") {
    var scale=1.8f
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if(!enabled)return false
        try {
            val meta=when(pkt){is AddPlayerPacket->pkt.metadata;is AddEntityPacket->pkt.metadata;else->return false}
            val put=meta.javaClass.methods.firstOrNull{it.name=="put"&&it.parameterCount==2}
            val kc=put?.parameterTypes?.firstOrNull()
            if(kc!=null&&kc.isEnum){
                val w=kc.enumConstants?.firstOrNull{it.toString().contains("WIDTH",true)&&it.toString().contains("BOUNDING",true)}
                val h=kc.enumConstants?.firstOrNull{it.toString().contains("HEIGHT",true)&&it.toString().contains("BOUNDING",true)}
                if(w!=null)put.invoke(meta,w,scale); if(h!=null)put.invoke(meta,h,scale)
            }
        }catch(_:Exception){}
        return false
    }
}

// AntiKnockback
class AntiKnockbackModule : Module("AntiKnockback","Cancel all knockback","Combat") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if(!enabled)return false
        try {
            val name=pkt.javaClass.simpleName
            if(name.contains("Velocity",true)||name.contains("Motion",true)) {
                listOf("setMotionX","setMotionY","setMotionZ","setVelocityX","setVelocityY","setVelocityZ").forEach{m->try{pkt.javaClass.getMethod(m,Float::class.java).invoke(pkt,0f)}catch(_:Exception){}}
                listOf("motionX","motionY","motionZ").forEach{f->try{val fd=pkt.javaClass.getDeclaredField(f);fd.isAccessible=true;fd.set(pkt,0f)}catch(_:Exception){}}
            }
        }catch(_:Exception){}
        return false
    }
}

// NoFall
class NoFallModule : Module("NoFall","Cancel fall damage","Combat") {
    override fun onServerBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if(!enabled)return false
        try {
            val name=pkt.javaClass.simpleName
            if(name.contains("MovePlayer",true)||name.contains("PlayerAuthInput",true)) {
                try{val f=pkt.javaClass.getDeclaredField("onGround");f.isAccessible=true;f.set(pkt,true)}catch(_:Exception){}
                try{pkt.javaClass.getMethod("setOnGround",Boolean::class.java).invoke(pkt,true)}catch(_:Exception){}
            }
        }catch(_:Exception){}
        return false
    }
}

// AutoSprint
class AutoSprintModule : Module("AutoSprint","Always sprint","Motion") {
    override fun onServerBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// AntiAFK
class AntiAFKModule : Module("AntiAFK","Prevent AFK kick","Misc") {
    private var tick=0
    override fun onServerBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// NoSlow
class NoSlowModule : Module("NoSlow","No slowdown from items","Motion") {
    override fun onServerBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// AntiVoid
class AntiVoidModule : Module("AntiVoid","Stop falling into void","World") {
    override fun onServerBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if(!enabled)return false
        try {
            val name=pkt.javaClass.simpleName
            if(name.contains("MovePlayer",true)||name.contains("PlayerAuthInput",true)) {
                try{val f=pkt.javaClass.getDeclaredField("y");f.isAccessible=true;val y=f.getFloat(pkt);if(y<-64f)f.set(pkt,-60f)}catch(_:Exception){}
            }
        }catch(_:Exception){}
        return false
    }
}

// FastPlace
class FastPlaceModule : Module("FastPlace","Place blocks faster","World") {
    override fun onServerBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// KillAura (visual only - shows in module list)
class KillAuraModule : Module("KillAura","Auto attack nearby entities","Combat") {
    var range = 3.5f
    var delayMs = 150
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// Reach
class ReachModule : Module("Reach","Extended attack reach","Combat") {
    var reach = 4.5f
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// NameTags
class NameTagsModule : Module("NameTags","Always show player names","Visual") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if(!enabled)return false
        try {
            if(pkt is AddPlayerPacket) {
                val meta=pkt.metadata
                try{val put=meta.javaClass.methods.firstOrNull{it.name=="put"&&it.parameterCount==2}
                    val kc=put?.parameterTypes?.firstOrNull()
                    if(kc!=null&&kc.isEnum){val nv=kc.enumConstants?.firstOrNull{it.toString().contains("NAMETAG_ALWAYS_SHOW",true)||it.toString().contains("ALWAYS_SHOW_NAMETAG",true)}
                        if(nv!=null)put.invoke(meta,nv,1.toByte())}}catch(_:Exception){}
            }
        }catch(_:Exception){}
        return false
    }
}

// Tracers (visual only)
class TracersModule : Module("Tracers","Draw lines to entities","Visual") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// Timer
class TimerModule : Module("Timer","Speed up game tick","Misc") {
    var speed = 1.0f
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// AutoClicker
class AutoClickerModule : Module("AutoClicker","Auto click on entities","Combat") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// ChestStealer
class ChestStealerModule : Module("ChestStealer","Auto loot chests","Misc") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

// Module registry
object Modules {
    val all: List<Module> = listOf(
        XRayModule(), FullbrightModule(), ESPModule(), HitboxModule(),
        AntiKnockbackModule(), NoFallModule(), KillAuraModule(), ReachModule(),
        AutoClickerModule(), AutoSprintModule(), NoSlowModule(), AntiAFKModule(),
        FastPlaceModule(), ChestStealerModule(), AntiVoidModule(),
        NameTagsModule(), TracersModule(), TimerModule()
    )
    fun byCategory(): Map<String,List<Module>> = all.groupBy { it.category }
}

// Relay session
class RelaySession {
    var serverSession: BedrockServerSession? = null
    var clientSession: BedrockClientSession? = null
        set(value) { field=value; if(value==null)return; serverSession?.let{value.codec=it.codec}; var p=queue.poll(); while(p!=null){value.sendPacket(p);p=queue.poll()} }
    private val queue=PlatformDependent.newMpscQueue<BedrockPacket>()
    fun sendToClient(p: BedrockPacket) { serverSession?.sendPacket(p) }
    fun sendToServer(p: BedrockPacket) { val c=clientSession; if(c!=null)c.sendPacket(p) else queue.offer(p) }
    fun handleFromServer(pkt: BedrockPacket) {
        if (pkt is NetworkSettingsPacket) {
            serverSession?.let { srv ->
                val codec = srv.codec
                clientSession?.codec = codec
                serverSession?.codec = codec
            }
        }
        for(m in Modules.all){try{if(m.onClientBound(pkt,this))return}catch(_:Exception){}}; sendToClient(pkt)
    }
    fun handleFromClient(pkt: BedrockPacket) { for(m in Modules.all){try{if(m.onServerBound(pkt,this))return}catch(_:Exception){}}; sendToServer(pkt) }
    fun disconnect() { try{clientSession?.disconnect()}catch(_:Exception){} }
}

// Relay server
object ManesRelay {
    @Volatile var active: RelaySession? = null
    var currentTarget = "Manes"

    fun start(remoteIp: String, remotePort: Int, displayName: String) {
        stop(); currentTarget=displayName
        val ses=RelaySession().also{active=it}
        val group=NioEventLoopGroup()
        val pong=BedrockPong().edition("MCPE").motd("Manes >> $displayName").subMotd("Join to connect").playerCount(0).maximumPlayerCount(1).gameType("Survival").protocolVersion(924).version("1.26.3")
        val pongBuf:ByteBuf=try{pong.toByteBuf() as ByteBuf}catch(_:Exception){Unpooled.wrappedBuffer(pong.toByteBuf() as ByteArray)}
        ServerBootstrap()
            .channelFactory(RakChannelFactory.server(NioDatagramChannel::class.java))
            .group(group)
            .option(RakChannelOption.RAK_ADVERTISEMENT,pongBuf)
            .childHandler(object:BedrockServerInitializer(){
                override fun initSession(srv: BedrockServerSession) {
                    ses.serverSession=srv
                    srv.packetHandler=object:BedrockPacketHandler{override fun handlePacket(pkt:BedrockPacket):PacketSignal{ses.handleFromClient(pkt);return PacketSignal.HANDLED}}
                    Bootstrap()
                        .channelFactory(RakChannelFactory.client(NioDatagramChannel::class.java))
                        .group(group)
                        .handler(object:BedrockClientInitializer(){
                            override fun initSession(cli: BedrockClientSession) {
                                ses.clientSession=cli
                                cli.packetHandler=object:BedrockPacketHandler{override fun handlePacket(pkt:BedrockPacket):PacketSignal{ses.handleFromServer(pkt);return PacketSignal.HANDLED}}
                            }
                        }).connect(InetSocketAddress(remoteIp,remotePort))
                }
            }).bind(InetSocketAddress("0.0.0.0",19132)).syncUninterruptibly()
    }
    fun stop() { active?.disconnect(); active=null }
}

// MainActivity
class MainActivity : ComponentActivity() {
    private val defServers=listOf(ServerEntry("hive","The Hive","geo.hivebedrock.network",19132),ServerEntry("cube","CubeCraft","mco.cubecraft.net",19132),ServerEntry("lbsg","Lifeboat","play.lbsg.net",19132),ServerEntry("mnpl","Mineplex","pe.mineplex.com",19132),ServerEntry("neth","NetherGames","play.nethergames.org",19132))
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        // Ask for overlay permission on first launch
        if (!OverlayService.hasPermission(this)) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Overlay Permission Needed")
                .setMessage("Manes needs \"Display over other apps\" permission to show the module button inside Minecraft.")
                .setPositiveButton("Allow") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Skip") { d, _ -> d.dismiss() }
                .create().show()
        }
        val sv=Store.loadServers(this).ifEmpty{defServers.toMutableList()}
        val wv=Store.loadWorlds(this)
        val rv=Store.loadRealms(this)
        val loggedIn=Store.loadStr(this,"gamertag").isNotBlank()
        setContent { AppTheme {
            if(!loggedIn) LoginScreen{gamertag->Store.saveStr(this,"gamertag",gamertag);recreate()}
            else ManesApp(sv,wv,rv,Store.loadStr(this,"gamertag"),{Store.saveServers(this,it)},{Store.saveWorlds(this,it)},{Store.saveRealms(this,it)},{addr,port,name->doLaunch(addr,port,name)},{Store.saveStr(this,"gamertag","");recreate()})
        }}
    }
    override fun onDestroy() { super.onDestroy(); ManesRelay.stop(); OverlayService.stop(this) }
    private fun doLaunch(addr: String, port: Int, name: String) {
        Thread{try{ManesRelay.start(addr,port,name)}catch(e:Exception){e.printStackTrace()}}.apply{isDaemon=true}.start()
        if (OverlayService.hasPermission(this)) {
            OverlayService.start(this)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("minecraft://?addExternalServer=Manes+Proxy+${Uri.encode(name)}%7C127.0.0.1:19132")))
            } catch(_:Exception) {
                // fallback: open Minecraft directly
                try {
                    val mc = packageManager.getLaunchIntentForPackage("com.mojang.minecraftpe")
                    if (mc != null) startActivity(mc)
                } catch(_:Exception) {}
            }
            android.widget.Toast.makeText(this, "Manes proxy started! In Minecraft: Servers tab, join 'Manes Proxy $name'", android.widget.Toast.LENGTH_LONG).show()
        }, 1200)
    }
}

// Theme
@Composable fun AppTheme(c: @Composable () -> Unit) {
    val acc = ThemeManager.accent
    MaterialTheme(colorScheme=darkColorScheme(background=BG,surface=Surf,primary=acc,onPrimary=Color.White,onBackground=TxtP,onSurface=TxtP),content=c)
}

// Login screen — exact Lumina splash style
@Composable fun LoginScreen(onLogin: (String)->Unit) {
    var gamertag by remember{mutableStateOf("")}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}
    var showForm by remember{mutableStateOf(false)}

    Box(Modifier.fillMaxSize().background(Color(0xFF12121A))) {
        // Wave lines background like Lumina
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val waveColor = Color(0xFF2A3A2A).copy(alpha = 0.6f)
            val pts = listOf(0.42f, 0.47f, 0.52f, 0.57f)
            pts.forEachIndexed { i, frac ->
                val y = h * frac
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, y)
                    cubicTo(w*0.25f, y-30f+(i*8f), w*0.5f, y+20f-(i*5f), w*0.75f, y-15f+(i*6f))
                    cubicTo(w*0.85f, y+10f, w*0.95f, y-5f, w, y+8f)
                }
                drawPath(path, waveColor.copy(alpha = 0.4f - i*0.08f), style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth = 1.2f))
            }
        }

        if (!showForm) {
            // Lumina-style mode select — centered vertically in lower half
            Column(
                Modifier.fillMaxSize().padding(horizontal=24.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.Center
            ) {
                Text("PROJECT MANES", fontSize=18.sp, fontWeight=FontWeight.Bold, color=TxtP, letterSpacing=3.sp)
                Text("Select Mode", fontSize=12.sp, color=TxtM, modifier=Modifier.padding(top=4.dp, bottom=32.dp))

                Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    // Client Mode — active card (Lumina style: dark bg, visible border)
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF222228))
                        .border(1.dp, Color(0xFF3A3A45), RoundedCornerShape(14.dp))
                        .clickable{showForm=true}.padding(18.dp)) {
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.GridView, null, tint=TxtM, modifier=Modifier.size(22.dp))
                            Column {
                                Text("Client Mode", fontSize=14.sp, fontWeight=FontWeight.Medium, color=TxtP)
                                Text("Manes For Mobile", fontSize=11.sp, color=TxtM)
                            }
                        }
                    }
                    // Remote Link — inactive
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF222228))
                        .border(1.dp, Color(0xFF3A3A45), RoundedCornerShape(14.dp))
                        .padding(18.dp)) {
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Link, null, tint=TxtM, modifier=Modifier.size(22.dp))
                            Column {
                                Text("Remote Link", fontSize=14.sp, fontWeight=FontWeight.Medium, color=TxtM)
                                Text("Connect to external systems", fontSize=11.sp, color=TxtM.a(0.5f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("© Project Manes 2025", fontSize=10.sp, color=TxtM.a(0.4f))
            }
        } else {
            // Login form
            Column(Modifier.fillMaxSize().padding(horizontal=28.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                Text("Sign In", fontSize=24.sp, fontWeight=FontWeight.SemiBold, color=TxtP)
                Text("Enter your Minecraft gamertag", fontSize=13.sp, color=TxtM, modifier=Modifier.padding(top=6.dp, bottom=28.dp))
                OutlinedTextField(gamertag,{gamertag=it},label={Text("Gamertag",fontSize=12.sp)},
                    modifier=Modifier.fillMaxWidth(),singleLine=true,
                    colors=OutlinedTextFieldDefaults.colors(focusedTextColor=TxtP,unfocusedTextColor=TxtP,focusedBorderColor=TxtM,unfocusedBorderColor=Surf2,focusedLabelColor=TxtM,unfocusedLabelColor=TxtM.a(0.6f),cursorColor=TxtP))
                if(error.isNotBlank()){Spacer(Modifier.height(8.dp));Text(error,fontSize=12.sp,color=RedC)}
                Spacer(Modifier.height(16.dp))
                Button(onClick={if(gamertag.isBlank())error="Enter your gamertag" else{loading=true;onLogin(gamertag.trim())}},
                    modifier=Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(10.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=StartBtn)){
                    if(loading)CircularProgressIndicator(color=StartBtnTxt,modifier=Modifier.size(18.dp),strokeWidth=2.dp)
                    else Text("Continue",fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=StartBtnTxt)
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick={showForm=false},modifier=Modifier.fillMaxWidth()){Text("← Back",color=TxtM,fontSize=13.sp)}
            }
        }
    }
}

// Main app — exact Lumina UI layout
@Composable
fun ManesApp(initSrv:List<ServerEntry>,initWld:List<WorldEntry>,initRlm:List<RealmEntry>,gamertag:String,saveSrv:(List<ServerEntry>)->Unit,saveWld:(List<WorldEntry>)->Unit,saveRlm:(List<RealmEntry>)->Unit,onLaunch:(String,Int,String)->Unit,onLogout:()->Unit) {
    var navPage by remember{mutableStateOf("Home")} // Home, About, Realms, Settings
    var tab by remember{mutableStateOf(0)} // 0=Servers,1=Accounts,2=Packs,3=Realms
    var srvs by remember{mutableStateOf(initSrv)}
    var wlds by remember{mutableStateOf(initWld)}
    var rlms by remember{mutableStateOf(initRlm)}
    var selS by remember{mutableStateOf<String?>(null)}
    var selR by remember{mutableStateOf<String?>(null)}
    var shAS by remember{mutableStateOf(false)}
    var shAR by remember{mutableStateOf(false)}
    var launch by remember{mutableStateOf(false)}
    var lName by remember{mutableStateOf("")}
    var showMods by remember{mutableStateOf(false)}

    // Draggable button
    var dragX by remember{mutableStateOf(20f)}
    var dragY by remember{mutableStateOf(400f)}

    val cs = srvs.firstOrNull{it.id==selS}
    val cr = rlms.firstOrNull{it.id==selR}

    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.fillMaxSize()) {

            // TOP NAV — "Lumina | Home About Realms Settings" exactly like Lumina
            Row(
                Modifier.fillMaxWidth().background(Surf).padding(horizontal=16.dp, top=44.dp, bottom=0.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween
            ) {
                Text("Manes", fontSize=18.sp, fontWeight=FontWeight.SemiBold, color=TxtP)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp), verticalAlignment=Alignment.Bottom) {
                    listOf("Home","About","Realms","Settings").forEach { page ->
                        val active = navPage == page
                        Column(
                            Modifier.clickable{navPage=page}.padding(horizontal=10.dp, vertical=10.dp),
                            horizontalAlignment=Alignment.CenterHorizontally
                        ) {
                            Text(page, fontSize=12.sp, color=if(active) TxtP else TxtM,
                                fontWeight=if(active) FontWeight.Medium else FontWeight.Normal)
                            if(active) Box(Modifier.padding(top=4.dp).width(16.dp).height(1.5.dp).background(TxtP))
                            else Spacer(Modifier.height(5.5.dp))
                        }
                    }
                }
            }

            // PAGE CONTENT
            when(navPage) {
                "Home" -> {
                    // Two-column Lumina layout
                    Row(Modifier.weight(1f)) {
                        // LEFT COLUMN — pill tabs + list
                        Column(Modifier.weight(0.48f).fillMaxHeight().background(Surf)) {
                            // Pill tabs row
                            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                listOf("Servers","Accounts","Packs","Realms").forEachIndexed { i, l ->
                                    val on = tab == i
                                    Box(
                                        Modifier.wrapContentWidth().clip(RoundedCornerShape(20.dp))
                                            .background(if(on) Surf2 else Color.Transparent)
                                            .border(1.dp, if(on) Color(0xFF4A4A55) else Color(0xFF333338), RoundedCornerShape(20.dp))
                                            .clickable{tab=i; selS=null; selR=null}
                                            .padding(horizontal=10.dp, vertical=6.dp),
                                        contentAlignment=Alignment.Center
                                    ) {
                                        Text(l, fontSize=11.sp, color=if(on) TxtP else TxtM)
                                    }
                                }
                            }

                            // List content
                            LazyColumn(Modifier.weight(1f)) {
                                when(tab) {
                                    0 -> {
                                        items(srvs, key={it.id}) { sv ->
                                            LuminaRow(sv.name, sv.id==selS) {
                                                selS = if(selS==sv.id) null else sv.id
                                            }
                                        }
                                        item {
                                            Row(Modifier.fillMaxWidth().clickable{shAS=true}.padding(horizontal=12.dp, vertical=10.dp),
                                                verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                                Icon(Icons.Default.Add, null, tint=TxtM, modifier=Modifier.size(14.dp))
                                                Text("Add server", fontSize=12.sp, color=TxtM)
                                            }
                                        }
                                    }
                                    1 -> { item { EM("Accounts coming soon") } }
                                    2 -> { item { EM("Packs coming soon") } }
                                    3 -> {
                                        items(rlms, key={it.id}) { rl ->
                                            LuminaRow(rl.name, rl.id==selR) {
                                                selR = if(selR==rl.id) null else rl.id
                                            }
                                        }
                                        item {
                                            Row(Modifier.fillMaxWidth().clickable{shAR=true}.padding(horizontal=12.dp, vertical=10.dp),
                                                verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(6.dp)){
                                                Icon(Icons.Default.Add, null, tint=TxtM, modifier=Modifier.size(14.dp))
                                                Text("Add realm", fontSize=12.sp, color=TxtM)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // DIVIDER
                        Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF2E2E38)))

                        // RIGHT COLUMN — selected info + Start button
                        Column(Modifier.weight(0.52f).fillMaxHeight().background(BG).padding(14.dp)) {
                            // Hello greeting like Lumina
                            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Person, null, tint=TxtM, modifier=Modifier.size(20.dp))
                                Text("Hello! $gamertag", fontSize=13.sp, color=TxtM)
                            }
                            Spacer(Modifier.height(16.dp))

                            // Selected server info
                            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.PlayArrow, null, tint=TxtM, modifier=Modifier.size(14.dp))
                                Text("Selected Server", fontSize=12.sp, color=TxtM, fontWeight=FontWeight.Medium)
                            }
                            Spacer(Modifier.height(8.dp))
                            if(tab==0 && cs!=null) {
                                Text(cs.address, fontSize=14.sp, color=TxtP, fontWeight=FontWeight.Medium)
                                Text("Port: ${cs.port}", fontSize=12.sp, color=TxtM, modifier=Modifier.padding(top=2.dp))
                            } else if(tab==3 && cr!=null) {
                                Text(cr.name, fontSize=14.sp, color=TxtP, fontWeight=FontWeight.Medium)
                                Text("Code: ${cr.code}", fontSize=12.sp, color=TxtM, modifier=Modifier.padding(top=2.dp))
                            } else {
                                Text("—", fontSize=14.sp, color=TxtM)
                            }

                            Spacer(Modifier.weight(1f))

                            // Relay status dot
                            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(6.dp),
                                modifier=Modifier.padding(bottom=10.dp)) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(if(ManesRelay.active!=null) Grn else TxtM))
                                Text(if(ManesRelay.active!=null)"Relay Active" else "Idle", fontSize=10.sp, color=TxtM)
                            }

                            // START button — Lumina cream/beige style
                            val rdy = (tab==0&&cs!=null)||(tab==3&&cr!=null)
                            Button(
                                onClick={
                                    when {
                                        tab==0&&cs!=null->{lName=cs.name;launch=true;onLaunch(cs.address,cs.port,cs.name)}
                                        tab==3&&cr!=null->{lName=cr.name;launch=true;onLaunch("127.0.0.1",19132,cr.name)}
                                    }
                                },
                                modifier=Modifier.fillMaxWidth().height(48.dp),
                                shape=RoundedCornerShape(10.dp),
                                enabled=rdy,
                                colors=ButtonDefaults.buttonColors(
                                    containerColor=StartBtn, contentColor=StartBtnTxt,
                                    disabledContainerColor=Surf2, disabledContentColor=TxtM
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier=Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Start", fontSize=14.sp, fontWeight=FontWeight.SemiBold)
                            }
                        }
                    }
                }

                "About" -> {
                    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text("About Manes", fontSize=18.sp, fontWeight=FontWeight.SemiBold, color=TxtP, modifier=Modifier.padding(bottom=12.dp))
                        Text("Manes is a Minecraft Bedrock proxy client for Android.", fontSize=13.sp, color=TxtM, lineHeight=20.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Compatible with Android 9.0+ devices. Uses RakNet relay to inject modules.", fontSize=13.sp, color=TxtM, lineHeight=20.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("© 2025 Project Manes", fontSize=11.sp, color=TxtM.a(0.5f))
                    }
                }

                "Realms" -> {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text("Realms", fontSize=18.sp, fontWeight=FontWeight.SemiBold, color=TxtP, modifier=Modifier.padding(bottom=12.dp))
                        if(rlms.isEmpty()) EM("No realms yet — add an invite code")
                        rlms.forEach { rl ->
                            LuminaRow(rl.name, rl.id==selR) { selR = if(selR==rl.id) null else rl.id }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick={shAR=true}, modifier=Modifier.fillMaxWidth(),
                            colors=ButtonDefaults.outlinedButtonColors(contentColor=TxtM)){
                            Icon(Icons.Default.Add, null, modifier=Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Realm", fontSize=13.sp)
                        }
                    }
                }

                "Settings" -> SettingsScreen(onLogout=onLogout)
            }
        }

        // Draggable module button
        Box(
            Modifier
                .offset{ IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                .size(44.dp)
                .clip(CircleShape)
                .background(Surf2)
                .border(1.dp, Color(0xFF4A4A55), CircleShape)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        dragX = (dragX + dragAmount.x).coerceIn(0f, (size.width - 44.dp.toPx()))
                        dragY = (dragY + dragAmount.y).coerceIn(0f, (size.height - 44.dp.toPx()))
                    }
                }
                .clickable { showMods = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.GridView, null, tint=TxtM, modifier=Modifier.size(20.dp))
        }
    }

    if(showMods) ModuleOverlay{showMods=false}
    if(launch) LO(lName){launch=false}
    if(shAS)ASD({shAS=false}){n,a,p->srvs=srvs+ServerEntry(UUID.randomUUID().toString(),n,a,p);saveSrv(srvs);shAS=false}
    if(shAR)ARD({shAR=false}){n,c->rlms=rlms+RealmEntry(UUID.randomUUID().toString(),n,c);saveRlm(rlms);shAR=false}
}

// Lumina-style simple server row
@Composable fun LuminaRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if(selected) Surf2 else Color.Transparent)
            .clickable(onClick=onClick)
            .padding(horizontal=12.dp, vertical=11.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.SpaceBetween
    ) {
        Text(name, fontSize=13.sp, color=if(selected) TxtP else TxtM)
        if(selected) Icon(Icons.Default.Check, null, tint=TxtP, modifier=Modifier.size(14.dp))
    }
}

// Settings screen
@Composable fun SettingsScreen(onLogout: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            SLbl("Accent Color")
            Spacer(Modifier.height(8.dp))
            // Color grid
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                ThemeManager.themes.chunked(3).forEachIndexed { rowIdx, row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { colIdx, (name, color) ->
                            val idx = rowIdx * 3 + colIdx
                            val selected = ThemeManager.currentIndex == idx
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(color.a(if(selected) 1f else 0.2f))
                                .border(if(selected) 2.dp else 0.dp, Color.White.a(0.8f), RoundedCornerShape(12.dp))
                                .clickable { ThemeManager.currentIndex = idx }
                                .padding(vertical=14.dp),
                                contentAlignment=Alignment.Center) {
                                Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                    if(selected) Icon(Icons.Default.Check, null, tint=Color.White, modifier=Modifier.size(16.dp))
                                    Text(name, fontSize=12.sp, fontWeight=FontWeight.SemiBold,
                                        color=if(selected) Color.White else color)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Divider(color=Surf2) }
        item {
            SLbl("App")
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surf)
                .border(1.dp, Surf2, RoundedCornerShape(14.dp)).padding(16.dp)) {
                Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("Manes Client", fontSize=14.sp, fontWeight=FontWeight.SemiBold, color=TxtP)
                    Text("Minecraft Bedrock Proxy", fontSize=12.sp, color=TxtM)
                    Text("Protocol 924 · v1.0.0", fontSize=11.sp, color=TxtM.a(0.6f))
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// Module overlay — Lumina style: left category tabs + right scrollable module list
@Composable fun ModuleOverlay(onClose: () -> Unit) {
    val categories = listOf("Combat", "Visual", "Motion", "World", "Misc")
    var selCat by remember { mutableStateOf("Combat") }
    val catColor = @Composable { cat: String -> when(cat) { "Combat"->RedC; "Visual"->AccLt; "Motion"->Ylw; "World"->Grn; else->Cyn } }
    val catIcon = { cat: String -> when(cat) { "Combat"->"⚔"; "Visual"->"👁"; "Motion"->"💨"; "World"->"🌍"; else->"⚙" } }

    Box(Modifier.fillMaxSize().background(BG.a(0.97f))) {
        Row(Modifier.fillMaxSize()) {

            // LEFT: vertical category tabs
            Column(
                Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(Surf)
                    .padding(vertical = 60.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                categories.forEach { cat ->
                    val sel = selCat == cat
                    val cc = catColor(cat)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                            .background(if (sel) cc.a(0.15f) else Color.Transparent)
                            .border(
                                width = if (sel) 1.dp else 0.dp,
                                color = if (sel) cc.a(0.5f) else Color.Transparent,
                                shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                            )
                            .clickable { selCat = cat }
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(catIcon(cat), fontSize = 18.sp)
                        Text(
                            cat, fontSize = 9.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) cc else TxtM,
                            textAlign = TextAlign.Center,
                            letterSpacing = 0.3.sp
                        )
                    }
                    // Active indicator bar on left edge
                    if (sel) {
                        Box(Modifier.width(3.dp).height(0.dp)) // spacer placeholder
                    }
                }
            }

            // RIGHT: module list
            Column(Modifier.weight(1f).fillMaxHeight().background(BG)) {
                // Header
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 52.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(selCat, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TxtP)
                        val count = Modules.all.count { it.category == selCat }
                        Text("$count modules", fontSize = 11.sp, color = TxtM)
                    }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Surf)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, null, tint = TxtM, modifier = Modifier.size(18.dp))
                    }
                }

                // Scrollable module list
                val mods = Modules.all.filter { it.category == selCat }
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mods, key = { it.name }) { mod -> MC(mod) }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}

// Reusable composables
@Composable fun EC(icon:String,title:String,sub:String,sel:Boolean,ac:Color,onClick:()->Unit,onDel:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(sel)ac.a(0.1f) else Surf).border(0.5.dp,if(sel)ac else Surf2,RoundedCornerShape(14.dp)).clickable(onClick=onClick).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(ac.a(0.13f)),contentAlignment=Alignment.Center){Text(icon,fontSize=20.sp)}
        Column(Modifier.weight(1f)){Text(title,fontSize=15.sp,fontWeight=FontWeight.Medium,color=TxtP);if(sub.isNotBlank())Text(sub,fontSize=12.sp,color=TxtM)}
        if(sel)Box(Modifier.size(22.dp).clip(CircleShape).background(ac),contentAlignment=Alignment.Center){Icon(Icons.Default.Check,null,tint=Color.White,modifier=Modifier.size(13.dp))}
        else IconButton(onClick=onDel,modifier=Modifier.size(32.dp)){Icon(Icons.Default.Delete,null,tint=TxtM,modifier=Modifier.size(16.dp))}
    }
}

@Composable fun MC(mod:Module) {
    var on by remember{mutableStateOf(mod.enabled)}
    val cc=when(mod.category){"Combat"->RedC;"Visual"->AccLt;"World"->Grn;"Motion"->Ylw;"Misc"->Cyn;else->TxtM}
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if(on)cc.a(0.08f) else Surf).border(0.5.dp,if(on)cc else Surf2,RoundedCornerShape(12.dp)).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Text(mod.name,fontSize=14.sp,fontWeight=FontWeight.Medium,color=TxtP)
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(cc.a(0.2f)).padding(horizontal=5.dp,vertical=2.dp)){Text(mod.category,fontSize=9.sp,color=cc,fontWeight=FontWeight.Medium)}
            }
            Text(mod.desc,fontSize=11.sp,color=TxtM)
        }
        Switch(checked=on,onCheckedChange={v->on=v;mod.enabled=v},colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=cc))
    }
}

@Composable fun AB(label:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(0.5.dp,TxtM.a(0.2f),RoundedCornerShape(14.dp)).clickable(onClick=onClick).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp,TxtM.a(0.35f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Add,null,tint=TxtM,modifier=Modifier.size(20.dp))}
        Text(label,fontSize=14.sp,color=TxtM)
    }
}

@Composable fun SLbl(t:String)=Text(t.uppercase(),fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=TxtM,letterSpacing=0.8.sp,modifier=Modifier.padding(top=8.dp,bottom=4.dp))
@Composable fun EM(t:String)=Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){Text(t,fontSize=13.sp,color=TxtM,textAlign=TextAlign.Center)}

@Composable fun LO(name:String,onCancel:()->Unit) {
    var step by remember{mutableStateOf(0)}
    val steps=listOf("Starting relay\u2026","Binding port 19132\u2026","Connecting to $name\u2026","Handshaking\u2026","Loading world\u2026","Launching Minecraft\u2026")
    LaunchedEffect(Unit){for(i in steps.indices){delay(700L);step=i}}
    Box(Modifier.fillMaxSize().background(BG.a(0.97f)),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(32.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Acc),contentAlignment=Alignment.Center){Text("M",fontSize=36.sp,fontWeight=FontWeight.Bold,color=Color.White)}
            Spacer(Modifier.height(20.dp))
            Text(name,fontSize=20.sp,fontWeight=FontWeight.Bold,color=TxtP)
            Text("Join  Manes \u00BB $name  in Minecraft",fontSize=12.sp,color=TxtM,modifier=Modifier.padding(top=4.dp,bottom=4.dp),textAlign=TextAlign.Center)
            Text("Server will appear in your world list",fontSize=11.sp,color=Acc,modifier=Modifier.padding(bottom=24.dp))
            CircularProgressIndicator(color=Acc,modifier=Modifier.size(32.dp),strokeWidth=2.5.dp)
            Spacer(Modifier.height(12.dp))
            Text(steps.getOrElse(step){"Ready"},fontSize=13.sp,color=TxtM)
            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick=onCancel,colors=ButtonDefaults.outlinedButtonColors(contentColor=TxtM)){Icon(Icons.Default.Close,null,modifier=Modifier.size(14.dp));Spacer(Modifier.width(6.dp));Text("Cancel",fontSize=13.sp)}
        }
    }
}

@Composable fun ASD(onDismiss:()->Unit,onAdd:(String,String,Int)->Unit) {
    var n by remember{mutableStateOf("")};var a by remember{mutableStateOf("")};var p by remember{mutableStateOf("19132")}
    AlertDialog(onDismissRequest=onDismiss,containerColor=Surf,title={Text("Add server",color=TxtP)},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){MF("Server name",n){n=it};MF("Address",a){a=it};MF("Port",p,KeyboardType.Number){p=it}}},confirmButton={Button(onClick={if(n.isNotBlank()&&a.isNotBlank())onAdd(n.trim(),a.trim(),p.toIntOrNull()?:19132)},colors=ButtonDefaults.buttonColors(containerColor=Acc)){Text("Add")}},dismissButton={TextButton(onDismiss){Text("Cancel",color=TxtM)}})
}

@Composable fun AWD(onDismiss:()->Unit,onAdd:(String)->Unit) {
    var n by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,containerColor=Surf,title={Text("Import world",color=TxtP)},text={MF("World name",n){n=it}},confirmButton={Button(onClick={if(n.isNotBlank())onAdd(n.trim())},colors=ButtonDefaults.buttonColors(containerColor=Acc)){Text("Import")}},dismissButton={TextButton(onDismiss){Text("Cancel",color=TxtM)}})
}

@Composable fun ARD(onDismiss:()->Unit,onAdd:(String,String)->Unit) {
    var n by remember{mutableStateOf("")};var c by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,containerColor=Surf,title={Text("Add realm",color=TxtP)},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){MF("Realm name",n){n=it};MF("Invite code",c){c=it}}},confirmButton={Button(onClick={if(n.isNotBlank()&&c.isNotBlank())onAdd(n.trim(),c.trim())},colors=ButtonDefaults.buttonColors(containerColor=Acc)){Text("Add")}},dismissButton={TextButton(onDismiss){Text("Cancel",color=TxtM)}})
}

@Composable fun MF(label:String,value:String,kb:KeyboardType=KeyboardType.Text,onChange:(String)->Unit)=OutlinedTextField(value,onChange,label={Text(label,fontSize=12.sp)},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=kb),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=TxtP,unfocusedTextColor=TxtP,focusedBorderColor=Acc,unfocusedBorderColor=Surf2,focusedLabelColor=Acc,unfocusedLabelColor=TxtM,cursorColor=Acc))
