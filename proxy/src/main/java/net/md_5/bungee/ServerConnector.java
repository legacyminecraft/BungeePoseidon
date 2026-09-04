package net.md_5.bungee;

import com.google.common.base.Preconditions;
import com.legacyminecraft.bungeeposeidon.forwarding.PlayerDataForwarding;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.connection.CancelSendSignal;
import net.md_5.bungee.connection.DownstreamBridge;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.netty.PacketHandler;
import net.md_5.bungee.protocol.packet.Packet1Login;
import net.md_5.bungee.protocol.packet.Packet2Handshake;
import net.md_5.bungee.protocol.packet.Packet9Respawn;
import net.md_5.bungee.protocol.packet.PacketFAPluginMessage;
import net.md_5.bungee.protocol.packet.PacketFFKick;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@RequiredArgsConstructor
public class ServerConnector extends PacketHandler {

    private final ProxyServer bungee;
    private ChannelWrapper ch;
    private final UserConnection user;
    private final BungeeServerInfo target;
    private State thisState = State.LOGIN;

    private enum State {
        LOGIN, FINISHED
    }

    @Override
    public void exception(Throwable t) throws Exception {
        String message = "Exception Connecting:" + Util.exception(t);
        if (user.getServer() == null) {
            user.disconnect(message);
        } else {
            user.sendMessage(ChatColor.RED + message);
        }
    }

    @Override
    public void connected(ChannelWrapper channel) throws Exception {
        this.ch = channel;

        forwardPlayerData();
        channel.write(user.getPendingConnection().getHandshake());
        channel.write(user.getPendingConnection().getLogin());
    }

    private void forwardPlayerData() {
        String secret = target.getSecret();
        if (secret == null) {
            return;
        }

        byte[] payload = PlayerDataForwarding.createForwardingData(
                secret.getBytes(StandardCharsets.UTF_8),
                user.getAddress().getAddress(),
                user.getProfile());
        ch.write(new PacketFAPluginMessage(PlayerDataForwarding.CHANNEL, payload));
    }

    @Override
    public void disconnected(ChannelWrapper channel) throws Exception {
        user.getPendingConnects().remove(target);
    }

    @Override
    public void handle(Packet1Login login) throws Exception {
        Preconditions.checkState(thisState == State.LOGIN, "Not exepcting LOGIN");

        ServerConnection server = new ServerConnection(ch, target);
        ServerConnectedEvent event = new ServerConnectedEvent(user, server);
        bungee.getPluginManager().callEvent(event);

        synchronized (user.getSwitchMutex()) {
            // Once again, first connection
            user.setClientEntityId(login.getEntityId());
            user.setServerEntityId(login.getEntityId());

            user.unsafe().sendPacket(login);

            if (user.getServer() != null) {
                byte oppositeDimension = (byte) (login.getDimension() >= 0 ? -1 : 0);
                user.unsafe().sendPacket(new Packet9Respawn(oppositeDimension));
                user.unsafe().sendPacket(new Packet9Respawn(login.getDimension()));

                // Remove from old servers
                user.getServer().setObsolete(true);
                user.getServer().disconnect("Quitting");
            }

            // TODO: Fix this?
            if (!user.isActive()) {
                server.disconnect("Quitting");
                // Silly server admins see stack trace and die
                bungee.getLogger().warning("No client connected for pending server!");
                return;
            }

            // Add to new server
            // TODO: Move this to the connected() method of DownstreamBridge
            target.addPlayer(user);
            user.getPendingConnects().remove(target);

            user.setServer(server);
            ch.getHandle().pipeline().get(HandlerBoss.class).setHandler(new DownstreamBridge(bungee, user, server));
        }

        bungee.getPluginManager().callEvent(new ServerSwitchEvent(user));

        thisState = State.FINISHED;

        throw new CancelSendSignal();
    }

    @Override
    public void handle(Packet2Handshake handshake) throws Exception {
        throw new CancelSendSignal();
    }

    @Override
    public void handle(PacketFFKick kick) throws Exception {
        ServerInfo def = bungee.getServerInfo(user.getPendingConnection().getListener().getFallbackServer());
        if (Objects.equals(target, def)) {
            def = null;
        }
        ServerKickEvent event = bungee.getPluginManager().callEvent(new ServerKickEvent(user, kick.getMessage(), def, ServerKickEvent.State.CONNECTING));
        if (event.isCancelled() && event.getCancelServer() != null) {
            user.connect(event.getCancelServer());
            return;
        }

        if (user.getServer() == null) {
            user.disconnect(event.getKickReason());
        } else {
            String message = bungee.getTranslation("connect_kick") + target.getName() + ": " + event.getKickReason();
            user.sendMessage(message);
        }
    }

    @Override
    public String toString() {
        return "[" + user.getName() + "] <-> ServerConnector [" + target.getName() + "]";
    }
}
