package net.md_5.bungee.api.connection;

import net.md_5.bungee.api.config.ListenerInfo;

import java.util.UUID;

/**
 * Represents a user attempting to log into the proxy.
 */
public interface PendingConnection extends Connection {

    /**
     * Get the player's name, if set.
     *
     * @return the name
     */
    String getName();

    /**
     * Get the player's UUID, if set.
     *
     * @return the UUID
     */
    UUID getUniqueId();

    /**
     * Get the numerical client version of the player attempting to log in.
     *
     * @return the protocol version of the remote client
     */
    int getVersion();

    /**
     * Get the listener that accepted this connection.
     *
     * @return the accepting listener
     */
    ListenerInfo getListener();
}
