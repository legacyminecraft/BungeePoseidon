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

    /**
     * Returns the flag bitmask the client appended to its handshake name after
     * a {@code ;}. The mask is written in base 36 and never exceeds 12
     * characters in length. For example, {@code Name;hra0ht} means bits
     * {@code 0} and {@code 30} are set.
     * <p>
     * Bits 0 to 29 are features and bits 30 and up are client identities.
     * <p>
     * Known bits:
     * <ul>
     *     <li>{@code 0}: The client has a session and can authenticate
     *     <li>{@code 30}: The client is Beta Evolutions
     * </ul>
     *
     * @return the flag bitmask, or 0 if the client sent none
     */
    long getHandshakeFlags();

    /**
     * Returns if the client set the given bit in its handshake flags.
     *
     * @param bit the bit number, from 0 to 61
     * @return {@code true} if the client set the bit
     * @see #getHandshakeFlags()
     */
    boolean hasHandshakeFlag(int bit);
}
