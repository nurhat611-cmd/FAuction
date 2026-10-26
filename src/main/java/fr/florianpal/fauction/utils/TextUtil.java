package fr.florianpal.fauction.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The texts of the plugin, read both ways : the legacy colour codes every configuration has
 * always used ({@code &a}, {@code &#FFA500}, {@code &x&F&F&A&5&0&0}, their {@code §} twins and
 * the bare {@code #FFA500} of {@link FormatUtil#format}) and MiniMessage tags
 * ({@code <green>}, {@code <gradient:red:gold>}). A text may mix the two — the legacy codes are
 * first rewritten as the tags they mean, then MiniMessage reads the whole of it.
 * <p>
 * Only the styling tags are switched on : colours, decorations, gradients, rainbow, reset and
 * new lines. Interactivity (click, hover, insertion) stays off on purpose — item names written
 * by players flow through these same texts ({@code {item}} of a sale message), and a renamed
 * item must not become a clickable command in somebody else's chat.
 */
public final class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.transition(),
                    StandardTags.reset(),
                    StandardTags.newline()))
            .build();

    /**
     * Writes a component back the way the plugin has always carried its texts : the section
     * codes, hex colours in the {@code §x§R§R§G§G§B§B} form.
     */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('\u00a7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final Pattern BUNGEE_HEX = Pattern.compile("[&§]x((?:[&§][0-9a-fA-F]){6})", Pattern.CASE_INSENSITIVE);

    private static final Pattern AMPERSAND_HEX = Pattern.compile("&#([0-9a-fA-F]{6})");

    private static final Pattern BARE_HEX = Pattern.compile("(?<![&<\\w])#([0-9a-fA-F]{6})");

    private static final Pattern LEGACY_CODE = Pattern.compile("[&§]([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);

    private TextUtil() {
    }

    /**
     * A text of the configuration or of a language file as a component, whichever of the two
     * notations it is written in.
     */
    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(legacyToMiniMessage(text));
    }

    /**
     * The same text as a legacy string, for the places still speaking strings (item names and
     * lore, inventory titles) : MiniMessage written in a configuration reaches them too,
     * spelled out in section codes.
     */
    public static String toLegacy(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return LEGACY.serialize(parse(text));
    }

    /**
     * The legacy codes of a text rewritten as MiniMessage tags, the rest untouched — MiniMessage
     * already written in the text therefore survives, and the two notations compose.
     */
    static String legacyToMiniMessage(String text) {
        String result = text;

        Matcher bungeeHex = BUNGEE_HEX.matcher(result);
        StringBuilder converted = new StringBuilder();
        while (bungeeHex.find()) {
            String hex = bungeeHex.group(1).replaceAll("[&§]", "").toUpperCase(Locale.ROOT);
            bungeeHex.appendReplacement(converted, Matcher.quoteReplacement("<#" + hex + ">"));
        }
        bungeeHex.appendTail(converted);
        result = converted.toString();

        result = AMPERSAND_HEX.matcher(result)
                .replaceAll(match -> Matcher.quoteReplacement("<#" + match.group(1).toUpperCase(Locale.ROOT) + ">"));
        result = BARE_HEX.matcher(result)
                .replaceAll(match -> Matcher.quoteReplacement("<#" + match.group(1).toUpperCase(Locale.ROOT) + ">"));

        Matcher codes = LEGACY_CODE.matcher(result);
        StringBuilder tagged = new StringBuilder();
        while (codes.find()) {
            codes.appendReplacement(tagged, Matcher.quoteReplacement("<" + tagName(codes.group(1).charAt(0)) + ">"));
        }
        codes.appendTail(tagged);
        return tagged.toString();
    }

    private static String tagName(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "black";
            case '1' -> "dark_blue";
            case '2' -> "dark_green";
            case '3' -> "dark_aqua";
            case '4' -> "dark_red";
            case '5' -> "dark_purple";
            case '6' -> "gold";
            case '7' -> "gray";
            case '8' -> "dark_gray";
            case '9' -> "blue";
            case 'a' -> "green";
            case 'b' -> "aqua";
            case 'c' -> "red";
            case 'd' -> "light_purple";
            case 'e' -> "yellow";
            case 'f' -> "white";
            case 'k' -> "obfuscated";
            case 'l' -> "bold";
            case 'm' -> "strikethrough";
            case 'n' -> "underlined";
            case 'o' -> "italic";
            default -> "reset";
        };
    }
}
