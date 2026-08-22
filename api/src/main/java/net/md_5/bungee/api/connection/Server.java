package net.md_5.bungee.api.connection;

import net.md_5.bungee.api.config.ServerInfo;

/**
 * Represents a destination which this proxy might connect to.
 */
public interface Server extends Connection {

    /**
     * Returns the basic information about this server.
     *
     * @return the {@link ServerInfo} for this server
     */
    public ServerInfo getInfo();
}
