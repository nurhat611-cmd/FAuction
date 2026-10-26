package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.managers.VaultIntegrationManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LimitationManagerTest extends FAuctionTestBase {

    private LimitationManager limitationManager;

    private Player player;

    @BeforeEach
    void setUpLimitationManager() {

        VaultIntegrationManager vaultIntegrationManager = mock(VaultIntegrationManager.class);
        when(vaultIntegrationManager.getPerms()).thenReturn(null);
        when(plugin.getVaultIntegrationManager()).thenReturn(vaultIntegrationManager);

        limitationManager = new LimitationManager(plugin);
        player = server.addPlayer();
    }

    @Test
    @DisplayName("An empty limitations map denies selling instead of throwing")
    void emptyLimitationsMapDeniesByDefault() {

        when(globalConfig.getLimitations()).thenReturn(Map.of());

        assertEquals(0, limitationManager.getAuctionLimitationByConfig(player));
    }

    @Test
    @DisplayName("The configured default limit is used when no permission group matches")
    void configuredDefaultIsUsed() {

        when(globalConfig.getLimitations()).thenReturn(Map.of("default", 5));

        assertEquals(5, limitationManager.getAuctionLimitationByConfig(player));
    }

    @Test
    @DisplayName("The FAuction_dubleSlot_* permissions stack together")
    void extraSlotPermissionsStack() {
        assertEquals(0, LimitationManager.extraSlotsFromPermissions(permission -> false));
        assertEquals(13, LimitationManager.extraSlotsFromPermissions(Set.of(
                "FAuction_dubleSlot_3", "FAuction_dubleSlot_10")::contains));
        assertEquals(55, LimitationManager.extraSlotsFromPermissions(permission -> true));
    }

    @Test
    @DisplayName("The effective limit is the rank limit plus the permission bonus")
    void effectiveLimitAddsTheBonus() {
        when(globalConfig.getLimitations()).thenReturn(Map.of("default", 5));
        Player guildTrader = mock(Player.class);
        when(guildTrader.hasPermission("FAuction_dubleSlot_2")).thenReturn(true);
        when(guildTrader.hasPermission("FAuction_dubleSlot_3")).thenReturn(true);

        assertEquals(5, limitationManager.getBaseAuctionLimitation(guildTrader));
        assertEquals(5, limitationManager.getExtraSlots(guildTrader));
        assertEquals(10, limitationManager.getAuctionLimitation(guildTrader));
    }

    @Test
    @DisplayName("An unlimited rank stays unlimited, bonus or not")
    void unlimitedStaysUnlimited() {
        when(globalConfig.getLimitations()).thenReturn(Map.of("default", -1));
        Player guildTrader = mock(Player.class);
        when(guildTrader.hasPermission("FAuction_dubleSlot_10")).thenReturn(true);

        assertEquals(-1, limitationManager.getAuctionLimitation(guildTrader));
    }
}