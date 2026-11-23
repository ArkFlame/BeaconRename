package com.arkflame.flameforge.item;

import com.arkflame.flameforge.model.AttributeSpec;
import com.arkflame.flameforge.model.FailureVariant;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FailureMutationTest {
    private ItemIdentityService identityService;
    private AttributeBridge attributeBridge;
    private ItemMutationService service;

    @BeforeEach
    void setUp() {
        identityService = mock(ItemIdentityService.class);
        attributeBridge = mock(AttributeBridge.class);
        EnchantmentResolver enchantments = mock(EnchantmentResolver.class);
        com.arkflame.flameforge.text.TextRenderer renderer =
            mock(com.arkflame.flameforge.text.TextRenderer.class);
        ItemDisplayNameResolver names = mock(ItemDisplayNameResolver.class);
        when(names.resolve(any(), any())).thenReturn("Diamond Sword");
        when(renderer.renderItemLegacyInheritedLiteral(anyString(), any(), anyString(), isNull()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(identityService.writeForgeIdentity(any(), any())).thenAnswer(invocation ->
            java.util.Optional.of((ItemStack) invocation.getArgument(0)));
        service = new ItemMutationService(identityService, attributeBridge, enchantments,
            renderer, names);
    }

    private FailureVariant variant(String id, double penalty) {
        return new FailureVariant(id, true, false, "<red>Broken",
            Collections.singletonList("<gray>Lost its power."), true, penalty,
            Collections.emptyList(), true, 0, true, true, true, true, true, true,
            true, "", Collections.emptyList(), Collections.emptyList(), "", Collections.emptyList());
    }

    @Test
    void halvesDurabilityAppliesPenaltyAndMarksItem() {
        ItemStack input = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(input.clone()).thenReturn(clone);
        when(input.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(input.getItemMeta()).thenReturn(meta);
        when(clone.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(clone.getDurability()).thenReturn((short) 100);
        when(clone.getItemMeta()).thenReturn(meta);
        when(meta.getLore()).thenReturn(new ArrayList<String>());

        ItemMutationService.MutationResult result = service.mutateFailure(input,
            variant("shatter", 4), ItemIdentityCodec.Identity.empty(), UUID.randomUUID());

        assertTrue(result.isSuccess());
        short expected = (short) (Material.DIAMOND_SWORD.getMaxDurability()
            - Math.max(1, (Material.DIAMOND_SWORD.getMaxDurability() - 100) / 2));
        verify(clone).setDurability(expected);

        ArgumentCaptor<List<AttributeSpec>> attributeCaptor = ArgumentCaptor.forClass(List.class);
        verify(attributeBridge).applyAttributes(any(), attributeCaptor.capture());
        AttributeSpec spec = attributeCaptor.getValue().get(0);
        assertEquals("generic.attackDamage", spec.getAttribute());
        assertEquals(-4.0, spec.getMinValue());

        ArgumentCaptor<ItemIdentityCodec.Identity> identityCaptor =
            ArgumentCaptor.forClass(ItemIdentityCodec.Identity.class);
        verify(identityService).writeForgeIdentity(any(), identityCaptor.capture());
        assertEquals("shatter", identityCaptor.getValue().getFailureVariantId());
        assertTrue(identityCaptor.getValue().isFailed());
        assertFalse(identityCaptor.getValue().isCursed());
    }

    @Test
    void destroyItemFlagSkipsMetaAndReturnsDestroyed() {
        ItemStack input = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(input.clone()).thenReturn(clone);
        when(input.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(input.getItemMeta()).thenReturn(meta);
        when(clone.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(clone.getItemMeta()).thenReturn(meta);
        when(meta.getLore()).thenReturn(new ArrayList<String>());

        FailureVariant destroying = new FailureVariant("shatter", true, true, "",
            Collections.emptyList(), false, 0, Collections.emptyList(), true, 0,
            true, true, true, true, true, true, true, "", Collections.emptyList(),
            Collections.emptyList(), "", Collections.emptyList());
        ItemMutationService.MutationResult result = service.mutateFailure(input,
            destroying, ItemIdentityCodec.Identity.empty(), UUID.randomUUID());

        assertTrue(result.isDestroyed());
        verify(clone, org.mockito.Mockito.never()).setDurability(org.mockito.ArgumentMatchers.anyShort());
    }
}
