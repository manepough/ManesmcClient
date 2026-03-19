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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

// Colors
private val BG    = Color(0xFF0D0D0F)
private val Surf  = Color(0xFF18181C)
private val Surf2 = Color(0xFF222228)
private val Acc   = Color(0xFF7C6EF7)
private val AccLt = Color(0xFFA78BFA)
private val TxtP  = Color(0xFFF1F0FB)
private val TxtM  = Color(0xFF8B8A9B)
private val Grn   = Color(0xFF4ADE80)
private val RedC  = Color(0xFFF87171)
private val Ylw   = Color(0xFFFBBF24)
private val Cyn   = Color(0xFF22D3EE)
fun Color.a(v: Float) = Color(red, green, blue, v)

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
        val pong=BedrockPong().edition("MCPE").motd("Manes >> $displayName").subMotd("Join to connect").playerCount(0).maximumPlayerCount(1).gameType("Survival")..protocolVersion(924).version("1.26.3")
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
@Composable fun AppTheme(c: @Composable () -> Unit)=MaterialTheme(colorScheme=darkColorScheme(background=BG,surface=Surf,primary=Acc,onPrimary=Color.White,onBackground=TxtP,onSurface=TxtP),content=c)

// Login screen
@Composable fun LoginScreen(onLogin: (String)->Unit) {
    var gamertag by remember{mutableStateOf("")}
    var password by remember{mutableStateOf("")}
    var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}

    Box(Modifier.fillMaxSize().background(BG),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(32.dp).fillMaxWidth()) {
            Box(Modifier.size(80.dp).clip(RoundedCornerShape(22.dp)).background(Acc),contentAlignment=Alignment.Center){
                Text("M",fontSize=40.sp,fontWeight=FontWeight.Bold,color=Color.White)}
            Spacer(Modifier.height(16.dp))
            Text("Manes",fontSize=28.sp,fontWeight=FontWeight.Bold,color=TxtP)
            Text("Sign in with your Microsoft account",fontSize=13.sp,color=TxtM,modifier=Modifier.padding(top=4.dp,bottom=32.dp))

            OutlinedTextField(gamertag,{gamertag=it},label={Text("Gamertag / Email",fontSize=12.sp)},modifier=Modifier.fillMaxWidth(),singleLine=true,
                colors=OutlinedTextFieldDefaults.colors(focusedTextColor=TxtP,unfocusedTextColor=TxtP,focusedBorderColor=Acc,unfocusedBorderColor=Surf2,focusedLabelColor=Acc,unfocusedLabelColor=TxtM,cursorColor=Acc))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password,{password=it},label={Text("Password",fontSize=12.sp)},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=PasswordVisualTransformation(),
                colors=OutlinedTextFieldDefaults.colors(focusedTextColor=TxtP,unfocusedTextColor=TxtP,focusedBorderColor=Acc,unfocusedBorderColor=Surf2,focusedLabelColor=Acc,unfocusedLabelColor=TxtM,cursorColor=Acc))

            if(error.isNotBlank()){Spacer(Modifier.height(8.dp));Text(error,fontSize=12.sp,color=RedC)}

            Spacer(Modifier.height(20.dp))
            Button(onClick={
                if(gamertag.isBlank()){error="Enter your gamertag"}
                else{loading=true;error="";onLogin(gamertag.trim())}
            },modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Acc)){
                if(loading)CircularProgressIndicator(color=Color.White,modifier=Modifier.size(20.dp),strokeWidth=2.dp)
                else Text("Sign In",fontSize=15.sp,fontWeight=FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            Text("Your credentials are stored locally only",fontSize=11.sp,color=TxtM,textAlign=TextAlign.Center)
        }
    }
}

// Main app
@Composable
fun ManesApp(initSrv:List<ServerEntry>,initWld:List<WorldEntry>,initRlm:List<RealmEntry>,gamertag:String,saveSrv:(List<ServerEntry>)->Unit,saveWld:(List<WorldEntry>)->Unit,saveRlm:(List<RealmEntry>)->Unit,onLaunch:(String,Int,String)->Unit,onLogout:()->Unit) {
    var tab by remember{mutableStateOf(0)}
    var srvs by remember{mutableStateOf(initSrv)}
    var wlds by remember{mutableStateOf(initWld)}
    var rlms by remember{mutableStateOf(initRlm)}
    var selS by remember{mutableStateOf<String?>(null)}
    var selW by remember{mutableStateOf<String?>(null)}
    var selR by remember{mutableStateOf<String?>(null)}
    var shAS by remember{mutableStateOf(false)}
    var shAW by remember{mutableStateOf(false)}
    var shAR by remember{mutableStateOf(false)}
    var launch by remember{mutableStateOf(false)}
    var lName by remember{mutableStateOf("")}
    var showMods by remember{mutableStateOf(false)}
    var showProfile by remember{mutableStateOf(false)}

    val cs=srvs.firstOrNull{it.id==selS}
    val cw=wlds.firstOrNull{it.id==selW}
    val cr=rlms.firstOrNull{it.id==selR}
    val rdy=(tab==0&&cs!=null)||(tab==1&&cw!=null)||(tab==2&&cr!=null)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(BG)) {
            // Header
            Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=52.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Acc),contentAlignment=Alignment.Center){Text("M",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Color.White)}
                    Text("Manes",fontSize=22.sp,fontWeight=FontWeight.Bold,color=TxtP)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if(ManesRelay.active!=null)Grn else TxtM))
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Surf).clickable{showProfile=true}.padding(horizontal=10.dp,vertical=5.dp)){Text(gamertag,fontSize=12.sp,color=TxtP)}
                }
            }

            // Tabs
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("Servers","Worlds","Realms").forEachIndexed{i,l->val on=tab==i
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if(on)Acc else Surf).clickable{tab=i;selS=null;selW=null;selR=null}.padding(vertical=10.dp),contentAlignment=Alignment.Center){
                        Text(l,fontSize=13.sp,fontWeight=FontWeight.Medium,color=if(on)Color.White else TxtM)
                    }
                }
            }

            // Content
            LazyColumn(Modifier.weight(1f).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                when(tab) {
                    0 -> {
                        item{SLbl("Featured Servers")}
                        items(srvs,key={it.id}){sv->EC("\uD83C\uDF10",sv.name,"${sv.address}:${sv.port}",sv.id==selS,Acc,{selS=if(selS==sv.id)null else sv.id},{srvs=srvs.filter{it.id!=sv.id};saveSrv(srvs);if(selS==sv.id)selS=null})}
                        item{AB("Add a server"){shAS=true}}
                        item{Spacer(Modifier.height(120.dp))}
                    }
                    1 -> {
                        item{SLbl("Local Worlds")}
                        if(wlds.isEmpty())item{EM("No worlds yet")}
                        items(wlds,key={it.id}){wl->EC("\uD83C\uDF32",wl.name,wl.info,wl.id==selW,Grn,{selW=if(selW==wl.id)null else wl.id},{wlds=wlds.filter{it.id!=wl.id};saveWld(wlds);if(selW==wl.id)selW=null})}
                        item{AB("Import a world"){shAW=true}}
                        item{Spacer(Modifier.height(120.dp))}
                    }
                    else -> {
                        item{SLbl("Realms")}
                        if(rlms.isEmpty())item{EM("No realms yet — add an invite code")}
                        items(rlms,key={it.id}){rl->EC("\u2601\uFE0F",rl.name,"Code: ${rl.code}",rl.id==selR,Ylw,{selR=if(selR==rl.id)null else rl.id},{rlms=rlms.filter{it.id!=rl.id};saveRlm(rlms);if(selR==rl.id)selR=null})}
                        item{AB("Add a realm"){shAR=true}}
                        item{Spacer(Modifier.height(120.dp))}
                    }
                }
            }

            // Launch bar
            Column(Modifier.fillMaxWidth().background(BG).padding(horizontal=20.dp,vertical=16.dp)) {
                val bl=when{tab==0&&cs!=null->"\u25B6  Launch ${cs.name}";tab==1&&cw!=null->"\u25B6  Open ${cw.name}";tab==2&&cr!=null->"\u25B6  Join ${cr.name}";else->"Select a destination"}
                val sl=when{tab==0&&cs!=null->"World: Manes \u00BB ${cs.name}";tab==1&&cw!=null->"Local world via proxy";tab==2&&cr!=null->"Realm code: ${cr.code}";else->"Tap a server, world, or realm to select"}
                Button(onClick={when{
                    tab==0&&cs!=null->{lName=cs.name;launch=true;onLaunch(cs.address,cs.port,cs.name)}
                    tab==1&&cw!=null->{lName=cw.name;launch=true;onLaunch("127.0.0.1",19132,cw.name)}
                    tab==2&&cr!=null->{lName=cr.name;launch=true;onLaunch("127.0.0.1",19132,cr.name)}
                }},modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp),enabled=rdy,
                    colors=ButtonDefaults.buttonColors(containerColor=if(rdy)Acc else Surf2,contentColor=if(rdy)Color.White else TxtM,disabledContainerColor=Surf2,disabledContentColor=TxtM)){
                    Text(bl,fontSize=15.sp,fontWeight=FontWeight.SemiBold)}
                Text(sl,fontSize=12.sp,color=TxtM,modifier=Modifier.fillMaxWidth().padding(top=6.dp),textAlign=TextAlign.Center)
            }
        }

        // Floating yin-yang module button
        Box(Modifier.align(Alignment.BottomEnd).padding(end=20.dp,bottom=110.dp)) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Surf).border(1.dp,Acc.a(0.6f),CircleShape).clickable{showMods=true},contentAlignment=Alignment.Center){
                Text("\u262F",fontSize=26.sp)
            }
        }
    }

    // Module overlay
    if(showMods) ModuleOverlay{showMods=false}
    // Launch overlay
    if(launch) LO(lName){launch=false}
    // Profile dialog
    if(showProfile) AlertDialog(onDismissRequest={showProfile=false},containerColor=Surf,
        title={Text("Account",color=TxtP)},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Signed in as:",fontSize=12.sp,color=TxtM)
            Text(gamertag,fontSize=16.sp,fontWeight=FontWeight.Medium,color=TxtP)
        }},
        confirmButton={Button(onClick={showProfile=false;onLogout()},colors=ButtonDefaults.buttonColors(containerColor=RedC)){Text("Sign Out")}},
        dismissButton={TextButton({showProfile=false}){Text("Close",color=TxtM)}})

    // Dialogs
    if(shAS)ASD({shAS=false}){n,a,p->srvs=srvs+ServerEntry(UUID.randomUUID().toString(),n,a,p);saveSrv(srvs);shAS=false}
    if(shAW)AWD({shAW=false}){n->wlds=wlds+WorldEntry(UUID.randomUUID().toString(),n,"Just added");saveWld(wlds);shAW=false}
    if(shAR)ARD({shAR=false}){n,c->rlms=rlms+RealmEntry(UUID.randomUUID().toString(),n,c);saveRlm(rlms);shAR=false}
}

// Module overlay
@Composable fun ModuleOverlay(onClose:()->Unit) {
    Box(Modifier.fillMaxSize().background(BG.a(0.96f)).clickable{onClose()}) {
        Column(Modifier.fillMaxSize().clickable(onClick={}).padding(horizontal=20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top=52.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Modules",fontSize=22.sp,fontWeight=FontWeight.Bold,color=TxtP)
                Box(Modifier.size(36.dp).clip(CircleShape).background(Surf).clickable{onClose()},contentAlignment=Alignment.Center){Icon(Icons.Default.Close,null,tint=TxtM,modifier=Modifier.size(18.dp))}
            }
            val cats=Modules.byCategory()
            LazyColumn(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                cats.forEach{(cat,mods)->
                    item{
                        val catCol=when(cat){"Combat"->RedC;"Visual"->AccLt;"World"->Grn;"Motion"->Ylw;"Misc"->Cyn;else->TxtM}
                        Text(cat.uppercase(),fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=catCol,letterSpacing=1.sp,modifier=Modifier.padding(top=12.dp,bottom=4.dp))
                    }
                    items(mods,key={it.name}){mod->MC(mod)}
                }
                item{Spacer(Modifier.height(40.dp))}
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
