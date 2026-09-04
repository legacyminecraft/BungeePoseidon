package com.legacyminecraft.bungeeposeidon.profile;

import net.md_5.bungee.BungeeCord;

import java.util.UUID;

public record MinecraftProfile(UUID id, String name, boolean online) {

    public static MinecraftProfile createOffline(String name) {
        UUID id = BungeeCord.getInstance().config.isUseLegacyUuidGeneration()
                ? UuidUtil.generateLegacyOfflineUuid(name)
                : UuidUtil.generateOfflineUuid(name);
        return new MinecraftProfile(id, name, false);
    }
}
