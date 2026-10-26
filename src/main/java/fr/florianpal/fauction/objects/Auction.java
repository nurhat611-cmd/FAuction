package fr.florianpal.fauction.objects;

import fr.florianpal.fauction.utils.SerializationUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

@Getter
public class Auction {

    private final int id;

    private final UUID playerUUID;

    private final String playerName;

    @Setter
    private double price;

    private final ItemStack itemStack;

    private final Date date;

    /**
     * Listed without paying the auction commission, out of the {@code fauction.nocommission.<n>}
     * quota of the seller. In {@code ACTIVE} quota mode this flag IS the quota accounting : the
     * free listings a player has left are their quota minus the flagged lots still on the market.
     */
    private final boolean commissionFree;

    public Auction(int id, UUID playerUUID, String playerName, double price, byte[] item, long date) {
        this(id, playerUUID, playerName, price, item, date, false);
    }

    public Auction(int id, UUID playerUUID, String playerName, double price, byte[] item, long date, boolean commissionFree) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.price = price;
        try {
            this.itemStack = SerializationUtil.deserialize(item);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.date = new Date(date);
        this.commissionFree = commissionFree;
    }

}
