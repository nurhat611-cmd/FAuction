package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.objects.Auction;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * How long a forgotten item stays in {@code /ah expire} before being deleted.
 * <p>
 * The base is {@code expiration.expiredStorageDays} of config.yml (3 days, -1 keeps everything
 * forever). The {@code FAuction_expireDay_1} .. {@code FAuction_expireDay_10} permissions each
 * add their number of days and stack together, so a traders guild can reward reputation with a
 * longer memory for the items of its members : holding _2 and _5 is 3 + 7 = 10 days.
 * <p>
 * The clock starts when the lot leaves the market. Expired rows carry the listing date of the
 * lot (that is what {@code expires.date} has always been), so the deadline of a row is
 * {@code listing date + market lifetime + storage} — no new column, and rows written before the
 * storage time existed get a deadline from the same formula.
 */
public class ExpireStorageManager {

    public static final String PERMISSION_PREFIX = "FAuction_expireDay_";

    private final FAuction plugin;

    public ExpireStorageManager(FAuction plugin) {
        this.plugin = plugin;
    }

    /**
     * The extra days a set of permissions grants : every held node adds its number.
     */
    public static int bonusDaysFromPermissions(Predicate<String> hasPermission) {
        int bonus = 0;
        for (int days = 1; days <= 10; days++) {
            if (hasPermission.test(PERMISSION_PREFIX + days)) {
                bonus += days;
            }
        }
        return bonus;
    }

    /**
     * A deadline that cannot overflow : with a very long market lifetime, a plain addition of
     * three durations could wrap around and read as "long overdue".
     */
    static long deadlineMillis(long listingMillis, long marketMillis, long storageMillis) {
        long deadline = listingMillis;
        deadline = storageMillis > Long.MAX_VALUE - deadline ? Long.MAX_VALUE : deadline + storageMillis;
        deadline = marketMillis > Long.MAX_VALUE - deadline ? Long.MAX_VALUE : deadline + marketMillis;
        return deadline;
    }

    public int getBonusDays(OfflinePlayer player) {
        if (player == null) {
            return 0;
        }
        return bonusDaysFromPermissions(permission -> hasPermission(player, permission));
    }

    public int getBaseStorageDays() {
        GlobalConfig config = globalConfig();
        return config != null ? config.getExpiredStorageDays() : 3;
    }

    /**
     * False only when config.yml disables the purge altogether (-1) : the historical behaviour,
     * where /ah expire kept everything forever.
     */
    public boolean isPurgeEnabled() {
        return getBaseStorageDays() >= 0;
    }

    /**
     * The full storage time of a player, base and stacked permission bonus together. -1 when
     * the purge is disabled.
     */
    public int getStorageDays(OfflinePlayer player) {
        int base = getBaseStorageDays();
        return base < 0 ? -1 : base + getBonusDays(player);
    }

    public long getStorageDurationMillis(OfflinePlayer player) {
        int days = getStorageDays(player);
        return days < 0 ? Long.MAX_VALUE : TimeUnit.DAYS.toMillis(days);
    }

    public long getDeadlineMillis(Auction expire) {
        return deadlineMillis(expire.getDate().getTime(), marketDurationMillis(),
                getStorageDurationMillis(Bukkit.getOfflinePlayer(expire.getPlayerUUID())));
    }

    public boolean isStorageExpired(Auction expire, Date now) {
        return getDeadlineMillis(expire) <= now.getTime();
    }

    /**
     * The same test with a storage duration resolved by the caller : the purge pass resolves
     * the permissions of an owner once, however many items they forgot.
     */
    public boolean isStorageExpired(Auction expire, long storageDurationMillis, Date now) {
        return deadlineMillis(expire.getDate().getTime(), marketDurationMillis(), storageDurationMillis) <= now.getTime();
    }

    private long marketDurationMillis() {
        GlobalConfig config = globalConfig();
        return config != null ? TimeUnit.SECONDS.toMillis(Math.max(0, config.getTime())) : 0;
    }

    /**
     * The permission of a player who is most often offline when their items come of age : read
     * on the online player when there is one, through Vault otherwise (which is how LuckPerms
     * answers for offline players). No Vault, no bonus — the base days still apply.
     */
    private boolean hasPermission(OfflinePlayer offlinePlayer, String permission) {
        Player online = offlinePlayer.getPlayer();
        if (online != null) {
            return online.hasPermission(permission);
        }

        VaultIntegrationManager vault = plugin.getVaultIntegrationManager();
        Permission perms = vault != null ? vault.getPerms() : null;
        if (perms == null) {
            return false;
        }

        try {
            return perms.playerHas(null, offlinePlayer, permission);
        } catch (RuntimeException e) {
            // A permission provider that refuses a null world must not stop the expiration
            // pass ; the owner simply gets no bonus this time.
            return false;
        }
    }

    private GlobalConfig globalConfig() {
        return plugin.getConfigurationManager() != null
                ? plugin.getConfigurationManager().getGlobalConfig()
                : null;
    }
}
