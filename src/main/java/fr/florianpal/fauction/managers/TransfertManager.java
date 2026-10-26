package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.utils.SerializationUtil;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.Map;

public class TransfertManager {

    private final FAuction plugin;

    public TransfertManager(FAuction plugin) {
        this.plugin = plugin;
    }

    public void transfertBDD(boolean toPaper) {

        FAuction.newChain().asyncFirst(() -> plugin.getAuctionQueries().getAuctionsBrut()).asyncLast(auctions -> {
            try {
                for (Map.Entry<Integer, byte[]> entry : auctions.entrySet()) {
                    if (toPaper) {
                        ItemStack item = SerializationUtil.deserializeBukkit(entry.getValue());
                        plugin.getAuctionQueries().updateItem(entry.getKey(), SerializationUtil.serializePaper(item));
                    } else {
                        ItemStack item = SerializationUtil.deserializePaper(entry.getValue());
                        plugin.getAuctionQueries().updateItem(entry.getKey(), SerializationUtil.serializeBukkit(item));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).execute();

        FAuction.newChain().asyncFirst(() -> plugin.getExpireQueries().getExpiresBrut()).asyncLast(expires -> {
            try {
                for (Map.Entry<Integer, byte[]> entry : expires.entrySet()) {
                    if (toPaper) {
                        ItemStack item = SerializationUtil.deserializeBukkit(entry.getValue());
                        plugin.getExpireQueries().updateItem(entry.getKey(), SerializationUtil.serializePaper(item));
                    } else {
                        ItemStack item = SerializationUtil.deserializePaper(entry.getValue());
                        plugin.getExpireQueries().updateItem(entry.getKey(), SerializationUtil.serializeBukkit(item));
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).execute();
    }
}
