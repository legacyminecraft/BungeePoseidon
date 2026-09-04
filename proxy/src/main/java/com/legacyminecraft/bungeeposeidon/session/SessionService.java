package com.legacyminecraft.bungeeposeidon.session;

import com.google.gson.JsonObject;
import com.legacyminecraft.bungeeposeidon.service.ServiceClient;
import com.legacyminecraft.bungeeposeidon.service.ServiceClientException;
import org.jspecify.annotations.Nullable;

import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class SessionService {

    private final ServiceClient client;

    public SessionService(ServiceClient client) {
        this.client = client;
    }

    public boolean verifySession(String name, String serverId, @Nullable InetAddress address) throws ServiceClientException {
        StringBuilder sb = new StringBuilder("https://sessionserver.mojang.com/session/minecraft/hasJoined");
        sb.append("?username=").append(encode(name)).append("&serverId=").append(encode(serverId));
        InetAddress finalAddress = address == null || address.isLoopbackAddress() ? null : address;
        if (finalAddress != null) {
            sb.append("&ip=").append(encode(finalAddress.getHostAddress()));
        }

        String url = sb.toString();
        JsonObject response = this.client.get(url, JsonObject.class);
        return response != null;
    }

    private static String encode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }
}
