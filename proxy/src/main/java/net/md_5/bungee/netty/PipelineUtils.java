package net.md_5.bungee.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueIoHandler;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.BungeeServerInfo;
import net.md_5.bungee.ServerConnector;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.protocol.Vanilla;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class PipelineUtils {

    public static final AttributeKey<ListenerInfo> LISTENER = AttributeKey.valueOf("ListerInfo");
    public static final AttributeKey<UserConnection> USER = AttributeKey.valueOf("User");
    public static final AttributeKey<BungeeServerInfo> TARGET = AttributeKey.valueOf("Target");
    public static final ChannelInitializer<Channel> SERVER_CHILD = new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
            InetAddress address = ((InetSocketAddress) ch.remoteAddress()).getAddress();
            if (!address.isLoopbackAddress() && BungeeCord.getInstance().getConnectionThrottle().throttle(address)) {
                ch.close();
                return;
            }

            BASE.initChannel(ch);
            ch.pipeline().get(HandlerBoss.class).setHandler(new InitialHandler(ProxyServer.getInstance(), ch.attr(LISTENER).get()));
        }
    };
    public static final ChannelInitializer<Channel> CLIENT = new ChannelInitializer<Channel>() {
        @Override
        protected void initChannel(Channel ch) throws Exception {
            BASE.initChannel(ch);
            ch.pipeline().get(HandlerBoss.class).setHandler(new ServerConnector(ProxyServer.getInstance(), ch.attr(USER).get(), ch.attr(TARGET).get()));
        }
    };
    public static final Base BASE = new Base();
    private static final DefinedPacketEncoder packetEncoder = new DefinedPacketEncoder();
    public static String TIMEOUT_HANDLER = "timeout";
    public static String PACKET_DECODE_HANDLER = "packet-decoder";
    public static String PACKET_ENCODE_HANDLER = "packet-encoder";
    public static String BOSS_HANDLER = "inbound-boss";

    public final static class Base extends ChannelInitializer<Channel> {

        @Override
        public void initChannel(Channel ch) throws Exception {
            try {
                ch.config().setOption(ChannelOption.IP_TOS, 0x18);
            } catch (ChannelException ex) {
                // IP_TOS is not supported (Windows XP / Windows Server 2003)
            }

            HandlerBoss handlerBoss = new HandlerBoss();
            ch.pipeline().addLast(TIMEOUT_HANDLER, new ReadTimeoutHandler(BungeeCord.getInstance().config.getTimeout(), TimeUnit.MILLISECONDS));
            ch.pipeline().addLast(PACKET_DECODE_HANDLER, new PacketDecoder(Vanilla.getInstance(), handlerBoss));
            ch.pipeline().addLast(PACKET_ENCODE_HANDLER, packetEncoder);
            ch.pipeline().addLast(BOSS_HANDLER, handlerBoss);
        }
    }

    public static EventLoopGroup newEventLoopGroup(int threads, ThreadFactory threadFactory) {
        IoHandlerFactory ioHandlerFactory =
                Epoll.isAvailable() ? EpollIoHandler.newFactory()
                : KQueue.isAvailable() ? KQueueIoHandler.newFactory()
                : NioIoHandler.newFactory();

        return new MultiThreadIoEventLoopGroup(threads, threadFactory, ioHandlerFactory);
    }

    public static Class<? extends ServerSocketChannel> getServerChannelType() {
        return Epoll.isAvailable() ? EpollServerSocketChannel.class
                : KQueue.isAvailable() ? KQueueServerSocketChannel.class
                : NioServerSocketChannel.class;
    }

    public static Class<? extends SocketChannel> getChannelType() {
        return Epoll.isAvailable() ? EpollSocketChannel.class
                : KQueue.isAvailable() ? KQueueSocketChannel.class
                : NioSocketChannel.class;
    }
}
