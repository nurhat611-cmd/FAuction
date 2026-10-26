package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.objects.SoundSetting;
import fr.florianpal.fauction.queries.PlayerProfileQueries;
import fr.florianpal.fauction.utils.BedrockUtil;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every sound of the auction, and the per-player switch that silences them all
 * ({@code /ah sounds on|off}, stored in the player profile).
 * <p>
 * A configured name is first tried as a vanilla {@link Sound} constant
 * ({@code ENTITY_ITEM_PICKUP}) ; anything else is played as a custom sound event, which is how
 * resource-pack sounds such as {@code custom.cashregister} are reached. The constant is looked
 * up as a public field rather than with {@code valueOf} : the fields exist whether the class is
 * the old enum or the registry-backed type of the recent Paper APIs.
 * <p>
 * There is deliberately no "hover" sound here : the client never tells the server which slot
 * the mouse is over (no packet, no event), so a hover sound cannot exist server-side. The click
 * sound covers every press on a gui button or item instead.
 */
public class SoundManager {

    /**
     * The last-resort purchase sound, used when every configured name is a resource-pack sound
     * the player cannot hear. Vanilla, always resolvable, and thematically a trade.
     */
    static final String BUY_FALLBACK_SOUND = "ENTITY_VILLAGER_TRADE";

    private final FAuction plugin;

    /**
     * The {@code /ah sounds} switch of every player seen this session, read from the profile
     * once (on join, or lazily at the first sound) instead of hitting the database per click.
     * An absent player reads as "on" while their row is being loaded.
     */
    private final Map<UUID, Boolean> soundsEnabled = new ConcurrentHashMap<>();

    public SoundManager(FAuction plugin) {
        this.plugin = plugin;
    }

    /**
     * The vanilla sound of a constant name, null when the name is a custom (resource-pack)
     * sound event.
     */
    static Sound resolveVanilla(String name) {
        if (name == null) {
            return null;
        }
        String upper = name.trim().toUpperCase(Locale.ROOT);
        if (!upper.matches("[A-Z0-9_]+")) {
            return null;
        }
        try {
            Object constant = Sound.class.getField(upper).get(null);
            return constant instanceof Sound sound ? sound : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public boolean isSoundsEnabled(Player player) {
        return isSoundsEnabled(player.getUniqueId());
    }

    public boolean isSoundsEnabled(UUID playerUuid) {
        Boolean enabled = soundsEnabled.get(playerUuid);
        if (enabled == null) {
            // Optimistic placeholder : one load per player, and sounds on (the default) until
            // the profile answers.
            soundsEnabled.put(playerUuid, Boolean.TRUE);
            loadAsync(playerUuid);
            return true;
        }
        return enabled;
    }

    /**
     * Reads the switch of a joining player ahead of their first click.
     */
    public void preload(Player player) {
        if (soundsEnabled.putIfAbsent(player.getUniqueId(), Boolean.TRUE) == null) {
            loadAsync(player.getUniqueId());
        }
    }

    public void unload(UUID playerUuid) {
        soundsEnabled.remove(playerUuid);
    }

    /**
     * The {@code /ah sounds on|off} switch : effective immediately from the cache, written to
     * the profile asynchronously.
     */
    public void setSoundsEnabled(Player player, boolean enabled) {
        soundsEnabled.put(player.getUniqueId(), enabled);
        PlayerProfileQueries queries = queries();
        if (queries == null) {
            return;
        }
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        runAsync(() -> queries.setSoundsEnabled(playerUuid, playerName, enabled));
    }

    public void playSell(Player player) {
        GlobalConfig config = config();
        if (config != null) {
            play(player, config.getSellSound());
        }
    }

    public void playExpireClaim(Player player) {
        GlobalConfig config = config();
        if (config != null) {
            play(player, config.getExpireClaimSound());
        }
    }

    public void playGuiClick(Player player) {
        GlobalConfig config = config();
        if (config != null) {
            play(player, config.getGuiClickSound());
        }
    }

    /**
     * The cash register of a completed purchase, for the buyer and for the seller when they are
     * online. The custom resource-pack sound wins when one is configured ; an empty
     * {@code sounds.buy.custom} falls back on the vanilla sound of the same section.
     */
    public void playBuy(Player buyer, Player seller) {
        GlobalConfig config = config();
        if (config == null || config.getBuySound() == null) {
            return;
        }
        playBuyTo(buyer, config);
        if (seller != null && !seller.equals(buyer)) {
            playBuyTo(seller, config);
        }
    }

    /**
     * The purchase sound of one player. Who hears the custom register is decided per player, not
     * per purchase : a Bedrock player never has the Java pack, and a Java player who declined it
     * (or whose download failed) would only hear silence — both get the vanilla sound.
     */
    private void playBuyTo(Player player, GlobalConfig config) {
        if (player == null) {
            return;
        }
        SoundSetting setting = config.getBuySound();
        String name = buySoundName(customSoundAllowed(player), config.getBuyCustomSound(),
                setting.name(), resolveVanilla(setting.name()) != null);
        playNamed(player, name, setting);
    }

    /**
     * The sound one player hears for a purchase. The configured custom sound wins where it can
     * be heard ; where it cannot, the configured vanilla name answers, or the hard-coded villager
     * trade when that name is itself a resource-pack sound — silence is the one answer a purchase
     * never gives.
     */
    static String buySoundName(boolean customAllowed, String custom, String configuredName,
                               boolean configuredNameIsVanilla) {
        if (custom != null && !custom.isBlank()) {
            if (customAllowed) {
                return custom.trim();
            }
            return configuredNameIsVanilla ? configuredName : BUY_FALLBACK_SOUND;
        }
        if (!customAllowed && !configuredNameIsVanilla) {
            return BUY_FALLBACK_SOUND;
        }
        return configuredName;
    }

    private boolean customSoundAllowed(Player player) {
        ResourcePackManager packs = plugin.getResourcePackManager();
        boolean managed = packs != null && packs.isEnabled();
        boolean loaded = packs != null && packs.hasLoadedPack(player.getUniqueId());
        return ResourcePackManager.shouldUseCustomSound(BedrockUtil.isBedrockPlayer(player), managed, loaded);
    }

    public void play(Player player, SoundSetting setting) {
        if (setting != null) {
            playNamed(player, setting.name(), setting);
        }
    }

    private void playNamed(Player player, String name, SoundSetting setting) {
        if (player == null || name == null || name.isBlank() || !isSoundsEnabled(player)) {
            return;
        }
        float volume = (float) setting.volume();
        float pitch = (float) setting.pitch();
        Sound vanilla = resolveVanilla(name);
        if (vanilla != null) {
            player.playSound(player.getLocation(), vanilla, volume, pitch);
        } else {
            player.playSound(player.getLocation(), name.trim(), SoundCategory.MASTER, volume, pitch);
        }
    }

    private void loadAsync(UUID playerUuid) {
        PlayerProfileQueries queries = queries();
        if (queries == null) {
            return;
        }
        runAsync(() -> soundsEnabled.put(playerUuid, queries.getSoundsEnabled(playerUuid)));
    }

    private void runAsync(Runnable task) {
        var foliaLib = FAuction.getFoliaLib();
        if (foliaLib != null) {
            foliaLib.getScheduler().runAsync(scheduled -> task.run());
        } else {
            // No scheduler (tests, or a half-started plugin) : the work is one profile row,
            // running it in place beats losing the switch.
            task.run();
        }
    }

    private PlayerProfileQueries queries() {
        return plugin.getPlayerProfileQueries();
    }

    private GlobalConfig config() {
        return plugin.getConfigurationManager() != null
                ? plugin.getConfigurationManager().getGlobalConfig()
                : null;
    }
}
