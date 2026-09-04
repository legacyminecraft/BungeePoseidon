package net.md_5.bungee;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.legacyminecraft.bungeeposeidon.BungeeBuildInformation;
import com.legacyminecraft.bungeeposeidon.api.ping.ServerIcon;
import com.legacyminecraft.bungeeposeidon.api.util.TextWrapper;
import com.legacyminecraft.bungeeposeidon.profile.ProfileCache;
import com.legacyminecraft.bungeeposeidon.profile.ProfileService;
import com.legacyminecraft.bungeeposeidon.service.ServiceClient;
import com.legacyminecraft.bungeeposeidon.session.SessionService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.util.ResourceLeakDetector;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ReconnectHandler;
import net.md_5.bungee.api.config.ConfigurationAdapter;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.command.CommandAlert;
import net.md_5.bungee.command.CommandBungee;
import net.md_5.bungee.command.CommandEnd;
import net.md_5.bungee.command.CommandFind;
import net.md_5.bungee.command.CommandIP;
import net.md_5.bungee.command.CommandList;
import net.md_5.bungee.command.CommandPerms;
import net.md_5.bungee.command.CommandReload;
import net.md_5.bungee.command.CommandSend;
import net.md_5.bungee.command.CommandServer;
import net.md_5.bungee.command.ConsoleCommandSender;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.YamlConfig;
import net.md_5.bungee.log.BungeeLogger;
import net.md_5.bungee.log.LoggingOutputStream;
import net.md_5.bungee.netty.PipelineUtils;
import net.md_5.bungee.protocol.Vanilla;
import net.md_5.bungee.protocol.packet.DefinedPacket;
import net.md_5.bungee.protocol.packet.Packet3Chat;
import net.md_5.bungee.reconnect.YamlReconnectHandler;
import net.md_5.bungee.scheduler.BungeeScheduler;
import net.md_5.bungee.util.CaseInsensitiveMap;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main BungeeCord proxy class.
 */
public class BungeeCord extends ProxyServer {

    /**
     * Build information.
     */
    private final BungeeBuildInformation buildInformation = new BungeeBuildInformation();
    /**
     * Current operation state.
     */
    public volatile boolean isRunning;
    /**
     * Configuration.
     */
    public final Configuration config = new Configuration();
    /**
     * Localization bundle.
     */
    public final ResourceBundle bundle = ResourceBundle.getBundle("messages");
    public final EventLoopGroup eventLoops = PipelineUtils.newEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty IO Thread #%1$d").build());
    /**
     * locations.yml save thread.
     */
    private final Timer saveThread = new Timer("Reconnect Saver");
    /**
     * Server socket listener.
     */
    private Collection<Channel> listeners = new HashSet<>();
    /**
     * Fully qualified connections.
     */
    private final Map<String, UserConnection> connections = new CaseInsensitiveMap<>();
    private final ReadWriteLock connectionLock = new ReentrantReadWriteLock();
    /**
     * Plugin manager.
     */
    @Getter
    public final PluginManager pluginManager = new PluginManager(this);
    @Getter
    @Setter
    private ReconnectHandler reconnectHandler;
    @Getter
    @Setter
    private ConfigurationAdapter configurationAdapter = new YamlConfig();
    @Getter
    private final File pluginsFolder = new File("plugins");
    @Getter
    private final BungeeScheduler scheduler = new BungeeScheduler();
    @Getter
    private LineReader lineReader;
    @Getter
    private final Logger logger;
    @Getter
    private ConnectionThrottle connectionThrottle;
    @Getter
    private @Nullable ServerIcon serverIcon;
    @Getter
    private final ServiceClient serviceClient = new ServiceClient();
    @Getter
    private final SessionService sessionService = new SessionService(this.serviceClient);
    @Getter
    private final ProfileService profileService = new ProfileService(this.serviceClient);
    @Getter
    private final ProfileCache profileCache = new ProfileCache();


    {
        // TODO: Proper fallback when we interface the manager
        getPluginManager().registerCommand(null, new CommandReload());
        getPluginManager().registerCommand(null, new CommandEnd());
        getPluginManager().registerCommand(null, new CommandList());
        getPluginManager().registerCommand(null, new CommandServer());
        getPluginManager().registerCommand(null, new CommandIP());
        getPluginManager().registerCommand(null, new CommandAlert());
        getPluginManager().registerCommand(null, new CommandBungee());
        getPluginManager().registerCommand(null, new CommandPerms());
        getPluginManager().registerCommand(null, new CommandSend());
        getPluginManager().registerCommand(null, new CommandFind());
    }

    public static BungeeCord getInstance() {
        return (BungeeCord) ProxyServer.getInstance();
    }

    public BungeeCord() throws IOException {
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .graphemeCluster(false)
                .build();

        lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build();

        logger = new BungeeLogger(this);
        System.setErr(new PrintStream(new LoggingOutputStream(logger, Level.SEVERE), true));
        System.setOut(new PrintStream(new LoggingOutputStream(logger, Level.INFO), true));
    }

    /**
     * Start this proxy instance by loading the configuration, plugins and
     * starting the connect thread.
     */
    @Override
    public void start() throws IOException {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED); // Eats performance

        Path iconPath = Path.of("server-icon.png");
        if (Files.exists(iconPath)) {
            this.serverIcon = ServerIcon.load(iconPath);
        }

        pluginsFolder.mkdir();
        pluginManager.detectPlugins(pluginsFolder);
        config.load();
        for (ListenerInfo info : config.getListeners()) {
            if (!info.isForceDefault() && reconnectHandler == null) {
                reconnectHandler = new YamlReconnectHandler();
                break;
            }
        }
        isRunning = true;

        profileCache.load();
        pluginManager.loadAndEnablePlugins();
        connectionThrottle = new ConnectionThrottle(config.getThrottle());
        startListeners();

        saveThread.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (getReconnectHandler() != null) {
                    getReconnectHandler().save();
                }
            }
        }, 0, TimeUnit.MINUTES.toMillis(5));
    }

    public void startListeners() {
        for (final ListenerInfo info : config.getListeners()) {
            ChannelFutureListener listener = new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        listeners.add(future.channel());
                        getLogger().info("Listening on " + info.getHost());
                    } else {
                        getLogger().log(Level.WARNING, "Could not bind to host " + info.getHost(), future.cause());
                    }
                }
            };
            new ServerBootstrap()
                    .channel(PipelineUtils.getServerChannelType())
                    .childAttr(PipelineUtils.LISTENER, info)
                    .childHandler(PipelineUtils.SERVER_CHILD)
                    .group(eventLoops)
                    .localAddress(info.getHost())
                    .bind().addListener(listener);
        }
    }

    public void stopListeners() {
        for (Channel listener : listeners) {
            getLogger().log(Level.INFO, "Closing listener {0}", listener);
            try {
                listener.close().syncUninterruptibly();
            } catch (ChannelException ex) {
                getLogger().severe("Could not close listen thread");
            }
        }
        listeners.clear();
    }

    @Override
    public void stop() {
        new Thread("Shutdown Thread") {
            @Override
            public void run() {
                BungeeCord.this.isRunning = false;

                stopListeners();
                getLogger().info("Closing pending connections");

                connectionLock.readLock().lock();
                try {
                    getLogger().info("Disconnecting " + connections.size() + " connections");
                    for (UserConnection user : connections.values()) {
                        user.disconnect(getTranslation("restart"));
                    }
                } finally {
                    connectionLock.readLock().unlock();
                }

                getLogger().info("Closing IO threads");
                eventLoops.shutdownGracefully();
                try {
                    eventLoops.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException ex) {
                }

                if (reconnectHandler != null) {
                    getLogger().info("Saving reconnect locations");
                    reconnectHandler.save();
                    reconnectHandler.close();
                }
                saveThread.cancel();

                // TODO: Fix this shit
                getLogger().info("Disabling plugins");
                for (Plugin plugin : pluginManager.getPlugins()) {
                    try {
                        plugin.onDisable();
                    } catch (Throwable t) {
                        getLogger().severe("Exception disabling plugin " + plugin.getDescription().getName());
                        t.printStackTrace();
                    }
                    getScheduler().cancel(plugin);
                }

                scheduler.shutdown();
                profileCache.save();
                getLogger().info("Thank you and goodbye");
                System.exit(0);
            }
        }.start();
    }

    /**
     * Broadcasts a packet to all clients that is connected to this instance.
     *
     * @param packet the packet to send
     */
    public void broadcast(DefinedPacket packet) {
        connectionLock.readLock().lock();
        try {
            for (UserConnection con : connections.values()) {
                con.unsafe().sendPacket(packet);
            }
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    @Override
    public String getName() {
        return "BungeeCord";
    }

    @Override
    public String getVersion() {
        return this.buildInformation.implVersion();
    }

    @Override
    public String getFullVersion() {
        return this.buildInformation.asFullVersionString();
    }

    @Override
    public String getTranslation(String name, Object... args) {
        String translation = "<translation '" + name + "' missing>";
        try {
            translation = MessageFormat.format(bundle.getString(name), args);
        } catch (MissingResourceException ex) {
        }
        return translation;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<ProxiedPlayer> getPlayers() {
        connectionLock.readLock().lock();
        try {
            return (Collection) new HashSet<>(connections.values());
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    @Override
    public int getOnlineCount() {
        return connections.size();
    }

    @Override
    public ProxiedPlayer getPlayer(String name) {
        connectionLock.readLock().lock();
        try {
            return connections.get(name);
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    @Override
    public Map<String, ServerInfo> getServers() {
        return config.getServers();
    }

    @Override
    public ServerInfo getServerInfo(String name) {
        return getServers().get(name);
    }

    @Override
    public byte getProtocolVersion() {
        return Vanilla.PROTOCOL_VERSION;
    }

    @Override
    public String getGameVersion() {
        return Vanilla.GAME_VERSION;
    }

    @Override
    public ServerInfo constructServerInfo(String name, InetSocketAddress address, String motd, boolean restricted, String secret) {
        return new BungeeServerInfo(name, address, motd, restricted, secret);
    }

    @Override
    public CommandSender getConsole() {
        return ConsoleCommandSender.getInstance();
    }

    @Override
    public void broadcast(String message) {
        getConsole().sendMessage(message);
        for (String line : TextWrapper.wrapText(message)) {
            broadcast(new Packet3Chat(line));
        }
    }

    public void addConnection(UserConnection con) {
        connectionLock.writeLock().lock();
        try {
            connections.put(con.getName(), con);
        } finally {
            connectionLock.writeLock().unlock();
        }
    }

    public void removeConnection(UserConnection con) {
        connectionLock.writeLock().lock();
        try {
            connections.remove(con.getName());
        } finally {
            connectionLock.writeLock().unlock();
        }
    }

    public Collection<String> getDisabledCommands() {
        return config.getDisabledCommands();
    }
}
