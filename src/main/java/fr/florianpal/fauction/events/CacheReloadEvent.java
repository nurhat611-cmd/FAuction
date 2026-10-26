package fr.florianpal.fauction.events;

import fr.florianpal.fauction.enums.CacheType;
import fr.florianpal.fauction.objects.Auction;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CacheReloadEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Map<UUID, List<Auction>> cache;

    private final CacheType cacheType;

    public CacheReloadEvent(Map<UUID, List<Auction>> cache, CacheType cacheType) {

        this.cache = cache;
        this.cacheType = cacheType;
    }


    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public Map<UUID, List<Auction>> getCache() {
        return cache;
    }

    public CacheType getCacheType() {
        return cacheType;
    }
}
