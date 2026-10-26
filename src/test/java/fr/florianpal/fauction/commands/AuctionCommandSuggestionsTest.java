package fr.florianpal.fauction.commands;

import fr.florianpal.fauction.FAuctionTestBase;
import org.bukkit.Material;
import org.incendo.cloud.context.CommandInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * What the completion of {@code /ah sell} and {@code /ah search} proposes, and the spellings
 * {@code /ah search} accepts : the regression of step 4 was a search that only understood the
 * uppercase name of a material.
 */
class AuctionCommandSuggestionsTest extends FAuctionTestBase {

    private AuctionCommand command;

    @BeforeEach
    void setUpCommand() {
        when(globalConfig.getDecimalFormat()).thenReturn("0.00");
        command = new AuctionCommand(plugin);
    }

    @Test
    @DisplayName("A sale proposes stack amounts and round prices")
    void sellSuggestions() {
        assertEquals(List.of("1", "16", "32", "64"), command.amountSuggestions());
        assertEquals(List.of("100", "1000", "25000", "50000", "100000", "500000"), command.priceSuggestions());
    }

    @Test
    @DisplayName("A material is read in any case, with or without its namespace")
    void materialNamesAreCaseInsensitive() {
        assertEquals(Material.DIAMOND_SWORD, AuctionCommand.parseMaterialName("diamond_sword").orElseThrow());
        assertEquals(Material.DIAMOND_SWORD, AuctionCommand.parseMaterialName("DIAMOND_SWORD").orElseThrow());
        assertEquals(Material.DIAMOND_SWORD, AuctionCommand.parseMaterialName("Diamond_Sword").orElseThrow());
        assertEquals(Material.DIAMOND_SWORD, AuctionCommand.parseMaterialName("minecraft:diamond_sword").orElseThrow());
        assertEquals(Material.DIAMOND_SWORD, AuctionCommand.parseMaterialName(" MINECRAFT:DIAMOND_SWORD ").orElseThrow());
        assertTrue(AuctionCommand.parseMaterialName("not_a_material").isEmpty());
        assertTrue(AuctionCommand.parseMaterialName("").isEmpty());
        assertTrue(AuctionCommand.parseMaterialName(null).isEmpty());
    }

    @Test
    @DisplayName("The material suggestions narrow on what is typed, in any case")
    void materialSuggestionsNarrow() {
        List<String> sword = AuctionCommand.materialSuggestionsFor("diamond_sw");
        assertTrue(sword.contains("diamond_sword"), sword.toString());
        assertTrue(sword.stream().allMatch(name -> name.contains("diamond_sw")), sword.toString());
        assertFalse(sword.contains("stone"), sword.toString());

        // The fragment does not have to be a prefix, nor lowercase.
        List<String> upper = AuctionCommand.materialSuggestionsFor("SWORD");
        assertTrue(upper.contains("diamond_sword"), upper.toString());
        assertTrue(upper.contains("iron_sword"), upper.toString());

        // Nothing typed yet : every material, the names the players type.
        List<String> all = AuctionCommand.materialSuggestionsFor("");
        assertTrue(all.size() > 500, String.valueOf(all.size()));
        assertTrue(all.contains("diamond_sword"), all.toString());

        // The suggestions Cloud calls read the fragment out of the input.
        assertTrue(command.materialSuggestions(CommandInput.of("diamond_sw")).contains("diamond_sword"));
    }
}
