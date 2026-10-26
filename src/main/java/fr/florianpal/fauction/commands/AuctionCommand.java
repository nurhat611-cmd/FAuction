package fr.florianpal.fauction.commands;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.MigrateVersion;
import fr.florianpal.fauction.enums.SpamAction;
import fr.florianpal.fauction.events.AuctionAddEvent;
import fr.florianpal.fauction.gui.subGui.*;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.managers.ClaimManager;
import fr.florianpal.fauction.managers.CommissionManager;
import fr.florianpal.fauction.managers.ExpireStorageManager;
import fr.florianpal.fauction.managers.SpamManager;
import fr.florianpal.fauction.managers.commandmanagers.AuctionCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.ExpireCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.HistoricCommandManager;
import fr.florianpal.fauction.managers.commandmanagers.LimitationManager;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Category;
import fr.florianpal.fauction.utils.CurrencyUtil;
import fr.florianpal.fauction.utils.FormatUtil;
import fr.florianpal.fauction.utils.ListUtil;
import fr.florianpal.fauction.utils.MessageUtil;
import fr.florianpal.fauction.utils.SerializationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.parser.Parser;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandInput;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.ceil;

public class AuctionCommand {

    /**
     * The words of the command paths, root first.
     * <p>
     * The {@code ${...}} are not read by Cloud : {@link fr.florianpal.fauction.commands.CommandPlaceholders}
     * replaces each of them with the names of the {@code commands:} section of config.yml —
     * {@code ah|hdv} by default — while the annotations are parsed. Every placeholder used here must
     * be a {@link fr.florianpal.fauction.enums.CommandKey}, which {@code AuctionCommandAnnotationsTest}
     * checks at build time.
     * <p>
     * The permissions below are deliberately left out of that mechanism : renaming a command must
     * not move its permission node.
     */
    private static final String ROOT = "${root}";

    private static final String LIST = "${list}";

    private static final String SEARCH = "${search}";

    private static final String SELL = "${sell}";

    private static final String EXPIRE = "${expire}";

    private static final String HELP = "${help}";

    private static final String ADMIN = "${admin}";

    private static final String SPACE = " ";

    static final String PRICE_PARSER = "fauction:price";

    static final String MIGRATE_VERSION_PARSER = "fauction:migrate_version";

    static final String MIGRATE_VERSION_SUGGESTIONS = "fauction:migrate_versions";

    static final String ONLINE_PLAYER_SUGGESTIONS = "fauction:online_players";

    static final String SOUND_STATE_SUGGESTIONS = "fauction:sound_states";

    static final String PRICE_SUGGESTIONS = "fauction:prices";

    static final String AMOUNT_SUGGESTIONS = "fauction:amounts";

    static final String MATERIAL_PARSER = "fauction:material";

    static final String MATERIAL_SUGGESTIONS = "fauction:materials";

    /**
     * The prices tab completion proposes for a sale. Starting points, not a cage : any other
     * price stays typeable.
     */
    static final List<String> PRICE_SUGGESTION_VALUES = List.of("100", "1000", "25000", "50000", "100000", "500000");

    /**
     * The amounts tab completion proposes for a sale : one item, and the quantities stacks are
     * usually traded in.
     */
    static final List<String> AMOUNT_SUGGESTION_VALUES = List.of("1", "16", "32", "64");

    /**
     * The names behind {@link #materialNames()}, built on first use. Not a static initializer :
     * {@code Material} leans on the registries of a running server, and the class must stay
     * loadable before one is up (the tests, for instance).
     */
    private static volatile List<String> materialNames;

    private final FAuction plugin;

    private final AuctionCommandManager auctionCommandManager;

    private final ExpireCommandManager expireCommandManager;

    private final HistoricCommandManager historicCommandManager;

    private final SpamManager spamManager;

    private final GlobalConfig globalConfig;

    private List<Integer> itemHash = new ArrayList<>();

    protected DecimalFormat df;

    /**
     * What the first asynchronous step of a sale reads from the database : the lots of the seller
     * (their limit, and the free ones among them) and their lifetime free-listing counter. Both
     * feed the commission decision of the sync step, which must not read the database itself.
     */
    private record SellData(List<Auction> auctions, int usedFreeListings) {
    }

    public AuctionCommand(FAuction plugin) {
        this.plugin = plugin;
        this.auctionCommandManager = plugin.getAuctionCommandManager();
        this.expireCommandManager = plugin.getExpireCommandManager();
        this.spamManager = plugin.getSpamManager();
        this.globalConfig = plugin.getConfigurationManager().getGlobalConfig();
        this.historicCommandManager = plugin.getHistoricCommandManager();

        df = new DecimalFormat(plugin.getConfigurationManager().getGlobalConfig().getDecimalFormat());
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
    }

    // Two paths, one handler, as under ACF (@Default + @Subcommand("list")) : a bare /ah is the
    // main entry point of the plugin and must not be lost.
    @Command(ROOT)
    @Command(ROOT + SPACE + LIST)
    @Permission("fauction.list")
    @CommandDescription("{@@fauction.auction_list_help_description}")
    public void onList(Player playerSender) {

        if (spamManager.spamTest(playerSender, SpamAction.COMMAND)) {
            return;
        }

        switch (globalConfig.getDefaultGui()) {
            case "AUCTION":
                FAuction.newChain(playerSender).asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
                    AuctionsGui auctionsGui = new AuctionsGui(plugin, playerSender, auctions, 1, null, null);
                    auctionsGui.initialize();
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                }).execute();
                break;
            case "EXPIRE":
                FAuction.newChain(playerSender).asyncFirst(() -> expireCommandManager.getExpires(playerSender.getUniqueId())).syncLast(expires -> {
                    ExpireGui expireGui = new ExpireGui(plugin, playerSender, expires, 1, null, null);
                    expireGui.initialize();
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                }).execute();
                break;
            case "HISTORIC":
                FAuction.newChain(playerSender).asyncFirst(() -> historicCommandManager.getHistorics(playerSender.getUniqueId())).syncLast(historics -> {
                    HistoricGui historicGui = new HistoricGui(plugin, playerSender, ListUtil.historicToAuction(historics), 1, null, null);
                    historicGui.initialize();
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                }).execute();
                break;
            case "PLAYER":
                FAuction.newChain(playerSender).asyncFirst(() -> auctionCommandManager.getAuctions(playerSender.getUniqueId())).syncLast(auctions -> {
                    PlayerViewGui playerViewGui = new PlayerViewGui(plugin, playerSender, auctions, 1, null, null);
                    playerViewGui.initialize();
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                }).execute();
                break;
            default:
                String[] id = globalConfig.getDefaultGui().split(":");

                if (!"MENU".equals(id[0])) {
                    return;
                }

                MainGui gui = new MainGui(plugin, id[1], playerSender, 1);
                gui.initialize();
                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
                break;

        }

    }

    @Command(ROOT + SPACE + SEARCH + SPACE + "<material>")
    @Permission("fauction.search")
    @CommandDescription("{@@fauction.auction_search_help_description}")
    public void onSearch(Player playerSender, @Argument(value = "material", parserName = MATERIAL_PARSER) Material material) {

        if (spamManager.spamTest(playerSender, SpamAction.COMMAND)) {
            return;
        }

        if (material.isAir()) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.SEARCH_AIR);
            return;
        }

        FAuction.newChain(playerSender).asyncFirst(auctionCommandManager::getAuctions).syncLast(auctions -> {
            AuctionsGui auctionsGui = new AuctionsGui(plugin, playerSender, auctions, 1, new Category("-1", material.name(), List.of(material.toString())), null);
            auctionsGui.initialize();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
        }).execute();
    }

    @Command(ROOT + SPACE + "lookup <playerName>")
    @Permission("fauction.lookup")
    @CommandDescription("{@@fauction.lookup_help_description}")
    public void onLookup(Player playerSender, @Argument(value = "playerName", suggestions = ONLINE_PLAYER_SUGGESTIONS) String playerName) {

        if (spamManager.spamTest(playerSender, SpamAction.COMMAND)) {
            return;
        }

        // Online first, then the user cache : looking a player up must never block the command
        // thread on a Mojang query for a name the server has never seen.
        Player onlineTarget = Bukkit.getPlayerExact(playerName);
        OfflinePlayer target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayerIfCached(playerName);

        if (target == null) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.LOOKUP_NOT_FOUND, "{player}", playerName);
            return;
        }

        String targetName = target.getName() != null ? target.getName() : playerName;

        FAuction.newChain(playerSender).asyncFirst(() -> auctionCommandManager.getAuctions(target.getUniqueId())).syncLast(auctions -> {
            if (auctions.isEmpty()) {
                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.LOOKUP_EMPTY, "{player}", targetName);
                return;
            }

            LookupGui lookupGui = new LookupGui(plugin, playerSender, target.getUniqueId(), auctions, 1, null, null);
            lookupGui.initialize();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
        }).execute();
    }

    @Command(ROOT + SPACE + SELL + SPACE + "<priceEntry> [amount]")
    @Permission("fauction.sell")
    @CommandDescription("{@@fauction.auction_add_help_description}")
    public void onAdd(Player playerSender, @Argument(value = "priceEntry", parserName = PRICE_PARSER) double priceEntry, @Argument(value = "amount", suggestions = AMOUNT_SUGGESTIONS) Integer amount) {

        if (spamManager.spamTest(playerSender, SpamAction.TRANSACTION)) {
            return;
        }

        // Reserved on the main thread : only one sale in flight per player, so the other packets of
        // the same tick cannot start a second sale of the very same item.
        long saleClaim = plugin.getClaimManager().tryClaim(playerSender.getUniqueId());
        if (saleClaim == ClaimManager.NOT_CLAIMED) {
            return;
        }

        ItemStack itemToSell = playerSender.getInventory().getItemInMainHand().clone();

        // Without an amount the whole stack is sold, as the command has always done. With one,
        // it has to be a quantity the hand really holds : the price is the price of the lot, so
        // silently selling fewer items than typed would sell them at the wrong price.
        int handAmount = itemToSell.getAmount();
        int sellAmount = amount == null ? handAmount : amount;

        if (sellAmount < 1 || sellAmount > handAmount) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.SELL_INVALID_AMOUNT, "{amount}", String.valueOf(sellAmount), "{max}", String.valueOf(handAmount));
            plugin.getClaimManager().release(playerSender.getUniqueId(), saleClaim);
            return;
        }

        itemToSell.setAmount(sellAmount);

        double price = FormatUtil.applyMoneyFormat(priceEntry, df, globalConfig.isFeatureFlippingMoneyFormat());

        // Everything is checked while the item is still in the inventory, so a refused sale has
        // nothing to give back.
        if (!isSellable(playerSender, itemToSell, price)) {
            plugin.getClaimManager().release(playerSender.getUniqueId(), saleClaim);
            return;
        }

        AtomicBoolean itemTaken = new AtomicBoolean(false);

        FAuction.newChain(playerSender).asyncFirst(() -> new SellData(
                plugin.getAuctionCommandManager().getAuctions(playerSender.getUniqueId()),
                plugin.getCommissionManager().getUsedFreeListings(playerSender.getUniqueId()))).syncLast(sellData -> {

            List<Auction> auctions = sellData.auctions();

            int limitations = plugin.getLimitationManager().getAuctionLimitation(playerSender);


            if (limitations != -1 && limitations <= auctions.size()) {
                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MAX_AUCTION);
                return;
            }

            // The hand may have changed during the round trip, only the checked item can be sold.
            ItemStack inHand = playerSender.getInventory().getItemInMainHand();
            if (!inHand.isSimilar(itemToSell) || inHand.getAmount() != handAmount) {
                return;
            }

            // The commission is decided and taken while the item is still in the inventory, for
            // the same reason as every other check above : a seller who cannot pay it keeps their
            // item, and a free listing of their fauction.nocommission.<n> quota is only spent on
            // a sale that really reaches the database (recorded with the save, further down).
            CommissionManager commissionManager = plugin.getCommissionManager();
            double commissionFee = commissionManager.getFee(price);
            int freeListingsLeft = commissionManager.getRemainingFreeListings(playerSender, auctions, sellData.usedFreeListings());
            boolean commissionFree = commissionFee > 0 && freeListingsLeft > 0;
            double feeToPay = commissionFree ? 0 : commissionFee;
            AtomicBoolean feePaid = new AtomicBoolean(false);

            if (feeToPay > 0) {
                if (!CurrencyUtil.haveCurrency(plugin, playerSender, globalConfig.getCurrencyType(), feeToPay)
                        || !CurrencyUtil.getCurrency(plugin, playerSender, globalConfig.getCurrencyType(), feeToPay)) {
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.COMMISSION_NOT_ENOUGH, "{fee}", df.format(feeToPay), "{percent}", df.format(commissionManager.getPercent()));
                    return;
                }
                feePaid.set(true);
            }

            if (globalConfig.isFeatureDuplicationHashCodeControl()) {
                if (itemHash.contains((Integer) itemToSell.hashCode())) {
                    if (feePaid.get()) {
                        CurrencyUtil.giveCurrency(plugin, playerSender, globalConfig.getCurrencyType(), feeToPay);
                    }
                    return;
                }
                itemHash.add((Integer) itemToSell.hashCode());
            }

            // Taken and resynchronised right away, so the client cannot keep a ghost item and send
            // it back to the server. A partial sale only draws the sold amount from the stack.
            if (sellAmount >= inHand.getAmount()) {
                playerSender.getInventory().setItemInMainHand(null);
            } else {
                inHand.setAmount(inHand.getAmount() - sellAmount);
            }
            playerSender.updateInventory();
            itemTaken.set(true);

            FAuction.newChain(playerSender).asyncFirst(() -> {
                boolean addedAuction = auctionCommandManager.addAuction(playerSender, itemToSell, price, commissionFree);
                // The free listing is spent with the save it belongs to, and only with it : a lot
                // the database refused goes back to the inventory further down, quota untouched.
                if (addedAuction && commissionFree) {
                    commissionManager.recordFreeListing(playerSender.getUniqueId(), playerSender.getName());
                }
                return addedAuction;
            }).syncLast(added -> {

                // The hash only guards this sale while it is in flight ; it must not survive the
                // round trip, or every future sale of a visually identical item would be blocked
                // forever and the list would grow without bound.
                if (globalConfig.isFeatureDuplicationHashCodeControl()) {
                    itemHash.remove((Integer) itemToSell.hashCode());
                }

                if (!Boolean.TRUE.equals(added)) {
                    plugin.getLogger().severe("Auction of " + playerSender.getName() + " could not be saved, item given back");
                    if (feePaid.get()) {
                        CurrencyUtil.giveCurrency(plugin, playerSender, globalConfig.getCurrencyType(), feeToPay);
                    }
                    resetItem(playerSender, itemToSell);
                    return;
                }

                String itemName = itemToSell.getItemMeta().getDisplayName() == null || itemToSell.getItemMeta().getDisplayName().isEmpty() ? itemToSell.getType().toString() : itemToSell.getItemMeta().getDisplayName();
                plugin.getLogger().info("Player " + playerSender.getName() + " add item to ah Item : " + itemName + ", At Price : " + price
                        + ", Commission : " + (commissionFree ? "free (nocommission quota)" : df.format(feeToPay)));

                Bukkit.getPluginManager().callEvent(new AuctionAddEvent(playerSender, itemToSell, price));

                MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_ADD_SUCCESS, "{item}", FormatUtil.titleItemFormat(itemToSell), "{price}", df.format(price));

                if (commissionFree) {
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.COMMISSION_FREE, "{remaining}", String.valueOf(freeListingsLeft - 1));
                } else if (feePaid.get()) {
                    MessageUtil.sendMessage(plugin, playerSender, MessageKeys.COMMISSION_PAID, "{fee}", df.format(feeToPay), "{percent}", df.format(commissionManager.getPercent()));
                }

                plugin.getSoundManager().playSell(playerSender);
            }, added -> {

                // Same reason as above : the hash must not survive the round trip, and it is no
                // longer removed by the sync step that has just been skipped.
                if (globalConfig.isFeatureDuplicationHashCodeControl()) {
                    itemHash.remove((Integer) itemToSell.hashCode());
                }

                if (!Boolean.TRUE.equals(added)) {
                    // The commission goes back with the same priority as the item : a sale that
                    // never happened was never charged. Vault refunds an offline player, the
                    // other currencies queue the amount until they come back.
                    if (feePaid.get()) {
                        CurrencyUtil.giveCurrency(plugin, playerSender, globalConfig.getCurrencyType(), feeToPay);
                    }
                    // The item left the inventory, was never saved, and the seller is gone : park it
                    // in their expired items rather than losing it. The id is the database's to
                    // assign, addExpire() ignores the one carried here.
                    Auction lost = new Auction(-1, playerSender.getUniqueId(), playerSender.getName(), price, SerializationUtil.serialize(itemToSell), System.currentTimeMillis());
                    if (!plugin.getExpireCommandManager().addExpire(lost)) {
                        plugin.getLogger().severe("Auction of " + playerSender.getName() + " could not be saved, and the seller left before the item could be given back ; the item is lost.");
                        return;
                    }
                    plugin.getLogger().severe("Auction of " + playerSender.getName() + " could not be saved, and the seller left before the item could be given back ; moved to their expired items.");
                }
            }).execute(() -> plugin.getClaimManager().release(playerSender.getUniqueId(), saleClaim));

        }).execute(() -> {
            // The sale is released by the chain that really took the item, if any.
            if (!itemTaken.get()) {
                plugin.getClaimManager().release(playerSender.getUniqueId(), saleClaim);
            }
        });
    }

    /**
     * Every check done before the item leaves the inventory.
     */
    private boolean isSellable(Player playerSender, ItemStack itemToSell, double price) {

        if (itemToSell.getType().equals(Material.AIR)) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.ITEM_AIR);
            return false;
        }

        if (price < 0) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.NEGATIVE_PRICE);
            return false;
        }

        if (globalConfig.getBlacklistItem().contains(itemToSell.getType())) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.ITEM_BLACKLIST);
            return false;
        }

        if (!haveCorrectMinPrice(itemToSell, playerSender, price)) {
            return false;
        }

        if (!haveCorrectMaxPrice(itemToSell, playerSender, price)) {
            return false;
        }

        return haveCorrectShulkerPrice(playerSender, itemToSell, price);
    }

    public boolean haveCorrectShulkerPrice(Player playerSender, ItemStack itemToSell, double price) {

        if (!Tag.SHULKER_BOXES.getValues().contains(itemToSell.getType())) {
            return true;
        }

        if (!(itemToSell.getItemMeta() instanceof BlockStateMeta im) || !(im.getBlockState() instanceof ShulkerBox shulker)) {
            return true;
        }

        double minPrice = 0;
        double maxPrice = 0;
        boolean minPriceSet = false;
        boolean maxPriceSet = false;

        for (ItemStack itemIn : shulker.getInventory().getContents()) {
            if (itemIn != null && (itemIn.getType() != Material.AIR)) {
                if (globalConfig.getMinPrice().containsKey(itemIn.getType())) {
                    minPrice += itemIn.getAmount() * globalConfig.getMinPrice().get(itemIn.getType());
                    minPriceSet = true;
                } else if (globalConfig.isDefaultMinValueEnable()) {
                    minPrice += itemIn.getAmount() * globalConfig.getDefaultMinValue();
                    minPriceSet = true;
                }

                if (globalConfig.getMaxPrice().containsKey(itemIn.getType())) {
                    maxPrice += itemIn.getAmount() * globalConfig.getMaxPrice().get(itemIn.getType());
                    maxPriceSet = true;
                } else if (globalConfig.isDefaultMaxValueEnable()) {
                    maxPrice += itemIn.getAmount() * globalConfig.getDefaultMaxValue();
                    maxPriceSet = true;
                }
            }
        }

        if (minPriceSet && minPrice > price) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MIN_PRICE, "{minPrice}", df.format(ceil(minPrice)));
            return false;
        }

        if (maxPriceSet && maxPrice < price) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MAX_PRICE, "{maxPrice}", df.format(ceil(maxPrice)));
            return false;
        }

        return true;
    }

    public boolean haveCorrectMinPrice(ItemStack itemToSell, Player player, double price) {

        if (globalConfig.getMinPrice().containsKey(itemToSell.getType())) {
            double minPrice = itemToSell.getAmount() * globalConfig.getMinPrice().get(itemToSell.getType());
            if (minPrice > price) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.MIN_PRICE, "{minPrice}", df.format(ceil(minPrice)));
                return false;
            }
        } else if (globalConfig.isDefaultMinValueEnable()) {
            double minPrice = itemToSell.getAmount() * globalConfig.getDefaultMinValue();
            if (minPrice > price) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.MIN_PRICE, "{minPrice}", df.format(ceil(minPrice)));
                return false;
            }
        }

        return true;
    }

    /**
     * Gives an item back to a player. Never writes in the main hand, which would destroy whatever
     * the player put there meanwhile.
     */
    public void resetItem(Player playerSender, ItemStack item) {
        if (playerSender.getInventory().firstEmpty() == -1) {
            playerSender.getWorld().dropItem(playerSender.getLocation(), item);
        } else {
            playerSender.getInventory().addItem(item);
        }
    }

    public boolean haveCorrectMaxPrice(ItemStack itemToSell, Player player, double price) {

        if (globalConfig.getMaxPrice().containsKey(itemToSell.getType())) {
            double maxPrice = itemToSell.getAmount() * globalConfig.getMaxPrice().get(itemToSell.getType());
            if (maxPrice < price) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.MAX_PRICE, "{maxPrice}", df.format(ceil(maxPrice)));
                return false;
            }
        } else if (globalConfig.isDefaultMaxValueEnable()) {
            double maxPrice = itemToSell.getAmount() * globalConfig.getDefaultMaxValue();
            if (maxPrice < price) {
                MessageUtil.sendMessage(plugin, player, MessageKeys.MAX_PRICE, "{maxPrice}", df.format(ceil(maxPrice)));
                return false;
            }
        }
        return true;
    }

    @Command(ROOT + SPACE + "sounds")
    @Permission("fauction.sounds")
    @CommandDescription("{@@fauction.sounds_help_description}")
    public void onSoundsStatus(Player playerSender) {
        MessageUtil.sendMessage(plugin, playerSender,
                plugin.getSoundManager().isSoundsEnabled(playerSender) ? MessageKeys.SOUNDS_ON : MessageKeys.SOUNDS_OFF);
    }

    @Command(ROOT + SPACE + "sounds <state>")
    @Permission("fauction.sounds")
    @CommandDescription("{@@fauction.sounds_help_description}")
    public void onSounds(Player playerSender, @Argument(value = "state", suggestions = SOUND_STATE_SUGGESTIONS) String state) {

        Boolean enabled = switch (state.toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable", "enabled" -> Boolean.TRUE;
            case "off", "false", "disable", "disabled" -> Boolean.FALSE;
            default -> null;
        };

        // An unknown state changes nothing and simply reports where the switch stands.
        if (enabled == null) {
            onSoundsStatus(playerSender);
            return;
        }

        plugin.getSoundManager().setSoundsEnabled(playerSender, enabled);
        MessageUtil.sendMessage(plugin, playerSender, enabled ? MessageKeys.SOUNDS_ON : MessageKeys.SOUNDS_OFF);
    }

    @Suggestions(SOUND_STATE_SUGGESTIONS)
    public List<String> soundStateSuggestions() {
        return List.of("on", "off");
    }

    @Command(ROOT + SPACE + EXPIRE)
    @Permission("fauction.expire")
    @CommandDescription("{@@fauction.expire_add_help_description}")
    public void onExpire(Player playerSender) {

        FAuction.newChain(playerSender).asyncFirst(() -> expireCommandManager.getExpires(playerSender.getUniqueId())).syncLast(auctions -> {
            ExpireGui gui = new ExpireGui(plugin, playerSender, auctions, 1, null, null);
            gui.initialize();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_OPEN);
        }).execute();
    }

    @Command(ROOT + SPACE + ADMIN + SPACE + "reload")
    @Permission("fauction.admin.reload")
    @CommandDescription("{@@fauction.reload_help_description}")
    public void onReload(Player playerSender) {

        boolean commandNamesChanged = plugin.reloadConfiguration();
        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_RELOAD);

        // The commands answer under the names Bukkit was given when the plugin enabled ; the ones
        // just read from config.yml only take effect on the next start.
        if (commandNamesChanged) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.COMMANDS_RESTART_REQUIRED);
        }
    }

    @Command(ROOT + SPACE + ADMIN + SPACE + "purge all")
    @Permission("fauction.admin.purge.all")
    @CommandDescription("{@@fauction.reload_help_description}")
    public void onPurgeAll(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllData();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_PURGE);
        }).execute();
    }

    @Command(ROOT + SPACE + ADMIN + SPACE + "purge historic")
    @Permission("fauction.admin.purge.hictoric")
    @CommandDescription("{@@fauction.reload_help_description}")
    public void onPurgeAllHistoric(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllHistoric();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_PURGE);
        }).execute();
    }

    @Command(ROOT + SPACE + ADMIN + SPACE + "purge expire")
    @Permission("fauction.admin.purge.expire")
    @CommandDescription("{@@fauction.reload_help_description}")
    public void onPurgeAllExpire(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllExpire();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_PURGE);
        }).execute();
    }

    @Command(ROOT + SPACE + ADMIN + SPACE + "purge auction")
    @Permission("fauction.admin.purge.auction")
    @CommandDescription("{@@fauction.reload_help_description}")
    public void onPurgeAllAucton(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.purgeAllAuction();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.AUCTION_PURGE);
        }).execute();
    }

    @Command(ROOT + SPACE + ADMIN + SPACE + "slots <playerName>")
    @Permission("fauction.admin.slots")
    @CommandDescription("{@@fauction.slots_info_help_description}")
    public void onAdminSlots(Player playerSender, @Argument(value = "playerName", suggestions = ONLINE_PLAYER_SUGGESTIONS) String playerName) {

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.PLAYER_NOT_ONLINE, "{player}", playerName);
            return;
        }

        FAuction.newChain(playerSender).asyncFirst(() -> auctionCommandManager.getAuctions(target.getUniqueId()).size()).syncLast(active -> {
            int baseLimit = plugin.getLimitationManager().getBaseAuctionLimitation(target);
            int extraSlots = plugin.getLimitationManager().getExtraSlots(target);
            int totalLimit = plugin.getLimitationManager().getAuctionLimitation(target);
            String totalText = totalLimit < 0 ? "∞" : String.valueOf(totalLimit);
            String freeText = totalLimit < 0 ? "∞" : String.valueOf(Math.max(0, totalLimit - active));

            int baseStorageDays = plugin.getExpireStorageManager().getBaseStorageDays();
            int storageBonusDays = plugin.getExpireStorageManager().getBonusDays(target);
            String storageDaysText = baseStorageDays < 0 ? "∞" : String.valueOf(baseStorageDays + storageBonusDays);

            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.SLOTS_INFO_HEADER, "{player}", target.getName());
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.SLOTS_INFO_BASE, "{base}", String.valueOf(baseLimit));
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.SLOTS_INFO_BONUS, "{extra}", String.valueOf(extraSlots), "{permissions}", heldPermissions(target, LimitationManager.EXTRA_SLOT_PERMISSION_PREFIX));
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.SLOTS_INFO_TOTAL, "{free}", freeText, "{total}", totalText, "{active}", String.valueOf(active));
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.SLOTS_INFO_STORAGE, "{days}", storageDaysText, "{base}", String.valueOf(baseStorageDays), "{bonus}", String.valueOf(storageBonusDays), "{permissions}", heldPermissions(target, ExpireStorageManager.PERMISSION_PREFIX));
        }).execute();
    }

    /**
     * The {@code <prefix>1 .. <prefix>10} permissions of the series the player actually holds,
     * "-" when they hold none : what the administrator needs to see to understand a bonus.
     */
    private String heldPermissions(Player target, String prefix) {
        List<String> held = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            if (target.hasPermission(prefix + i)) {
                held.add(prefix + i);
            }
        }
        return held.isEmpty() ? "-" : String.join(", ", held);
    }

    @Suggestions(ONLINE_PLAYER_SUGGESTIONS)
    public List<String> onlinePlayerSuggestions() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    // Two spellings, one wipe : "clear data players" is the form asked for when a season ends,
    // "admin purge players" keeps it next to the other purges. The words are literals, like
    // "purge all" : only the root (and the admin branch) can be renamed in config.yml.
    @Command(ROOT + SPACE + "clear data players")
    @Command(ROOT + SPACE + ADMIN + SPACE + "purge players")
    @Permission("fauction.admin.clear.players")
    @CommandDescription("{@@fauction.clear_players_help_description}")
    public void onClearPlayerData(Player playerSender) {

        FAuction.newChain().async(() -> {
            plugin.clearPlayerData();
            MessageUtil.sendMessage(plugin, playerSender, MessageKeys.PLAYER_DATA_CLEAR);
        }).execute();
    }

    @Command(ROOT + SPACE + ADMIN + SPACE + "transfertToPaper")
    @Permission("fauction.admin.transfertBddToPaper")
    @CommandDescription("{@@fauction.transfert_bdd_help_description}")
    public void onTransferBddPaper(Player playerSender) {

        plugin.getTransfertManager().transfertBDD(true);
        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.TRANSFERT_BDD);
    }

    @Command(ROOT + SPACE + ADMIN + SPACE + "transfertToBukkit")
    @Permission("fauction.admin.transfertBddToPaper")
    @CommandDescription("{@@fauction.transfert_bdd_help_description}")
    public void onTransferBddSpigot(Player playerSender) {

        plugin.getTransfertManager().transfertBDD(false);
        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.TRANSFERT_BDD);
    }

    @Command(ROOT + SPACE + ADMIN + SPACE + "migrate <migrateVersion>")
    @Permission("fauction.admin.migrate")
    @CommandDescription("{@@fauction.migrate_help_description}")
    public void onMigrate(Player playerSender,
                          @Argument(value = "migrateVersion", parserName = MIGRATE_VERSION_PARSER) MigrateVersion migrateVersion) {

        plugin.migrate(migrateVersion);
        MessageUtil.sendMessage(plugin, playerSender, MessageKeys.MIGRATE, "{version}", migrateVersion.getId());
    }

    @Command(ROOT + SPACE + HELP + SPACE + "[query]")
    @CommandDescription("{@@fauction.help_description}")
    public void doHelp(CommandSender sender, @Argument("query") @Greedy String query) {
        plugin.getCommandManager().help(sender, query == null ? "" : query);
    }

    /**
     * The price of a sale, checked before the handler runs — so before the ClaimManager reservation
     * and before the item leaves the inventory.
     * <p>
     * Every comparison involving {@code NaN} is false, so {@code price < 0}, {@code minPrice > price}
     * and {@code maxPrice < price} all let it through, and {@code applyMoneyFormat} hands the input
     * back unchanged when it cannot parse it. {@code Infinity} only ever hit a maximum price when one
     * happened to be configured. Neither can reach the business code any more.
     */
    @Parser(name = PRICE_PARSER, suggestions = PRICE_SUGGESTIONS)
    public double parsePrice(CommandInput input) {
        String token = input.readString();

        double price;
        try {
            price = Double.parseDouble(token);
        } catch (NumberFormatException e) {
            throw new NotANumberException(token);
        }

        if (!Double.isFinite(price) || price < 0) {
            throw new InvalidPriceException(token);
        }

        return price;
    }

    /**
     * A migration version the plugin actually knows how to run. An unknown version is refused here
     * instead of being announced as a success by the handler.
     */
    @Parser(name = MIGRATE_VERSION_PARSER, suggestions = MIGRATE_VERSION_SUGGESTIONS)
    public MigrateVersion parseMigrateVersion(CommandInput input) {
        String token = input.readString();
        return MigrateVersion.byId(token).orElseThrow(() -> new UnknownMigrateVersionException(token));
    }

    /**
     * Cloud passes the context and the input to a suggestion method only if it asks for them ; the
     * list of versions depends on neither, and Brigadier filters the suggestions on what is typed.
     */
    @Suggestions(MIGRATE_VERSION_SUGGESTIONS)
    public List<String> migrateVersionSuggestions() {
        return MigrateVersion.ids();
    }

    @Suggestions(PRICE_SUGGESTIONS)
    public List<String> priceSuggestions() {
        return PRICE_SUGGESTION_VALUES;
    }

    @Suggestions(AMOUNT_SUGGESTIONS)
    public List<String> amountSuggestions() {
        return AMOUNT_SUGGESTION_VALUES;
    }

    /**
     * The material of a search, read the way players type it : any case, with or without the
     * {@code minecraft:} namespace. The parser Cloud registers by default wants the exact name
     * of the enum, so {@code /ah search diamond_sword} failed where {@code DIAMOND_SWORD}
     * worked — and Minecraft players type items in lowercase everywhere else.
     */
    @Parser(name = MATERIAL_PARSER, suggestions = MATERIAL_SUGGESTIONS)
    public Material parseMaterial(CommandInput input) {
        String token = input.readString();
        return parseMaterialName(token).orElseThrow(() -> new UnknownMaterialException(token));
    }

    static Optional<Material> parseMaterialName(String raw) {
        if (raw == null) {
            return Optional.empty();
        }

        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("minecraft:")) {
            name = name.substring("minecraft:".length());
        }
        name = name.replace(' ', '_').replace('-', '_');
        if (name.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Material.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknownEnumName) {
            return Optional.ofNullable(Material.matchMaterial(name));
        }
    }

    /**
     * The materials still matching what the player has typed. Left to itself, the completion
     * proposes every material of the game and filters them on a case-sensitive prefix ;
     * narrowing the list here is what makes {@code dia} — or {@code DIA} — land on the handful
     * of materials the player is actually aiming at.
     */
    @Suggestions(MATERIAL_SUGGESTIONS)
    public List<String> materialSuggestions(CommandInput input) {
        String remaining = input.remainingInput();
        return materialSuggestionsFor(remaining.substring(remaining.lastIndexOf(' ') + 1));
    }

    /**
     * Every material there is under the lowercase name players type, minus the airs and the
     * legacy aliases : the names the suggestions of {@code /ah search} narrow on what is
     * typed. Names only, deliberately — asking each material whether it is an item would go
     * through the server registries, and a suggestion has no business depending on them.
     */
    static List<String> materialNames() {
        List<String> names = materialNames;
        if (names == null) {
            names = Arrays.stream(Material.values())
                    .map(material -> material.name().toLowerCase(Locale.ROOT))
                    .filter(name -> !name.equals("air") && !name.endsWith("_air") && !name.startsWith("legacy_"))
                    .sorted()
                    .toList();
            materialNames = names;
        }
        return names;
    }

    static List<String> materialSuggestionsFor(String typed) {
        String fragment = typed == null ? "" : typed.trim().toLowerCase(Locale.ROOT);
        if (fragment.startsWith("minecraft:")) {
            fragment = fragment.substring("minecraft:".length());
        }
        fragment = fragment.replace('-', '_');

        // Prefix matches first — what the player is most likely typing — then the names merely
        // containing the fragment, for the {@code sword} typed in search of every sword.
        List<String> byPrefix = new ArrayList<>();
        List<String> byFragment = new ArrayList<>();
        for (String materialName : materialNames()) {
            if (materialName.startsWith(fragment)) {
                byPrefix.add(materialName);
            } else if (materialName.contains(fragment)) {
                byFragment.add(materialName);
            }
        }
        byPrefix.addAll(byFragment);
        return byPrefix;
    }

    /**
     * The input is not a number at all. Carries the input so the message can name it, like the
     * {@code must_be_a_number} of ACF did.
     */
    public static final class NotANumberException extends IllegalArgumentException {

        private final transient String input;

        NotANumberException(String input) {
            super("Not a number : " + input);
            this.input = input;
        }

        public String getInput() {
            return input;
        }
    }

    /**
     * The input is a number, but not a price : negative, {@code NaN} or infinite.
     */
    public static final class InvalidPriceException extends IllegalArgumentException {

        private final transient String input;

        InvalidPriceException(String input) {
            super("Not a valid price : " + input);
            this.input = input;
        }

        public String getInput() {
            return input;
        }
    }

    public static final class UnknownMigrateVersionException extends IllegalArgumentException {

        private final transient String input;

        UnknownMigrateVersionException(String input) {
            super("Unknown migration version : " + input);
            this.input = input;
        }

        public String getInput() {
            return input;
        }
    }

    public static final class UnknownMaterialException extends IllegalArgumentException {

        private final transient String input;

        UnknownMaterialException(String input) {
            super("Unknown material : " + input);
            this.input = input;
        }

        public String getInput() {
            return input;
        }
    }
}