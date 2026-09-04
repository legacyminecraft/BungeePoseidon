package com.legacyminecraft.bungeeposeidon.login;

import com.legacyminecraft.bungeeposeidon.profile.MinecraftProfile;
import com.legacyminecraft.bungeeposeidon.profile.ProfileLookupCallback;
import com.legacyminecraft.bungeeposeidon.profile.ProfileNotFoundException;
import com.legacyminecraft.bungeeposeidon.service.ServiceClientException;
import net.md_5.bungee.BungeeCord;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public final class LookupProfileLoginStage implements LoginStage {

    @Override
    public void run(LoginProcessHandler loginProcessHandler) {
        String name = loginProcessHandler.getPlayerName();
        MinecraftProfile profile;
        Optional<MinecraftProfile> optional = BungeeCord.getInstance().getProfileCache().getProfile(name, true);
        if (optional.isPresent() && optional.get().online()) {
            profile = optional.get();
        } else {
            try {
                profile = lookupProfile(name);
            } catch (ProfileNotFoundException e) {
                if (BungeeCord.getInstance().config.isAllowOfflineAccounts()) {
                    if (BungeeCord.getInstance().config.isPrefixOfflineUsernames() && !name.startsWith(".")) {
                        name = "." + name;
                    }
                    profile = MinecraftProfile.createOffline(name);
                } else {
                    BungeeCord.getInstance().getLogger().log(Level.INFO, "Disconnecting " + name + " as they do not have an online profile and offline accounts are disallowed.");
                    loginProcessHandler.disconnect("Offline accounts are not supported");
                    return;
                }
            } catch (ServiceClientException e) {
                BungeeCord.getInstance().getLogger().log(Level.WARNING, "Failed to lookup profile for " + name, e);
                loginProcessHandler.disconnect("Failed to lookup profile");
                return;
            }
        }

        BungeeCord.getInstance().getLogger().log(Level.INFO, "UUID of player " + name + " is " + profile.id());
        BungeeCord.getInstance().getProfileCache().addProfile(profile);
        loginProcessHandler.setProfile(profile);
    }

    private static MinecraftProfile lookupProfile(String name) throws ProfileNotFoundException, ServiceClientException {
        CompletableFuture<MinecraftProfile> future = new CompletableFuture<>();
        switch (BungeeCord.getInstance().config.getProfileLookupMethod()) {
            case GET -> future.complete(BungeeCord.getInstance().getProfileService().lookupProfileByName(name));
            case POST -> BungeeCord.getInstance().getProfileService().lookupProfilesByNames(Set.of(name), new ProfileLookupCallback() {
                @Override
                public void onLookupSuccess(MinecraftProfile profile) {
                    future.complete(profile);
                }

                @Override
                public void onLookupFailure(Throwable cause) {
                    future.completeExceptionally(cause);
                }
            });
        }

        try {
            return future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            switch (cause) {
                case ProfileNotFoundException profileNotFound -> throw profileNotFound;
                case ServiceClientException clientException -> throw clientException;
                default -> throw new RuntimeException(cause);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
