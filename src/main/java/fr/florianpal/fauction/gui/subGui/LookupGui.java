package fr.florianpal.fauction.gui.subGui;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.objects.Auction;
import fr.florianpal.fauction.objects.Category;
import fr.florianpal.fauction.objects.Sort;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * The market narrowed to the lots of one seller — what {@code /ah lookup <player>} opens.
 * <p>
 * Everything a click does is the one of {@link AuctionsGui} : buy on the left click, preview —
 * or withdraw, for the owner looking themselves up — on the right. Only the refreshes differ :
 * paging, categories and sorting reload the lots of the looked-up seller instead of the whole
 * market, so the view stays the one the command promised. The navigation buttons of the gui
 * (market, expired, mine, history) still leave it, like they leave a search.
 */
public class LookupGui extends AuctionsGui {

    private final UUID ownerUuid;

    public LookupGui(FAuction plugin, Player player, UUID ownerUuid, List<Auction> auctions, int page, Category category, Sort sort) {
        super(plugin, player, auctions, page, category, sort);
        this.ownerUuid = ownerUuid;
    }

    @Override
    protected void previousAction() {
        FAuction.newChain(player).asyncFirst(() -> auctionCommandManager.getAuctions(ownerUuid)).syncLast(auctions -> {
            LookupGui gui = new LookupGui(plugin, player, ownerUuid, auctions, this.page - 1, category, sort);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void nextAction() {
        FAuction.newChain(player).asyncFirst(() -> auctionCommandManager.getAuctions(ownerUuid)).syncLast(auctions -> {
            LookupGui gui = new LookupGui(plugin, player, ownerUuid, auctions, this.page + 1, category, sort);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void categoryAction(Category nextCategory) {
        FAuction.newChain(player).asyncFirst(() -> auctionCommandManager.getAuctions(ownerUuid)).syncLast(auctions -> {
            LookupGui gui = new LookupGui(plugin, player, ownerUuid, auctions, 1, nextCategory, sort);
            gui.initialize();
        }).execute();
    }

    @Override
    protected void sortingAction(Sort nextSort) {
        FAuction.newChain(player).asyncFirst(() -> auctionCommandManager.getAuctions(ownerUuid)).syncLast(auctions -> {
            LookupGui gui = new LookupGui(plugin, player, ownerUuid, auctions, 1, category, nextSort);
            gui.initialize();
        }).execute();
    }
}
