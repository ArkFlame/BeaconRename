package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.chance.OutcomeSelector;
import com.arkflame.flameforge.chance.RandomSource;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.effect.ForgeAnimationService;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.TierChances;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.persistence.PendingDeliveryRepository;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.session.ForgeSession;
import com.arkflame.flameforge.session.ForgeSessionService;
import com.arkflame.flameforge.station.ForgeStationService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeServiceTest {
    private ForgeService forgeService;
    private ControlledScheduler scheduler;
    private ForgeStationService stationService;
    private PlayerStateRepository playerStateRepository;
    private ItemIdentityService identityService;
    private org.bukkit.entity.Player player;

    @BeforeEach
    void setUp() {
        scheduler = new ControlledScheduler();
        stationService = mock(ForgeStationService.class);
        ConfigService configService = mock(ConfigService.class);
        ConfigSnapshot configSnapshot = mock(ConfigSnapshot.class);
        when(configService.getCurrentSnapshot()).thenReturn(configSnapshot);

        player = mock(org.bukkit.entity.Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        playerStateRepository = mock(PlayerStateRepository.class);
        when(playerStateRepository.getOrLoad(playerId))
            .thenReturn(new PlayerStateRepository.PlayerState(playerId, 0, 0L));

        identityService = mock(ItemIdentityService.class);
        when(identityService.readForgeIdentity(any())).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.NONE,
                ItemIdentityCodec.Identity.empty()
            )
        );

        OutcomeSelector outcomeSelector = mock(OutcomeSelector.class);
        ForgeVariantEligibility variantEligibility = mock(ForgeVariantEligibility.class);

        forgeService = new ForgeService(
            mock(JavaPlugin.class), scheduler, configService, mock(ForgeSessionService.class),
            stationService, mock(CostService.class), mock(ForgeAnimationService.class),
            mock(OutcomeExecutor.class), mock(DeliveryService.class), playerStateRepository,
            mock(PendingDeliveryRepository.class), mock(AuditLogService.class),
            outcomeSelector, variantEligibility, identityService
        );
    }

    @Test
    void createPlanReturnsNullForNullParameters() {
        ForgePlanResult result = forgeService.createPlan(null, null, null);
        assertNull(result.plan);
    }

    @Test
    void createPlanReturnsNullWhenNoTierAvailable() {
        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveTierLevel()).thenReturn(0);
        org.bukkit.inventory.ItemStack input = mock(org.bukkit.inventory.ItemStack.class);

        ForgePlanResult result = forgeService.createPlan(player, session, input);
        assertNull(result.plan);
    }

    @Test
    void freshNoneIdentityTargetsTier1() {
        ConfigService configService = mock(ConfigService.class);
        ConfigSnapshot configSnapshot = mock(ConfigSnapshot.class);
        when(configService.getCurrentSnapshot()).thenReturn(configSnapshot);

        TierDefinition tier1 = createTier("tier1", 1);
        TierDefinition tier2 = createTier("tier2", 2);
        when(configSnapshot.getTiers()).thenReturn(Arrays.asList(tier1, tier2));

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveTierLevel()).thenReturn(99);

        ItemStack realItem = mock(ItemStack.class);
        when(realItem.hasItemMeta()).thenReturn(false);
        when(realItem.getType()).thenReturn(Material.DIAMOND_SWORD);

        ItemIdentityService identityServiceMock = mock(ItemIdentityService.class);
        when(identityServiceMock.readForgeIdentity(realItem)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.NONE,
                ItemIdentityCodec.Identity.empty()
            )
        );

        OutcomeSelector outcomeSelector = mock(OutcomeSelector.class);
        ForgeVariantEligibility variantEligibility = mock(ForgeVariantEligibility.class);

        ForgeService service = new ForgeService(
            mock(JavaPlugin.class), scheduler, configService, mock(ForgeSessionService.class),
            stationService, mock(CostService.class), mock(ForgeAnimationService.class),
            mock(OutcomeExecutor.class), mock(DeliveryService.class), playerStateRepository,
            mock(PendingDeliveryRepository.class), mock(AuditLogService.class),
            outcomeSelector, variantEligibility, identityServiceMock
        );

        ForgePlanResult result = service.createPlan(player, session, realItem);
        assertNotNull(result.plan);
        assertEquals(1, result.plan.getTargetTier().getLevel());
    }

    @Test
    void currentTierNTargetsExactNPlus1() {
        ConfigService configService = mock(ConfigService.class);
        ConfigSnapshot configSnapshot = mock(ConfigSnapshot.class);
        when(configService.getCurrentSnapshot()).thenReturn(configSnapshot);

        TierDefinition tier1 = createTier("tier1", 1);
        TierDefinition tier2 = createTier("tier2", 2);
        TierDefinition tier3 = createTier("tier3", 3);
        when(configSnapshot.getTiers()).thenReturn(Arrays.asList(tier1, tier2, tier3));

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveTierLevel()).thenReturn(1);

        ItemStack realItem = mock(ItemStack.class);
        when(realItem.hasItemMeta()).thenReturn(true);

        ItemIdentityCodec.Identity existingIdentity = ItemIdentityCodec.Identity.empty()
            .withCurrentTier(1)
            .withHighestTier(1)
            .withLastTierId("tier1")
            .withLastVariantId("variant1");

        ItemIdentityService identityServiceMock = mock(ItemIdentityService.class);
        when(identityServiceMock.readForgeIdentity(realItem)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID,
                existingIdentity
            )
        );

        OutcomeSelector outcomeSelector = mock(OutcomeSelector.class);
        ForgeVariantEligibility variantEligibility = mock(ForgeVariantEligibility.class);

        ForgeService service = new ForgeService(
            mock(JavaPlugin.class), scheduler, configService, mock(ForgeSessionService.class),
            stationService, mock(CostService.class), mock(ForgeAnimationService.class),
            mock(OutcomeExecutor.class), mock(DeliveryService.class), playerStateRepository,
            mock(PendingDeliveryRepository.class), mock(AuditLogService.class),
            outcomeSelector, variantEligibility, identityServiceMock
        );

        ForgePlanResult result = service.createPlan(player, session, realItem);
        assertNotNull(result.plan);
        assertEquals(2, result.plan.getTargetTier().getLevel());
    }

    @Test
    void successFiltersVariantsBeforeSharedSelection() {
        ForgeVariantEligibility variantEligibility = mock(ForgeVariantEligibility.class);
        OutcomeSelector outcomeSelector = mock(OutcomeSelector.class);
        CostService costService = mock(CostService.class);
        OutcomeExecutor outcomeExecutor = mock(OutcomeExecutor.class);

        ForgeVariant selectedVariant = mock(ForgeVariant.class);
        when(variantEligibility.eligibleVariants(any(), any())).thenReturn(Collections.singletonList(selectedVariant));
        when(outcomeSelector.selectVariant(any())).thenReturn(selectedVariant);
        when(outcomeSelector.rollCategory(any())).thenReturn(ForgeOutcomeCategory.SUCCESS);

        ChargeReceipt chargeReceipt = mock(ChargeReceipt.class);
        when(chargeReceipt.isSuccess()).thenReturn(true);
        when(costService.charge(any(), any(), any())).thenReturn(chargeReceipt);

        ItemStack realItem = mock(ItemStack.class);
        when(realItem.hasItemMeta()).thenReturn(true);

        verify(variantEligibility, never()).eligibleVariants(any(), any());
        verify(outcomeSelector, never()).selectVariant(any());
        verify(costService, never()).charge(any(), any(), any());
    }

    @Test
    void transactionCategoryAndVariantForwardedUnchangedToExecutor() {
        ConfigService configService = mock(ConfigService.class);
        ConfigSnapshot configSnapshot = mock(ConfigSnapshot.class);
        when(configService.getCurrentSnapshot()).thenReturn(configSnapshot);

        TierDefinition tier1 = createTier("tier1", 1);
        when(configSnapshot.getTiers()).thenReturn(Collections.singletonList(tier1));

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveTierLevel()).thenReturn(0);

        ItemStack realItem = mock(ItemStack.class);
        when(realItem.hasItemMeta()).thenReturn(true);
        when(realItem.getType()).thenReturn(Material.DIAMOND_SWORD);

        ItemIdentityCodec.Identity existingIdentity = ItemIdentityCodec.Identity.empty();

        ItemIdentityService identityServiceMock = mock(ItemIdentityService.class);
        when(identityServiceMock.readForgeIdentity(realItem)).thenReturn(
            new ItemIdentityService.ForgeIdentityRead(
                ItemIdentityService.ForgeIdentityStatus.VALID,
                existingIdentity
            )
        );

        ForgeVariantEligibility variantEligibility = mock(ForgeVariantEligibility.class);

        ForgeVariant selectedVariant = mock(ForgeVariant.class);
        when(variantEligibility.eligibleVariants(any(), any())).thenReturn(Collections.singletonList(selectedVariant));

        OutcomeSelector outcomeSelector = mock(OutcomeSelector.class);
        when(outcomeSelector.selectVariant(any())).thenReturn(selectedVariant);

        JavaPlugin plugin = mock(JavaPlugin.class);
        ForgeService service = new ForgeService(
            plugin, scheduler, configService, mock(ForgeSessionService.class),
            stationService, mock(CostService.class), mock(ForgeAnimationService.class),
            mock(OutcomeExecutor.class), mock(DeliveryService.class), playerStateRepository,
            mock(PendingDeliveryRepository.class), mock(AuditLogService.class),
            outcomeSelector, variantEligibility, identityServiceMock
        );

        ForgePlanResult result = service.createPlan(player, session, realItem);
        assertNotNull(result.plan);
    }

    private TierDefinition createTier(String id, int level) {
        return new TierDefinition(
            id, level, true, "", null, 0L, Collections.emptyList(), Collections.emptyList(),
            new com.arkflame.flameforge.model.TierRequirements(
                com.arkflame.flameforge.model.TierRequirements.Combine.ALL,
                new com.arkflame.flameforge.model.TierRequirements.XpRequirement(false, 0),
                new com.arkflame.flameforge.model.TierRequirements.MoneyRequirement(false, BigDecimal.ZERO),
                new com.arkflame.flameforge.model.TierRequirements.ItemsRequirement(false, Collections.emptyList())),
            new TierChances(BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO),
            null, null, null, Collections.emptyList()
        );
    }

    private static final class ControlledScheduler implements SchedulerBridge {
        private static final TaskHandle HANDLE = new TaskHandle() {
            @Override
            public void cancel() {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };

        private final List<Runnable> globalTasks = new ArrayList<>();

        @Override
        public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
            globalTasks.add(task);
            return HANDLE;
        }

        void runNextGlobalTask() {
            if (globalTasks.isEmpty()) return;
            globalTasks.remove(0).run();
        }

        int globalTaskCount() {
            return globalTasks.size();
        }

        @Override
        public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) {
            return HANDLE;
        }

        @Override
        public TaskHandle runEntity(Entity entity, Runnable runnable, Runnable retireCallback) {
            return HANDLE;
        }

        @Override
        public TaskHandle runEntityLater(Entity entity, Runnable runnable, Runnable retireCallback, long delay) {
            return HANDLE;
        }

        @Override
        public TaskHandle runRegion(Location location, Runnable task) {
            return HANDLE;
        }

        @Override
        public TaskHandle runRegionLater(Location location, Runnable task, long delay) {
            return HANDLE;
        }

        @Override
        public TaskHandle runAsync(JavaPlugin plugin, Runnable task) {
            return HANDLE;
        }

        @Override
        public void cancelAll(JavaPlugin plugin) {
        }

        @Override
        public boolean isFolia() {
            return false;
        }
    }
}
