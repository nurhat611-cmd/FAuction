package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.utils.SerializationUtil;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpireStorageManagerTest extends FAuctionTestBase {

    private static final Date NOW = new Date(1_700_000_000_000L);

    private ExpireStorageManager storageManager;

    @BeforeEach
    void setUpStorageManager() {
        when(globalConfig.getTime()).thenReturn(3600);
        when(globalConfig.getExpiredStorageDays()).thenReturn(3);
        storageManager = new ExpireStorageManager(plugin);
    }

    @Test
    @DisplayName("The FAuction_expireDay_* permissions stack together")
    void bonusDaysStack() {
        assertEquals(0, ExpireStorageManager.bonusDaysFromPermissions(permission -> false));
        assertEquals(7, ExpireStorageManager.bonusDaysFromPermissions(Set.of(
                "FAuction_expireDay_2", "FAuction_expireDay_5")::contains));
        assertEquals(55, ExpireStorageManager.bonusDaysFromPermissions(permission -> true));
    }

    @Test
    @DisplayName("The bonus of an online owner is read on the player")
    void bonusOfOnlineOwner() {
        OfflinePlayer owner = mock(OfflinePlayer.class);
        Player online = mock(Player.class);
        when(owner.getPlayer()).thenReturn(online);
        when(online.hasPermission("FAuction_expireDay_4")).thenReturn(true);

        assertEquals(4, storageManager.getBonusDays(owner));
        assertEquals(7, storageManager.getStorageDays(owner));
    }

    @Test
    @DisplayName("Storage runs from the end of the market lifetime, three days by default")
    void storageDeadlineFollowsTheMarketLifetime() {
        // Listed 2 days ago, one hour on the market : still kept.
        assertFalse(storageManager.isStorageExpired(expireLot(ago(2, 1)), NOW));
        // Listed 4 days ago : the 3 storage days are over.
        assertTrue(storageManager.isStorageExpired(expireLot(ago(4, 1)), NOW));
        // Exactly one hour of market plus three days of storage ago : over, that very second.
        assertTrue(storageManager.isStorageExpired(expireLot(ago(3, 1)), NOW));
    }

    @Test
    @DisplayName("A base of -1 disables the purge, however old the item")
    void negativeBaseKeepsEverything() {
        when(globalConfig.getExpiredStorageDays()).thenReturn(-1);

        assertFalse(storageManager.isPurgeEnabled());
        assertFalse(storageManager.isStorageExpired(expireLot(ago(365, 1)), NOW));
    }

    @Test
    @DisplayName("The deadline cannot overflow into the past")
    void deadlineDoesNotOverflow() {
        assertEquals(Long.MAX_VALUE,
                ExpireStorageManager.deadlineMillis(Long.MAX_VALUE - 5, 1000, 1000));
    }

    private long ago(int days, int hours) {
        return NOW.getTime() - TimeUnit.DAYS.toMillis(days) - TimeUnit.HOURS.toMillis(hours);
    }

    private Auction expireLot(long listingMillis) {
        return new Auction(1, SELLER, "Seller", 10.0,
                SerializationUtil.serialize(namedItem(Material.DIAMOND, 1, "Forgotten")),
                listingMillis);
    }
}
