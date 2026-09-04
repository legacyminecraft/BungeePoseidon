package com.legacyminecraft.bungeeposeidon.login;

import com.legacyminecraft.bungeeposeidon.api.profile.PlayerProfile;
import com.legacyminecraft.bungeeposeidon.profile.PlayerProfileImpl;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.event.PlayerHandshakeEvent;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.protocol.packet.Packet2Handshake;

import java.util.Random;

public final class SendHandshakeLoginStage implements LoginStage {

    private static final Random RANDOM = new Random();

    @Override
    public void run(LoginProcessHandler loginProcessHandler) {
        InitialHandler initialHandler = loginProcessHandler.getInitialHandler();
        PlayerProfile profile = new PlayerProfileImpl(loginProcessHandler.getProfile());
        PlayerHandshakeEvent event = new PlayerHandshakeEvent(initialHandler, profile);
        BungeeCord.getInstance().getPluginManager().callEvent(event);

        if (initialHandler.isOnlineMode()) {
            initialHandler.setServerId(Long.toHexString(RANDOM.nextLong()));
            initialHandler.unsafe().sendPacket(new Packet2Handshake(initialHandler.getServerId()));
        } else {
            initialHandler.unsafe().sendPacket(new Packet2Handshake("-"));
        }

        initialHandler.setThisState(InitialHandler.State.LOGIN);
    }
}
