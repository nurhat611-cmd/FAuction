package fr.florianpal.fauction.enums;

/**
 * How the free-listing quota of {@code fauction.nocommission.<n>} is spent.
 * <p>
 * Both readings exist among server owners, so the choice is one line of config.yml
 * ({@code commission.free-quota-mode}) rather than a behaviour baked into the code.
 */
public enum FreeQuotaMode {

    /**
     * The quota is a lifetime budget : a player with {@code fauction.nocommission.10} lists ten
     * lots without commission, ever, and every next lot pays the commission. The spent counter is
     * stored per player (table {@code fa_player_profiles}) and only ever goes back to zero on a
     * wipe ({@code /ah clear data players}).
     */
    LIFETIME,

    /**
     * The quota is a number of simultaneous free lots : a lot listed without commission keeps its
     * free slot busy while it is on the market, and the slot comes back as soon as the lot leaves
     * it — sold, cancelled by its owner, or expired into {@code /ah expire}. Nothing is counted
     * here : the value is derived from the {@code commissionFree} flag carried by the active lots
     * themselves.
     */
    ACTIVE
}
