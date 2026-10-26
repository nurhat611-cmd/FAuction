package fr.florianpal.fauction.placeholders;

import fr.florianpal.fauction.FAuction;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class FPlaceholderExpansion extends PlaceholderExpansion {

    private final FAuction plugin;

    public FPlaceholderExpansion(FAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "fauction";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Florianpal";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (identifier.contains("auction_number")) {
            return String.valueOf(plugin.getAuctionCommandManager().getCache().getOrDefault(player.getUniqueId(), new ArrayList<>()).size());
        } else if (identifier.contains("expire_number")) {
            return String.valueOf(plugin.getExpireCommandManager().getCache().getOrDefault(player.getUniqueId(), new ArrayList<>()).size());
        } else if (identifier.contains("historic_number")) {
            return String.valueOf(plugin.getHistoricCommandManager().getCache().getOrDefault(player.getUniqueId(), new ArrayList<>()).size());
        }
        return null;
    }

}
