package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.enums.FreeQuotaMode;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.utils.SerializationUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommissionManagerTest extends FAuctionTestBase {

    @Test
    @DisplayName("The highest nocommission permission wins and never stacks")
    void quotaUsesTheHighestTier() {
        assertEquals(0, CommissionManager.quotaFromPermissions(permission -> false));
        assertEquals(3, CommissionManager.quotaFromPermissions(Set.of("fauction.nocommission.3")::contains));
        assertEquals(10, CommissionManager.quotaFromPermissions(Set.of(
                "fauction.nocommission.3",
                "fauction.nocommission.10",
                "fauction.nocommission.5")::contains));
        assertEquals(25, CommissionManager.quotaFromPermissions(Set.of("fauction.nocommission.25")::contains));
        assertEquals(25, CommissionManager.quotaFromPermissions(permission -> true));
    }

    @Test
    @DisplayName("The fee is a rounded percentage and refuses unusable numbers")
    void feeIsRoundedPercentage() {
        assertEquals(70.0, CommissionManager.calculateFee(1000, 7.0));
        assertEquals(7.0, CommissionManager.calculateFee(99.99, 7.0));
        assertEquals(23.31, CommissionManager.calculateFee(333, 7.0));
        assertEquals(0.0, CommissionManager.calculateFee(0, 7.0));
        assertEquals(0.0, CommissionManager.calculateFee(100, 0));
        assertEquals(0.0, CommissionManager.calculateFee(Double.NaN, 7.0));
        assertEquals(0.0, CommissionManager.calculateFee(-100, 7.0));
    }

    @Test
    @DisplayName("A disabled commission has no fee")
    void disabledCommissionHasNoFee() {
        CommissionManager manager = new CommissionManager(plugin);
        when(globalConfig.isCommissionEnabled()).thenReturn(false);
        when(globalConfig.getCommissionPercent()).thenReturn(7.0);

        assertEquals(0.0, manager.getFee(1000));

        when(globalConfig.isCommissionEnabled()).thenReturn(true);
        assertEquals(70.0, manager.getFee(1000));
    }

    @Test
    @DisplayName("Lifetime mode subtracts the persistent counter from the permission quota")
    void lifetimeRemainingUsesCounter() {
        CommissionManager manager = new CommissionManager(plugin);
        when(globalConfig.getCommissionFreeQuotaMode()).thenReturn(FreeQuotaMode.LIFETIME);
        Player player = quotaPlayer(10);

        assertEquals(10, manager.getFreeQuota(player));
        assertEquals(6, manager.getRemainingFreeListings(player, List.of(), 4));
        assertEquals(0, manager.getRemainingFreeListings(player, List.of(), 12));
    }

    @Test
    @DisplayName("Active mode counts only flagged lots still on the market")
    void activeRemainingUsesFlaggedAuctions() {
        CommissionManager manager = new CommissionManager(plugin);
        when(globalConfig.getCommissionFreeQuotaMode()).thenReturn(FreeQuotaMode.ACTIVE);
        Player player = quotaPlayer(10);
        List<Auction> auctions = List.of(
                auctionWithFlag(1, true),
                auctionWithFlag(2, true),
                auctionWithFlag(3, false));

        assertEquals(2, CommissionManager.countCommissionFree(auctions));
        assertEquals(8, manager.getRemainingFreeListings(player, auctions, 99));
    }

    @Test
    @DisplayName("Recording is safe before a database profile exists")
    void recordingToleratesMissingProfileQueries() {
        CommissionManager manager = new CommissionManager(plugin);
        when(globalConfig.getCommissionFreeQuotaMode()).thenReturn(FreeQuotaMode.LIFETIME);

        assertEquals(0, manager.getUsedFreeListings(SELLER));
        assertDoesNotThrow(() -> manager.recordFreeListing(SELLER, "Seller"));
        assertDoesNotThrow(manager::resetAllCounters);
    }

    private Player quotaPlayer(int tier) {
        Player player = mock(Player.class);
        when(player.hasPermission(CommissionManager.PERMISSION_PREFIX + tier)).thenReturn(true);
        return player;
    }

    private Auction auctionWithFlag(int id, boolean commissionFree) {
        return new Auction(
                id,
                SELLER,
                "Seller",
                10.0,
                SerializationUtil.serialize(namedItem(Material.DIAMOND, 1, "Auction " + id)),
                System.currentTimeMillis(),
                commissionFree);
    }
}
