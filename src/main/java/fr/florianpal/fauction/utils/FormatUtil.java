package fr.florianpal.fauction.utils;

import fr.florianpal.fauction.FAuction;
import me.seetch.mlang.MLang;
import me.seetch.mlang.TranslationKeyGenerator;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FormatUtil {

    /**
     * A text of the plugin as a component — what Cloud expects for the messages of the
     * framework. Read by {@link TextUtil}, so the legacy codes and MiniMessage keep meaning
     * the same colours here as everywhere else.
     */
    public static Component component(String msg) {
        return TextUtil.parse(msg);
    }

    /**
     * Rounds a price entered by a player to the precision of the configured money format, when that
     * feature is enabled. Parses back through {@code df} itself (not {@link Double#parseDouble}), so a
     * pattern with a grouping separator (e.g. "#,##0") round-trips instead of throwing.
     */
    public static double applyMoneyFormat(double priceEntry, DecimalFormat df, boolean featureFlippingMoneyFormat) {
        if (!featureFlippingMoneyFormat) {
            return priceEntry;
        }
        try {
            return df.parse(df.format(priceEntry)).doubleValue();
        } catch (ParseException e) {
            return priceEntry;
        }
    }

    /**
     * A tag {@link TextUtil} actually reads. {@code <price>} of the usage lines is not one, so
     * those texts keep the historical path — and its exact spelling, trailing {@code §r} and
     * all — while a text carrying a real tag goes through MiniMessage.
     */
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile(
            "</?(black|dark_blue|dark_green|dark_aqua|dark_red|dark_purple|gold|gray|dark_gray|blue"
                    + "|green|aqua|red|light_purple|yellow|white|bold|italic|underlined|strikethrough"
                    + "|obfuscated|reset|gradient|rainbow|transition|newline|color|#[0-9a-fA-F]{6})(?=[>:\\s])",
            Pattern.CASE_INSENSITIVE);

    /**
     * A text of the plugin as a legacy string — the form item names, lore and inventory titles
     * still travel in. A text without a MiniMessage tag takes the historical path below
     * untouched, byte for byte ; one that carries tags is read by {@link TextUtil} and spelled
     * back in section codes, which is what lets a configuration write
     * {@code <gradient:red:gold>} where it used to write {@code &c}.
     */
    public static String format(String msg) {
        if (msg != null && MINI_MESSAGE_TAG.matcher(msg).find()) {
            return TextUtil.toLegacy(msg);
        }
        return legacyFormat(msg);
    }

    private static final Pattern HEX_COLOUR = Pattern.compile("#[a-fA-F0-9]{6}");

    private static final String LEGACY_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    /**
     * The historical spelling, kept byte for byte but written out by hand : the hex colours in
     * the {@code §x§R§R§G§G§B§B} sequence, then the {@code &} codes turned into {@code §} ones.
     * This is exactly what Bungee's {@code ChatColor} produced (the hex digits keep the case
     * they were written with, the single-letter codes are lower-cased) — without depending on it, the whole plugin now speaks Adventure.
     */
    private static String legacyFormat(String msg) {
        if (msg == null) {
            return null;
        }

        Matcher hex = HEX_COLOUR.matcher(msg);
        StringBuilder out = new StringBuilder();
        while (hex.find()) {
            StringBuilder sequence = new StringBuilder("§x");
            for (char digit : hex.group().substring(1).toCharArray()) {
                sequence.append('§').append(digit);
            }
            hex.appendReplacement(out, Matcher.quoteReplacement(sequence.toString()));
        }
        hex.appendTail(out);

        char[] chars = out.toString().toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && LEGACY_CODES.indexOf(chars[i + 1]) > -1) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    public static String durationFormat(String format, Duration duration) {

        var year = 0;
        var month = 0;
        var day = 0;
        var hour = 0;
        var minute = 0;
        var seconde = 0;

        if (!duration.isNegative()) {

            long baseSecond = duration.getSeconds();
            year = (int) Math.floor((double) duration.getSeconds() / 31557600);
            baseSecond = baseSecond - year * 31557600L;
            month = (int) Math.floor((double) baseSecond / 2629800);
            baseSecond = baseSecond - month * 2629800L;
            day = (int) Math.floor((double) baseSecond / 86400);
            baseSecond = baseSecond - day * 86400L;
            hour = (int) Math.floor((double) baseSecond / 3600);
            baseSecond = baseSecond - hour * 3600L;
            minute = (int) Math.floor((double) baseSecond / 60);
            baseSecond = baseSecond - minute * 60L;
            seconde = (int) baseSecond;
        }

        format = format.replace("yyyy", "" + year);
        format = format.replace("MM", "" + month);
        format = format.replace("dd", "" + day);
        format = format.replace("HH", "" + hour);
        format = format.replace("mm", "" + minute);
        format = format.replace("ss", "" + seconde);

        return format;
    }

    public static String titleItemFormat(ItemStack item, String replacement, String title) {
        return title.replace(replacement, titleItemFormat(item));
    }

    public static String titleItemFormat(ItemStack item) {
        return itemName(item, translate(item));
    }

    /**
     * The name shown to the player: the custom name of the item, otherwise its vanilla translation.
     * MLang gives back the translation key itself when the language file could not be loaded, which
     * would show "block.minecraft.diamond_block" in the message instead of a name, so the material
     * is then written the same way as in the menus.
     */
    static String itemName(ItemStack item, String translation) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        if (translation == null || translation.isEmpty()
                || translation.equals(TranslationKeyGenerator.getItemStackKey(item))) {
            return item.getType().name().replace('_', ' ').toLowerCase();
        }

        return translation;
    }

    private static String translate(ItemStack item) {
        FAuction plugin = FAuction.getApi();
        if (plugin == null) {
            return null;
        }

        MLang mLang = plugin.getMLang();
        return mLang == null ? null : mLang.getItemStackTranslation(item);
    }

    public static String formatLanguageCode(String languageCode) {
        if (languageCode.contains("_")) {
            return languageCode.toLowerCase();
        }

        return switch (languageCode.toLowerCase()) {
            case "ru" -> "ru_ru";
            case "en" -> "en_us";
            case "fr" -> "fr_fr";
            case "zhcn" -> "zh_cn";
            default -> languageCode + "_" + languageCode;
        };
    }

    public static String formatServerVersion() {
        String bukkitVersion = Bukkit.getServer().getBukkitVersion();

        if (bukkitVersion.contains("-")) {
            return bukkitVersion.split("-")[0];
        }

        return bukkitVersion;
    }
}
