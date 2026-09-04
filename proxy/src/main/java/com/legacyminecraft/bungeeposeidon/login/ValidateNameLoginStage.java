package com.legacyminecraft.bungeeposeidon.login;

import net.md_5.bungee.BungeeCord;

import java.util.regex.Pattern;

public final class ValidateNameLoginStage implements LoginStage {

    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 16;
    private static final Pattern ALLOWED_CHARS = Pattern.compile("[A-Za-z0-9_]*");

    @Override
    public void run(LoginProcessHandler loginProcessHandler) {
        String name = loginProcessHandler.getProfile().name();

        if (name.length() < MIN_LENGTH) {
            loginProcessHandler.disconnect("Your name is too short, minimum length: " + MIN_LENGTH);
            return;
        }

        boolean prefixed = BungeeCord.getInstance().config.isPrefixOfflineUsernames() && name.startsWith(".");
        if (name.length() > MAX_LENGTH) {
            loginProcessHandler.disconnect("Your name is too long, maximum length: " + (prefixed ? (MAX_LENGTH - 1) : MAX_LENGTH));
            return;
        }

        if (prefixed) {
            name = name.substring(1);
        }

        if (!ALLOWED_CHARS.matcher(name).matches()) {
            loginProcessHandler.disconnect("Your name is invalid, allowed characters: " + ALLOWED_CHARS);
        }
    }
}
