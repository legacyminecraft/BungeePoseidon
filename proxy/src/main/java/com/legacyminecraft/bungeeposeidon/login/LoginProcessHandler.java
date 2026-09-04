package com.legacyminecraft.bungeeposeidon.login;

import com.legacyminecraft.bungeeposeidon.profile.MinecraftProfile;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.connection.InitialHandler;

import java.util.Iterator;
import java.util.List;

@Getter
public final class LoginProcessHandler implements Runnable {

    private static final List<LoginStage> LOGIN_STAGES = List.of(
            new LookupProfileLoginStage(),
            new ValidateNameLoginStage(),
            new VerifyNameCasingLoginStage(),
            new SendHandshakeLoginStage()
    );

    private final InitialHandler initialHandler;
    private final String playerName;

    @Setter
    private MinecraftProfile profile;

    public LoginProcessHandler(InitialHandler initialHandler, String playerName) {
        this.initialHandler = initialHandler;
        this.playerName = playerName;
    }

    @Override
    public void run() {
        Iterator<LoginStage> iterator = LOGIN_STAGES.iterator();
        while (BungeeCord.getInstance().isRunning && this.initialHandler.getCh().getHandle().isOpen() && iterator.hasNext()) {
            LoginStage loginStage = iterator.next();
            loginStage.run(this);
        }
    }

    public void disconnect(String message) {
        this.initialHandler.disconnect(message);
    }
}
