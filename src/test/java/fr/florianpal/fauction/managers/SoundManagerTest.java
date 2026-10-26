package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.objects.SoundSetting;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoundManagerTest extends FAuctionTestBase {

    private SoundManager soundManager;

    @BeforeEach
    void setUpSoundManager() {
        soundManager = new SoundManager(plugin);
    }

    @Test
    @DisplayName("Vanilla constant names resolve, resource-pack names stay custom")
    void vanillaNamesResolve() {
        assertEquals(Sound.ENTITY_ITEM_PICKUP, SoundManager.resolveVanilla("ENTITY_ITEM_PICKUP"));
        assertEquals(Sound.UI_BUTTON_CLICK, SoundManager.resolveVanilla("ui_button_click"));
        assertEquals(Sound.ENTITY_PUFFER_FISH_BLOW_UP, SoundManager.resolveVanilla("ENTITY_PUFFER_FISH_BLOW_UP"));
        assertNull(SoundManager.resolveVanilla("custom.cashregister"));
        assertNull(SoundManager.resolveVanilla("mypack:ui.click"));
        assertNull(SoundManager.resolveVanilla("NOT_A_SOUND"));
    }

    @Test
    @DisplayName("Listing a lot plays the configured vanilla sound at its volume and pitch")
    void sellPlaysConfiguredSound() {
        when(globalConfig.getSellSound()).thenReturn(new SoundSetting("ENTITY_ITEM_PICKUP", 2.0, 1.0));
        Player seller = soundPlayer(SELLER);

        soundManager.playSell(seller);

        verify(seller).playSound(any(Location.class), eq(Sound.ENTITY_ITEM_PICKUP), eq(2.0f), eq(1.0f));
    }

    @Test
    @DisplayName("A purchase plays the custom cash register for buyer and seller alike")
    void buyPrefersTheCustomSound() {
        when(globalConfig.getBuySound()).thenReturn(new SoundSetting("ENTITY_VILLAGER_TRADE", 0.5, 1.0));
        when(globalConfig.getBuyCustomSound()).thenReturn("custom.cashregister");
        Player buyer = soundPlayer(BUYER);
        Player seller = soundPlayer(SELLER);

        soundManager.playBuy(buyer, seller);

        verify(buyer).playSound(any(Location.class), eq("custom.cashregister"), eq(SoundCategory.MASTER), eq(0.5f), eq(1.0f));
        verify(seller).playSound(any(Location.class), eq("custom.cashregister"), eq(SoundCategory.MASTER), eq(0.5f), eq(1.0f));
    }

    @Test
    @DisplayName("Without a custom sound the purchase falls back on the villager trade")
    void buyFallsBackOnVanilla() {
        when(globalConfig.getBuySound()).thenReturn(new SoundSetting("ENTITY_VILLAGER_TRADE", 0.5, 1.0));
        when(globalConfig.getBuyCustomSound()).thenReturn("");
        Player buyer = soundPlayer(BUYER);

        soundManager.playBuy(buyer, null);

        verify(buyer).playSound(any(Location.class), eq(Sound.ENTITY_VILLAGER_TRADE), eq(0.5f), eq(1.0f));
    }

    @Test
    @DisplayName("/ah sounds off silences every sound of the player")
    void soundsOffSilencesEverything() {
        when(globalConfig.getSellSound()).thenReturn(new SoundSetting("ENTITY_ITEM_PICKUP", 2.0, 1.0));
        when(globalConfig.getGuiClickSound()).thenReturn(new SoundSetting("UI_BUTTON_CLICK", 0.5, 1.0));
        Player player = soundPlayer(SELLER);

        soundManager.setSoundsEnabled(player, false);
        assertFalse(soundManager.isSoundsEnabled(player));

        soundManager.playSell(player);
        soundManager.playGuiClick(player);

        verify(player, never()).playSound(any(Location.class), any(Sound.class), anyFloat(), anyFloat());
        verify(player, never()).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());

        soundManager.setSoundsEnabled(player, true);
        assertTrue(soundManager.isSoundsEnabled(player));
        soundManager.playSell(player);
        verify(player).playSound(any(Location.class), eq(Sound.ENTITY_ITEM_PICKUP), eq(2.0f), eq(1.0f));
    }

    @Test
    @DisplayName("Who hears what on a purchase: the custom sound only where it can be heard")
    void buySoundNameMatrix() {
        // A custom sound is configured.
        assertEquals("custom.cashregister",
                SoundManager.buySoundName(true, "custom.cashregister", "ENTITY_VILLAGER_TRADE", true));
        assertEquals("ENTITY_VILLAGER_TRADE",
                SoundManager.buySoundName(false, "custom.cashregister", "ENTITY_VILLAGER_TRADE", true));
        // The vanilla slot itself holds a pack sound : the hard-coded trade is the last resort.
        assertEquals("ENTITY_VILLAGER_TRADE",
                SoundManager.buySoundName(false, "custom.cashregister", "custom.other", false));
        // No custom sound : the configured one answers when it can be heard.
        assertEquals("custom.other", SoundManager.buySoundName(true, "", "custom.other", false));
        assertEquals("ENTITY_VILLAGER_TRADE", SoundManager.buySoundName(false, "", "custom.other", false));
        assertEquals("ENTITY_ITEM_PICKUP", SoundManager.buySoundName(false, "", "ENTITY_ITEM_PICKUP", true));
    }

    @Test
    @DisplayName("A Bedrock buyer hears the villager trade even with a custom sound configured")
    void bedrockFallsBackOnVanilla() {
        when(globalConfig.getBuySound()).thenReturn(new SoundSetting("ENTITY_VILLAGER_TRADE", 0.5, 1.0));
        when(globalConfig.getBuyCustomSound()).thenReturn("custom.cashregister");
        Player buyer = soundPlayer(UUID.fromString("00000000-0000-0000-0009-0000000000be"));

        soundManager.playBuy(buyer, null);

        verify(buyer).playSound(any(Location.class), eq(Sound.ENTITY_VILLAGER_TRADE), eq(0.5f), eq(1.0f));
    }

    @Test
    @DisplayName("Without the served pack loaded the register falls back on vanilla, per player")
    void packNotLoadedFallsBackPerPlayer() {
        ResourcePackManager packs = mock(ResourcePackManager.class);
        when(packs.isEnabled()).thenReturn(true);
        when(packs.hasLoadedPack(BUYER)).thenReturn(false);
        when(packs.hasLoadedPack(SELLER)).thenReturn(true);
        when(plugin.getResourcePackManager()).thenReturn(packs);
        when(globalConfig.getBuySound()).thenReturn(new SoundSetting("ENTITY_VILLAGER_TRADE", 0.5, 1.0));
        when(globalConfig.getBuyCustomSound()).thenReturn("custom.cashregister");
        Player buyer = soundPlayer(BUYER);
        Player seller = soundPlayer(SELLER);

        soundManager.playBuy(buyer, seller);

        verify(buyer).playSound(any(Location.class), eq(Sound.ENTITY_VILLAGER_TRADE), eq(0.5f), eq(1.0f));
        verify(seller).playSound(any(Location.class), eq("custom.cashregister"), eq(SoundCategory.MASTER), eq(0.5f), eq(1.0f));
    }

    private Player soundPlayer(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getLocation()).thenReturn(mock(Location.class));
        return player;
    }
}
