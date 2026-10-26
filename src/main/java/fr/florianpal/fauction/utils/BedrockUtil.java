package fr.florianpal.fauction.utils;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bedrock (Geyser/Floodgate) players, seen from a Java server. A Bedrock client cannot use a
 * Java resource pack at all — Geyser does not translate it — so everything pack-shaped in the
 * plugin has to know who is sitting on the other side of the proxy and fall back on vanilla for
 * them.
 * <p>
 * Floodgate is a {@code softdepend} : its API is reached by reflection, never linked, so the
 * plugin still runs where Floodgate is absent. The reflection is backed by the Floodgate UUID
 * shape ({@code 00000000-0000-0000-0009-...}), which still answers when the API itself is
 * unreachable (a Floodgate build that moved its classes, a proxy-side Floodgate install where
 * only the UUID crosses to the backend...).
 */
public final class BedrockUtil {

    /** Floodgate gives every Bedrock player a UUID under this prefix on the Java side. */
    static final String FLOODGATE_UUID_PREFIX = "00000000-0000-0000-0009-";

    private static volatile Boolean floodgateAvailable;
    private static volatile Object floodgateApi;
    private static volatile Method isFloodgatePlayer;

    private BedrockUtil() {
    }

    public static boolean isBedrockPlayer(Player player) {
        return player != null && isBedrockUuid(player.getUniqueId());
    }

    static boolean isBedrockUuid(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        if (uuid.toString().startsWith(FLOODGATE_UUID_PREFIX)) {
            return true;
        }
        return floodgateSaysBedrock(uuid);
    }

    private static boolean floodgateSaysBedrock(UUID uuid) {
        Boolean available = floodgateAvailable;
        if (available == null) {
            available = resolveFloodgate();
            floodgateAvailable = available;
        }
        if (!available) {
            return false;
        }
        try {
            Object verdict = isFloodgatePlayer.invoke(floodgateApi, uuid);
            return verdict instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static boolean resolveFloodgate() {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) {
                return false;
            }
            Method method = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            floodgateApi = api;
            isFloodgatePlayer = method;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            floodgateApi = null;
            isFloodgatePlayer = null;
            return false;
        }
    }
}
