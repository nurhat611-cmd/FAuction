package fr.florianpal.fauction.events;

import fr.florianpal.fauction.objects.Historic;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CacheHistoricReloadEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Map<UUID, List<Historic>> cache;

    public CacheHistoricReloadEvent(Map<UUID, List<Historic>> cache) {

        this.cache = cache;
    }


    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public Map<UUID, List<Historic>> getCache() {
        return cache;
    }
}
