package com.legacyminecraft.bungeeposeidon.login;

import com.legacyminecraft.bungeeposeidon.profile.MinecraftProfile;
import net.md_5.bungee.BungeeCord;

import java.util.logging.Level;

public final class VerifyNameCasingLoginStage implements LoginStage {

    @Override
    public void run(LoginProcessHandler loginProcessHandler) {
        if (!loginProcessHandler.getProfile().online()) {
            return;
        }

        String name = loginProcessHandler.getPlayerName();
        MinecraftProfile profile = loginProcessHandler.getProfile();
        if (!name.equals(profile.name())) {
            switch (BungeeCord.getInstance().config.getHandleWrongNameCasing()) {
                case KEEP -> {
                    MinecraftProfile newProfile = new MinecraftProfile(profile.id(), name, profile.online());
                    loginProcessHandler.setProfile(newProfile);
                }
                case REJECT -> {
                    BungeeCord.getInstance().getLogger().log(Level.INFO, "Disconnecting " + name + " as the correct name is '" + profile.name() + "' and wrongly cased names should be rejected.");
                    loginProcessHandler.disconnect("Wrongly cased name '" + name + "', correct name: '" + profile.name() + "'");
                }
            }
        }
    }
}
