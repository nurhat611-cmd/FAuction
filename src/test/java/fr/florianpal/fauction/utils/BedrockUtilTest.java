package fr.florianpal.fauction.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockUtilTest {

    @Test
    @DisplayName("Floodgate-shaped UUIDs read as Bedrock, Java UUIDs do not")
    void detectsFloodgateUuids() {
        assertTrue(BedrockUtil.isBedrockUuid(UUID.fromString("00000000-0000-0000-0009-000012345678")));
        assertTrue(BedrockUtil.isBedrockUuid(UUID.fromString("00000000-0000-0000-0009-ffffffffffff")));
        assertFalse(BedrockUtil.isBedrockUuid(UUID.fromString("00000000-0000-0000-0000-00000000000a")));
        // A version-4 UUID carries its 4 in the third group : it can never wear the prefix.
        assertFalse(BedrockUtil.isBedrockUuid(UUID.randomUUID()));
        assertFalse(BedrockUtil.isBedrockUuid(null));
    }
}
