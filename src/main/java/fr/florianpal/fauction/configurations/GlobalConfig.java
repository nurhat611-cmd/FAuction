package fr.florianpal.fauction.configurations;

import dev.dejvokep.boostedyaml.YamlDocument;
import fr.florianpal.fauction.enums.CurrencyType;
import fr.florianpal.fauction.enums.FreeQuotaMode;
import fr.florianpal.fauction.enums.SpamAction;
import fr.florianpal.fauction.objects.SoundSetting;
import fr.florianpal.fauction.objects.SpamLimit;
import lombok.Getter;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class GlobalConfig {

    private String lang = "en";
    private String orderBy;
    private boolean onBuyCommandUse;

    private boolean securityForSpammingPacket;

    private long spamMessageCooldown = 3000;

    private int spamLogThreshold = 25;

    private final Map<SpamAction, SpamLimit> spamLimits = new EnumMap<>(SpamAction.class);

    private String dateFormat;

    private String remainingDateFormat;

    private String onBuyCommand;

    private boolean limitationsUseMetaLuckperms = false;
    private Map<String, Integer> limitations = new HashMap<>();
    private Map<Material, Double> minPrice = new HashMap<>();

    private Map<Material, Double> maxPrice = new HashMap<>();

    private List<Material> blacklistItem = new ArrayList<>();

    private boolean defaultMaxValueEnable = false;

    private boolean defaultMinValueEnable = false;

    private double defaultMinValue = 0;

    private double defaultMaxValue = 100000000;

    private int time;
    private int checkEvery;

    private int expiredStorageDays = 3;

    private int timeCurrency;
    private int checkEveryCurrency;

    private int updateCacheEvery;

    private boolean featureFlippingExpiration;

    private boolean featureFlippingCacheUpdate;

    private boolean featureFlippingMoneyFormat;

    private boolean featureDuplicationHashCodeControl;

    private String decimalFormat;

    private String defaultGui;

    private CurrencyType currencyType;

    private boolean updateCheckerEnabled = true;

    private boolean commissionEnabled = true;

    private double commissionPercent = 7.0;

    private FreeQuotaMode commissionFreeQuotaMode = FreeQuotaMode.LIFETIME;

    private SoundSetting sellSound = new SoundSetting("ENTITY_ITEM_PICKUP", 2.0, 1.0);

    private SoundSetting expireClaimSound = new SoundSetting("ENTITY_PUFFER_FISH_BLOW_UP", 1.0, 1.0);

    private SoundSetting buySound = new SoundSetting("ENTITY_VILLAGER_TRADE", 0.5, 1.0);

    private String buyCustomSound = "custom.cashregister";

    private SoundSetting guiClickSound = new SoundSetting("UI_BUTTON_CLICK", 0.5, 1.0);

    public void load(YamlDocument config) {
        lang = config.getString("lang");

        defaultGui = config.getString("defaultGui", "AUCTION");

        currencyType = CurrencyType.valueOf(config.getString("currencyUse", "VAULT"));

        decimalFormat = config.getString("decimalFormat", "0.00");

        featureFlippingExpiration = config.getBoolean("feature-flipping.item-expiration", true);
        featureFlippingCacheUpdate = config.getBoolean("feature-flipping.cache-update", true);
        featureFlippingMoneyFormat = config.getBoolean("feature-flipping.money-format", false);
        featureDuplicationHashCodeControl = config.getBoolean("feature-flipping.duplication-hashcode-control", false);

        updateCheckerEnabled = config.getBoolean("update-checker.enabled", true);

        commissionEnabled = config.getBoolean("commission.enabled", true);
        // A percentage outside 0-100 would take more than the price, or pay the seller for
        // listing : clamp it rather than let a typo reach the economy.
        double percent = config.getDouble("commission.percent", 7.0);
        commissionPercent = Double.isFinite(percent) ? Math.min(100, Math.max(0, percent)) : 7.0;
        commissionFreeQuotaMode = parseFreeQuotaMode(config.getString("commission.free-quota-mode", "LIFETIME"));

        sellSound = readSound(config, "sounds.sell", sellSound);
        expireClaimSound = readSound(config, "sounds.expireClaim", expireClaimSound);
        buySound = readSound(config, "sounds.buy", buySound);
        buyCustomSound = config.getString("sounds.buy.custom", buyCustomSound);
        guiClickSound = readSound(config, "sounds.guiClick", guiClickSound);

        orderBy = config.getString("orderBy");
        dateFormat = config.getString("dateFormat");
        remainingDateFormat = config.getString("remainingDateFormat");
        onBuyCommandUse = config.getBoolean("onBuy.sendCommand.use");
        onBuyCommand = config.getString("onBuy.sendCommand.command");

        securityForSpammingPacket = config.getBoolean("securityForSpammingPacket", true);
        spamMessageCooldown = config.getLong("anti-spam.messageCooldown", 3000L);
        spamLogThreshold = config.getInt("anti-spam.logThreshold", 25);

        spamLimits.clear();
        for (SpamAction action : SpamAction.values()) {
            SpamLimit defaultLimit = action.getDefaultLimit();
            double burst = config.getDouble("anti-spam." + action.getConfigKey() + ".burst", defaultLimit.burst());
            double perSecond = config.getDouble("anti-spam." + action.getConfigKey() + ".perSecond", defaultLimit.perSecond());
            spamLimits.put(action, new SpamLimit(Math.max(1, burst), Math.max(0.1, perSecond)));
        }

        // A period of zero makes Bukkit schedule the task every tick, so a missing section has to
        // fall back on a usable value.
        time = config.getInt("expiration.time", 3600);
        checkEvery = config.getInt("expiration.checkEvery", 72000);
        // -1 keeps the historical behaviour (expired items are kept forever) for the servers
        // that do not want a forgotten-items purge ; anything lower is read as -1.
        expiredStorageDays = Math.max(-1, config.getInt("expiration.expiredStorageDays", 3));
        updateCacheEvery = config.getInt("cacheUpdate", 72000);

        timeCurrency = config.getInt("currencyCheck.time", 3600);
        checkEveryCurrency = config.getInt("currencyCheck.checkEvery", 72000);

        minPrice = new HashMap<>();
        maxPrice = new HashMap<>();
        blacklistItem = new ArrayList<>();

        limitationsUseMetaLuckperms = config.getBoolean("limitations-use-meta-luckperms", false);
        limitations = new HashMap<>();
        if (config.contains("limitations")) {
            for (Object limitationGroup : config.getSection("limitations").getKeys()) {
                limitations.put(limitationGroup.toString(), config.getInt("limitations." + limitationGroup));
            }
        }

        if (config.contains("min-price-default")) {
            defaultMinValueEnable = config.getBoolean("min-price-default.enable");
            defaultMinValue = config.getDouble("min-price-default.value");
        }

        if (config.contains("max-price-default")) {
            defaultMaxValueEnable = config.getBoolean("max-price-default.enable");
            defaultMaxValue = config.getDouble("max-price-default.value");
        }

        if (config.contains("min-price")) {
            minPrice = new HashMap<>();
            for (Object material : config.getSection("min-price").getKeys()) {
                minPrice.put(Material.valueOf(material.toString()), config.getDouble("min-price." + material));
            }
        }

        if (config.contains("max-price")) {
            maxPrice = new HashMap<>();
            for (Object material : config.getSection("max-price").getKeys()) {
                maxPrice.put(Material.valueOf(material.toString()), config.getDouble("max-price." + material));
            }
        }

        if (config.contains("item-blacklist")) {
            blacklistItem = config.getStringList("item-blacklist").stream().map(Material::valueOf).collect(Collectors.toList());
        }
    }

    /**
     * An unknown mode falls back on the lifetime budget instead of keeping the plugin from
     * starting : the worst a typo does is spend the quota the other way, and the value stays
     * visible in the file for the administrator to fix.
     */
    /**
     * One {@code sounds.<key>} section. A missing section keeps the default sound whole ; an
     * unusable volume or pitch falls back on the default of that sound rather than reaching the
     * client (a negative volume and a pitch of 9 are both accepted by the API and heard as
     * garbage).
     */
    private static SoundSetting readSound(YamlDocument config, String path, SoundSetting fallback) {
        String name = config.getString(path + ".name", fallback.name());
        double volume = config.getDouble(path + ".volume", fallback.volume());
        double pitch = config.getDouble(path + ".pitch", fallback.pitch());
        if (!Double.isFinite(volume) || volume < 0) {
            volume = fallback.volume();
        }
        if (!Double.isFinite(pitch)) {
            pitch = fallback.pitch();
        }
        return new SoundSetting(name != null ? name : fallback.name(),
                Math.min(10.0, volume), Math.min(2.0, Math.max(0.1, pitch)));
    }

    private static FreeQuotaMode parseFreeQuotaMode(String value) {
        if (value != null) {
            try {
                return FreeQuotaMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return FreeQuotaMode.LIFETIME;
            }
        }
        return FreeQuotaMode.LIFETIME;
    }

    public SpamLimit getSpamLimit(SpamAction action) {
        return spamLimits.getOrDefault(action, action.getDefaultLimit());
    }

}
