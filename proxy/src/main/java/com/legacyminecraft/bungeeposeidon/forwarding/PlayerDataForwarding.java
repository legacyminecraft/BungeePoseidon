package com.legacyminecraft.bungeeposeidon.forwarding;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.legacyminecraft.bungeeposeidon.api.profile.PlayerProfile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.security.GeneralSecurityException;

public final class PlayerDataForwarding {

    public static final String CHANNEL = "proxy:forward_player_data";
    public static final String MAC_ALGORITHM = "HmacSHA256";

    private PlayerDataForwarding() {
    }

    public static byte[] createForwardingData(byte[] secret, InetAddress address, PlayerProfile profile) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF(address.getHostAddress());
        output.writeLong(profile.getUniqueId().getMostSignificantBits());
        output.writeLong(profile.getUniqueId().getLeastSignificantBits());
        output.writeUTF(profile.getName());
        output.writeBoolean(profile.isOnlineProfile());
        byte[] forwardedData = output.toByteArray();

        try {
            Mac mac = Mac.getInstance(MAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, MAC_ALGORITHM));
            byte[] signature = mac.doFinal(forwardedData);
            ByteArrayOutputStream buf = new ByteArrayOutputStream(signature.length + forwardedData.length);
            buf.writeBytes(signature);
            buf.writeBytes(forwardedData);
            return buf.toByteArray();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
