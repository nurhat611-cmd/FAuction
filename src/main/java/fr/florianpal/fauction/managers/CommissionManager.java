package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.FreeQuotaMode;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.queries.PlayerProfileQueries;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The auction commission and the free listings that escape it.
 * <p>
 * The commission is a percentage of the price ({@code commission.percent} of config.yml, 7% by
 * default), charged to the seller when the lot is listed with {@code /ah sell}. A player holding
 * one of the {@code fauction.nocommission.<n>} permissions lists {@code n} lots without paying
 * it ; when several tiers are held at once — a base group and a donor group, typically — the
 * highest one wins, they never add up. How the quota is spent (a lifetime budget, or a number of
 * simultaneous free lots) is {@link FreeQuotaMode}, chosen in config.yml.
 * <p>
 * This class only decides and counts. Taking and refunding the money itself stays with the sell
 * command, which is the one knowing when the item really leaves the inventory and when a failed
 * save has to give everything back.
 */
public class CommissionManager {

    public static final String PERMISSION_PREFIX = "fauction.nocommission.";

    /**
     * The tiers a permission can grant, highest first : the first one held is the quota.
     */
    public static final List<Integer> FREE_TIERS = List.of(25, 16, 10, 7, 5, 3);

    private final FAuction plugin;

    public CommissionManager(FAuction plugin) {
        this.plugin = plugin;
    }

    /**
     * The quota of a set of permissions : the highest tier held, zero when none is.
     */
    public static int quotaFromPermissions(Predicate<String> hasPermission) {
        for (int tier : FREE_TIERS) {
            if (hasPermission.test(PERMISSION_PREFIX + tier)) {
                return tier;
            }
        }
        return 0;
    }

    /**
     * The commission of a price, rounded to the cent : an economy balance has no use for the
     * {@code 69.99999999999} a raw multiplication hands back, and neither has the message
     * announcing it.
     */
    public static double calculateFee(double price, double percent) {
        if (!Double.isFinite(price) || !Double.isFinite(percent) || price <= 0 || percent <= 0) {
            return 0;
        }
        return Math.round(price * percent) / 100.0;
    }

    /**
     * How many of these lots were listed without commission.
     */
    public static int countCommissionFree(List<Auction> auctions) {
        int count = 0;
        for (Auction auction : auctions) {
            if (auction.isCommissionFree()) {
                count++;
            }
        }
        return count;
    }

    public boolean isEnabled() {
        GlobalConfig config = globalConfig();
        return config != null && config.isCommissionEnabled();
    }

    public double getPercent() {
        GlobalConfig config = globalConfig();
        return config != null ? config.getCommissionPercent() : 0;
    }

    public FreeQuotaMode getMode() {
        GlobalConfig config = globalConfig();
        FreeQuotaMode mode = config != null ? config.getCommissionFreeQuotaMode() : null;
        return mode != null ? mode : FreeQuotaMode.LIFETIME;
    }

    /**
     * The commission listing an item at this price costs. Zero when the commission is switched
     * off : a free listing is never spent on escaping a commission nobody pays.
     */
    public double getFee(double price) {
        return isEnabled() ? calculateFee(price, getPercent()) : 0;
    }

    /**
     * The number of lots this player can list without commission, from their permissions alone.
     */
    public int getFreeQuota(Player player) {
        return quotaFromPermissions(player::hasPermission);
    }

    /**
     * The free listings the player can still use, the number the sell command decides on and the
     * gui shows.
     *
     * @param playerAuctions    the lots the player currently has on the market (what {@code ACTIVE}
     *                          counts)
     * @param usedFreeListings  the lifetime counter of the player (what {@code LIFETIME} counts),
     *                          read by the caller in its asynchronous step :
     *                          {@link #getUsedFreeListings(UUID)} is a database read and has no
     *                          business on a region thread
     */
    public int getRemainingFreeListings(Player player, List<Auction> playerAuctions, int usedFreeListings) {
        int used = getMode() == FreeQuotaMode.ACTIVE ? countCommissionFree(playerAuctions) : usedFreeListings;
        return Math.max(0, getFreeQuota(player) - used);
    }

    /**
     * The lifetime counter of a player. A database read : call it asynchronously.
     */
    public int getUsedFreeListings(UUID playerUuid) {
        PlayerProfileQueries queries = plugin.getPlayerProfileQueries();
        return queries != null ? queries.getUsedFreeListings(playerUuid) : 0;
    }

    /**
     * Marks one free listing as spent. Only the lifetime mode has anything to write down : in
     * {@code ACTIVE} mode the lot itself carries its free flag, and the slot frees itself when
     * the lot leaves the market. A database write : call it asynchronously, and only once the
     * lot is really saved — a sale the database refused must not cost the player a free listing.
     */
    public void recordFreeListing(UUID playerUuid, String playerName) {
        if (getMode() != FreeQuotaMode.LIFETIME) {
            return;
        }
        PlayerProfileQueries queries = plugin.getPlayerProfileQueries();
        if (queries != null) {
            queries.incrementUsedFreeListings(playerUuid, playerName);
        }
    }

    /**
     * Every lifetime counter back to zero. Part of the player data wipe, with the auctions, the
     * expired items and the history.
     */
    public void resetAllCounters() {
        PlayerProfileQueries queries = plugin.getPlayerProfileQueries();
        if (queries != null) {
            queries.deleteAll();
        }
    }

    private GlobalConfig globalConfig() {
        return plugin.getConfigurationManager() != null
                ? plugin.getConfigurationManager().getGlobalConfig()
                : null;
    }
}
