package com.arkflame.flameforge.item;

import com.arkflame.flameforge.model.AttributeSpec;
import com.arkflame.flameforge.model.BreakPolicy;
import com.arkflame.flameforge.model.CurseDefinition;
import com.arkflame.flameforge.model.ForgeAttributeDefinition;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class ItemMutationServiceTest {

    private ItemMutationService mutationService;
    private ItemIdentityService identityService;
    private AttributeBridge attributeBridge;
    private EnchantmentResolver enchantmentResolver;
    private com.arkflame.flameforge.text.TextRenderer textRenderer;
    private ItemDisplayNameResolver displayNameResolver;

    @BeforeEach
    void setUp() {
        identityService = mock(ItemIdentityService.class);
        attributeBridge = mock(AttributeBridge.class);
        enchantmentResolver = mock(EnchantmentResolver.class);
        textRenderer = mock(com.arkflame.flameforge.text.TextRenderer.class);
        displayNameResolver = mock(ItemDisplayNameResolver.class);
        when(identityService.defaultBaseDisplayName(any(Material.class))).thenReturn("Diamond Sword");
        when(displayNameResolver.resolve(any(), any())).thenReturn("Diamond Sword");
        when(textRenderer.renderItemLegacyInheritedLiteral(anyString(), any(), anyString(), isNull()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(identityService.writeForgeIdentity(any(), any())).thenAnswer(invocation ->
                java.util.Optional.of((ItemStack) invocation.getArgument(0)));
        mutationService = new ItemMutationService(
                identityService, attributeBridge, enchantmentResolver, textRenderer, displayNameResolver);
    }

    @Test
    void successMutationPreservesMaterialAndPersistsPresentationAndIdentity() {
        ItemStack input = createMockedItemStack(Material.DIAMOND_SWORD);
        ItemStack cloned = input.clone();
        ItemMeta meta = createMockMeta(cloned, "Old name", Collections.singletonList("Old lore"));
        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getName()).thenReturn("Forged Sword");
        when(variant.getLore()).thenReturn(Collections.singletonList("Forged lore"));
        when(variant.getPowerIds()).thenReturn(Collections.singletonList("flame"));
        when(variant.getAttributes()).thenReturn(Collections.emptyList());
        when(variant.getEnchantments()).thenReturn(Collections.emptyList());
        when(variant.getId()).thenReturn("variant");
        TierDefinition tier = mock(TierDefinition.class);
        when(tier.getLevel()).thenReturn(3);
        when(tier.getId()).thenReturn("tier3");
        UUID forgeId = UUID.randomUUID();

        ItemMutationService.MutationResult result = mutationService.mutateSuccess(
                input, tier, variant, ItemIdentityCodec.Identity.empty(), forgeId);

        assertTrue(result.isSuccess());
        assertEquals(cloned, result.getResult());
        assertEquals(Material.DIAMOND_SWORD, result.getResult().getType());
        assertEquals("Forged Sword", meta.getDisplayName());
        assertEquals(Collections.singletonList("Forged lore"), meta.getLore());
        verify(identityService).writeForgeIdentity(any(), org.mockito.ArgumentMatchers.argThat(identity ->
                identity.getCurrentTier() == 3
                        && identity.getHighestTier() == 3
                        && identity.getReforgeCount() == 1
                        && forgeId.equals(identity.getForgeId())
                        && "DIAMOND_SWORD".equals(identity.getBaseMaterial())
                        && "Diamond Sword".equals(identity.getBaseDisplayName())
                        && identity.getActivePowerIds().equals(Collections.singletonList("flame"))));
    }

    @Test
    void successMutationPersistsAllAttributeIdsButAppliesOnlyNativeAttackDamageToBukkit() {
        ItemStack input = createMockedItemStack(Material.DIAMOND_SWORD);
        ItemStack cloned = input.clone();
        ItemMeta meta = createMockMeta(cloned, "Old name", Collections.singletonList("Old lore"));
        List<ForgeAttributeDefinition> attributes = Arrays.asList(
                new ForgeAttributeDefinition("attack_damage_flat",
                        ForgeAttributeDefinition.AttributeType.ATTACK_DAMAGE_FLAT, 2.0),
                new ForgeAttributeDefinition("damage_reduction_percent",
                        ForgeAttributeDefinition.AttributeType.DAMAGE_REDUCTION_PERCENT, 0.1),
                new ForgeAttributeDefinition("poison_damage_reduction_percent",
                        ForgeAttributeDefinition.AttributeType.POISON_DAMAGE_REDUCTION_PERCENT, 0.2),
                new ForgeAttributeDefinition("magic_damage_reduction_percent",
                        ForgeAttributeDefinition.AttributeType.MAGIC_DAMAGE_REDUCTION_PERCENT, 0.3),
                new ForgeAttributeDefinition("fall_damage_reduction_percent",
                        ForgeAttributeDefinition.AttributeType.FALL_DAMAGE_REDUCTION_PERCENT, 0.4));
        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getName()).thenReturn("Forged Sword");
        when(variant.getLore()).thenReturn(Collections.singletonList("Forged lore"));
        when(variant.getPowerIds()).thenReturn(Collections.singletonList("flame"));
        when(variant.getAttributes()).thenReturn(attributes);
        when(variant.getEnchantments()).thenReturn(Collections.emptyList());
        when(variant.getId()).thenReturn("variant");
        TierDefinition tier = mock(TierDefinition.class);
        when(tier.getLevel()).thenReturn(3);
        when(tier.getId()).thenReturn("tier3");
        UUID forgeId = UUID.randomUUID();

        ItemMutationService.MutationResult result = mutationService.mutateSuccess(
                input, tier, variant, ItemIdentityCodec.Identity.empty(), forgeId);

        assertTrue(result.isSuccess());
        verify(identityService).writeForgeIdentity(any(), org.mockito.ArgumentMatchers.argThat(identity ->
                identity.getActiveAttributeIds().containsAll(Arrays.asList(
                        "attack_damage_flat", "damage_reduction_percent",
                        "poison_damage_reduction_percent", "magic_damage_reduction_percent",
                        "fall_damage_reduction_percent"))
                        && identity.getActiveAttributeIds().size() == 5));
        org.mockito.ArgumentCaptor<List<AttributeSpec>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(attributeBridge).applyAttributes(eq(cloned), captor.capture());
        List<AttributeSpec> nativeSpecs = captor.getValue();
        assertEquals(1, nativeSpecs.size());
        assertEquals("attack_damage_flat", nativeSpecs.get(0).getAttribute());
    }

    @Test
    void curseMutationReplacesLoreUsesUnsafeVanishingAndStripsIdentityState() {
        ItemStack input = createMockedItemStack(Material.DIAMOND_SWORD);
        ItemStack cloned = input.clone();
        ItemMeta meta = createMockMeta(cloned, "Sword", Arrays.asList(
                "Old lore",
                "\u00A70\u00A70FLAMEFORGE:old",
                "\u00A76FLAMEFORGE_PWR:old",
                "\u00A75FLAMEFORGE_ATTR:old",
                "\u00A7cFLAMEFORGE_CURSED:true"));
        CurseDefinition curse = mock(CurseDefinition.class);
        when(curse.getName()).thenReturn("Cursed Blade");
        when(curse.getLore()).thenReturn(Collections.singletonList("Cursed lore"));
        when(curse.getEnchantments()).thenReturn(Arrays.asList("BINDING_CURSE", "VANISHING_CURSE"));
        Enchantment binding = mock(Enchantment.class);
        when(binding.getName()).thenReturn("BINDING_CURSE");
        Enchantment vanishing = mock(Enchantment.class);
        when(vanishing.getName()).thenReturn("VANISHING_CURSE");
        when(enchantmentResolver.resolve("BINDING_CURSE")).thenReturn(Optional.of(binding));
        when(enchantmentResolver.resolve("VANISHING_CURSE")).thenReturn(Optional.of(vanishing));
        when(enchantmentResolver.resolveLevel(1)).thenReturn(1);
        UUID forgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity currentIdentity = ItemIdentityCodec.Identity.empty()
                .withCurrentTier(2)
                .withHighestTier(4)
                .withReforgeCount(7)
                .withLastTierId("tier2")
                .withLastVariantId("variant")
                .withForgeId(forgeId)
                .withBaseMaterial("DIAMOND_SWORD")
                .withBaseDisplayName("Diamond Sword")
                .withOriginalEnchantments(Collections.singletonMap("sharpness", 3))
                .withForgeEnchantments(Collections.singletonMap("power", 2))
                .withActiveAttributeIds(Collections.singletonList("damage"))
                .withActivePowerIds(Collections.singletonList("flame"));

        ItemMutationService.MutationResult result = mutationService.mutateCurse(
                input, curse, false, currentIdentity, forgeId);

        assertTrue(result.isSuccess());
        assertNotNull(result.getResult());
        verify(attributeBridge).removeFlameForgeAttributes(cloned);
        assertEquals("Cursed Blade", meta.getDisplayName());
        assertEquals(Collections.singletonList("Cursed lore"), meta.getLore());
        verify(meta).addEnchant(vanishing, 1, true);
        verify(meta, never()).addEnchant(eq(binding), anyInt(), anyBoolean());
        verify(cloned).setItemMeta(meta);
        org.mockito.ArgumentCaptor<List<String>> loreCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(meta, org.mockito.Mockito.times(3)).setLore(loreCaptor.capture());
        assertEquals(Collections.singletonList("Old lore"), loreCaptor.getAllValues().get(0));
        assertTrue(loreCaptor.getAllValues().get(1).isEmpty());
        assertEquals(Collections.singletonList("Cursed lore"), loreCaptor.getAllValues().get(2));
        verify(identityService).writeForgeIdentity(any(), org.mockito.ArgumentMatchers.argThat(written ->
                written.isCursed()
                        && written.getCurrentTier() == 2
                        && written.getHighestTier() == 4
                        && written.getReforgeCount() == 7
                        && written.getLastTierId() == null
                        && written.getLastVariantId() == null
                        && written.getActiveAttributeIds().isEmpty()
                        && written.getActivePowerIds().isEmpty()
                        && written.getForgeEnchantments().isEmpty()
                        && written.getOriginalEnchantments().equals(Collections.singletonMap("sharpness", 3))
                        && written.getBaseMaterial().equals("DIAMOND_SWORD")
                        && written.getBaseDisplayName().equals("Diamond Sword")
                        && written.getForgeId().equals(forgeId)));
    }

    @Test
    void breakMutationAppliesResetPolicyAndReturnsCoherentResult() {
        ItemStack input = createMockedItemStack(Material.DIAMOND_SWORD);
        ItemStack cloned = input.clone();
        ItemMeta meta = createMockMeta(cloned, "Tier3 Sword", Collections.singletonList("Old lore"));
        UUID forgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
                .withCurrentTier(3)
                .withHighestTier(5)
                .withLastTierId("tier3")
                .withLastVariantId("variant")
                .withForgeId(forgeId)
                .withActiveAttributeIds(Collections.singletonList("damage"))
                .withActivePowerIds(Collections.singletonList("flame"))
                .withForgeEnchantments(Collections.singletonMap("power", 3));

        ItemMutationService.MutationResult result = mutationService.mutateBreak(
                input, BreakPolicy.defaultPolicy(), identity, forgeId);

        assertTrue(result.isSuccess());
        assertFalse(result.isDestroyed());
        assertEquals(cloned, result.getResult());
        assertEquals(Material.DIAMOND_SWORD, result.getResult().getType());
        assertEquals(BreakPolicy.defaultPolicy().getResultDisplayName(), meta.getDisplayName());
        assertEquals(BreakPolicy.defaultPolicy().getResultLore(), meta.getLore());
        verify(identityService).writeForgeIdentity(any(), org.mockito.ArgumentMatchers.argThat(written ->
                written.getCurrentTier() == 0
                        && written.getHighestTier() == 5
                        && written.getLastTierId() == null
                        && written.getLastVariantId() == null
                        && written.getActiveAttributeIds().isEmpty()
                        && written.getActivePowerIds().isEmpty()
                        && written.getForgeEnchantments().isEmpty()
                        && forgeId.equals(written.getForgeId())));
    }

    @Test
    void breakPolicyCanPreserveOrDestroyWithoutCorruptingIdentity() {
        UUID forgeId = UUID.randomUUID();
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
                .withCurrentTier(3)
                .withHighestTier(4)
                .withReforgeCount(6)
                .withLastTierId("tier3")
                .withLastVariantId("variant")
                .withForgeId(forgeId)
                .withActiveAttributeIds(Collections.singletonList("damage"))
                .withActivePowerIds(Collections.singletonList("flame"));
        BreakPolicy preserve = new BreakPolicy(false, 0, false, false, false,
                false, false, false, false, "Preserved", Collections.singletonList("Lore"));
        ItemStack preservedInput = createMockedItemStack(Material.DIAMOND_SWORD);
        ItemStack preservedClone = preservedInput.clone();
        ItemMeta preservedMeta = createMockMeta(preservedClone, "Sword", Collections.emptyList());

        ItemMutationService.MutationResult preserved = mutationService.mutateBreak(
                preservedInput, preserve, identity, forgeId);

        assertTrue(preserved.isSuccess());
        assertFalse(preserved.isDestroyed());
        assertEquals(preservedClone, preserved.getResult());
        assertEquals("Preserved", preservedMeta.getDisplayName());
        assertEquals(Collections.singletonList("Lore"), preservedMeta.getLore());

        BreakPolicy destroy = new BreakPolicy(false, 0, false, false, false,
                false, false, false, true);
        ItemStack destroyedInput = createMockedItemStack(Material.DIAMOND_SWORD);
        ItemStack destroyedClone = destroyedInput.clone();
        createMockMeta(destroyedClone, "Sword", Collections.emptyList());

        ItemMutationService.MutationResult destroyed = mutationService.mutateBreak(
                destroyedInput, destroy, identity, forgeId);

        assertTrue(destroyed.isSuccess());
        assertTrue(destroyed.isDestroyed());
        assertNull(destroyed.getResult());
        org.mockito.ArgumentCaptor<ItemIdentityCodec.Identity> captor =
                org.mockito.ArgumentCaptor.forClass(ItemIdentityCodec.Identity.class);
        verify(identityService, org.mockito.Mockito.times(2)).writeForgeIdentity(any(), captor.capture());
        for (ItemIdentityCodec.Identity written : captor.getAllValues()) {
            assertEquals(forgeId, written.getForgeId());
            assertEquals(3, written.getCurrentTier());
            assertEquals("tier3", written.getLastTierId());
            assertEquals("variant", written.getLastVariantId());
            assertEquals(Collections.singletonList("damage"), written.getActiveAttributeIds());
            assertEquals(Collections.singletonList("flame"), written.getActivePowerIds());
        }
    }

    private ItemStack createMockedItemStack(Material material) {
        ItemStack input = mock(ItemStack.class);
        ItemStack clone = mock(ItemStack.class);
        when(input.hasItemMeta()).thenReturn(true);
        when(input.getType()).thenReturn(material);
        when(input.clone()).thenReturn(clone);
        when(clone.getType()).thenReturn(material);
        return input;
    }

    private ItemMeta createMockMeta(ItemStack clone, String displayName, List<String> initialLore) {
        ItemMeta meta = mock(ItemMeta.class);
        final String[] name = {displayName};
        final List<String> lore = new ArrayList<>(initialLore);
        when(clone.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenAnswer(invocation -> name[0] != null);
        when(meta.getDisplayName()).thenAnswer(invocation -> name[0]);
        when(meta.hasLore()).thenAnswer(invocation -> !lore.isEmpty());
        when(meta.getLore()).thenAnswer(invocation -> lore);
        when(meta.hasEnchants()).thenReturn(false);
        doAnswer(invocation -> {
            name[0] = invocation.getArgument(0);
            return null;
        }).when(meta).setDisplayName(anyString());
        doAnswer(invocation -> {
            lore.clear();
            lore.addAll((List<String>) invocation.getArgument(0));
            return null;
        }).when(meta).setLore(anyList());
        return meta;
    }
}
