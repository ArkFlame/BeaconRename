package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.chance.OutcomeSelector;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.effect.AnimationHandle;
import com.arkflame.flameforge.effect.ForgeAnimationService;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.TierChances;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.model.TierRequirements;
import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.persistence.PendingDeliveryRepository;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.session.ForgeSession;
import com.arkflame.flameforge.session.ForgeSessionService;
import com.arkflame.flameforge.station.ForgeStationService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ForgeServiceTest {
    @Test
    void successfulForgeCompletesTransactionAndDeliversResult() {
        Fixture f = fixture();
        ItemStack resultItem = new ItemStack(Material.DIAMOND_SWORD);
        when(f.outcome.execute(any(), any(), eq(f.player), any(), eq(ForgeOutcomeCategory.SUCCESS), eq(f.variant)))
            .thenReturn(OutcomeExecutionResult.successWithItem("forge_execution", Collections.singleton("forge_execution"), resultItem));
        when(f.delivery.generateDeliveryId(f.player, "SUCCESS")).thenReturn("result-delivery");
        when(f.delivery.deliverItem(same(resultItem), eq(f.player), any(), eq("result-delivery"))).thenReturn(true);

        AtomicInteger callbacks = new AtomicInteger();
        ForgeResolution[] resolution = new ForgeResolution[1];
        f.service.confirmAndExecute(f.player, PlayerForgeState.of(f.playerId.toString()), f.input, f.plan,
            value -> { callbacks.incrementAndGet(); resolution[0] = value; });

        assertNotNull(resolution[0]);
        assertTrue(resolution[0].isSuccess());
        assertTrue(f.session.isClosed());
        assertEquals(1, callbacks.get());
        verify(f.cost).charge(eq(f.player), eq(f.tier.getCost()), anyList());
        verify(f.delivery).deliverItem(same(resultItem), eq(f.player), any(), eq("result-delivery"));
    }

    @Test
    void preTransactionFailureReturnsCustodyWithoutChargeLoss() {
        Fixture f = fixture();
        when(f.cost.charge(any(), any(), any())).thenReturn(ChargeReceipt.failure("insufficient"));
        when(f.delivery.deliverItem(any(), eq(f.player), any(), isNull())).thenReturn(true);
        ForgeResolution[] resolution = new ForgeResolution[1];

        f.service.confirmAndExecute(f.player, PlayerForgeState.of(f.playerId.toString()), f.input, f.plan,
            value -> resolution[0] = value);

        assertNotNull(resolution[0]);
        assertFalse(resolution[0].isSuccess());
        assertTrue(resolution[0].isPreRollFailure());
        verify(f.cost, never()).refund(any(), any());
        verify(f.delivery, times(1)).deliverItem(any(), eq(f.player), any(), isNull());
    }

    @Test
    void animationFailureRollsBackChargeAndCustody() {
        Fixture f = fixture();
        ChargeReceipt receipt = ChargeReceipt.success(3, new BigDecimal("4.00"), Collections.emptyList());
        when(f.cost.charge(any(), any(), any())).thenReturn(receipt);
        when(f.animation.playAnimation(anyString(), any(), any(), any(), any(), any(ForgeOutcomeCategory.class),
            any(ForgeVariant.class), any(), any()))
            .thenReturn(mock(AnimationHandle.class));
        when(f.delivery.deliverItem(any(), eq(f.player), any(), isNull())).thenReturn(true);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<String>> failure = ArgumentCaptor.forClass(Consumer.class);
        ForgeResolution[] resolution = new ForgeResolution[1];

        f.service.confirmAndExecute(f.player, PlayerForgeState.of(f.playerId.toString()), f.input, f.plan,
            value -> resolution[0] = value);
        verify(f.animation).playAnimation(anyString(), any(), any(), any(), any(), any(ForgeOutcomeCategory.class),
            any(ForgeVariant.class), any(), failure.capture());
        failure.getValue().accept("animation-failed");

        assertNotNull(resolution[0]);
        assertFalse(resolution[0].isSuccess());
        verify(f.cost, times(1)).refund(eq(f.player), eq(receipt));
        verify(f.delivery, times(1)).deliverItem(any(), eq(f.player), any(), isNull());
    }

    @Test
    void outcomeOrDeliveryFailureRollsBackDeterministically() {
        Fixture outcomeFailure = fixture();
        when(outcomeFailure.outcome.execute(any(), any(), any(), any(), any(), any()))
            .thenReturn(OutcomeExecutionResult.error("forge_execution", "mutation failed"));
        when(outcomeFailure.delivery.deliverItem(any(), eq(outcomeFailure.player), any(), isNull())).thenReturn(true);
        ForgeResolution[] firstResolution = new ForgeResolution[1];
        outcomeFailure.service.confirmAndExecute(outcomeFailure.player,
            PlayerForgeState.of(outcomeFailure.playerId.toString()), outcomeFailure.input, outcomeFailure.plan,
            value -> firstResolution[0] = value);
        assertNotNull(firstResolution[0]);
        assertFalse(firstResolution[0].isSuccess());
        verify(outcomeFailure.cost, times(1)).refund(any(), any());
        verify(outcomeFailure.delivery, times(1)).deliverItem(any(), eq(outcomeFailure.player), any(), isNull());

        Fixture deliveryFailure = fixture();
        ItemStack resultItem = new ItemStack(Material.DIAMOND_SWORD);
        when(deliveryFailure.outcome.execute(any(), any(), any(), any(), any(), any()))
            .thenReturn(OutcomeExecutionResult.successWithItem("forge_execution",
                Collections.singleton("forge_execution"), resultItem));
        when(deliveryFailure.delivery.generateDeliveryId(any(), eq("SUCCESS"))).thenReturn("output-delivery");
        when(deliveryFailure.delivery.deliverItem(same(resultItem), eq(deliveryFailure.player), any(), eq("output-delivery")))
            .thenReturn(false);
        when(deliveryFailure.delivery.deliverItem(any(), eq(deliveryFailure.player), any(), isNull())).thenReturn(true);
        ForgeResolution[] secondResolution = new ForgeResolution[1];
        deliveryFailure.service.confirmAndExecute(deliveryFailure.player,
            PlayerForgeState.of(deliveryFailure.playerId.toString()), deliveryFailure.input, deliveryFailure.plan,
            value -> secondResolution[0] = value);
        assertNotNull(secondResolution[0]);
        assertFalse(secondResolution[0].isSuccess());
        verify(deliveryFailure.cost, times(1)).refund(any(), any());
        verify(deliveryFailure.delivery).deliverItem(same(resultItem), eq(deliveryFailure.player), any(), eq("output-delivery"));
        verify(deliveryFailure.delivery, times(1)).deliverItem(any(), eq(deliveryFailure.player), any(), isNull());
    }

    @Test
    void duplicateOrTerminalCompletionDoesNotSettleTwice() {
        Fixture f = fixture();
        ItemStack resultItem = new ItemStack(Material.DIAMOND_SWORD);
        when(f.outcome.execute(any(), any(), any(), any(), any(), any()))
            .thenReturn(OutcomeExecutionResult.successWithItem("forge_execution",
                Collections.singleton("forge_execution"), resultItem));
        when(f.delivery.generateDeliveryId(any(), eq("SUCCESS"))).thenReturn("once");
        when(f.delivery.deliverItem(same(resultItem), eq(f.player), any(), eq("once"))).thenReturn(true);
        when(f.animation.playAnimation(anyString(), any(), any(), any(), any(), any(ForgeOutcomeCategory.class),
            any(ForgeVariant.class), any(), any()))
            .thenReturn(mock(AnimationHandle.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<String>> completion = ArgumentCaptor.forClass(Consumer.class);
        AtomicInteger callbacks = new AtomicInteger();

        f.service.confirmAndExecute(f.player, PlayerForgeState.of(f.playerId.toString()), f.input, f.plan,
            value -> callbacks.incrementAndGet());
        verify(f.animation).playAnimation(anyString(), any(), any(), any(), any(), any(ForgeOutcomeCategory.class),
            any(ForgeVariant.class), completion.capture(), any());
        completion.getValue().accept("complete");
        completion.getValue().accept("complete-again");

        assertTrue(f.session.isClosed());
        assertEquals(1, callbacks.get());
        verify(f.cost, times(1)).charge(any(), any(), any());
        verify(f.delivery, times(1)).deliverItem(same(resultItem), eq(f.player), any(), eq("once"));
        verify(f.cost, never()).refund(any(), any());
    }

    private static Fixture fixture() {
        Fixture f = new Fixture();
        f.playerId = UUID.randomUUID();
        f.plugin = mock(JavaPlugin.class);
        when(f.plugin.getLogger()).thenReturn(Logger.getLogger(ForgeServiceTest.class.getName()));
        f.scheduler = mock(SchedulerBridge.class);
        f.player = mock(Player.class);
        when(f.player.getUniqueId()).thenReturn(f.playerId);
        when(f.player.isOnline()).thenReturn(true);
        when(f.player.getName()).thenReturn("forge-player");
        when(f.player.getLocation()).thenReturn(mock(Location.class));
        f.config = mock(ConfigService.class);
        when(f.config.getCurrentSnapshot()).thenReturn(mock(ConfigSnapshot.class));
        f.session = new ForgeSession(f.playerId.toString());
        f.sessions = mock(ForgeSessionService.class);
        when(f.sessions.openSession(f.playerId.toString())).thenReturn(f.session);
        f.station = mock(ForgeStationService.class);
        f.cost = mock(CostService.class);
        when(f.cost.charge(any(), any(), any())).thenReturn(ChargeReceipt.success(0, BigDecimal.ZERO, Collections.emptyList()));
        f.animation = mock(ForgeAnimationService.class);
        f.outcome = mock(OutcomeExecutor.class);
        f.delivery = mock(DeliveryService.class);
        f.playerStates = mock(PlayerStateRepository.class);
        f.audit = mock(AuditLogService.class);
        f.selector = mock(OutcomeSelector.class);
        f.eligibility = mock(ForgeVariantEligibility.class);
        f.identity = mock(ItemIdentityService.class);
        f.input = new ItemStack(Material.DIAMOND_SWORD);
        f.variant = variant();
        f.tier = tier(f.variant);
        f.quote = mock(CostQuote.class);
        when(f.quote.isAffordable()).thenReturn(true);
        f.plan = ForgePlan.createWithTier(f.input, 0, f.tier, f.tier.getCost(), f.tier.getChances(),
            f.quote, null, null, null, null, 0, 0, 0);
        when(f.eligibility.eligibleVariants(any(), any())).thenReturn(Collections.singletonList(f.variant));
        when(f.selector.selectVariant(anyList())).thenReturn(f.variant);
        when(f.selector.rollCategory(any())).thenReturn(ForgeOutcomeCategory.SUCCESS);
        f.service = new ForgeService(f.plugin, f.scheduler, f.config, f.sessions, f.station, f.cost,
            f.animation, f.outcome, f.delivery, f.playerStates, mock(PendingDeliveryRepository.class),
            f.audit, f.selector, f.eligibility, f.identity);
        return f;
    }

    private static ForgeVariant variant() {
        return new ForgeVariant("variant", "Variant", Collections.emptyList(), 1.0, "DIAMOND_SWORD",
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private static TierDefinition tier(ForgeVariant variant) {
        TierRequirements requirements = new TierRequirements(TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(false, 0),
            new TierRequirements.MoneyRequirement(false, BigDecimal.ZERO),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList()));
        return new TierDefinition("tier1", 1, true, "", null, 0L, Collections.emptyList(),
            Collections.emptyList(), requirements, new TierChances(BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO),
            null, null, null, Collections.singletonList(variant));
    }

    private static final class Fixture {
        private JavaPlugin plugin;
        private SchedulerBridge scheduler;
        private ConfigService config;
        private ForgeSessionService sessions;
        private ForgeStationService station;
        private CostService cost;
        private ForgeAnimationService animation;
        private OutcomeExecutor outcome;
        private DeliveryService delivery;
        private PlayerStateRepository playerStates;
        private AuditLogService audit;
        private OutcomeSelector selector;
        private ForgeVariantEligibility eligibility;
        private ItemIdentityService identity;
        private ForgeService service;
        private ForgeSession session;
        private Player player;
        private UUID playerId;
        private ItemStack input;
        private ForgeVariant variant;
        private TierDefinition tier;
        private CostQuote quote;
        private ForgePlan plan;
    }
}
