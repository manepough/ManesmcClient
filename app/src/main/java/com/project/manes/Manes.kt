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
import io.netty.buffer.Unpooled
import io.netty.util.internal.PlatformDependent
import kotlinx.coroutines.delay
import org.cloudburstmc.protocol.bedrock.packet.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

private val BG       = Color(0xFF0D0D0F)
private val Surface  = Color(0xFF18181C)
private val Surface2 = Color(0xFF222228)
private val Accent   = Color(0xFF7C6EF7)
private val AccentLt = Color(0xFFA78BFA)
private val TxtPri   = Color(0xFFF1F0FB)
private val TxtMut   = Color(0xFF8B8A9B)
private val Green    = Color(0xFF4ADE80)
private val RedCol   = Color(0xFFF87171)

data class ServerEntry(val id: String, val name: String, val address: String, val port: Int = 19132)
data class WorldEntry(val id: String, val name: String, val info: String = "")

object Store {
    private const val P = "manes"
    fun saveServers(ctx: Context, list: List<ServerEntry>) {
        val a = JSONArray()
        list.forEach { s -> a.put(JSONObject().apply { put("id",s.id);put("n",s.name);put("a",s.address);put("p",s.port) }) }
        ctx.getSharedPreferences(P,Context.MODE_PRIVATE).edit().putString("sv",a.toString()).apply()
    }
    fun loadServers(ctx: Context): MutableList<ServerEntry> = try {
        val a = JSONArray(ctx.getSharedPreferences(P,Context.MODE_PRIVATE).getString("sv","[]")?:"[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { ServerEntry(it.getString("id"),it.getString("n"),it.getString("a"),it.optInt("p",19132)) } }.toMutableList()
    } catch(_:Exception){ mutableListOf() }
    fun saveWorlds(ctx: Context, list: List<WorldEntry>) {
        val a = JSONArray()
        list.forEach { w -> a.put(JSONObject().apply { put("id",w.id);put("n",w.name);put("i",w.info) }) }
        ctx.getSharedPreferences(P,Context.MODE_PRIVATE).edit().putString("wd",a.toString()).apply()
    }
    fun loadWorlds(ctx: Context): MutableList<WorldEntry> = try {
        val a = JSONArray(ctx.getSharedPreferences(P,Context.MODE_PRIVATE).getString("wd","[]")?:"[]")
        (0 until a.length()).map { i -> a.getJSONObject(i).let { WorldEntry(it.getString("id"),it.getString("n"),it.optString("i","")) } }.toMutableList()
    } catch(_:Exception){ mutableListOf() }
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
            val defs = ses.serverSession?.blockDefinitions ?: return false
            val nameToId = HashMap<String,Int>()
            defs.forEachEntry { k,id -> nameToId[k.name]=id }
            val allowed = keep.mapNotNullTo(HashSet()) { nameToId[it] }
            val air = nameToId["minecraft:air"] ?: 0
            val result = rewrite(pkt,allowed,air) ?: return false
            ses.sendToClient(result); true
        } catch(_:Exception){ false }
    }
    private fun rewrite(orig: LevelChunkPacket, allowed: Set<Int>, air: Int): LevelChunkPacket? {
        val buf = Unpooled.wrappedBuffer(orig.data ?: return null)
        val out = ByteArrayOutputStream(); var changed = false
        repeat(orig.subChunksLength) {
            val ver = buf.readUnsignedByte().toInt()
            val layers = if(ver==8||ver==9) buf.readUnsignedByte().toInt() else 1
            out.write(ver); if(ver==8||ver==9) out.write(layers)
            for(layer in 0 until layers) {
                val bf = buf.readUnsignedByte().toInt(); val bpb = bf ushr 1
                val bpw = if(bpb>0) 32/bpb else 4096; val wc = if(bpb>0)(4096+bpw-1)/bpw else 0
                val words = IntArray(wc){buf.readIntLE()}; val ps = buf.readIntLE(); val pal = IntArray(ps){buf.readIntLE()}
                val np = if(layer==0&&bpb>0) pal.map{if(it in allowed)it else air}.toIntArray().also{if(!it.contentEquals(pal))changed=true} else pal
                out.write(bf); words.forEach{wl(out,it)}; wl(out,np.size); np.forEach{wl(out,it)}
            }
        }
        if(!changed) return null
        val rest = ByteArray(buf.readableBytes()); buf.readBytes(rest); out.write(rest)
        return LevelChunkPacket().also{it.chunkX=orig.chunkX;it.chunkZ=orig.chunkZ;it.subChunksLength=orig.subChunksLength;it.isCachingEnabled=orig.isCachingEnabled;it.data=out.toByteArray()}
    }
    private fun wl(o:ByteArrayOutputStream,v:Int){o.write(v and 0xFF);o.write((v ushr 8) and 0xFF);o.write((v ushr 16) and 0xFF);o.write((v ushr 24) and 0xFF)}
}

class FullbrightModule : Module("Fullbright","Remove darkness and fog","Visual") {
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if(!enabled||pkt !is PlayerFogPacket) return false
        pkt.fogStack.clear(); return false
    }
}

class ESPModule : Module("ESP","See entities through walls","Visual") {
    private val GLOWING = 46
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if(!enabled) return false
        when(pkt) { is AddPlayerPacket->runCatching{pkt.metadata.putBoolean(GLOWING,true)}; is AddEntityPacket->runCatching{pkt.metadata.putBoolean(GLOWING,true)} }
        return false
    }
}

class HitboxModule : Module("Hitbox","Expand enemy hit boxes","Combat") {
    private val scale = 1.8f
    override fun onClientBound(pkt: BedrockPacket, ses: RelaySession): Boolean {
        if(!enabled) return false
        when(pkt) {
            is AddPlayerPacket->runCatching{pkt.metadata.putFloat(39,scale);pkt.metadata.putFloat(41,scale)}
            is AddEntityPacket->runCatching{pkt.metadata.putFloat(39,scale);pkt.metadata.putFloat(41,scale)}
        }
        return false
    }
}

object Modules { val all: List<Module> = listOf(XRayModule(),FullbrightModule(),ESPModule(),HitboxModule()) }

class RelaySession {
    var serverSession: org.cloudburstmc.protocol.bedrock.BedrockServerSession? = null
    var clientSession: org.cloudburstmc.protocol.bedrock.BedrockClientSession? = null
        set(value) { field=value;value?:return;serverSession?.let{value.codec=it.codec};var q=queue.poll();while(q!=null){value.sendPacket(q);q=queue.poll()} }
    private val queue = PlatformDependent.newMpscQueue<BedrockPacket>()
    fun sendToClient(p:BedrockPacket){serverSession?.sendPacket(p)}
    fun sendToServer(p:BedrockPacket){val c=clientSession;if(c!=null)c.sendPacket(p) else queue.offer(p)}
    fun handleFromServer(pkt:BedrockPacket){for(m in Modules.all){runCatching{if(m.onClientBound(pkt,this))return}};sendToClient(pkt)}
    fun handleFromClient(pkt:BedrockPacket){for(m in Modules.all){runCatching{if(m.onServerBound(pkt,this))return}};sendToServer(pkt)}
    fun disconnect(){runCatching{clientSession?.disconnect()}}
}

object ManesRelay {
    @Volatile var active:RelaySession?=null
    @Volatile private var running=false
    fun start(remoteIp:String,remotePort:Int){
        if(running)stop();running=true;val ses=RelaySession().also{active=it}
        try {
            val lg=io.netty.channel.nio.NioEventLoopGroup()
            val pong=org.cloudburstmc.protocol.bedrock.BedrockPong().edition("MCPE").motd("Manes").subMotd("Manes").playerCount(0).maximumPlayerCount(1).gameType("Survival").protocolVersion(818).version("1.21.80")
            io.netty.bootstrap.ServerBootstrap()
                .channelFactory(org.cloudburstmc.netty.channel.raknet.RakChannelFactory.server(lg)).group(lg)
                .option(org.cloudburstmc.netty.channel.raknet.config.RakChannelOption.RAK_ADVERTISEMENT,pong.toByteBuf())
                .childHandler(object:org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer<org.cloudburstmc.protocol.bedrock.BedrockServerSession>(){
                    override fun initSession(srv:org.cloudburstmc.protocol.bedrock.BedrockServerSession){
                        ses.serverSession=srv
                        srv.packetHandler=BPH{pkt->ses.handleFromClient(pkt);true}
                        srv.channel().closeFuture().addListener{ses.disconnect()}
                        io.netty.bootstrap.Bootstrap()
                            .channelFactory(org.cloudburstmc.netty.channel.raknet.RakChannelFactory.client(lg)).group(lg)
                            .handler(object:org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer<org.cloudburstmc.protocol.bedrock.BedrockClientSession>(){
                                override fun initSession(cli:org.cloudburstmc.protocol.bedrock.BedrockClientSession){
                                    ses.clientSession=cli
                                    cli.packetHandler=BPH{pkt->ses.handleFromServer(pkt);true}
                                    cli.channel().closeFuture().addListener{ses.disconnect()}
                                }
                            }).connect(java.net.InetSocketAddress(remoteIp,remotePort))
                    }
                }).bind(java.net.InetSocketAddress("0.0.0.0",19132)).syncUninterruptibly()
        } catch(e:Exception){e.printStackTrace();running=false}
    }
    fun stop(){active?.disconnect();active=null;running=false}
    fun interface BPH:org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler{override fun handle(p:BedrockPacket):Boolean=onPacket(p);fun onPacket(p:BedrockPacket):Boolean}
}

class MainActivity : ComponentActivity() {
    private val def = listOf(ServerEntry("hive","The Hive","geo.hivebedrock.network"),ServerEntry("cube","CubeCraft","mco.cubecraft.net"),ServerEntry("lbsg","Lifeboat","play.lbsg.net"),ServerEntry("mnpl","Mineplex","pe.mineplex.com"),ServerEntry("neth","NetherGames","play.nethergames.org"))
    override fun onCreate(s:Bundle?){super.onCreate(s);val sv=Store.loadServers(this).ifEmpty{def.toMutableList()};val wv=Store.loadWorlds(this)
        setContent{AppTheme{ManesApp(sv,wv,{Store.saveServers(this,it)},{Store.saveWorlds(this,it)},{a,p->go(a,p)})}}}
    override fun onDestroy(){super.onDestroy();ManesRelay.stop()}
    private fun go(addr:String,port:Int){
        Thread{runCatching{ManesRelay.start(addr,port)}}.apply{isDaemon=true}.start()
        Handler(Looper.getMainLooper()).postDelayed({runCatching{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("minecraft://?addExternalServer=Manes%7C127.0.0.1:19132")))}},1500)
    }
}

@Composable fun AppTheme(c:@Composable()->Unit)=MaterialTheme(colorScheme=darkColorScheme(background=BG,surface=Surface,primary=Accent,onPrimary=Color.White,onBackground=TxtPri,onSurface=TxtPri),content=c)

@Composable
fun ManesApp(initServers:List<ServerEntry>,initWorlds:List<WorldEntry>,onSaveSrv:(List<ServerEntry>)->Unit,onSaveWld:(List<WorldEntry>)->Unit,onLaunch:(String,Int)->Unit){
    var tab by remember{mutableStateOf(0)};var servers by remember{mutableStateOf(initServers)};var worlds by remember{mutableStateOf(initWorlds)}
    var selSrv by remember{mutableStateOf<String?>(null)};var selWld by remember{mutableStateOf<String?>(null)}
    var addSrv by remember{mutableStateOf(false)};var addWld by remember{mutableStateOf(false)}
    var launching by remember{mutableStateOf(false)};var launchNm by remember{mutableStateOf("")}
    val curSrv=servers.firstOrNull{it.id==selSrv};val curWld=worlds.firstOrNull{it.id==selWld}
    val ready=(tab==0&&curSrv!=null)||(tab==1&&curWld!=null)||tab==2
    Column(Modifier.fillMaxSize().background(BG)){
        Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=52.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Accent),contentAlignment=Alignment.Center){Text("M",fontSize=18.sp,fontWeight=FontWeight.Bold,color=Color.White)}
                Text("Manes",fontSize=22.sp,fontWeight=FontWeight.Bold,color=TxtPri)
            }
            Box(Modifier.size(8.dp).clip(CircleShape).background(if(ManesRelay.active!=null)Green else TxtMut))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            listOf("Servers","Worlds","Modules").forEachIndexed{i,l->val on=tab==i
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if(on)Accent else Surface).clickable{tab=i;selSrv=null;selWld=null}.padding(vertical=10.dp),contentAlignment=Alignment.Center){Text(l,fontSize=13.sp,fontWeight=FontWeight.Medium,color=if(on)Color.White else TxtMut)}}
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            when(tab){
                0->{item{SLabel("Servers")};items(servers,key={it.id}){sv->ECard("🌐",sv.name,"${sv.address}:${sv.port}",sv.id==selSrv,Accent,{selSrv=if(selSrv==sv.id)null else sv.id},{servers=servers.filter{it.id!=sv.id};onSaveSrv(servers);if(selSrv==sv.id)selSrv=null})};item{ABtn("Add a server"){addSrv=true}};item{Spacer(Modifier.height(90.dp))}}
                1->{item{SLabel("Worlds")};if(worlds.isEmpty())item{EMsg("No worlds yet")};items(worlds,key={it.id}){wl->ECard("🌲",wl.name,wl.info,wl.id==selWld,Green,{selWld=if(selWld==wl.id)null else wl.id},{worlds=worlds.filter{it.id!=wl.id};onSaveWld(worlds);if(selWld==wl.id)selWld=null})};item{ABtn("Import a world"){addWld=true}};item{Spacer(Modifier.height(90.dp))}}
                2->{item{SLabel("Modules")};items(Modules.all,key={it.name}){mod->MCard(mod)};item{Spacer(Modifier.height(90.dp))}}
            }
        }
        Column(Modifier.fillMaxWidth().background(BG).padding(horizontal=20.dp,vertical=16.dp)){
            Button(onClick={when{tab==0&&curSrv!=null->{launchNm=curSrv.name;launching=true;onLaunch(curSrv.address,curSrv.port)};tab==1&&curWld!=null->{launchNm=curWld.name;launching=true;onLaunch("127.0.0.1",19132)};tab==2->{launchNm="Minecraft";launching=true;onLaunch("127.0.0.1",19132)}}},
                modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(14.dp),enabled=ready,
                colors=ButtonDefaults.buttonColors(containerColor=if(ready)Accent else Surface2,contentColor=if(ready)Color.White else TxtMut,disabledContainerColor=Surface2,disabledContentColor=TxtMut)){
                Text(when{tab==0&&curSrv!=null->"▶  Launch ${curSrv.name}";tab==1&&curWld!=null->"▶  Open ${curWld.name}";tab==2->"▶  Launch with modules";else->"Select a destination"},fontSize=15.sp,fontWeight=FontWeight.SemiBold)}
            Text(when{tab==0&&curSrv!=null->"${curSrv.address}:${curSrv.port}";tab==1&&curWld!=null->"Local world via proxy";tab==2->"Modules active on next launch";else->"Tap a server or world to select"},fontSize=12.sp,color=TxtMut,modifier=Modifier.fillMaxWidth().padding(top=6.dp),textAlign=TextAlign.Center)
        }
    }
    if(launching)LOv(launchNm){launching=false}
    if(addSrv)ASrvD(onDismiss={addSrv=false}){n,a,p->servers=servers+ServerEntry(UUID.randomUUID().toString(),n,a,p);onSaveSrv(servers);addSrv=false}
    if(addWld)AWldD(onDismiss={addWld=false}){n->worlds=worlds+WorldEntry(UUID.randomUUID().toString(),n,"Just added");onSaveWld(worlds);addWld=false}
}

@Composable fun ECard(icon:String,title:String,sub:String,sel:Boolean,ac:Color,onClick:()->Unit,onDel:()->Unit){
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(sel)ac.copy(.1f)else Surface).border(.5.dp,if(sel)ac else Surface2,RoundedCornerShape(14.dp)).clickable(onClick=onClick).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(ac.copy(.13f)),contentAlignment=Alignment.Center){Text(icon,fontSize=20.sp)}
        Column(Modifier.weight(1f)){Text(title,fontSize=15.sp,fontWeight=FontWeight.Medium,color=TxtPri);if(sub.isNotBlank())Text(sub,fontSize=12.sp,color=TxtMut)}
        if(sel)Box(Modifier.size(22.dp).clip(CircleShape).background(ac),contentAlignment=Alignment.Center){Icon(Icons.Default.Check,null,tint=Color.White,modifier=Modifier.size(13.dp))}
        else IconButton(onClick=onDel,modifier=Modifier.size(32.dp)){Icon(Icons.Default.Delete,null,tint=TxtMut,modifier=Modifier.size(16.dp))}
    }
}

@Composable fun MCard(mod:Module){
    var on by remember{mutableStateOf(mod.enabled)};val cc=when(mod.category){"Combat"->RedCol;"Visual"->AccentLt;else->Green}
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(on)cc.copy(.08f)else Surface).border(.5.dp,if(on)cc else Surface2,RoundedCornerShape(14.dp)).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Column(Modifier.weight(1f)){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Text(mod.name,fontSize=15.sp,fontWeight=FontWeight.Medium,color=TxtPri);Box(Modifier.clip(RoundedCornerShape(4.dp)).background(cc.copy(.2f)).padding(horizontal=6.dp,vertical=2.dp)){Text(mod.category,fontSize=10.sp,color=cc,fontWeight=FontWeight.Medium)}}
            Text(mod.desc,fontSize=12.sp,color=TxtMut)}
        Switch(checked=on,onCheckedChange={on=it;mod.enabled=it},colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=cc))
    }
}

@Composable fun ABtn(label:String,onClick:()->Unit){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(.5.dp,TxtMut.copy(.2f),RoundedCornerShape(14.dp)).clickable(onClick=onClick).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangeme
