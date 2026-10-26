package fr.florianpal.fauction.events;

import fr.florianpal.fauction.enums.CancelReason;
import fr.florianpal.fauction.objects.Auction;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class AuctionCancelEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Player player;

    private final Auction auction;

    private final CancelReason cancelReason;

    public AuctionCancelEvent(Player player, Auction auction, CancelReason cancelReason) {

        this.player = player;
        this.auction = auction;
        this.cancelReason = cancelReason;
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

    public CancelReason getCancelReason() {
        return cancelReason;
    }
}
