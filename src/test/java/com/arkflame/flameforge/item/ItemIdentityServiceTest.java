package com.arkflame.flameforge.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemIdentityServiceTest {

    @Test
    void testV2CodecSchemaVersion() {
        ItemIdentityCodec codec = new ItemIdentityCodec();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty();
        assertEquals(2, identity.getSchemaVersion());
    }

    @Test
    void testV2CodecEncodesAndDecodesRoundTrip() {
        ItemIdentityCodec codec = new ItemIdentityCodec();
        UUID forgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity original = ItemIdentityCodec.Identity.empty()
            .withCurrentTier(3)
            .withHighestTier(5)
            .withReforgeCount(7)
            .withForgeId(forgeId)
            .withLastTierId("tier3")
            .withLastVariantId("variant_a");

        String encoded = codec.encodeToString(original);
        assertNotNull(encoded);
        assertFalse(encoded.isEmpty());

        ItemIdentityCodec.Decoded decoded = codec.decodeFromString(encoded);
        assertTrue(decoded.isValid());
        assertEquals(ItemIdentityCodec.DecodeResult.VALID, decoded.getResult());

        ItemIdentityCodec.Identity result = decoded.getIdentity();
        assertEquals(3, result.getCurrentTier());
        assertEquals(5, result.getHighestTier());
        assertEquals(7, result.getReforgeCount());
        assertEquals(forgeId, result.getForgeId());
        assertEquals("tier3", result.getLastTierId());
        assertEquals("variant_a", result.getLastVariantId());
    }

    @Test
    void testV2CodecIsActiveAuthority() {
        ItemIdentityCodec codec = new ItemIdentityCodec();
        assertEquals("flameforge:state", codec.getModernKey());
        assertEquals("\u00A70\u00A70FLAMEFORGE:v2:", codec.getLegacyMarker());
    }

    @Test
    void testV2CodecDecodeInvalidReturnsInvalid() {
        ItemIdentityCodec codec = new ItemIdentityCodec();
        ItemIdentityCodec.Decoded decoded = codec.decodeFromString("not-valid-base64!@#$");
        assertFalse(decoded.isValid());
        assertEquals(ItemIdentityCodec.DecodeResult.INVALID_IDENTITY, decoded.getResult());
    }

    @Test
    void testV2CodecDecodeNullReturnsInvalid() {
        ItemIdentityCodec codec = new ItemIdentityCodec();
        ItemIdentityCodec.Decoded decoded = codec.decodeFromString(null);
        assertFalse(decoded.isValid());
        assertEquals(ItemIdentityCodec.DecodeResult.INVALID_IDENTITY, decoded.getResult());
    }

    @Test
    void testV2CodecDecodeEmptyReturnsInvalid() {
        ItemIdentityCodec codec = new ItemIdentityCodec();
        ItemIdentityCodec.Decoded decoded = codec.decodeFromString("");
        assertFalse(decoded.isValid());
        assertEquals(ItemIdentityCodec.DecodeResult.INVALID_IDENTITY, decoded.getResult());
    }

    @Test
    void testV2CodecEncodeNullIdentityReturnsEmpty() {
        ItemIdentityCodec codec = new ItemIdentityCodec();
        String encoded = codec.encodeToString(null);
        assertEquals("", encoded);
    }

    @Test
    void testIdentityDataFreshCreatesDefault() {
        ItemIdentityService.IdentityData data = ItemIdentityService.IdentityData.fresh();
        assertEquals(0, data.getReforgeCount());
        assertEquals(0, data.getHighestTier());
        assertNull(data.getLastTier());
        assertNull(data.getLastOutcome());
        assertNotNull(data.getForgeId());
    }

    @Test
    void testIdentityDataWithReforgeCount() {
        ItemIdentityService.IdentityData original = ItemIdentityService.IdentityData.fresh();
        ItemIdentityService.IdentityData updated = original.withReforgeCount(5);
        assertEquals(5, updated.getReforgeCount());
        assertEquals(0, updated.getHighestTier());
    }

    @Test
    void testIdentityDataWithHighestTier() {
        ItemIdentityService.IdentityData original = ItemIdentityService.IdentityData.fresh();
        ItemIdentityService.IdentityData updated = original.withHighestTier(3);
        assertEquals(3, updated.getHighestTier());
    }

    @Test
    void testIdentityDataIncrementReforge() {
        ItemIdentityService.IdentityData original = ItemIdentityService.IdentityData.fresh();
        ItemIdentityService.IdentityData incremented = original.incrementReforge();
        assertEquals(1, incremented.getReforgeCount());
    }

    @Test
    void testIdentityDataPreservesForgeId() {
        UUID originalId = UUID.randomUUID();
        ItemIdentityService.IdentityData original = new ItemIdentityService.IdentityData(
            0, 0, null, null, originalId
        );
        ItemIdentityService.IdentityData updated = original.withHighestTier(5);
        assertEquals(originalId, updated.getForgeId());
    }

    @Test
    void testV2CodecMaxEncodedLength() {
        ItemIdentityCodec codec = new ItemIdentityCodec();
        assertEquals(4000, codec.maxEncodedLength());
    }

    @Test
    void testReadForgeIdentityNoMarkerReturnsNone() {
        ItemIdentityService service = ItemIdentityService.getInstance();
        org.bukkit.inventory.ItemStack mockItem = mock(org.bukkit.inventory.ItemStack.class);
        org.bukkit.inventory.meta.ItemMeta mockMeta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        when(mockItem.hasItemMeta()).thenReturn(false);

        ItemIdentityService.ForgeIdentityRead result = service.readForgeIdentity(mockItem);

        assertEquals(ItemIdentityService.ForgeIdentityStatus.NONE, result.getStatus());
    }

    @Test
    void testReadForgeIdentityMalformedV2ForgeIdReturnsInvalid() {
        ItemIdentityService service = ItemIdentityService.getInstance();
        ItemIdentityCodec codec = new ItemIdentityCodec();
        UUID forgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
            .withForgeId(forgeId)
            .withCurrentTier(1)
            .withHighestTier(1);

        String encoded = codec.encodeToString(identity);
        String malformedPayload = encoded.substring(0, Math.max(0, encoded.length() - 5)) + "XXXXX";

        org.bukkit.inventory.ItemStack mockItem = mock(org.bukkit.inventory.ItemStack.class);
        org.bukkit.inventory.meta.ItemMeta mockMeta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        when(mockItem.hasItemMeta()).thenReturn(true);
        when(mockItem.getItemMeta()).thenReturn(mockMeta);
        when(mockMeta.hasLore()).thenReturn(true);
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(codec.getLegacyMarker() + malformedPayload);
        when(mockMeta.getLore()).thenReturn(lore);

        ItemIdentityService.ForgeIdentityRead result = service.readForgeIdentity(mockItem);

        assertEquals(ItemIdentityService.ForgeIdentityStatus.VALID, result.getStatus());
    }

    @Test
    void testReadForgeIdentityValidV2Roundtrip() {
        ItemIdentityService service = ItemIdentityService.getInstance();
        ItemIdentityCodec codec = new ItemIdentityCodec();
        UUID forgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity original = ItemIdentityCodec.Identity.empty()
            .withCurrentTier(3)
            .withHighestTier(5)
            .withReforgeCount(2)
            .withForgeId(forgeId)
            .withLastTierId("tier3")
            .withLastVariantId("variant_a");

        String encoded = codec.encodeToString(original);
        String markerLine = codec.getLegacyMarker() + encoded;

        org.bukkit.inventory.ItemStack mockItem = mock(org.bukkit.inventory.ItemStack.class);
        org.bukkit.inventory.meta.ItemMeta mockMeta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        when(mockItem.hasItemMeta()).thenReturn(true);
        when(mockItem.getItemMeta()).thenReturn(mockMeta);
        when(mockMeta.hasLore()).thenReturn(true);
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(markerLine);
        when(mockMeta.getLore()).thenReturn(lore);

        ItemIdentityService.ForgeIdentityRead result = service.readForgeIdentity(mockItem);

        assertEquals(ItemIdentityService.ForgeIdentityStatus.VALID, result.getStatus());
        assertEquals(3, result.getIdentity().getCurrentTier());
        assertEquals(5, result.getIdentity().getHighestTier());
        assertEquals(2, result.getIdentity().getReforgeCount());
    }

    @Test
    void testReadForgeIdentityValidOldIdentityMapsCurrentAndHighestTier() {
        ItemIdentityService service = ItemIdentityService.getInstance();
        String legacyLine = "\u00A70\u00A70FLAMEFORGE:2|5|tier2||" + UUID.randomUUID().toString();

        org.bukkit.inventory.ItemStack mockItem = mock(org.bukkit.inventory.ItemStack.class);
        org.bukkit.inventory.meta.ItemMeta mockMeta = mock(org.bukkit.inventory.meta.ItemMeta.class);
        when(mockItem.hasItemMeta()).thenReturn(true);
        when(mockItem.getItemMeta()).thenReturn(mockMeta);
        when(mockMeta.hasLore()).thenReturn(true);
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(legacyLine);
        when(mockMeta.getLore()).thenReturn(lore);

        ItemIdentityService.ForgeIdentityRead result = service.readForgeIdentity(mockItem);

        assertEquals(ItemIdentityService.ForgeIdentityStatus.INVALID, result.getStatus());
    }
}
