package fr.florianpal.fauction.utils;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.enums.CurrencyType;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class CurrencyUtil {

    public static boolean haveCurrency(FAuction plugin, OfflinePlayer offlinePlayer, CurrencyType currencyType, double amount) {

        switch (currencyType) {
            case VAULT -> {
                double balance = plugin.getVaultIntegrationManager().getEconomy().getBalance(offlinePlayer);
                if (balance >= amount) {
                    return true;
                }
            }
            case EXPERIENCE -> {
                if (offlinePlayer.isOnline()) {
                    Player player = offlinePlayer.getPlayer();

                    if (player == null) return false;

                    if (player.getTotalExperience() >= amount) {
                        return true;
                    }
                }
            }
            case LEVEL -> {
                if (offlinePlayer.isOnline()) {
                    Player player = offlinePlayer.getPlayer();

                    if (player == null) return false;

                    if (player.getLevel() >= amount) {
                        return true;
                    }
                }
            }
        }

        return false;
    }


    public static boolean getCurrency(FAuction plugin, OfflinePlayer offlinePlayer, CurrencyType currencyType, double amount) {

        switch (currencyType) {
            case VAULT -> {
                EconomyResponse economyResponse5 = plugin.getVaultIntegrationManager().getEconomy().withdrawPlayer(offlinePlayer, amount);
                if (economyResponse5.transactionSuccess()) {
                    return true;
                }
            }
            case EXPERIENCE -> {
                if (offlinePlayer.isOnline()) {
                    Player player = offlinePlayer.getPlayer();

                    if (player == null) return false;

                    player.setTotalExperience((int) (player.getTotalExperience() - amount));
                    return true;
                }
            }
            case LEVEL -> {
                if (offlinePlayer.isOnline()) {
                    Player player = offlinePlayer.getPlayer();

                    if (player == null) return false;

                    player.setLevel((int) (player.getLevel() - amount));
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean giveCurrency(FAuction plugin, OfflinePlayer offlinePlayer, CurrencyType currencyType, double amount) {

        switch (currencyType) {
            case VAULT -> {
                EconomyResponse economyResponse4 = plugin.getVaultIntegrationManager().getEconomy().depositPlayer(offlinePlayer, amount);
                if (economyResponse4.transactionSuccess()) {
                    return true;
                }
            }
            case EXPERIENCE -> {
                if (offlinePlayer.isOnline()) {
                    Player player = offlinePlayer.getPlayer();

                    if (player == null) return false;

                    player.giveExp((int) amount);
                } else {
                    plugin.getCurrencyPendingQueries().addCurrencyPending(offlinePlayer.getUniqueId(), currencyType, amount);
                }
                return true;
            }
            case LEVEL -> {
                if (offlinePlayer.isOnline()) {
                    Player player = offlinePlayer.getPlayer();

                    if (player == null) return false;

                    player.giveExpLevels((int) amount);
                } else {
                    plugin.getCurrencyPendingQueries().addCurrencyPending(offlinePlayer.getUniqueId(), currencyType, amount);
                }
                return true;
            }
        }
        return false;
    }
}
