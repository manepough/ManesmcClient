package com.project.manes

// ═══════════════════════════════════════════════════════════════════
//  ManesRelay — uses NukkitX Protocol 2.9.4 + RakNet 1.6.16
//  Same stack as WClient v18
// ═══════════════════════════════════════════════════════════════════

import com.nukkitx.network.raknet.RakNetClientSession
import com.nukkitx.network.raknet.RakNetServerSession
import com.nukkitx.protocol.bedrock.BedrockClient
import com.nukkitx.protocol.bedrock.BedrockPong
import com.nukkitx.protocol.bedrock.BedrockServer
import com.nukkitx.protocol.bedrock.BedrockServerEventHandler
import com.nukkitx.protocol.bedrock.BedrockServerSession
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler
import com.nukkitx.protocol.bedrock.packet.UnknownPacket
import com.nukkitx.protocol.bedrock.v475.Bedrock_v475
import io.netty.util.internal.PlatformDependent
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import java.net.InetSocketAddress
import java.util.concurrent.Executors

// ── Relay session ─────────────────────────────────────────────────
class RelaySession {

    @Volatile var serverSession: com.nukkitx.protocol.bedrock.BedrockSession? = null
    @Volatile var clientSession: com.nukkitx.protocol.bedrock.BedrockSession? = null
        set(value) {
            field = value
            if (value == null) return
            // flush queued packets
            var pkt = queue.poll()
            while (pkt != null) {
                runCatching { value.sendPacket(pkt!!) }
                pkt = queue.poll()
            }
        }

    private val queue = PlatformDependent.newMpscQueue<com.nukkitx.protocol.bedrock.BedrockPacket>()

    fun sendToClient(pkt: com.nukkitx.protocol.bedrock.BedrockPacket) {
        runCatching { serverSession?.sendPacket(pkt) }
    }

    fun sendToServer(pkt: com.nukkitx.protocol.bedrock.BedrockPacket) {
        val c = clientSession
        if (c != null) runCatching { c.sendPacket(pkt) }
        else queue.offer(pkt)
    }

    fun handleFromServer(pkt: com.nukkitx.protocol.bedrock.BedrockPacket) {
        for (mod in ModuleRegistry.all) {
            runCatching {
                if (mod.enabled && mod.onClientBound(pkt, this)) return
            }
        }
        sendToClient(pkt)
    }

    fun handleFromClient(pkt: com.nukkitx.protocol.bedrock.BedrockPacket) {
        for (mod in ModuleRegistry.all) {
            runCatching {
                if (mod.enabled && mod.onServerBound(pkt, this)) return
            }
        }
        sendToServer(pkt)
    }

    fun disconnect() {
        runCatching { clientSession?.disconnect() }
        runCatching { serverSession?.disconnect() }
    }
}

// ── Relay server ──────────────────────────────────────────────────
object ManesRelay {

    @Volatile var active: RelaySession? = null
    @Volatile private var server: BedrockServer? = null
    @Volatile private var client: BedrockClient? = null
    var currentTarget = ""
    val isRunning get() = active != null

    fun start(remoteIp: String, remotePort: Int, displayName: String) {
        stop()
        currentTarget = displayName
        val ses = RelaySession().also { active = it }

        val codec = Bedrock_v475.V475_CODEC
        val local  = InetSocketAddress("0.0.0.0", 19132)
        val remote = InetSocketAddress(remoteIp, remotePort)

        val pong = BedrockPong()
        pong.edition          = "MCPE"
        pong.motd             = "Manes >> $displayName"
        pong.subMotd          = "Connect via Servers tab"
        pong.playerCount      = 0
        pong.maximumPlayerCount = 1
        pong.gameType         = "Survival"
        pong.protocolVersion  = codec.protocolVersion
        pong.version          = codec.minecraftVersion
        pong.ipv4Port         = 19132

        // Start local server (Minecraft app connects here)
        val srv = BedrockServer(local).also { server = it }
        srv.handler = object : BedrockServerEventHandler {
            override fun onConnectionRequest(address: InetSocketAddress) = true
            override fun onQuery(address: InetSocketAddress) = pong

            override fun onSessionCreation(srvSession: BedrockServerSession) {
                srvSession.addDisconnectHandler { ses.disconnect() }
                srvSession.setPacketCodec(codec)
                ses.serverSession = srvSession

                srvSession.packetHandler = object : BedrockPacketHandler {
                    override fun handle(pkt: com.nukkitx.protocol.bedrock.BedrockPacket): Boolean {
                        ses.handleFromClient(pkt)
                        return true
                    }
                }

                // Connect outward to real server
                val cli = BedrockClient(InetSocketAddress("0.0.0.0", 0))
                    .also { client = it }
                cli.bind().join()
                cli.connect(remote).whenComplete { cliSession, err ->
                    if (err != null) { ses.disconnect(); return@whenComplete }
                    cliSession.setPacketCodec(codec)
                    ses.clientSession = cliSession
                    cliSession.addDisconnectHandler { ses.disconnect() }
                    cliSession.packetHandler = object : BedrockPacketHandler {
                        override fun handle(pkt: com.nukkitx.protocol.bedrock.BedrockPacket): Boolean {
                            ses.handleFromServer(pkt)
                            return true
                        }
                    }
                }
            }
        }
        srv.bind().join()
    }

    fun stop() {
        active?.disconnect(); active = null
        client?.close();      client = null
        server?.close();      server = null
    }
}
