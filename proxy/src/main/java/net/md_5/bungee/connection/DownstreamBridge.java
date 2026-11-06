package net.md_5.bungee.connection;

import com.google.common.io.ByteStreams;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.EntityMap;
import net.md_5.bungee.ServerConnection;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.Util;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.PacketHandler;
import net.md_5.bungee.netty.PacketWrapper;
import net.md_5.bungee.protocol.packet.PacketFAPluginMessage;
import net.md_5.bungee.protocol.packet.PacketFFKick;

import java.io.DataInput;
import java.util.Objects;

@RequiredArgsConstructor
public class DownstreamBridge extends PacketHandler {

    private final ProxyServer bungee;
    private final UserConnection con;
    private final ServerConnection server;

    @Override
    public void exception(Throwable t) throws Exception {
        ServerInfo def = bungee.getServerInfo(con.getPendingConnection().getListener().getFallbackServer());
        if (server.getInfo() != def) {
            server.setObsolete(true);
            con.connectNow(def);
            con.sendMessage(bungee.getTranslation("server_went_down"));
        } else {
            con.disconnect(Util.exception(t));
        }
    }

    @Override
    public void disconnected(ChannelWrapper channel) throws Exception {
        // We lost connection to the server
        server.getInfo().removePlayer(con);
        if (bungee.getReconnectHandler() != null) {
            bungee.getReconnectHandler().setServer(con);
        }

        if (!server.isObsolete()) {
            con.disconnect(bungee.getTranslation("lost_connection"));
        }
    }

    @Override
    public void handle(PacketWrapper packet) throws Exception {
        if (!server.isObsolete()) {
            EntityMap.rewrite(packet.buf, con.getServerEntityId(), con.getClientEntityId());
            con.sendPacket(packet);
        }
    }

    @Override
    public void handle(PacketFAPluginMessage pluginMessage) throws Exception {
        DataInput in = ByteStreams.newDataInput(pluginMessage.getData());
        PluginMessageEvent event = new PluginMessageEvent(con.getServer(), con, pluginMessage.getTag(), pluginMessage.getData().clone());

        if (bungee.getPluginManager().callEvent(event).isCancelled()) {
            throw new CancelSendSignal();
        }

        if (pluginMessage.getTag().equals(PacketFAPluginMessage.BUNGEE_CHANNEL)) {
            String subChannel = in.readUTF();

            // TODO: we should expand it further to match modern handling
            // see: https://github.com/SpigotMC/BungeeCord/blob/55e1b0bbe776e4b3af6c572a2b3d43fb60a526b1/proxy/src/main/java/net/md_5/bungee/connection/DownstreamBridge.java#L323
            switch (subChannel) {
                case "Connect" -> {
                    ServerInfo server = bungee.getServerInfo(in.readUTF());
                    if (server != null) {
                        con.connect(server);
                    }
                }
                default ->
                    bungee.getLogger().warning("Received unknown/unimplemented Bungee subchannel \"" + subChannel + "\"!");
            }

            // clients will die if you don't cancel the message, it has no need to go out anyway
            throw new CancelSendSignal();
        }
    }

    @Override
    public void handle(PacketFFKick kick) throws Exception {
        ServerInfo def = bungee.getServerInfo(con.getPendingConnection().getListener().getFallbackServer());
        if (Objects.equals(server.getInfo(), def)) {
            def = null;
        }
        ServerKickEvent event = bungee.getPluginManager().callEvent(new ServerKickEvent(con, kick.getMessage(), def, ServerKickEvent.State.CONNECTED));
        if (event.isCancelled() && event.getCancelServer() != null) {
            con.connectNow(event.getCancelServer());
        } else {
            con.disconnect(event.getKickReason());
        }
        server.setObsolete(true);
        throw new CancelSendSignal();
    }

    @Override
    public String toString() {
        return "[" + con.getName() + "] <-> DownstreamBridge <-> [" + server.getInfo().getName() + "]";
    }
}
