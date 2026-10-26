package fr.florianpal.fauction.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.OfflinePlayer;

public class PlaceholderUtil {

    public static String parsePlaceholder(boolean placeholderAPIEnabled, OfflinePlayer player, String text) {
        if (placeholderAPIEnabled) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }
}
