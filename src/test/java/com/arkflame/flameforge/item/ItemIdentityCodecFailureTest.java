package com.arkflame.flameforge.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemIdentityCodecFailureTest {
    private final ItemIdentityCodec codec = new ItemIdentityCodec();

    @Test
    void failureVariantIdRoundTripsThroughJsonPayload() {
        ItemIdentityCodec.Identity original = ItemIdentityCodec.Identity.empty()
            .withCurrentTier(2)
            .withFailureVariantId("shatter");

        String payload = codec.encodeToString(original);
        ItemIdentityCodec.Decoded decoded = codec.decodeFromString(payload);

        assertTrue(decoded.isValid());
        assertEquals(original, decoded.getIdentity());
        assertEquals("shatter", decoded.getIdentity().getFailureVariantId());
        assertTrue(decoded.getIdentity().isFailed());
        assertFalse(decoded.getIdentity().isCursed());
    }

    @Test
    void legacyCursedFlagAndFailureIdCanCoexist() {
        ItemIdentityCodec.Identity original = ItemIdentityCodec.Identity.empty()
            .withCursed(true)
            .withFailureVariantId("curse");

        ItemIdentityCodec.Identity decoded = codec.decodeFromString(
            codec.encodeToString(original)).getIdentity();

        assertTrue(decoded.isCursed());
        assertEquals("curse", decoded.getFailureVariantId());
        assertEquals(original.hashCode(), decoded.hashCode());
    }

    @Test
    void absentFailureIdRemainsNullAndCanBeCleared() {
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty();
        assertNull(identity.getFailureVariantId());
        assertFalse(identity.isFailed());

        ItemIdentityCodec.Identity failed = identity.withFailureVariantId("break");
        assertNull(failed.withFailureVariantId(null).getFailureVariantId());
        assertTrue(failed.toString().contains("failureVariantId=break"));
    }
}
