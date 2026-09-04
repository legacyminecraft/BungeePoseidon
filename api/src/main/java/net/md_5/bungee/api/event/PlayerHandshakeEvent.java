package net.md_5.bungee.api.event;

import com.legacyminecraft.bungeeposeidon.api.profile.PlayerProfile;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.plugin.Event;

/**
 * Event called to represent a player first making their presence and username
 * known.
 */
@Data
@ToString(callSuper = false)
@EqualsAndHashCode(callSuper = false)
public class PlayerHandshakeEvent extends Event {

    /**
     * Connection attempting to login.
     */
    private final PendingConnection connection;
    /**
     * The player profile.
     */
    private final PlayerProfile profile;

    public PlayerHandshakeEvent(PendingConnection connection, PlayerProfile profile) {
        this.connection = connection;
        this.profile = profile;
    }
}
