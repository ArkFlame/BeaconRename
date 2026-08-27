package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.item.AttributeBridge;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForgeItemInspectionTest {
    private ItemIdentityService identityService;
    private TierRepository tierRepository;
    private ForgeVariantEligibility variantEligibility;
    private ForgeItemInspection inspection;
    private ItemStack item;
    private TierDefinition targetTier;

    @BeforeEach
    void setUp() {
        identityService = mock(ItemIdentityService.class);
        tierRepository = mock(TierRepository.class);
        variantEligibility = mock(ForgeVariantEligibility.class);
        inspection = new ForgeItemInspection(new ItemIdentityCodec(), identityService,
                mock(AttributeBridge.class), tierRepository, variantEligibility);
        item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(item.getAmount()).thenReturn(1);

        ForgeVariant variant = new ForgeVariant("variant", "Variant", Collections.emptyList(), 1.0,
                "DIAMOND_SWORD", Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        targetTier = mock(TierDefinition.class);
        when(targetTier.isEnabled()).thenReturn(true);
        when(targetTier.getVariants()).thenReturn(Collections.singletonList(variant));
        when(targetTier.getDeniedMaterials()).thenReturn(Collections.emptyList());
        when(targetTier.getAllowedGroups()).thenReturn(Collections.emptyList());
        when(variantEligibility.eligibleVariants(any(), any())).thenReturn(Collections.singletonList(variant));
        when(tierRepository.findForMaterialAndLevel(any(Material.class), anyInt())).thenReturn(Optional.empty());
        when(tierRepository.findExactNext(any(Material.class), anyInt())).thenReturn(Optional.of(targetTier));
        when(tierRepository.maxLevelFor(any(Material.class))).thenReturn(7);
        when(identityService.defaultBaseDisplayName(any(Material.class))).thenReturn("Diamond Sword");
    }

    @Test
    void ownedFracturedTierZeroIsForgeable() {
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
                .withCurrentTier(0)
                .withHighestTier(1)
                .withLastTierId("tier1")
                .withBaseMaterial("DIAMOND_SWORD");
        when(identityService.readForgeIdentity(item)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID, identity));

        ForgeItemInspection.InspectionResult result = inspection.inspect(null, null, item);

        assertEquals(ForgeItemInspection.Status.READY, result.getStatus());
    }

    @Test
    void freshCustomTierZeroIsDenied() {
        ItemMeta meta = mock(ItemMeta.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(identityService.readForgeIdentity(item)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.NONE, ItemIdentityCodec.Identity.empty()));

        ForgeItemInspection.InspectionResult result = inspection.inspect(null, null, item);

        assertEquals(ForgeItemInspection.Status.CUSTOM_NAME, result.getStatus());
    }

    @Test
    void cursedOwnedItemIsDeniedBeforeProgressionLookup() {
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty().withCursed(true);
        when(identityService.readForgeIdentity(item)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID, identity));

        ForgeItemInspection.InspectionResult result = inspection.inspect(null, null, item);

        assertEquals(ForgeItemInspection.Status.CURSED, result.getStatus());
    }

    @Test
    void invalidIdentityIsDenied() {
        when(identityService.readForgeIdentity(item)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.INVALID, ItemIdentityCodec.Identity.empty()));

        ForgeItemInspection.InspectionResult result = inspection.inspect(null, null, item);

        assertEquals(ForgeItemInspection.Status.INVALID_IDENTITY, result.getStatus());
    }
}
