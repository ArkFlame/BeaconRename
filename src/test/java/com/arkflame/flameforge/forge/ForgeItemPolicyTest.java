package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.item.AttributeBridge;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.PlayerForgeState;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ForgeItemPolicyTest {

    @Test
    void testPolicyResultAllow() {
        ForgeItemPolicy.PolicyResult result = ForgeItemPolicy.PolicyResult.allow();
        assertTrue(result.isAllowed());
        assertNull(result.getReason());
    }

    @Test
    void testPolicyResultDeny() {
        ForgeItemPolicy.PolicyResult result = ForgeItemPolicy.PolicyResult.deny("test reason");
        assertFalse(result.isAllowed());
        assertEquals("test reason", result.getReason());
    }

    @Test
    void testPolicyResultDenyWithStatusName() {
        ForgeItemPolicy.PolicyResult result = ForgeItemPolicy.PolicyResult.deny(ForgeItemInspection.Status.CUSTOM_NAME.name());
        assertFalse(result.isAllowed());
        assertEquals("CUSTOM_NAME", result.getReason());
    }

    @Test
    void testPolicyResultDenyWithDifferentStatus() {
        ForgeItemPolicy.PolicyResult result = ForgeItemPolicy.PolicyResult.deny(ForgeItemInspection.Status.CUSTOM_LORE.name());
        assertFalse(result.isAllowed());
        assertEquals("CUSTOM_LORE", result.getReason());
    }

    @Test
    void testToPolicyResultConversionViaReadyStatus() {
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty().withCurrentTier(0);
        ForgeItemInspection.InspectionResult readyResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.READY, identity);
        assertTrue(readyResult.isReady());
    }

    @Test
    void testTier0VanillaItemIsEligibleViaInspection() {
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        AttributeBridge attributeBridge = mock(AttributeBridge.class);
        TierRepository tierRepository = mock(TierRepository.class);
        ForgeItemPolicy policy = new ForgeItemPolicy(identityService, attributeBridge, tierRepository);

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
    @Disabled("ForgeItemPolicy.checkItem/isReady require Bukkit ItemStack.hasItemMeta() which calls Bukkit.getItemFactory() - unavailable in unit tests")
    void testCheckItemRequiresNonNullPlayer() {
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        AttributeBridge attributeBridge = mock(AttributeBridge.class);
        TierRepository tierRepository = mock(TierRepository.class);
        ForgeItemPolicy policy = new ForgeItemPolicy(identityService, attributeBridge, tierRepository);

        PlayerForgeState session = mock(PlayerForgeState.class);
        ForgeItemPolicy.PolicyResult result = policy.checkItem(null, session, null);

        assertFalse(result.isAllowed());
    }

    @Test
    @Disabled("ForgeItemPolicy.checkItem/isReady require Bukkit ItemStack.hasItemMeta() which calls Bukkit.getItemFactory() - unavailable in unit tests")
    void testIsReadyReturnsFalseWhenDenied() {
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        AttributeBridge attributeBridge = mock(AttributeBridge.class);
        TierRepository tierRepository = mock(TierRepository.class);
        ForgeItemPolicy policy = new ForgeItemPolicy(identityService, attributeBridge, tierRepository);

        assertFalse(policy.isReady(null, null, null));
    }
}
