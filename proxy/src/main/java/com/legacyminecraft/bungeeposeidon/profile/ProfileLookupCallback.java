package com.legacyminecraft.bungeeposeidon.profile;

public interface ProfileLookupCallback {

    void onLookupSuccess(MinecraftProfile profile);

    void onLookupFailure(Throwable cause);
}
