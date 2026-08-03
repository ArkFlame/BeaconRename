package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.effect.ForgeAnimationService;
import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.persistence.PendingDeliveryRepository;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.session.ForgeSessionService;
import com.arkflame.flameforge.station.ForgeStationService;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForgeServiceTest {
    private ForgeService forgeService;
    private ControlledScheduler scheduler;
    private ForgeStationService stationService;
    private PlayerStateRepository playerStateRepository;
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

        playerStateRepository = mock(PlayerStateRepository.class);
        when(playerStateRepository.getOrLoad(playerId))
            .thenReturn(new PlayerStateRepository.PlayerState(playerId, 0, 0L));

        forgeService = new ForgeService(
            mock(JavaPlugin.class), scheduler, configService, mock(ForgeSessionService.class),
            stationService, mock(CostService.class), mock(ForgeAnimationService.class),
            mock(OutcomeExecutor.class), mock(DeliveryService.class), playerStateRepository,
            mock(PendingDeliveryRepository.class), mock(AuditLogService.class)
        );
    }

    @Test
    void createPlanReturnsNullForNullParameters() {
        ForgePlan plan = forgeService.createPlan(null, null, null);
        assertNull(plan);
    }

    @Test
    void createPlanReturnsNullWhenNoTierAvailable() {
        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveTierLevel()).thenReturn(0);
        org.bukkit.inventory.ItemStack input = mock(org.bukkit.inventory.ItemStack.class);

        ForgePlan plan = forgeService.createPlan(player, session, input);
        assertNull(plan);
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
