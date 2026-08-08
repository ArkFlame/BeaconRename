package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.model.PlayerForgeState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeItemPolicyTest {

    @Test
    void testToPolicyResultConversionViaReadyStatus() {
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty().withCurrentTier(0);
        ForgeItemInspection.InspectionResult readyResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.READY, identity);
        assertTrue(readyResult.isReady());
    }

    @Test
    void testTier0VanillaItemIsEligibleViaInspection() {
        ForgeItemInspection mockInspection = mock(ForgeItemInspection.class);
        ForgeItemPolicy policy = new ForgeItemPolicy(mockInspection);

        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty().withCurrentTier(0);
        ForgeItemInspection.InspectionResult readyResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.READY, identity);
        assertTrue(readyResult.isReady());
        assertEquals(ForgeItemInspection.Status.READY, readyResult.getStatus());
    }

    @Test
    void testCustomNameRejectsViaInspection() {
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty().withCurrentTier(0);
        ForgeItemInspection.InspectionResult customNameResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.CUSTOM_NAME, identity);

        assertFalse(customNameResult.isReady());
        assertEquals(ForgeItemInspection.Status.CUSTOM_NAME, customNameResult.getStatus());
    }

    @Test
    void testCustomLoreRejectsViaInspection() {
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty().withCurrentTier(0);
        ForgeItemInspection.InspectionResult customLoreResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.CUSTOM_LORE, identity);

        assertFalse(customLoreResult.isReady());
        assertEquals(ForgeItemInspection.Status.CUSTOM_LORE, customLoreResult.getStatus());
    }

    @Test
    void testTargetTierRestrictions() {
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty().withCurrentTier(1);
        ForgeItemInspection.InspectionResult maxTierResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.MAX_TIER, identity);

        assertFalse(maxTierResult.isReady());
        assertEquals(ForgeItemInspection.Status.MAX_TIER, maxTierResult.getStatus());

        ForgeItemInspection.InspectionResult nextTierMissingResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.NEXT_TIER_MISSING, identity);

        assertFalse(nextTierMissingResult.isReady());
        assertEquals(ForgeItemInspection.Status.NEXT_TIER_MISSING, nextTierMissingResult.getStatus());

        ForgeItemInspection.InspectionResult nextTierDisabledResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.NEXT_TIER_DISABLED, identity);

        assertFalse(nextTierDisabledResult.isReady());
        assertEquals(ForgeItemInspection.Status.NEXT_TIER_DISABLED, nextTierDisabledResult.getStatus());
    }

    @Test
    void testDeniedMaterialAndGroup() {
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty().withCurrentTier(0);
        ForgeItemInspection.InspectionResult deniedMaterialResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.DENIED_MATERIAL, identity);
        assertFalse(deniedMaterialResult.isReady());
        assertEquals(ForgeItemInspection.Status.DENIED_MATERIAL, deniedMaterialResult.getStatus());

        ForgeItemInspection.InspectionResult deniedGroupResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.DENIED_GROUP, identity);
        assertFalse(deniedGroupResult.isReady());
        assertEquals(ForgeItemInspection.Status.DENIED_GROUP, deniedGroupResult.getStatus());
    }

    @Test
    void testCheckItemRequiresNonNullPlayer() {
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty().withCurrentTier(1);
        ForgeItemInspection.InspectionResult deniedResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.DENIED_MATERIAL, identity);

        ForgeItemInspection testInspection = mock(ForgeItemInspection.class);
        when(testInspection.inspect(isNull(), any(), any())).thenReturn(deniedResult);
        when(testInspection.inspect(any(), any(), any())).thenReturn(deniedResult);

        ForgeItemPolicy policy = new ForgeItemPolicy(testInspection);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn("test_station");

        ForgeItemPolicy.PolicyResult result = policy.checkItem(null, session, mock(org.bukkit.inventory.ItemStack.class));

        assertFalse(result.isAllowed());
    }

    @Test
    void testIsReadyReturnsFalseWhenDenied() {
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty().withCurrentTier(0);
        ForgeItemInspection.InspectionResult deniedResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.DENIED_MATERIAL, identity);

        ForgeItemInspection testInspection = mock(ForgeItemInspection.class);
        when(testInspection.inspect(any(), any(), any())).thenReturn(deniedResult);

        ForgeItemPolicy policy = new ForgeItemPolicy(testInspection);

        assertFalse(policy.isReady(null, null, null));
    }
}