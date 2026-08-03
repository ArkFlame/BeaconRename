package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.forge.ForgeService;
import com.arkflame.flameforge.menu.ForgeInventoryHolder;
import com.arkflame.flameforge.menu.ForgeMenuContext;
import com.arkflame.flameforge.menu.ForgeMenuService;
import com.arkflame.flameforge.model.PlayerForgeState;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ForgeInventoryListenerTest {

    private ControlledScheduler scheduler;
    private ForgeService forgeService;
    private ForgeMenuService menuService;
    private Player player;
    private Inventory inventory;
    private ForgeInventoryHolder menuHolder;
    private ForgeInventoryListener listener;

    @BeforeEach
    void setUp() {
        scheduler = new ControlledScheduler();
        forgeService = mock(ForgeService.class);
        menuService = mock(ForgeMenuService.class);
        player = mock(Player.class);

        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        PlayerForgeState session = PlayerForgeState.of(playerId.toString())
            .withActiveStation("station", 1);
        menuHolder = new ForgeInventoryHolder(UUID.randomUUID(), playerId, "station");
        inventory = mock(Inventory.class);
        menuHolder.setInventory(inventory);
        when(inventory.getHolder()).thenReturn(menuHolder);
        when(inventory.getSize()).thenReturn(54);
        when(inventory.getContents()).thenReturn(new ItemStack[54]);

        InventoryView view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);

        when(menuService.isCurrentMenu(any(Player.class), any(ForgeInventoryHolder.class))).thenReturn(true);
        when(menuService.closeIfCurrent(any(UUID.class), any(ForgeInventoryHolder.class))).thenReturn(Optional.empty());

        listener = new ForgeInventoryListener(
            mock(JavaPlugin.class), menuService, scheduler
        );
    }

    @Test
    void menuOpenedByServiceImmediatelyAuthorizesClickAndDrag() {
        InventoryClickEvent clickEvent = confirmClick();
        clickEvent.setCancelled(false);

        listener.onInventoryClick(clickEvent);

        verify(menuService).isCurrentMenu(eq(player), eq(menuHolder));
    }

    @Test
    void confirmClickProcessesOnEntityScheduler() {
        ForgeMenuContext context = createOpenContext();
        when(menuService.getContext(any(UUID.class))).thenReturn(context);

        listener.onInventoryClick(confirmClick());

        assertEquals(1, scheduler.entityTaskCount());
    }

    @Test
    void secondConfirmProcessesAnotherTask() {
        ForgeMenuContext context = createOpenContext();
        when(menuService.getContext(any(UUID.class))).thenReturn(context);

        listener.onInventoryClick(confirmClick());
        listener.onInventoryClick(confirmClick());

        assertEquals(2, scheduler.entityTaskCount());
    }

    @Test
    void menuCloseBeforeAsyncCompletionPreventsExecution() {
        PlayerForgeState session = PlayerForgeState.of(player.getUniqueId().toString())
            .withActiveStation("station", 1);
        ForgeMenuContext context = new ForgeMenuContext(
            UUID.randomUUID(), player.getUniqueId(), "station", session, System.currentTimeMillis()
        );
        when(menuService.closeIfCurrent(any(UUID.class), any(ForgeInventoryHolder.class)))
            .thenReturn(Optional.of(context));
        when(menuService.getContext(any(UUID.class))).thenReturn(context);

        ForgeInventoryListener testListener = new ForgeInventoryListener(
            mock(JavaPlugin.class), menuService, scheduler
        );

        testListener.onInventoryClick(confirmClick());
        scheduler.runNextEntityTask();
        testListener.onInventoryClose(closeEvent());

        assertEquals(0, scheduler.entityTaskCount());
        verify(forgeService, never()).createPlan(any(Player.class), any(PlayerForgeState.class), any(ItemStack.class));
    }

    @Test
    void normalAndExplicitCloseSettleExactlyOnceAndClearContext() {
        PlayerForgeState session = PlayerForgeState.of(player.getUniqueId().toString())
            .withActiveStation("station", 1);
        ForgeMenuContext context = new ForgeMenuContext(
            UUID.randomUUID(), player.getUniqueId(), "station", session, System.currentTimeMillis()
        );
        when(menuService.closeIfCurrent(any(UUID.class), any(ForgeInventoryHolder.class)))
            .thenReturn(Optional.of(context), Optional.empty());
        when(menuService.getContext(any(UUID.class))).thenReturn(context);

        ForgeInventoryListener testListener = new ForgeInventoryListener(
            mock(JavaPlugin.class), menuService, scheduler
        );

        InventoryCloseEvent closeEvt = closeEvent();
        testListener.onInventoryClose(closeEvt);
        verify(menuService).closeIfCurrent(eq(player.getUniqueId()), eq(menuHolder));
        verify(player, never()).closeInventory();

        InventoryClickEvent closeClick = createClickEvent(26);
        testListener.onInventoryClick(closeClick);
        verify(player).closeInventory();
        verify(menuService, times(2)).closeIfCurrent(eq(player.getUniqueId()), eq(menuHolder));
    }

    @Test
    void lateConfirmationIsRejectedAfterContextRetires() {
        when(menuService.isCurrentMenu(any(Player.class), any(ForgeInventoryHolder.class))).thenReturn(false);

        ForgeInventoryListener testListener = new ForgeInventoryListener(
            mock(JavaPlugin.class), menuService, scheduler
        );

        InventoryClickEvent confirmEvent = confirmClick();
        testListener.onInventoryClick(confirmEvent);

        assertEquals(0, scheduler.entityTaskCount());
        verify(forgeService, never()).createPlan(any(Player.class), any(PlayerForgeState.class), any(ItemStack.class));
    }

    @Test
    void listenerHasNoSecondaryMenuOpenAuthority() {
        ForgeInventoryListener testListener = new ForgeInventoryListener(
            mock(JavaPlugin.class), menuService, scheduler
        );

        InventoryClickEvent clickEvent = confirmClick();
        testListener.onInventoryClick(clickEvent);

        verify(menuService).isCurrentMenu(eq(player), eq(menuHolder));
        verify(menuService, never()).open(any(Player.class), any(PlayerForgeState.class));
    }

    private ForgeMenuContext createOpenContext() {
        PlayerForgeState session = PlayerForgeState.of(player.getUniqueId().toString())
            .withActiveStation("station", 1);
        return new ForgeMenuContext(
            UUID.randomUUID(), player.getUniqueId(), "station", session, System.currentTimeMillis()
        );
    }

    private InventoryClickEvent confirmClick() {
        return createClickEvent(22);
    }

    private InventoryClickEvent createClickEvent(int rawSlot) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.isCancelled()).thenReturn(false);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        return event;
    }

    private InventoryCloseEvent closeEvent() {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getPlayer()).thenReturn(player);
        return event;
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

        private final java.util.List<Runnable> entityTasks = new java.util.ArrayList<>();
        private boolean runningEntityTask;

        @Override
        public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) {
            return HANDLE;
        }

        @Override
        public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) {
            return HANDLE;
        }

        @Override
        public TaskHandle runEntity(Entity entity, Runnable runnable, Runnable retireCallback) {
            entityTasks.add(runnable);
            return HANDLE;
        }

        void runNextEntityTask() {
            if (entityTasks.isEmpty()) return;
            Runnable task = entityTasks.remove(0);
            runningEntityTask = true;
            try {
                task.run();
            } finally {
                runningEntityTask = false;
            }
        }

        int entityTaskCount() {
            return entityTasks.size();
        }

        boolean isRunningEntityTask() {
            return runningEntityTask;
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
