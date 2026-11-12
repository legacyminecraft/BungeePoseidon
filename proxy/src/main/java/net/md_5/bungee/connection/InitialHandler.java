package net.md_5.bungee.connection;

import com.google.common.base.Preconditions;
import com.legacyminecraft.bungeeposeidon.api.profile.PlayerProfile;
import com.legacyminecraft.bungeeposeidon.login.LoginProcessHandler;
import com.legacyminecraft.bungeeposeidon.profile.MinecraftProfile;
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
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.netty.HandlerBoss;
import net.md_5.bungee.netty.PacketHandler;
import net.md_5.bungee.protocol.Vanilla;
import net.md_5.bungee.protocol.packet.DefinedPacket;
import net.md_5.bungee.protocol.packet.Packet1Login;
import net.md_5.bungee.protocol.packet.Packet2Handshake;
import net.md_5.bungee.protocol.packet.PacketFAPluginMessage;
import net.md_5.bungee.protocol.packet.PacketFFKick;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
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
    @Getter
    @Setter
    private volatile boolean hybridAuthRequested = false;
    @Getter
    private volatile boolean authenticated = false;

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
        login.setUsername(getName());
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

        // Hybrid auth: offline mode proxies still verify clients that announced a session in the handshake
        boolean hybrid = !isOnlineMode() && isHybridAuthRequested();
        if (isOnlineMode() || hybrid) {
            boolean kickOnFailure = isOnlineMode() || BungeeCord.getInstance().config.isKickOnInvalidHybridSession();
            try {
                if (BungeeCord.getInstance().getSessionService().verifySession(getName(), getServerId())) {
                    this.authenticated = true;
                    if (hybrid) {
                        bungee.getLogger().log(Level.INFO, "{0} authenticated with the session server (hybrid auth)", getName());
                    }
                } else {
                    bungee.getLogger().log(Level.INFO, "{0} tried to login with an invalid session", getName());
                    if (kickOnFailure) {
                        disconnect("Not authenticated with Minecraft.net");
                        return;
                    }
                }
            } catch (ServiceClientException e) {
                bungee.getLogger().log(Level.SEVERE, "Error authenticating " + getName() + " with minecraft.net", e);
                if (kickOnFailure) {
                    disconnect(bungee.getTranslation("mojang_fail"));
                    return;
                }
            }
        }

        finish();
    }

    @Override
    public void handle(Packet2Handshake handshake) throws Exception {
        Preconditions.checkState(thisState == State.HANDSHAKE, "Not expecting HANDSHAKE");
        this.handshake = handshake;
        bungee.getLogger().log(Level.INFO, "{0} has connected", this);

        String name = handshake.getUsername();
        // Strip handshake flags from handshake username
        if (name.contains(";")) {
            name = name.split(";")[0];
        }

        this.loginProcessHandler = new LoginProcessHandler(this, name);
        LOGIN_EXECUTOR.execute(this.loginProcessHandler);
    }

    @Override
    public void handle(PacketFAPluginMessage pluginMessage) throws Exception {
        PluginMessageEvent event = new PluginMessageEvent(this, this, pluginMessage.getTag(), pluginMessage.getData().clone());
        bungee.getPluginManager().callEvent(event);
    }

    private void finish() {
        // Check for multiple connections
        ProxiedPlayer connected = bungee.getPlayer(getName());
        if (connected == null) {
            connected = bungee.getPlayer(getUniqueId());
        }

        if (connected != null) {
            disconnect(bungee.getTranslation("already_connected"));
            return;
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
        return Optional.ofNullable(this.loginProcessHandler)
                .map(LoginProcessHandler::getProfile)
                .map(MinecraftProfile::name)
                .orElse(null);
    }

    @Override
    public UUID getUniqueId() {
        return Optional.ofNullable(this.loginProcessHandler)
                .map(LoginProcessHandler::getProfile)
                .map(MinecraftProfile::id)
                .orElse(null);
    }

    @Override
    public int getVersion() {
        return (login == null) ? -1 : login.getEntityId();
    }

    @Override
    public long getHandshakeFlags() {
        if (this.handshake == null) {
            return 0L;
        }

        String[] tokens = this.handshake.getUsername().split(";");
        // tokens[0] is the username; the first token after it that is a base 36 mask of at most 12 digits (62 bits) is the flags.
        // Longer named tokens that other clients append are never read as a mask
        if (tokens.length != 2) {
            return 0L;
        }

        String mask = tokens[1];
        if (mask.isEmpty() || mask.length() > 12) {
            return 0L;
        }

        try {
            return Long.parseLong(mask, 36);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public boolean hasHandshakeFlag(int bit) {
        return bit >= 0 && bit < 62 && (getHandshakeFlags() & (1L << bit)) != 0;
    }

    @Override
    public InetSocketAddress getAddress() {
        return ch.getClientAddress();
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
