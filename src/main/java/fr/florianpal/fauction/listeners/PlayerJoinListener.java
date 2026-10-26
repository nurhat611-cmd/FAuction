package fr.florianpal.fauction.listeners;

import fr.florianpal.fauction.FAuction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * What the plugin prepares when a player arrives, and drops when they leave. For now the
 * {@code /ah sounds} switch of their profile is read ahead of their first click, so a player
 * who silenced the auction never hears one stray gui click while the row loads.
 */
public class PlayerJoinListener implements Listener {

    private final FAuction plugin;

    public PlayerJoinListener(FAuction plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getSoundManager().preload(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getSoundManager().unload(event.getPlayer().getUniqueId());
    }
}
