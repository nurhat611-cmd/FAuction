package fr.florianpal.fauction.events;

import fr.florianpal.fauction.objects.Auction;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ExpireAddEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final UUID playerUUID;

    private final Auction auction;

    public ExpireAddEvent(UUID playerUUID, Auction auction) {

        this.playerUUID = playerUUID;
        this.auction = auction;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public Auction getAuction() {
        return auction;
    }
}
