package com.legacyminecraft.bungeeposeidon.ping;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.legacyminecraft.bungeeposeidon.api.event.ping.ServerListPingEvent;
import com.legacyminecraft.bungeeposeidon.api.profile.PlayerProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.config.ListenerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.jspecify.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class ServerListPingHandler {

    private static final Random RANDOM = new Random();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private final ListenerInfo listener;
    private final InetSocketAddress clientAddress;
    private int protocolVersion;
    private @Nullable String address;
    private int port;
    private boolean receivedHandshake;
    @Getter
    private boolean closed;

    public ServerListPingHandler(ListenerInfo listener, InetSocketAddress clientAddress) {
        this.listener = listener;
        this.clientAddress = clientAddress;
    }

    public void handlePing(DataInput input, DataOutput output) throws IOException {
        int length = readVarInt(input);
        int id = readVarInt(input);
        if (id == 0) {
            if (length != 1) {
                this.protocolVersion = readVarInt(input);
                this.address = readUtf8String(input);
                this.port = input.readUnsignedShort();
                readVarInt(input);
                this.receivedHandshake = true;
            } else {
                sendStatusResponse(output);
            }
        } else if (id == 1) {
            long pingId = input.readLong();
            sendPongResponse(pingId, output);
        }
    }

    private void sendStatusResponse(DataOutput output) throws IOException {
        Preconditions.checkState(this.receivedHandshake, "handshake has not been received");

        ProxiedPlayer[] onlinePlayers = BungeeCord.getInstance().getPlayers().toArray(new ProxiedPlayer[0]);
        ServerListPingEvent event = new ServerListPingEvent(
                this.clientAddress,
                this.protocolVersion,
                InetSocketAddress.createUnresolved(this.address, this.port),
                "b1.7.3",
                this.protocolVersion,
                onlinePlayers.length,
                this.listener.getMaxPlayers(),
                this.listener.getMotd(),
                BungeeCord.getInstance().getServerIcon());

        event.getPlayerSample().addAll(createSample(onlinePlayers));
        BungeeCord.getInstance().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            this.closed = true;
            return;
        }

        String response = buildResponse(event);
        ByteArrayDataOutput data = ByteStreams.newDataOutput();
        writeVarInt(0, data);
        writeUtf8String(response, data);
        byte[] bytes = data.toByteArray();

        writeVarInt(bytes.length, output);
        output.write(bytes);
    }

    private void sendPongResponse(long pingId, DataOutput output) throws IOException {
        ByteArrayDataOutput data = ByteStreams.newDataOutput();
        writeVarInt(1, data);
        data.writeLong(pingId);
        byte[] bytes = data.toByteArray();

        writeVarInt(bytes.length, output);
        output.write(bytes);
        this.closed = true;
    }

    private List<PlayerProfile> createSample(ProxiedPlayer[] onlinePlayers) {
        int sampleSize = Math.min(onlinePlayers.length, this.listener.getMaxSampleSize());
        if (sampleSize <= 0) {
            return List.of();
        }

        List<PlayerProfile> sample = new ObjectArrayList<>();
        int offset = RANDOM.nextInt(onlinePlayers.length - sampleSize + 1);

        for (int i = 0; i < sampleSize; i++) {
            ProxiedPlayer player = onlinePlayers[offset + i];
            sample.add(new InternalPlayerProfile(player.getName()));
        }

        Collections.shuffle(sample);
        return sample;
    }

    private static String buildResponse(ServerListPingEvent event) {
        JsonObject response = new JsonObject();

        JsonObject version = new JsonObject();
        version.addProperty("protocol", event.getProtocolVersion());
        version.addProperty("name", event.getVersion());
        response.add("version", version);

        if (!event.shouldHidePlayers()) {
            JsonObject players = new JsonObject();
            players.addProperty("online", event.getNumPlayers());
            players.addProperty("max", event.getMaxPlayers());

            JsonArray sample = new JsonArray();
            event.getPlayerSample().forEach(profile -> {
                JsonObject player = new JsonObject();
                player.addProperty("name", profile.getName());
                player.addProperty("id", profile.getUniqueId().toString());
                sample.add(player);
            });

            players.add("sample", sample);
            response.add("players", players);
        }

        JsonObject description = new JsonObject();
        description.addProperty("text", event.getMotd());
        response.add("description", description);

        if (event.getServerIcon() != null) {
            response.addProperty("favicon", event.getServerIcon().asBase64String());
        }

        return GSON.toJson(response);
    }

    private static int readVarInt(DataInput input) throws IOException {
        int value = 0;
        int i = 0;
        int b;
        while (((b = input.readUnsignedByte()) & 0x80) != 0) {
            value |= (b & 0x7F) << i;
            i += 7;
            if (i > 35) {
                throw new IOException("received VarInt is longer than maximum 5 bytes");
            }
        }
        return value | (b << i);
    }

    private static void writeVarInt(int value, DataOutput output) throws IOException {
        while ((value & 0xFFFFFF80) != 0L) {
            output.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.write(value & 0x7F);
    }

    private static String readUtf8String(DataInput input) throws IOException {
        int length = readVarInt(input);
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeUtf8String(String string, DataOutput output) throws IOException {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length, output);
        output.write(bytes);
    }

    // TODO: rework when a proper profile api is added
    private record InternalPlayerProfile(String name) implements PlayerProfile {
        @Override
        public @Nullable String getName() {
            return name();
        }

        @Override
        public UUID getUniqueId() {
            return NIL_UUID;
        }

        @Override
        public boolean isOnlineProfile() {
            return false;
        }
    }
}
