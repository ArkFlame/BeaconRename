package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.model.PlayerForgeState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeItemPolicyTest {
    @Test
    void eligibleVanillaItemPassesAndDisallowedCustomizationFails() {
        ForgeItemInspection inspection = mock(ForgeItemInspection.class);
        ForgeItemPolicy policy = new ForgeItemPolicy(inspection);
        Player player = mock(Player.class);
        PlayerForgeState state = PlayerForgeState.of("player");
        ItemStack item = mock(ItemStack.class);
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty();

        when(inspection.inspect(player, state, item)).thenReturn(
            new ForgeItemInspection.InspectionResult(ForgeItemInspection.Status.READY, identity));
        assertTrue(policy.checkItem(player, state, item).isAllowed());

        when(inspection.inspect(player, state, item)).thenReturn(
            new ForgeItemInspection.InspectionResult(ForgeItemInspection.Status.CUSTOM_NAME, identity));
        ForgeItemPolicy.PolicyResult denied = policy.checkItem(player, state, item);
        assertFalse(denied.isAllowed());
        assertEquals("menu.item-denied.customized", denied.getMessageKey());
    }

    @Test
    void tierMaterialAndPlayerRequirementsGateReadiness() {
        ForgeItemInspection inspection = mock(ForgeItemInspection.class);
        ForgeItemPolicy policy = new ForgeItemPolicy(inspection);
        Player player = mock(Player.class);
        PlayerForgeState state = PlayerForgeState.of("player");
        ItemStack item = mock(ItemStack.class);
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty();

        when(inspection.inspect(player, state, item)).thenReturn(
            new ForgeItemInspection.InspectionResult(ForgeItemInspection.Status.DENIED_MATERIAL, identity));
        assertFalse(policy.isReady(player, state, item));

        when(inspection.inspect(player, state, item)).thenReturn(
            new ForgeItemInspection.InspectionResult(ForgeItemInspection.Status.TIER_PERMISSION_REQUIRED, identity));
        ForgeItemPolicy.PolicyResult denied = policy.checkItem(player, state, item);
        assertFalse(denied.isAllowed());
        assertEquals("menu.item-denied.permission", denied.getMessageKey());
    }
}
