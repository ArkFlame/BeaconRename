package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.item.AttributeBridge;
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
