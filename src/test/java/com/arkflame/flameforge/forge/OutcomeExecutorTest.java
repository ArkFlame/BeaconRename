package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.item.ItemMutationService;
import com.arkflame.flameforge.model.BreakPolicy;
import com.arkflame.flameforge.model.CurseDefinition;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.persistence.AuditLogService;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutcomeExecutorTest {
    private ItemMutationService mutation;
    private ItemIdentityService identity;
    private OutcomeExecutor executor;
    private ForgePlan plan;
    private TierDefinition tier;
    private ItemStack input;
    private Player player;
    private UUID forgeId;

    @BeforeEach
    void setUp() {
        mutation = mock(ItemMutationService.class);
        identity = mock(ItemIdentityService.class);
        executor = new OutcomeExecutor(mutation, identity, mock(AuditLogService.class), new HashMap<>());
        tier = mock(TierDefinition.class);
        input = mock(ItemStack.class);
        when(input.hasItemMeta()).thenReturn(false);
        player = mock(Player.class);
        forgeId = UUID.randomUUID();
        when(identity.readForgeIdentity(input)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
            ItemIdentityService.ForgeIdentityStatus.VALID, ItemIdentityCodec.Identity.empty()));
        plan = mock(ForgePlan.class);
        when(plan.getTargetTier()).thenReturn(tier);
    }

    @Test
    void explicitOutcomeCategoryDispatchesToMatchingMutation() {
        ForgeVariant variant = new ForgeVariant("variant", "Variant", Collections.emptyList(), 1.0,
            "DIAMOND_SWORD", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        ItemStack resultItem = mock(ItemStack.class);
        when(mutation.mutateSuccess(any(), eq(tier), eq(variant), any(), eq(forgeId)))
            .thenReturn(ItemMutationService.MutationResult.success(resultItem));
        BreakPolicy breakPolicy = BreakPolicy.defaultPolicy();
        when(tier.getBreakPolicy()).thenReturn(breakPolicy);
        when(mutation.mutateBreak(any(), any(), any(), eq(forgeId)))
            .thenReturn(ItemMutationService.MutationResult.success(resultItem));
        CurseDefinition curse = mock(CurseDefinition.class);
        when(tier.getCurseDefinition()).thenReturn(curse);
        when(mutation.mutateCurse(any(), eq(curse), anyBoolean(), any(), eq(forgeId)))
            .thenReturn(ItemMutationService.MutationResult.success(resultItem));

        OutcomeExecutionResult success = executor.execute(plan, input, player, forgeId,
            ForgeOutcomeCategory.SUCCESS, variant);
        OutcomeExecutionResult broken = executor.execute(plan, input, player, forgeId,
            ForgeOutcomeCategory.BREAK, null);
        OutcomeExecutionResult cursed = executor.execute(plan, input, player, forgeId,
            ForgeOutcomeCategory.CURSE, null);

        assertTrue(success.isSuccess());
        assertTrue(broken.isBreak());
        assertTrue(cursed.isCurse());
        verify(mutation).mutateSuccess(eq(input), eq(tier), eq(variant), any(), eq(forgeId));
        verify(mutation).mutateBreak(eq(input), eq(breakPolicy), any(), eq(forgeId));
        verify(mutation).mutateCurse(eq(input), eq(curse), eq(false), any(), eq(forgeId));
    }

    @Test
    void invalidSuccessWithoutVariantReturnsError() {
        OutcomeExecutionResult result = executor.execute(plan, input, player, forgeId,
            ForgeOutcomeCategory.SUCCESS, null);

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("null variant"));
        verifyNoInteractions(mutation);
    }

    @Test
    void curseMutationUsesIdentityCurseStateOnly() {
        ItemMeta meta = mock(ItemMeta.class);
        Enchantment vanishing = mock(Enchantment.class);
        when(vanishing.getName()).thenReturn("VANISHING_CURSE");
        when(input.hasItemMeta()).thenReturn(true);
        when(input.getItemMeta()).thenReturn(meta);
        when(meta.getEnchants()).thenReturn(Collections.singletonMap(vanishing, 1));
        CurseDefinition curse = mock(CurseDefinition.class);
        when(tier.getCurseDefinition()).thenReturn(curse);
        when(mutation.mutateCurse(any(), eq(curse), anyBoolean(), any(), eq(forgeId)))
                .thenReturn(ItemMutationService.MutationResult.success(input));

        ItemIdentityCodec.Identity unmarked = ItemIdentityCodec.Identity.empty();
        when(identity.readForgeIdentity(input)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID, unmarked));
        executor.execute(plan, input, player, forgeId, ForgeOutcomeCategory.CURSE, null);
        verify(mutation).mutateCurse(eq(input), eq(curse), eq(false), any(), eq(forgeId));

        clearInvocations(mutation);
        ItemIdentityCodec.Identity marked = unmarked.withCursed(true);
        when(identity.readForgeIdentity(input)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID, marked));
        executor.execute(plan, input, player, forgeId, ForgeOutcomeCategory.CURSE, null);
        verify(mutation).mutateCurse(eq(input), eq(curse), eq(true), any(), eq(forgeId));
    }
}
