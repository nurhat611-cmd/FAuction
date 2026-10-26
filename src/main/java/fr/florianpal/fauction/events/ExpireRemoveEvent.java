package fr.florianpal.fauction.events;

import fr.florianpal.fauction.objects.Auction;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ExpireRemoveEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;

    private final Auction auction;

    public ExpireRemoveEvent(Player player, Auction auction) {

        this.player = player;
        this.auction = auction;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public Player getPlayer() {
        return player;
    }

    public Auction getAuction() {
        return auction;
    }


}
