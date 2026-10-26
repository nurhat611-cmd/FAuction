package fr.florianpal.fauction.utils;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.languages.MessageKeys;
import org.bukkit.entity.Player;

public class MessageUtil {

    /**
     * Sends a message of the plugin to a player. Signature untouched by the move off ACF : the
     * 39 call sites, in six files, do not change.
     * <p>
     * A key absent from the language file sends nothing, which is what ACF did.
     */
    public static void sendMessage(FAuction plugin, Player player, MessageKeys messageKeys, String... replacements) {
        // A component since the texts learned MiniMessage : a gradient written in a language
        // file reaches the chat as one, and the legacy codes mean exactly what they meant.
        plugin.getLang().component(messageKeys, replacements).ifPresent(player::sendMessage);
    }
}
