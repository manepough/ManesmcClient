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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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

private val BG      = Color(0xFF0D0D0F)
private val Surf    = Color(0xFF18181C)
private val Surf2   = Color(0xFF222228)
private val Acc     = Color(0xFF7C6EF7)
private val AccLt   = Color(0xFFA78BFA)
private val TxtP    = Color(0xFFF1F0FB)
private val TxtM    = Color(0xFF8B8A9B)
private val Grn     = Color(0xFF4ADE80)
private val RedC    = Color(0xFFF87171)

fun Color.a(v: Float) = Color(red, green, blue, v)

data class ServerEntry(val id: String, val name: String, val address: String, val port: Int = 19132)
data class WorldEntry(val id: String, val name: String, val info: String = "")

object Store {
    private const val P = "manes"
    fun saveServers(ctx: Context, list: List<ServerEntry>) {
        val a = JSONArray(); for (s in list) a.put(JSONObject().apply { put("id",s.id);put("n",s.name);put("a",s.address);put("p",s.port) })
        ctx.getSharedPreferences(P,0).edit().putString("sv",a.toString()).apply()
    }
    fun loadServers(ctx: Context): MutableList<ServerEntry> = try {
        val a = JSONArray(ctx.getSharedPreferences(P,0).getString("sv","[]")?:"[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { ServerEntry(it.getString("id"),it.getString("n"),it.getString("a"),it.optInt("p",19132)) } }.toMutableList()
    } catch (e: Exception) { mutableListOf() }
    fun saveWorlds(ctx: Context, list: List<WorldEntry>) {
        val a = JSONArray(); for (w in list) a.put(JSONObject().apply { put("id",w.id);put("n",w.name);put("i",w.info) })
        ctx.getSharedPreferences(P,0).edit().putString("wd",a.toString()).apply()
    }
    fun loadWorlds(ctx: Context): MutableList<WorldEntry> = try {
        val a = JSONArray(ctx.getSharedPreferences(P,0).getString("wd","[]")?:"[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { WorldEntry(it.getString("id"),it.getString("n"),it.optString("i","")) } }.toMutableList()
    } catch (e: Exception) { mutableListOf() }
}

abstract class Module(val name: String, val desc: String, val category: String) {
    var enabled = false
    open fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
    open fun onServerBound(pkt: BedrockPacket, ses: RelaySession): Boolean = false
}

class XRayModule : Module("XRay","Show ores through walls","World") {
    private val keep = setOf("minecraft:coal_ore","minecraft:iron_ore","minecraft:gold_ore","minecraft:diamond_ore","minecraft:emerald_ore","minecraft:lapis_ore","minecraft:redstone_ore","minecraft:copper_ore","minecraft:deepslate_coal_ore","minecraft:deepslate_iron_ore","minecraft:deepslate_gold_ore","minecraft:deepslate_diamond_ore","minecraft:deepslate_emerald_ore","minecraft:deepslate_lapis_ore","minecraft:deepslate_redstone_ore","minecraft:deepslate_copper_ore","minecraft:nether_gold_ore","minecraft:ancient_debris","minecraft:quartz_ore","minecraft:air","minecraft:water","minecraft:flowing_water","minecraft:lava","minecraft:flowing_lava","minecraft:bedrock")

    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if (!enabled || pkt !is LevelChunkPacket) return false
        return try {
            val srv = ses.serverSession ?: return false
            val defs = srv.javaClass.getMethod("getBlockDefinitions").invoke(srv) ?: return false
            val n2i = HashMap<String,Int>()
            defs.javaClass.methods.firstOrNull { it.name == "forEachEntry" }?.invoke(defs,
                java.util.function.BiConsumer<Any,Int> { k, id ->
                    try { n2i[k.javaClass.getMethod("getName").invoke(k) as String] = id } catch (_: Exception) {}
                })
            val allowed = HashSet<Int>(); for (n in keep) { n2i[n]?.let { allowed.add(it) } }
            val air = n2i["minecraft:air"] ?: 0
            val result = rewrite(pkt, allowed, air) ?: return false
            ses.sendToClient(result); true
        } catch (_: Exception) { false }
    }

    private fun rewrite(orig: LevelChunkPacket, allowed: Set<Int>, air: Int): LevelChunkPacket? {
        val rawData = orig.data ?: return null
        val buf: ByteBuf = if (rawData is ByteBuf) rawData.duplicate() else Unpooled.wrappedBuffer(rawData as ByteArray)
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
                val np = if (layer == 0 && bpb > 0)
                    IntArray(pal.size) { i -> if (pal[i] in allowed) pal[i] else air }
                        .also { if (!it.contentEquals(pal)) changed = true }
                else pal
                out.write(bf)
                for (w in words) { out.write(w and 0xFF); out.write((w ushr 8) and 0xFF); out.write((w ushr 16) and 0xFF); out.write((w ushr 24) and 0xFF) }
                for (v in intArrayOf(np.size) + np) { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF); out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF) }
            }
        }
        if (!changed) return null
        val rest = ByteArray(buf.readableBytes()); buf.readBytes(rest); out.write(rest)
        return LevelChunkPacket().also {
            it.chunkX = orig.chunkX; it.chunkZ = orig.chunkZ
            it.subChunksLength = orig.subChunksLength
            it.isCachingEnabled = orig.isCachingEnabled
            it.data = Unpooled.wrappedBuffer(out.toByteArray())
        }
    }
}

class FullbrightModule : Module("Fullbright","Remove fog and darkness","Visual") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if (!enabled || pkt !is PlayerFogPacket) return false
        pkt.fogStack.clear(); return false
    }
}

class ESPModule : Module("ESP","See entities through walls","Visual") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if (!enabled) return false
        try {
            val meta = when (pkt) { is AddPlayerPacket -> pkt.metadata; is AddEntityPacket -> pkt.metadata; else -> return false }
            val flags = meta.javaClass.methods.firstOrNull { it.name == "getFlags" }?.invoke(meta) ?: return false
            val setFlag = flags.javaClass.methods.firstOrNull { it.name == "setFlag" && it.parameterCount == 2 }
            val glowing = setFlag?.parameterTypes?.firstOrNull()?.enumConstants?.firstOrNull { it.toString().contains("GLOW", true) }
            if (setFlag != null && glowing != null) setFlag.invoke(flags, glowing, true)
        } catch (_: Exception) {}
        return false
    }
}

class HitboxModule : Module("Hitbox","Expand enemy hit boxes","Combat") {
    private val scale = 1.8f
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if (!enabled) return false
        try {
            val meta = when (pkt) { is AddPlayerPacket -> pkt.metadata; is AddEntityPacket -> pkt.metadata; else -> return false }
            val put = meta.javaClass.methods.firstOrNull { it.name == "put" && it.parameterCount == 2 }
            val kc = put?.parameterTypes?.firstOrNull()
            if (kc != null && kc.isEnum) {
                val w = kc.enumConstants?.firstOrNull { it.toString().contains("WIDTH",true) && it.toString().contains("BOUNDING",true) }
                val h = kc.enumConstants?.firstOrNull { it.toString().contains("HEIGHT",true) && it.toString().contains("BOUNDING",true) }
                if (w != null) put.invoke(meta, w, scale); if (h != null) put.invoke(meta, h, scale)
            }
        } catch (_: Exception) {}
        return false
    }
}

object Modules { val all: List<Module> = listOf(XRayModule(), FullbrightModule(), ESPModule(), HitboxModule()) }

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
    fun handleFromServer(pkt: BedrockPacket) { for (m in Modules.all) { try { if (m.onClientBound(pkt, this)) return } catch (_: Exception) {} }; sendToClient(pkt) }
    fun handleFromClient(pkt: BedrockPacket) { for (m in Modules.all) { try { if (m.onServerBound(pkt, this)) return } catch (_: Exception) {} }; sendToServer(pkt) }
    fun disconnect() { try { clientSession?.disconnect() } catch (_: Exception) {} }
}

object ManesRelay {
    @Volatile var active: RelaySession? = null
    fun start(remoteIp: String, remotePort: Int) {
        stop()
        val ses = RelaySession().also { active = it }
        val group = NioEventLoopGroup()
        val pong = BedrockPong().edition("MCPE").motd("Manes").subMotd("Manes").playerCount(0).maximumPlayerCount(1).gameType("Survival").protocolVersion(818).version("1.21.80")
        val pongBuf: ByteBuf = try { pong.toByteBuf() as ByteBuf } catch (_: Exception) { Unpooled.wrappedBuffer(pong.toByteBuf() as ByteArray) }
        ServerBootstrap()
            .channelFactory(RakChannelFactory.server(NioDatagramChannel::class.java))
            .group(group)
            .option(RakChannelOption.RAK_ADVERTISEMENT, pongBuf)
            .childHandler(object : BedrockServerInitializer() {
                override fun initSession(srv: BedrockServerSession) {
                    ses.serverSession = srv
                    srv.packetHandler = object : BedrockPacketHandler { override fun handlePacket(pkt: BedrockPacket): PacketSignal { ses.handleFromClient(pkt); return PacketSignal.HANDLED } }
                    Bootstrap()
                        .channelFactory(RakChannelFactory.client(NioDatagramChannel::class.java))
                        .group(group)
                        .handler(object : BedrockClientInitializer() {
                            override fun initSession(cli: BedrockClientSession) {
                                ses.clientSession = cli
                                cli.packetHandler = object : BedrockPacketHandler { override fun handlePacket(pkt: BedrockPacket): PacketSignal { ses.handleFromServer(pkt); return PacketSignal.HANDLED } }
                            }
                        }).connect(InetSocketAddress(remoteIp, remotePort))
                }
            }).bind(InetSocketAddress("0.0.0.0", 19132)).syncUninterruptibly()
    }
    fun stop() { active?.disconnect(); active = null }
}

class MainActivity : ComponentActivity() {
    private val def = listOf(ServerEntry("hive","The Hive","geo.hivebedrock.network",19132),ServerEntry("cube","CubeCraft","mco.cubecraft.net",19132),ServerEntry("lbsg","Lifeboat","play.lbsg.net",19132),ServerEntry("mnpl","Mineplex","pe.mineplex.com",19132),ServerEntry("neth","NetherGames","play.nethergames.org",19132))
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val sv = Store.loadServers(this).ifEmpty { def.toMutableList() }
        val wv = Store.loadWorlds(this)
        setContent { AppTheme { ManesApp(sv,wv,{Store.saveServers(this,it)},{Store.saveWorlds(this,it)},{addr,port->doLaunch(addr,port)}) } }
    }
    override fun onDestroy() { super.onDestroy(); ManesRelay.stop() }
    private fun doLaunch(addr: String, port: Int) {
        Thread { try { ManesRelay.start(addr,port) } catch (e: Exception) { e.printStackTrace() } }.apply { isDaemon=true }.start()
        Handler(Looper.getMainLooper()).postDelayed({ try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("minecraft://?addExternalServer=Manes%7C127.0.0.1:19132"))) } catch (_: Exception) {} }, 1500)
    }
}

@Composable fun AppTheme(c: @Composable ()->Unit) = MaterialTheme(colorScheme=darkColorScheme(background=BG,surface=Surf,primary=Acc,onPrimary=Color.White,onBackground=TxtP,onSurface=TxtP),content=c)

@Composable
fun ManesApp(initSrv:List<ServerEntry>,initWld:List<WorldEntry>,saveSrv:(List<ServerEntry>)->Unit,saveWld:(List<WorldEntry>)->Unit,onLaunch:(String,Int)->Unit) {
    var tab by remember{mutableStateOf(0)}
    var srvs by remember{mutableStateOf(initSrv)}
    var wlds by remember{mutableStateOf(initWld)}
    var selS by remember{mutableStateOf<String?>(null)}
    var selW by remember{mutableStateOf<String?>(null)}
    var shAS by remember{mutableStateOf(false)}
    var shAW by remember{mutableStateOf(false)}
    var launch by remember{mutableStateOf(false)}
    var lName by remember{mutableStateOf("")}
    val cs=srvs.firstOrNull{it.id==selS}; val cw=wlds.firstOrNull{it.id==selW}
    val rdy=(tab==0&&cs!=null)||(tab==1&&cw!=null)||tab==2

    Column(Modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=52.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Acc),contentAlignment=Alignment.Center){Text("M",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Color.White)}
                Text("Manes",fontSize=22.sp,fontWeight=FontWeight.Bold,color=TxtP)
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(if(ManesRelay.active!=null) Grn else TxtM))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("Servers","Worlds","Modules").forEachIndexed{i,l->val on=tab==i
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if(on)Acc else Surf).clickable{tab=i;selS=null;selW=null}.padding(vertical=10.dp),contentAlignment=Alignment.Center){Text(l,fontSize=13.sp,fontWeight=FontWeight.Medium,color=if(on)Color.White else TxtM)}
            }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            when(tab) {
                0 -> {
                    item{SLbl("Servers")}
                    items(srvs,key={it.id}){sv->EC("🌐",sv.name,"${sv.address}:${sv.port}",sv.id==selS,Acc,{selS=if(selS==sv.id)null else sv.id},{srvs=srvs.filter{it.id!=sv.id};saveSrv(srvs);if(selS==sv.id)selS=null})}
                    item{AB("Add a server"){shAS=true}}
                    item{Spacer(Modifier.height(90.dp))}
                }
                1 -> {
                    item{SLbl("Worlds")}
                    if(wlds.isEmpty())item{EM("No worlds yet")}
                    items(wlds,key={it.id}){wl->EC("🌲",wl.name,wl.info,wl.id==selW,Grn,{selW=if(selW==wl.id)null else wl.id},{wlds=wlds.filter{it.id!=wl.id};saveWld(wlds);if(selW==wl.id)selW=null})}
                    item{AB("Import a world"){shAW=true}}
                    item{Spacer(Modifier.height(90.dp))}
                }
                else -> {
                    item{SLbl("Modules")}
                    items(Modules.all,key={it.name}){mod->MC(mod)}
                    item{Spacer(Modifier.height(90.dp))}
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(BG).padding(horizontal=20.dp,vertical=16.dp)) {
            val bl=when{tab==0&&cs!=null->"▶  Launch ${cs.name}";tab==1&&cw!=null->"▶  Open ${cw.name}";tab==2->"▶  Launch with modules";else->"Select a destination"}
            val sl=when{tab==0&&cs!=null->"${cs.address}:${cs.port}";tab==1&&cw!=null->"Local world via proxy";tab==2->"Modules active on launch";else->"Tap a server or world to select"}
            Button(onClick={when{tab==0&&cs!=null->{lName=cs.name;launch=true;onLaunch(cs.address,cs.port)};tab==1&&cw!=null->{lName=cw.name;launch=true;onLaunch("127.0.0.1",19132)};tab==2->{lName="Minecraft";launch=true;onLaunch("127.0.0.1",19132)}}},
                modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp),enabled=rdy,
                colors=ButtonDefaults.buttonColors(containerColor=if(rdy)Acc else Surf2,contentColor=if(rdy)Color.White else TxtM,disabledContainerColor=Surf2,disabledContentColor=TxtM)){
                Text(bl,fontSize=15.sp,fontWeight=FontWeight.SemiBold)}
            Text(sl,fontSize=12.sp,color=TxtM,modifier=Modifier.fillMaxWidth().padding(top=6.dp),textAlign=TextAlign.Center)
        }
    }
    if(launch)LO(lName){launch=false}
    if(shAS)ASD({shAS=false}){n,a,p->srvs=srvs+ServerEntry(UUID.randomUUID().toString(),n,a,p);saveSrv(srvs);shAS=false}
    if(shAW)AWD({shAW=false}){n->wlds=wlds+WorldEntry(UUID.randomUUID().toString(),n,"Just added");saveWld(wlds);shAW=false}
}

@Composable fun EC(icon:String,title:String,sub:String,sel:Boolean,ac:Color,onClick:()->Unit,onDel:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(sel)ac.a(0.1f)else Surf).border(0.5.dp,if(sel)ac else Surf2,RoundedCornerShape(14.dp)).clickable(onClick=onClick).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(ac.a(0.13f)),contentAlignment=Alignment.Center){Text(icon,fontSize=20.sp)}
        Column(Modifier.weight(1f)){Text(title,fontSize=15.sp,fontWeight=FontWeight.Medium,color=TxtP);if(sub.isNotBlank())Text(sub,fontSize=12.sp,color=TxtM)}
        if(sel)Box(Modifier.size(22.dp).clip(CircleShape).background(ac),contentAlignment=Alignment.Center){Icon(Icons.Default.Check,null,tint=Color.White,modifier=Modifier.size(13.dp))}
        else IconButton(onClick=onDel,modifier=Modifier.size(32.dp)){Icon(Icons.Default.Delete,null,tint=TxtM,modifier=Modifier.size(16.dp))}
    }
}

@Composable fun MC(mod:Module) {
    var on by remember{mutableStateOf(mod.enabled)}
    val cc=when(mod.category){"Combat"->RedC;"Visual"->AccLt;else->Grn}
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(on)cc.a(0.08f)else Surf).border(0.5.dp,if(on)cc else Surf2,RoundedCornerShape(14.dp)).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Text(mod.name,fontSize=15.sp,fontWeight=FontWeight.Medium,color=TxtP);Box(Modifier.clip(RoundedCornerShape(4.dp)).background(cc.a(0.2f)).padding(horizontal=6.dp,vertical=2.dp)){Text(mod.category,fontSize=10.sp,color=cc,fontWeight=FontWeight.Medium)}}
            Text(mod.desc,fontSize=12.sp,color=TxtM)
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
@Composable fun EM(t:String)=Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){Text(t,fontSize=13.sp,color=TxtM)}

@Composable fun LO(name:String,onCancel:()->Unit) {
    var step by remember{mutableStateOf(0)}
    val steps=listOf("Starting relay…","Binding 19132…","Connecting to $name…","Handshaking…","Loading…","Launching Minecraft…")
    LaunchedEffect(Unit){for(i in steps.indices){delay(700L);step=i}}
    Box(Modifier.fillMaxSize().background(BG.a(0.97f)),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(32.dp)) {
            Box(Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Acc),contentAlignment=Alignment.Center){Text("M",fontSize=36.sp,fontWeight=FontWeight.Bold,color=Color.White)}
            Spacer(Modifier.height(24.dp));Text(name,fontSize=22.sp,fontWeight=FontWeight.Bold,color=TxtP)
            Text("Connecting…",fontSize=14.sp,color=TxtM,modifier=Modifier.padding(top=4.dp,bottom=28.dp))
            CircularProgressIndicator(color=Acc,modifier=Modifier.size(32.dp),strokeWidth=2.5.dp)
            Spacer(Modifier.height(12.dp));Text(steps.getOrElse(step){"Done"},fontSize=13.sp,color=TxtM)
            Spacer(Modifier.height(32.dp))
            OutlinedButton(onClick=onCancel,colors=ButtonDefaults.outlinedButtonColors(contentColor=TxtM)){Icon(Icons.Default.Close,null,modifier=Modifier.size(14.dp));Spacer(Modifier.width(6.dp));Text("Cancel",fontSize=13.sp)}
        }
    }
}

@Composable fun ASD(onDismiss:()->Unit,onAdd:(String,String,Int)->Unit) {
    var n by remember{mutableStateOf("")};var a by remember{mutableStateOf("")};var p by remember{mutableStateOf("19132")}
    AlertDialog(onDismissRequest=onDismiss,containerColor=Surf,title={Text("Add server",color=TxtP)},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){MF("Name",n){n=it};MF("Address",a){a=it};MF("Port",p,KeyboardType.Number){p=it}}},confirmButton={Button(onClick={if(n.isNotBlank()&&a.isNotBlank())onAdd(n.trim(),a.trim(),p.toIntOrNull()?:19132)},colors=ButtonDefaults.buttonColors(containerColor=Acc)){Text("Add")}},dismissButton={TextButton(onDismiss){Text("Cancel",color=TxtM)}})
}

@Composable fun AWD(onDismiss:()->Unit,onAdd:(String)->Unit) {
    var n by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,containerColor=Surf,title={Text("Import world",color=TxtP)},text={MF("World name",n){n=it}},confirmButton={Button(onClick={if(n.isNotBlank())onAdd(n.trim())},colors=ButtonDefaults.buttonColors(containerColor=Acc)){Text("Import")}},dismissButton={TextButton(onDismiss){Text("Cancel",color=TxtM)}})
}

@Composable fun MF(label:String,value:String,kb:KeyboardType=KeyboardType.Text,onChange:(String)->Unit)=OutlinedTextField(value,onChange,label={Text(label,fontSize=12.sp)},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=kb),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=TxtP,unfocusedTextColor=TxtP,focusedBorderColor=Acc,unfocusedBorderColor=Surf2,focusedLabelColor=Acc,unfocusedLabelColor=TxtM,cursorColor=Acc))
