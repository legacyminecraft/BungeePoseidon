package com.legacyminecraft.bungeeposeidon.login;

import com.legacyminecraft.bungeeposeidon.api.profile.PlayerProfile;
import com.legacyminecraft.bungeeposeidon.profile.PlayerProfileImpl;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.event.PlayerHandshakeEvent;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.protocol.packet.Packet2Handshake;

import java.security.SecureRandom;

public final class SendHandshakeLoginStage implements LoginStage {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void run(LoginProcessHandler loginProcessHandler) {
        InitialHandler initialHandler = loginProcessHandler.getInitialHandler();
        PlayerProfile profile = new PlayerProfileImpl(loginProcessHandler.getProfile());
        PlayerHandshakeEvent event = new PlayerHandshakeEvent(initialHandler, profile);
        BungeeCord.getInstance().getPluginManager().callEvent(event);

        // Hybrid auth: offline mode proxies still verify clients that announced a session in the handshake
        initialHandler.setHybridAuthRequested(!initialHandler.isOnlineMode()
                && BungeeCord.getInstance().config.isHybridAuth()
                && initialHandler.hasHandshakeFlag(0));

        if (initialHandler.isOnlineMode() || initialHandler.isHybridAuthRequested()) {
            initialHandler.setServerId(Long.toHexString(RANDOM.nextLong()));
            initialHandler.unsafe().sendPacket(new Packet2Handshake(initialHandler.getServerId()));
        } else {
            initialHandler.unsafe().sendPacket(new Packet2Handshake("-"));
        }

        initialHandler.setThisState(InitialHandler.State.LOGIN);
    }
}
