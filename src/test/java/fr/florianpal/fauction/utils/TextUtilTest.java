package fr.florianpal.fauction.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two notations of the texts : what has always been written with {@code &} must keep its
 * colours, and MiniMessage must work next to it — without ever letting a text become clickable.
 */
class TextUtilTest {

    @Test
    @DisplayName("The legacy codes are rewritten as the tags they mean")
    void legacyCodesBecomeTags() {
        assertEquals("<green>Hi <bold>there", TextUtil.legacyToMiniMessage("&aHi &lthere"));
        assertEquals("<red>Red", TextUtil.legacyToMiniMessage("§cRed"));
        assertEquals("<#FFA500>Gold", TextUtil.legacyToMiniMessage("&#FFA500Gold"));
        assertEquals("<#FFA500>Gold", TextUtil.legacyToMiniMessage("#FFA500Gold"));
        assertEquals("<#FFA500>Gold", TextUtil.legacyToMiniMessage("&x&F&F&A&5&0&0Gold"));
        assertEquals("Diamond Sword", TextUtil.legacyToMiniMessage("Diamond Sword"));
        // Already MiniMessage : left alone.
        assertEquals("<green>Hi", TextUtil.legacyToMiniMessage("<green>Hi"));
    }

    @Test
    @DisplayName("Both notations give the same component, and may be mixed")
    void notationsCompose() {
        assertEquals(TextUtil.parse("<green>Hi"), TextUtil.parse("&aHi"));
        assertEquals(TextUtil.parse("<green>Hi"), TextUtil.parse("§aHi"));

        Component mixed = TextUtil.parse("&cRed <gradient:red:gold>Grad</gradient>");
        assertEquals("Red Grad", plain(mixed));
    }

    @Test
    @DisplayName("A MiniMessage text comes back from the legacy form with its colours")
    void toLegacyKeepsTheColours() {
        assertEquals("§aHi", TextUtil.toLegacy("<green>Hi"));
        assertEquals("§aHi", TextUtil.toLegacy("&aHi"));
        assertEquals("§aHi", FormatUtil.format("<green>Hi"));
        assertEquals(Component.empty(), TextUtil.parse(null));
        assertEquals("", TextUtil.toLegacy(null));
    }

    @Test
    @DisplayName("Interactivity stays off : a click tag is only ever text")
    void clickTagsAreInert() {
        Component parsed = TextUtil.parse("<click:run_command:/op me>Click me");
        assertTrue(plain(parsed).contains("<click"), plain(parsed));
        assertEquals(null, parsed.clickEvent());
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
