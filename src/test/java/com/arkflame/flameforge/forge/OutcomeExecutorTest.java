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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutcomeExecutorTest {

    private OutcomeExecutor executor;
    private ItemMutationService mutationService;
    private ItemIdentityService identityService;
    private AuditLogService auditLog;
    private Map<String, Object> wardConfig;

    @BeforeEach
    void setUp() {
        mutationService = mock(ItemMutationService.class);
        identityService = mock(ItemIdentityService.class);
        auditLog = mock(AuditLogService.class);
        wardConfig = new HashMap<>();
        executor = new OutcomeExecutor(mutationService, identityService, auditLog, wardConfig);
    }

    @Test
    void explicitSuccessConsumesSuppliedVariantAndDoesNotReroll() {
        ForgePlan plan = mock(ForgePlan.class);
        ItemStack inputItem = mock(ItemStack.class);
        when(inputItem.hasItemMeta()).thenReturn(true);
        when(inputItem.getType()).thenReturn(org.bukkit.Material.DIAMOND_SWORD);
        Player player = mock(Player.class);
        UUID forgeId = UUID.randomUUID();
        ForgeVariant selectedVariant = mock(ForgeVariant.class);
        when(selectedVariant.getName()).thenReturn("TestVariant");
        when(selectedVariant.getLore()).thenReturn(Collections.emptyList());
        when(selectedVariant.getPowerIds()).thenReturn(Collections.emptyList());
        when(selectedVariant.getAttributes()).thenReturn(Collections.emptyList());
        when(selectedVariant.getEnchantments()).thenReturn(Collections.emptyList());

        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty();
        when(identityService.readForgeIdentity(inputItem)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID,
                identity
            )
        );

        TierDefinition tierDef = mock(TierDefinition.class);
        when(plan.getTargetTier()).thenReturn(tierDef);
        when(plan.getTargetTierLevel()).thenReturn(2);

        ItemStack mutatedItem = mock(ItemStack.class);
        ItemMutationService.MutationResult mutationResult = ItemMutationService.MutationResult.success(mutatedItem);
        when(mutationService.mutateSuccess(any(), eq(tierDef), eq(selectedVariant), any(), eq(forgeId)))
            .thenReturn(mutationResult);

        OutcomeExecutionResult result = executor.execute(plan, inputItem, player, forgeId,
            ForgeOutcomeCategory.SUCCESS, selectedVariant);

        assertTrue(result.isSuccess());
        assertTrue(result.hasItemOutput());
        assertNotNull(result.getItemOutput());
        verify(mutationService).mutateSuccess(eq(inputItem), eq(tierDef), eq(selectedVariant), any(), eq(forgeId));
    }

    @Test
    void explicitBreakUsesTierBreakPolicy() {
        ForgePlan plan = mock(ForgePlan.class);
        ItemStack inputItem = mock(ItemStack.class);
        when(inputItem.hasItemMeta()).thenReturn(true);
        when(inputItem.getType()).thenReturn(org.bukkit.Material.DIAMOND_SWORD);
        Player player = mock(Player.class);
        UUID forgeId = UUID.randomUUID();

        BreakPolicy breakPolicy = BreakPolicy.defaultPolicy();
        TierDefinition tierDef = mock(TierDefinition.class);
        when(plan.getTargetTier()).thenReturn(tierDef);
        when(plan.getTargetTierLevel()).thenReturn(2);
        when(tierDef.getBreakPolicy()).thenReturn(breakPolicy);

        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty();
        when(identityService.readForgeIdentity(inputItem)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID,
                identity
            )
        );

        ItemStack brokenItem = mock(ItemStack.class);
        ItemMutationService.MutationResult mutationResult = ItemMutationService.MutationResult.success(brokenItem);
        when(mutationService.mutateBreak(any(), eq(breakPolicy), any(), eq(forgeId))).thenReturn(mutationResult);

        OutcomeExecutionResult result = executor.execute(plan, inputItem, player, forgeId,
            ForgeOutcomeCategory.BREAK, null);

        assertTrue(result.isBreak());
        verify(mutationService).mutateBreak(eq(inputItem), eq(breakPolicy), any(), eq(forgeId));
    }

    @Test
    void explicitCurseUsesTierCurse() {
        ForgePlan plan = mock(ForgePlan.class);
        ItemStack inputItem = mock(ItemStack.class);
        when(inputItem.hasItemMeta()).thenReturn(true);
        when(inputItem.getType()).thenReturn(org.bukkit.Material.DIAMOND_SWORD);
        Player player = mock(Player.class);
        UUID forgeId = UUID.randomUUID();

        CurseDefinition curseDef = mock(CurseDefinition.class);
        TierDefinition tierDef = mock(TierDefinition.class);
        when(plan.getTargetTier()).thenReturn(tierDef);
        when(plan.getTargetTierLevel()).thenReturn(2);
        when(tierDef.getCurseDefinition()).thenReturn(curseDef);

        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty();
        when(identityService.readForgeIdentity(inputItem)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID,
                identity
            )
        );

        ItemStack cursedItem = mock(ItemStack.class);
        ItemMutationService.MutationResult mutationResult = ItemMutationService.MutationResult.success(cursedItem);
        when(mutationService.mutateCurse(any(), eq(curseDef), anyBoolean(), any(), eq(forgeId))).thenReturn(mutationResult);

        OutcomeExecutionResult result = executor.execute(plan, inputItem, player, forgeId,
            ForgeOutcomeCategory.CURSE, null);

        assertTrue(result.isCurse());
        verify(mutationService).mutateCurse(eq(inputItem), eq(curseDef), anyBoolean(), any(), eq(forgeId));
    }

    @Test
    void successWithNullVariantIsError() {
        ForgePlan plan = mock(ForgePlan.class);
        ItemStack inputItem = mock(ItemStack.class);
        when(inputItem.hasItemMeta()).thenReturn(true);
        when(inputItem.getType()).thenReturn(org.bukkit.Material.DIAMOND_SWORD);
        Player player = mock(Player.class);
        UUID forgeId = UUID.randomUUID();

        TierDefinition tierDef = mock(TierDefinition.class);
        when(plan.getTargetTier()).thenReturn(tierDef);
        when(plan.getTargetTierLevel()).thenReturn(2);

        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty();
        when(identityService.readForgeIdentity(inputItem)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID,
                identity
            )
        );

        OutcomeExecutionResult result = executor.execute(plan, inputItem, player, forgeId,
            ForgeOutcomeCategory.SUCCESS, null);

        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("null variant"));
    }
}
