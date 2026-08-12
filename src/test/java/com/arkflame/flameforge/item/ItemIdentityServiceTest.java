package com.arkflame.flameforge.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemIdentityServiceTest {

    @Test
    void richIdentityRoundTripsAndMalformedIdentityIsRejected() {
        ItemIdentityCodec codec = new ItemIdentityCodec();
        UUID forgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
                .withCurrentTier(3)
                .withHighestTier(5)
                .withReforgeCount(7)
                .withCursed(true)
                .withLastTierId("tier3")
                .withLastVariantId("variant_a")
                .withForgeId(forgeId)
                .withBaseMaterial("DIAMOND_SWORD")
                .withBaseDisplayName("Sword")
                .withOriginalEnchantments(Collections.singletonMap("sharpness", 2))
                .withForgeEnchantments(Collections.singletonMap("power", 3))
                .withActiveAttributeIds(Collections.singletonList("damage"))
                .withActivePowerIds(Collections.singletonList("flame"));

        ItemIdentityCodec.Decoded decoded = codec.decodeFromString(codec.encodeToString(identity));

        assertTrue(decoded.isValid());
        assertEquals(identity, decoded.getIdentity());
        assertFalse(codec.decodeFromString("malformed-identity").isValid());

        ItemIdentityService service = serviceWithoutPdc();
        ItemStack item = itemWithLore(service.encodeHiddenLegacyPayload("malformed-identity"),
                "\u00A70\u00A70FLAMEFORGE:1|2|tier1||" + forgeId);
        ItemIdentityService.ForgeIdentityRead read = service.readForgeIdentity(item);

        assertEquals(ItemIdentityService.ForgeIdentityStatus.INVALID, read.getStatus());
    }

    @Test
    void newIdentityWriteReadPreservesStateAndVisibleShortId() {
        ItemIdentityService service = serviceWithoutPdc();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
                .withCurrentTier(4)
                .withHighestTier(6)
                .withReforgeCount(8)
                .withLastTierId("tier4")
                .withLastVariantId("variant_b")
                .withForgeId(UUID.randomUUID())
                .withBaseMaterial("DIAMOND_SWORD")
                .withBaseDisplayName("Diamond Sword")
                .withActivePowerIds(Collections.singletonList("power"));
        ItemStack item = mutableItemWithLore("user lore");

        ItemStack written = service.writeForgeIdentity(item, identity).orElseThrow(AssertionError::new);
        ItemIdentityService.ForgeIdentityRead read = service.readForgeIdentity(written);

        assertEquals(ItemIdentityService.ForgeIdentityStatus.VALID, read.getStatus());
        assertEquals(identity, read.getIdentity());
        assertTrue(service.readVisibleShortId(written).isPresent());
        assertTrue(service.readVisibleShortId(written).get().matches("[A-HJ-NP-Z2-9]{8}"));
    }

    @Test
    void legacyLoreFallsBackWhenPdcUnavailableAndMigrates() {
        ItemIdentityService service = serviceWithoutPdc();
        UUID forgeId = UUID.randomUUID();
        ItemStack item = mutableItemWithLore("user lore",
                "\u00A70\u00A70FLAMEFORGE:2|5|tier2|success|" + forgeId);
        when(item.getType()).thenReturn(Material.DIAMOND_SWORD);

        ItemIdentityService.ForgeIdentityRead legacyRead = service.readForgeIdentity(item);
        ItemIdentityCodec.Identity canonical = legacyRead.getIdentity();

        assertEquals(ItemIdentityService.ForgeIdentityStatus.VALID, legacyRead.getStatus());
        assertEquals(forgeId, canonical.getForgeId());
        assertEquals(5, canonical.getHighestTier());
        assertEquals(5, canonical.getCurrentTier());

        ItemStack migrated = service.writeForgeIdentity(item, canonical).orElseThrow(AssertionError::new);
        ItemIdentityService.ForgeIdentityRead migratedRead = service.readForgeIdentity(migrated);

        assertEquals(ItemIdentityService.ForgeIdentityStatus.VALID, migratedRead.getStatus());
        assertEquals(canonical, migratedRead.getIdentity());
    }

    @Test
    void rewriteRemovesOldMarkersAndPreservesUserLore() {
        ItemIdentityService service = serviceWithoutPdc();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty().withForgeId(UUID.randomUUID());
        String oldPayload = service.getCodec().encodeToString(ItemIdentityCodec.Identity.empty());
        ItemStack item = mutableItemWithLore(
                "Keep this line",
                "\u00A70\u00A70FLAMEFORGE:1|2|tier1||" + UUID.randomUUID(),
                service.getCodec().getLegacyMarker() + oldPayload,
                service.encodeHiddenLegacyPayload(oldPayload),
                "Forge ID: #ABCDEFG2");

        ItemStack rewritten = service.writeForgeIdentity(item, identity).orElseThrow(AssertionError::new);
        List<String> lore = rewritten.getItemMeta().getLore();

        assertTrue(lore.contains("Keep this line"));
        assertEquals(1, lore.stream().filter(line -> line.startsWith("Forge ID: #")).count());
        assertEquals(1, lore.stream().filter(line -> service.decodeHiddenLegacyPayload(line).isPresent()).count());
        assertFalse(lore.stream().anyMatch(line -> line.startsWith("\u00A70\u00A70FLAMEFORGE:")));
    }

    @Test
    void missingIdentityReturnsNone() {
        ItemIdentityService service = serviceWithoutPdc();
        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(false);

        ItemIdentityService.ForgeIdentityRead read = service.readForgeIdentity(item);

        assertEquals(ItemIdentityService.ForgeIdentityStatus.NONE, read.getStatus());
        assertNotNull(read.getIdentity());
    }

    private ItemIdentityService serviceWithoutPdc() {
        ItemIdentityService service = new ItemIdentityService(new ShortForgeIdRegistry());
        try {
            Field field = ItemIdentityService.class.getDeclaredField("modernPdcAvailable");
            field.setAccessible(true);
            field.set(service, false);
            return service;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private ItemStack itemWithLore(String... lines) {
        return mutableItemWithLore(lines);
    }

    private ItemStack mutableItemWithLore(String... lines) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        List<String> lore = new ArrayList<>(Arrays.asList(lines));
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(item.clone()).thenReturn(item);
        when(meta.hasLore()).thenAnswer(invocation -> !lore.isEmpty());
        when(meta.getLore()).thenAnswer(invocation -> lore);
        doAnswer(invocation -> {
            lore.clear();
            lore.addAll((List<String>) invocation.getArgument(0));
            return null;
        }).when(meta).setLore(anyList());
        return item;
    }
}
