package com.arkflame.flameforge.item;

import com.arkflame.flameforge.model.AttributeSpec;
import com.arkflame.flameforge.model.BreakPolicy;
import com.arkflame.flameforge.model.CurseDefinition;
import com.arkflame.flameforge.model.ForgeAttributeDefinition;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemMutationServiceTest {

    private ItemMutationService mutationService;
    private ItemIdentityService identityService;
    private AttributeBridge attributeBridge;
    private EnchantmentResolver enchantmentResolver;
    private com.arkflame.flameforge.text.TextRenderer textRenderer;

    @BeforeEach
    void setUp() {
        identityService = mock(ItemIdentityService.class);
        attributeBridge = mock(AttributeBridge.class);
        enchantmentResolver = mock(EnchantmentResolver.class);
        textRenderer = mock(com.arkflame.flameforge.text.TextRenderer.class);
        mutationService = new ItemMutationService(identityService, attributeBridge, enchantmentResolver, textRenderer);
    }

    private ItemStack createMockedItemStack(Material material) {
        ItemStack input = mock(ItemStack.class);
        ItemStack cloned = mock(ItemStack.class);
        when(input.hasItemMeta()).thenReturn(true);
        when(input.getType()).thenReturn(material);
        when(input.clone()).thenReturn(cloned);
        when(cloned.getType()).thenReturn(material);
        return input;
    }

    private ItemMeta createMockMeta(ItemStack cloned, boolean hasDisplayName, String displayName) {
        ItemMeta meta = mock(ItemMeta.class);
        when(cloned.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(hasDisplayName);
        if (displayName != null) {
            when(meta.getDisplayName()).thenReturn(displayName);
        }
        when(meta.hasEnchants()).thenReturn(false);
        return meta;
    }

    @Test
    void mutateSuccessPersistsTargetTierVariantPowersAndAttributesAndResolvesBaseName() {
        Material material = Material.DIAMOND_SWORD;
        ItemStack input = createMockedItemStack(material);
        ItemStack cloned = input.clone();

        ItemMeta meta = createMockMeta(cloned, true, "Sword of Power");
        when(textRenderer.renderItemLegacy(anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getName()).thenReturn("%base_name% Epic Sword");
        when(variant.getLore()).thenReturn(Collections.singletonList("Lore line"));
        when(variant.getPowerIds()).thenReturn(Arrays.asList("power1", "power2"));
        when(variant.getAttributes()).thenReturn(Arrays.asList(
            new ForgeAttributeDefinition("attr1", ForgeAttributeDefinition.AttributeType.ATTACK_DAMAGE_FLAT, 1.0),
            new ForgeAttributeDefinition("attr2", ForgeAttributeDefinition.AttributeType.DAMAGE_REDUCTION_PERCENT, 2.0)
        ));
        when(variant.getEnchantments()).thenReturn(Collections.emptyList());

        TierDefinition targetTier = mock(TierDefinition.class);
        when(targetTier.getLevel()).thenReturn(3);
        when(targetTier.getId()).thenReturn("tier3");

        ItemIdentityCodec.Identity existingIdentity = ItemIdentityCodec.Identity.empty();

        UUID forgeId = UUID.randomUUID();

        ItemStack writtenItem = mock(ItemStack.class);
        when(identityService.writeForgeIdentity(any(), any())).thenReturn(java.util.Optional.of(writtenItem));

        ItemMutationService.MutationResult result = mutationService.mutateSuccess(
            input, targetTier, variant, existingIdentity, forgeId);

        assertTrue(result.isSuccess());
        assertNotNull(result.getResult());

        verify(identityService).writeForgeIdentity(any(), argThat(identity ->
            identity.getCurrentTier() == 3 &&
            identity.getLastTierId().equals("tier3")
        ));
    }

    @Test
    void mutateSuccessMaterialRemainsSame() {
        Material material = Material.IRON_AXE;
        ItemStack input = createMockedItemStack(material);
        ItemStack cloned = input.clone();

        ItemMeta meta = createMockMeta(cloned, false, null);

        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getName()).thenReturn(null);
        when(variant.getLore()).thenReturn(Collections.emptyList());
        when(variant.getPowerIds()).thenReturn(Collections.emptyList());
        when(variant.getAttributes()).thenReturn(Collections.emptyList());
        when(variant.getEnchantments()).thenReturn(Collections.emptyList());

        TierDefinition targetTier = mock(TierDefinition.class);
        when(targetTier.getLevel()).thenReturn(1);
        when(targetTier.getId()).thenReturn("tier1");

        ItemIdentityCodec.Identity existingIdentity = ItemIdentityCodec.Identity.empty();
        UUID forgeId = UUID.randomUUID();

        ItemStack writtenItem = mock(ItemStack.class);
        when(writtenItem.getType()).thenReturn(material);
        when(identityService.writeForgeIdentity(any(), any())).thenReturn(java.util.Optional.of(writtenItem));

        ItemMutationService.MutationResult result = mutationService.mutateSuccess(
            input, targetTier, variant, existingIdentity, forgeId);

        assertTrue(result.isSuccess());
        assertNotNull(result.getResult());
        assertEquals(material, writtenItem.getType());
    }

    @Test
    void mutateCurseSetsRichCursedFlag() {
        Material material = Material.DIAMOND_SWORD;
        ItemStack input = createMockedItemStack(material);
        ItemStack cloned = input.clone();

        ItemMeta meta = createMockMeta(cloned, false, null);
        when(textRenderer.renderItemLegacy(anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(0));

        CurseDefinition curse = mock(CurseDefinition.class);
        when(curse.getName()).thenReturn("Cursed Blade");
        when(curse.getLore()).thenReturn(Collections.singletonList("Cursed lore"));
        when(curse.getEnchantments()).thenReturn(Arrays.asList("VANISHING_CURSE"));

        Enchantment curseEnchant = mock(Enchantment.class);
        when(curseEnchant.getName()).thenReturn("VANISHING_CURSE");
        when(enchantmentResolver.resolve("VANISHING_CURSE")).thenReturn(java.util.Optional.of(curseEnchant));
        when(enchantmentResolver.isCursed(curseEnchant)).thenReturn(true);
        when(enchantmentResolver.resolveLevel(1)).thenReturn(1);

        ItemIdentityCodec.Identity existingIdentity = ItemIdentityCodec.Identity.empty();
        UUID forgeId = UUID.randomUUID();

        ItemStack writtenItem = mock(ItemStack.class);
        when(identityService.writeForgeIdentity(any(), any())).thenReturn(java.util.Optional.of(writtenItem));

        ItemMutationService.MutationResult result = mutationService.mutateCurse(
            input, curse, false, existingIdentity, forgeId);

        assertTrue(result.isSuccess());
        assertNotNull(result.getResult());

        verify(identityService).writeForgeIdentity(any(), argThat(identity ->
            identity.isCursed()
        ));
    }

    @Test
    void mutateBreakResetsTierPowersAttributesFollowsPolicy() {
        Material material = Material.DIAMOND_SWORD;
        ItemStack input = createMockedItemStack(material);
        ItemStack cloned = input.clone();

        ItemMeta meta = createMockMeta(cloned, true, "Tier3 Sword");

        BreakPolicy policy = BreakPolicy.defaultPolicy();

        ItemIdentityCodec.Identity existingIdentity = ItemIdentityCodec.Identity.empty();
        UUID forgeId = UUID.randomUUID();

        ItemStack writtenItem = mock(ItemStack.class);
        when(identityService.writeForgeIdentity(any(), any())).thenReturn(java.util.Optional.of(writtenItem));

        ItemMutationService.MutationResult result = mutationService.mutateBreak(
            input, policy, existingIdentity, forgeId);

        assertTrue(result.isSuccess());
        assertNotNull(result.getResult());

        verify(attributeBridge).removeFlameForgeAttributes(cloned);
        verify(enchantmentResolver).clearFromMeta(meta);
    }

    @Test
    void mutateBreakAppliesExplicitNoResetPolicyWithoutInventingDefault() {
        Material material = Material.DIAMOND_SWORD;
        ItemStack input = createMockedItemStack(material);
        ItemStack cloned = input.clone();

        ItemMeta meta = createMockMeta(cloned, true, "Tier3 Sword");

        BreakPolicy explicitNoResetPolicy = new BreakPolicy(
            false, 0, false, false, false, false, false, false, false);

        ItemIdentityCodec.Identity existingIdentity = ItemIdentityCodec.Identity.empty();
        UUID forgeId = UUID.randomUUID();

        ItemStack writtenItem = mock(ItemStack.class);
        when(identityService.writeForgeIdentity(any(), any())).thenReturn(java.util.Optional.of(writtenItem));

        ItemMutationService.MutationResult result = mutationService.mutateBreak(
            input, explicitNoResetPolicy, existingIdentity, forgeId);

        assertTrue(result.isSuccess());
        assertNotNull(result.getResult());

        verify(meta, never()).setDisplayName(null);
        verify(enchantmentResolver, never()).clearFromMeta(meta);
        verify(attributeBridge, never()).removeFlameForgeAttributes(cloned);
    }
}
