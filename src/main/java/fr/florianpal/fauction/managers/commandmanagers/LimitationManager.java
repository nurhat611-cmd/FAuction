package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuction;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.function.Predicate;

public class LimitationManager {

    /**
     * The extra-slot permissions of the traders guild : {@code FAuction_dubleSlot_1} ..
     * {@code FAuction_dubleSlot_10}. Spelled exactly as the guild plugin grants them ; Bukkit
     * and LuckPerms both fold permission names to lower case, so the case never decides anything.
     */
    public static final String EXTRA_SLOT_PERMISSION_PREFIX = "FAuction_dubleSlot_";

    private final FAuction plugin;

    public LimitationManager(FAuction plugin) {
        this.plugin = plugin;
    }

    /**
     * The slots a set of permissions adds : every held {@code FAuction_dubleSlot_<n>} adds its
     * {@code n}, and they stack (holding _3 and _10 is +13), so a guild can compose any bonus
     * between 1 and 55 out of the ten nodes.
     */
    public static int extraSlotsFromPermissions(Predicate<String> hasPermission) {
        int extra = 0;
        for (int slots = 1; slots <= 10; slots++) {
            if (hasPermission.test(EXTRA_SLOT_PERMISSION_PREFIX + slots)) {
                extra += slots;
            }
        }
        return extra;
    }

    public int getExtraSlots(Player player) {
        return extraSlotsFromPermissions(player::hasPermission);
    }

    /**
     * The limit of the rank alone, before the {@code FAuction_dubleSlot_*} bonus : the LuckPerms
     * meta when config.yml asks for it (and LuckPerms is really there), the group limits of
     * config.yml otherwise. -1 means unlimited.
     */
    public int getBaseAuctionLimitation(Player player) {
        if (plugin.getConfigurationManager().getGlobalConfig().isLimitationsUseMetaLuckperms()
                && plugin.getLuckPermsImplementation() != null) {
            return getAuctionLimitationByMeta(player);
        }
        return getAuctionLimitationByConfig(player);
    }

    /**
     * The number of lots the player may have on the market at once, rank limit and permission
     * bonus together. -1 (unlimited) stays -1 : a bonus on top of infinity is still infinity,
     * and the call sites test the -1.
     */
    public int getAuctionLimitation(Player player) {
        int base = getBaseAuctionLimitation(player);
        return base < 0 ? -1 : base + getExtraSlots(player);
    }

    public int getAuctionLimitationByConfig(Player player) {
        Permission perms = plugin.getVaultIntegrationManager().getPerms();
        Map<String, Integer> limitations = plugin.getConfigurationManager().getGlobalConfig().getLimitations();
        String[] playerGroup;
        // Deny by default rather than NPE/unlimited if "limitations" or its "default" key is missing.
        int limit = limitations.getOrDefault("default", 0);
        if (perms != null) {
            String primaryGroup = perms.getPrimaryGroup(player);
            if (limitations.containsKey(primaryGroup) && (limit < limitations.get(primaryGroup))) {
                limit = limitations.get(primaryGroup);
            }
            playerGroup = perms.getPlayerGroups(player);
            for (String s : playerGroup) {
                if (limitations.containsKey(s) && (limit < limitations.get(s))) {
                    limit = limitations.get(s);
                }
            }
        }
        return limit;
    }

    public int getAuctionLimitationByMeta(Player player) {
        return plugin.getLuckPermsImplementation().getMetaData(player);
    }
}
