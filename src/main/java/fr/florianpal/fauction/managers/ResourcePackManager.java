package fr.florianpal.fauction.managers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.utils.BedrockUtil;
import fr.florianpal.fauction.utils.TextUtil;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The plugin's own resource pack (the cash-register sound of a purchase), served by the plugin
 * itself : the files are embedded in the jar under {@code resources/resourcepack/}, zipped in
 * memory at startup and handed out by a small JDK {@link HttpServer}, so no external hosting is
 * needed. The block lives in {@code resource-pack} in {@code config.yml} (port, public host,
 * mandatory or not).
 * <p>
 * Java players are offered the pack at join (Adventure {@code sendResourcePacks}) and their
 * answer is tracked through {@code PlayerResourcePackStatusEvent} : a pack declined, failed or
 * not loaded yet means the custom register would land in silence, so {@link SoundManager} gives
 * those players the vanilla trade instead. Bedrock players are never offered the pack — a Java
 * pack is meaningless to their client — and always hear the vanilla sound.
 */
public class ResourcePackManager {

    /** The pack entries, under {@code resourcepack/} in the jar resources. */
    static final String[] PACK_FILES = {
            "assets/minecraft/sounds.json",
            "assets/custom/sounds.json",
            "assets/custom/sounds/cashregister.ogg"
    };

    static final String PACK_PATH = "/fauction-pack.zip";

    /**
     * 75 is the resource-pack format of 1.21.11, the minimum a 1.21.11 client accepts without a
     * word. The range reaches far ahead on purpose : a sounds.json and an .ogg do not change
     * shape between versions, so future clients keep accepting the same pack instead of being
     * served a new download (and a new compatibility warning) every game update.
     */
    static final String PACK_MCMETA = """
            {
              "pack": {
                "min_format": 75,
                "max_format": 999,
                "description": "FAuction sounds"
              }
            }
            """;

    /** One fixed instant on every entry, so the zip — and its SHA-1 — is the same on every start. */
    private static final long ENTRY_TIME = 1640995200000L;

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("fauction-pack".getBytes(StandardCharsets.UTF_8));

    private final FAuction plugin;

    private final Map<UUID, Boolean> loadedByPlayer = new ConcurrentHashMap<>();

    private HttpServer server;

    private byte[] packBytes;

    private String packHash;

    private String packUrl;

    private boolean enabled;

    public ResourcePackManager(FAuction plugin) {
        this.plugin = plugin;
    }

    /**
     * (Re)builds the pack and starts serving it. Safe to call on reload : the old server is
     * stopped first, and the new block of the configuration (port, host, off switch) is what
     * answers afterwards. Any failure leaves the pack disabled with a warning, never the plugin
     * half-started — the sounds simply fall back on vanilla.
     */
    public synchronized void start() {
        stop();
        GlobalConfig config = config();
        if (config == null || !config.isResourcePackEnabled()) {
            return;
        }
        try {
            packBytes = buildPackZip(name -> plugin.getResource("resourcepack/" + name));
            packHash = sha1Hex(packBytes);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("The built-in resource pack could not be built (" + e.getMessage()
                    + ") : the purchase sound falls back on the vanilla one.");
            packBytes = null;
            return;
        }
        int port = config.getResourcePackPort();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(PACK_PATH, this::servePack);
            server.setExecutor(Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "FAuction-ResourcePack");
                thread.setDaemon(true);
                return thread;
            }));
            server.start();
        } catch (IOException e) {
            plugin.getLogger().warning("The built-in resource pack server could not listen on port " + port
                    + " (" + e.getMessage() + ") : is another plugin or program using it? "
                    + "The purchase sound falls back on the vanilla one.");
            server = null;
            return;
        }
        packUrl = "http://" + resolveHost(config.getResourcePackHost()) + ":" + port + PACK_PATH;
        enabled = true;
        plugin.getLogger().info("Resource pack (" + packBytes.length + " bytes) served to Java players at " + packUrl);
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        enabled = false;
        packUrl = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Offers the pack to a joining Java player. Bedrock players are skipped before anything is
     * built : their client cannot read a Java pack, and Geyser does not translate one, so the
     * prompt would only ever end in a failed download.
     */
    public void sendTo(Player player) {
        if (!enabled || packUrl == null || player == null || BedrockUtil.isBedrockPlayer(player)) {
            return;
        }
        GlobalConfig config = config();
        try {
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo()
                    .id(PACK_ID)
                    .uri(URI.create(packUrl))
                    .hash(packHash)
                    .build();
            ResourcePackRequest.Builder request = ResourcePackRequest.resourcePackRequest()
                    .packs(info)
                    .required(config != null && config.isResourcePackRequired());
            String prompt = config != null ? config.getResourcePackPrompt() : null;
            if (prompt != null && !prompt.isBlank()) {
                Component promptComponent = TextUtil.parse(prompt);
                if (promptComponent != null) {
                    request.prompt(promptComponent);
                }
            }
            player.sendResourcePacks(request.build());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not offer the resource pack to " + player.getName()
                    + " (" + e.getMessage() + ") : they keep the vanilla purchase sound.");
        }
    }

    /**
     * The answer of a client to the offer. Only a successful load unlocks the custom sound ;
     * declined and every failure spell vanilla, and the in-between states (accepted, downloading)
     * change nothing until the verdict lands.
     */
    public void handleStatus(UUID playerUuid, String statusName) {
        if (playerUuid == null || statusName == null) {
            return;
        }
        switch (statusName) {
            case "SUCCESSFULLY_LOADED" -> loadedByPlayer.put(playerUuid, Boolean.TRUE);
            case "DECLINED", "FAILED_DOWNLOAD", "FAILED_RELOAD", "DISCARDED", "INVALID_URL" ->
                    loadedByPlayer.put(playerUuid, Boolean.FALSE);
            default -> {
            }
        }
    }

    public boolean hasLoadedPack(UUID playerUuid) {
        return Boolean.TRUE.equals(loadedByPlayer.get(playerUuid));
    }

    public void forget(UUID playerUuid) {
        loadedByPlayer.remove(playerUuid);
    }

    /**
     * Whether a player can actually hear a resource-pack sound : Java edition (Bedrock clients
     * never receive Java packs), and — when this plugin serves the pack — the pack loaded on
     * their client. When the pack is not ours to serve ({@code resource-pack.enabled: false}) the
     * server may be shipping its own through {@code server.properties}, so a Java player keeps
     * the custom sound exactly as before this block existed.
     */
    static boolean shouldUseCustomSound(boolean bedrock, boolean packManaged, boolean packLoaded) {
        return !bedrock && (!packManaged || packLoaded);
    }

    static byte[] buildPackZip(Function<String, InputStream> resourceOpener) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            writeEntry(zip, "pack.mcmeta", PACK_MCMETA.getBytes(StandardCharsets.UTF_8));
            for (String file : PACK_FILES) {
                try (InputStream in = resourceOpener.apply(file)) {
                    if (in == null) {
                        throw new IOException("missing resource resourcepack/" + file);
                    }
                    writeEntry(zip, file, in.readAllBytes());
                }
            }
        }
        return buffer.toByteArray();
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(ENTRY_TIME);
        zip.putNextEntry(entry);
        zip.write(content);
        zip.closeEntry();
    }

    static String sha1Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    private void servePack(HttpExchange exchange) throws IOException {
        byte[] bytes = packBytes;
        if (bytes == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String resolveHost(String configuredHost) {
        if (configuredHost != null && !configuredHost.isBlank()) {
            return configuredHost.trim();
        }
        String serverIp = Bukkit.getIp();
        if (serverIp != null && !serverIp.isBlank()) {
            return serverIp.trim();
        }
        plugin.getLogger().warning("resource-pack.host is empty and server-ip is not set either : "
                + "players will be pointed at 127.0.0.1, which only works for clients running on this "
                + "machine. Set resource-pack.host to your server address so everyone can download the pack.");
        return "127.0.0.1";
    }

    private GlobalConfig config() {
        return plugin.getConfigurationManager() != null
                ? plugin.getConfigurationManager().getGlobalConfig()
                : null;
    }
}
