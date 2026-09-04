package net.md_5.bungee.connection;

import com.google.common.base.Preconditions;
import com.legacyminecraft.bungeeposeidon.api.profile.PlayerProfile;
import com.legacyminecraft.bungeeposeidon.login.LoginProcessHandler;
import com.legacyminecraft.bungeeposeidon.profile.PlayerProfileImpl;
import com.legacyminecraft.bungeeposeidon.service.ServiceClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.Util;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.netty.PacketHandler;
import net.md_5.bungee.protocol.Vanilla;
import net.md_5.bungee.protocol.packet.DefinedPacket;
import net.md_5.bungee.protocol.packet.Packet1Login;
import net.md_5.bungee.protocol.packet.Packet2Handshake;
import net.md_5.bungee.protocol.packet.PacketFFKick;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@RequiredArgsConstructor
public class InitialHandler extends PacketHandler implements PendingConnection {

    private static final Executor LOGIN_EXECUTOR = new ThreadPoolExecutor(
            0, Runtime.getRuntime().availableProcessors(),
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>());

    private final ProxyServer bungee;
    @Getter
    private ChannelWrapper ch;
    @Getter
    private final ListenerInfo listener;
    @Getter
    private LoginProcessHandler loginProcessHandler;
    @Getter
    private Packet2Handshake handshake;
    @Getter
    private Packet1Login login;
    @Getter
    @Setter
    private volatile State thisState = State.HANDSHAKE;
    private final Unsafe unsafe = new Unsafe() {
        @Override
        public void sendPacket(DefinedPacket packet) {
            ch.write(packet);
        }
    };
    @Getter
    private boolean onlineMode = BungeeCord.getInstance().config.isOnlineMode();
    @Getter
    @Setter
    private String serverId;

    public enum State {
        HANDSHAKE, LOGIN, FINISHED
    }

    @Override
    public void connected(ChannelWrapper channel) throws Exception {
        this.ch = channel;
    }

    @Override
    public void exception(Throwable t) throws Exception {
        disconnect(ChatColor.RED + Util.exception(t));
    }

    @Override
    public void handle(Packet1Login login) throws Exception {
        Preconditions.checkState(thisState == State.LOGIN, "Not expecting LOGIN");
        login.setUsername(this.loginProcessHandler.getProfile().name());
        this.login = login;

        if (login.getEntityId() > Vanilla.PROTOCOL_VERSION) {
            disconnect(bungee.getTranslation("outdated_server"));
        } else if (login.getEntityId() < Vanilla.PROTOCOL_VERSION) {
            disconnect(bungee.getTranslation("outdated_client"));
        }

        if (login.getUsername().length() > 16) {
            disconnect("Cannot have username longer than 16 characters");
            return;
        }

        int limit = BungeeCord.getInstance().config.getPlayerLimit();
        if (limit > 0 && bungee.getOnlineCount() > limit) {
            disconnect(bungee.getTranslation("proxy_full"));
            return;
        }

        // If offline mode and they are already on, don't allow connect
        if (!isOnlineMode() && bungee.getPlayer(login.getUsername()) != null) {
            disconnect(bungee.getTranslation("already_connected"));
            return;
        }

        if (isOnlineMode()) {
            InetAddress address = ((InetSocketAddress) this.ch.getHandle().remoteAddress()).getAddress();

            try {
                if (BungeeCord.getInstance().getSessionService().verifySession(getName(), this.serverId, address)) {
                    finish();
                } else {
                    disconnect("Not authenticated with Minecraft.net");
                }
            } catch (ServiceClientException e) {
                disconnect(bungee.getTranslation("mojang_fail"));
                bungee.getLogger().log(Level.SEVERE, "Error authenticating " + getName() + " with minecraft.net", e);
            }
        } else {
            finish();
        }
    }

    @Override
    public void handle(Packet2Handshake handshake) throws Exception {
        Preconditions.checkState(thisState == State.HANDSHAKE, "Not expecting HANDSHAKE");
        this.handshake = handshake;
        bungee.getLogger().log(Level.INFO, "{0} has connected", this);

        String name = handshake.getUsername();
        // Strip Glass Networking flag from handshake username
        if (name.contains(";")) {
            name = name.split(";")[0];
        }

        this.loginProcessHandler = new LoginProcessHandler(this, name);
        LOGIN_EXECUTOR.execute(this.loginProcessHandler);
    }

    private void finish() {
        // Check for multiple connections
        ProxiedPlayer old = bungee.getPlayer(this.loginProcessHandler.getProfile().name());
        if (old != null) {
            old.disconnect(bungee.getTranslation("already_connected"));
        }

        // fire login event
        LoginEvent event = new LoginEvent(InitialHandler.this);
        bungee.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            disconnect(event.getCancelReason());
            return;
        }
        if (ch.isClosed()) {
            return;
        }

        PlayerProfile profile = new PlayerProfileImpl(this.loginProcessHandler.getProfile());
        UserConnection userCon = new UserConnection(bungee, ch, profile, this);
        userCon.init();

        bungee.getPluginManager().callEvent(new PostLoginEvent(userCon));

        ch.getHandle().pipeline().get(HandlerBoss.class).setHandler(new UpstreamBridge(bungee, userCon));

        ServerInfo server;
        if (bungee.getReconnectHandler() != null) {
            server = bungee.getReconnectHandler().getServer(userCon);
        } else {
            server = bungee.getServerInfo(getListener().getDefaultServer());
            Preconditions.checkState(server != null, "Default server not defined");
        }
        userCon.connect(server, true);

        thisState = State.FINISHED;
    }

    @Override
    public synchronized void disconnect(String reason) {
        if (!ch.isClosed()) {
            unsafe().sendPacket(new PacketFFKick(Util.truncate(reason)));
            ch.close();
        }
    }

    @Override
    public String getName() {
        return (login == null) ? null : login.getUsername();
    }

    @Override
    public int getVersion() {
        return (login == null) ? -1 : login.getEntityId();
    }

    @Override
    public InetSocketAddress getAddress() {
        return (InetSocketAddress) ch.getHandle().remoteAddress();
    }

    @Override
    public Unsafe unsafe() {
        return unsafe;
    }

    public void setOnlineMode(boolean onlineMode) {
        Preconditions.checkState(thisState == State.HANDSHAKE, "Can only set online mode status whilst handshaking");
        this.onlineMode = onlineMode;
    }

    @Override
    public String toString() {
        return "[" + ((getName() != null) ? getName() : getAddress()) + "] <-> InitialHandler";
    }
}
