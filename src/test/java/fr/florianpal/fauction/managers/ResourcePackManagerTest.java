package fr.florianpal.fauction.managers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackManagerTest {

    @Test
    @DisplayName("The pack zip carries pack.mcmeta and every embedded sound file, whole")
    void buildsThePackZip() throws Exception {
        byte[] zip = buildFromClasspath();
        Map<String, byte[]> entries = unzip(zip);

        assertTrue(entries.containsKey("pack.mcmeta"));
        String mcmeta = new String(entries.get("pack.mcmeta"), StandardCharsets.UTF_8);
        assertTrue(mcmeta.contains("\"min_format\": 75"), mcmeta);
        for (String file : ResourcePackManager.PACK_FILES) {
            assertTrue(entries.containsKey(file), file);
            assertTrue(entries.get(file).length > 0, file);
        }
        // The register makes the trip byte for byte — a truncated .ogg would play as silence.
        try (InputStream in = ResourcePackManagerTest.class
                .getResourceAsStream("/resourcepack/assets/custom/sounds/cashregister.ogg")) {
            assertArrayEquals(in.readAllBytes(), entries.get("assets/custom/sounds/cashregister.ogg"));
        }
    }

    @Test
    @DisplayName("The same pack zips to the same bytes, so the served hash is stable across restarts")
    void buildsDeterministically() throws Exception {
        byte[] first = buildFromClasspath();
        byte[] second = buildFromClasspath();

        assertArrayEquals(first, second);
        String hash = ResourcePackManager.sha1Hex(first);
        assertEquals(40, hash.length());
        assertTrue(hash.matches("[0-9a-f]{40}"), hash);
        assertEquals(hash, ResourcePackManager.sha1Hex(second));
    }

    @Test
    @DisplayName("A missing pack file refuses the build instead of serving a hollow pack")
    void missingFileRefusesTheBuild() {
        assertThrows(java.io.IOException.class,
                () -> ResourcePackManager.buildPackZip(name -> null));
    }

    @Test
    @DisplayName("Only Java players with the pack (or an unmanaged pack setup) hear custom sounds")
    void decidesWhoHearsCustomSounds() {
        // Bedrock never hears a Java-pack sound, whatever the pack state.
        assertFalse(ResourcePackManager.shouldUseCustomSound(true, false, false));
        assertFalse(ResourcePackManager.shouldUseCustomSound(true, true, true));
        // Pack not ours to serve : the server may ship its own, Java players keep the custom sound.
        assertTrue(ResourcePackManager.shouldUseCustomSound(false, false, false));
        // Pack served by the plugin : loaded hears it, declined/failed/pending does not.
        assertTrue(ResourcePackManager.shouldUseCustomSound(false, true, true));
        assertFalse(ResourcePackManager.shouldUseCustomSound(false, true, false));
    }

    @Test
    @DisplayName("Declined and failed downloads never count as a loaded pack")
    void tracksPackAnswers() {
        ResourcePackManager manager = new ResourcePackManager(null);
        UUID player = UUID.randomUUID();

        assertFalse(manager.hasLoadedPack(player));
        manager.handleStatus(player, "ACCEPTED");
        assertFalse(manager.hasLoadedPack(player));
        manager.handleStatus(player, "SUCCESSFULLY_LOADED");
        assertTrue(manager.hasLoadedPack(player));
        manager.handleStatus(player, "FAILED_DOWNLOAD");
        assertFalse(manager.hasLoadedPack(player));
        manager.handleStatus(player, "SUCCESSFULLY_LOADED");
        manager.forget(player);
        assertFalse(manager.hasLoadedPack(player));
    }

    private static byte[] buildFromClasspath() throws Exception {
        return ResourcePackManager.buildPackZip(
                name -> ResourcePackManagerTest.class.getResourceAsStream("/resourcepack/" + name));
    }

    private static Map<String, byte[]> unzip(byte[] zipBytes) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
