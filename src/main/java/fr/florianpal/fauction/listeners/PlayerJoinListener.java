package fr.florianpal.fauction.listeners;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * What the plugin prepares when a player arrives, and drops when they leave. The profile reads
 * the gui needs synchronously — the {@code /ah sounds} switch, the free-listing counter behind
 * {@code {FreeListings}} — are warmed here, ahead of the first click : the data is in the
 * database, the region thread rendering a gui must never go there.
 */
public class PlayerJoinListener implements Listener {

    private final FAuction plugin;

    public PlayerJoinListener(FAuction plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getSoundManager().preload(player);
        plugin.getCommissionManager().preload(player.getUniqueId());
        remindExpiredItems(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getSoundManager().unload(event.getPlayer().getUniqueId());
        plugin.getCommissionManager().unload(event.getPlayer().getUniqueId());
    }

    /**
     * Expired items are invisible until somebody opens {@code /ah expire}, and quietly deleted
     * once their storage runs out : a line at login is the chance to claim them before that.
     */
    private void remindExpiredItems(Player player) {
        FAuction.newChain(player)
                .asyncFirst(() -> plugin.getExpireCommandManager().getExpires(player.getUniqueId()).size())
                .syncLast(count -> {
                    if (count != null && count > 0) {
                        MessageUtil.sendMessage(plugin, player, MessageKeys.EXPIRE_REMINDER, "{count}", String.valueOf(count));
                    }
                })
                .execute();
    }
}
