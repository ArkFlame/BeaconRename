package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.forge.ForgeItemPolicy;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.text.MessageService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeMenuInputServiceTest {

    private ForgeMenuRegistry registry;
    private ForgeMenuViewResolver viewResolver;
    private ForgeMenuService menuService;
    private ForgeItemPolicy itemPolicy;
    private ForgeMenuSettlementService settlementService;
    private SchedulerBridge scheduler;
    private MessageService messageService;

    private ForgeMenuInputService inputService;

    @BeforeEach
    void setUp() {
        registry = new ForgeMenuRegistry();
        viewResolver = new ForgeMenuViewResolver(registry);
        menuService = mock(ForgeMenuService.class);
        itemPolicy = mock(ForgeItemPolicy.class);
        settlementService = mock(ForgeMenuSettlementService.class);
        messageService = mock(MessageService.class);

        scheduler = mock(SchedulerBridge.class);
        when(scheduler.runEntity(any(Player.class), any(Runnable.class), any(Runnable.class)))
            .thenAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(1);
                runnable.run();
                return mock(TaskHandle.class);
            });

        inputService = new ForgeMenuInputService(
            registry, viewResolver, menuService, itemPolicy,
            settlementService, scheduler, messageService
        );
    }

    @Test
    void insertUsesBottomInventoryLocalSlotAndRendersOnce() {
        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station1";

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        ItemStack sourceItem = mock(ItemStack.class);
        when(sourceItem.getType()).thenReturn(Material.DIAMOND);
        when(sourceItem.clone()).thenReturn(sourceItem);
        when(sourceItem.isSimilar(any(ItemStack.class))).thenReturn(true);
        when(sourceItem.getAmount()).thenReturn(2);

        Inventory sourceInventory = mock(Inventory.class);
        when(sourceInventory.getSize()).thenReturn(54);
        when(sourceInventory.getItem(5)).thenReturn(sourceItem);

        InventoryView view = mock(InventoryView.class);
        when(view.getBottomInventory()).thenReturn(sourceInventory);
        when(player.getOpenInventory()).thenReturn(view);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn(stationId);
        when(session.getActiveTierLevel()).thenReturn(1);

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        registry.replace(context);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(menuId, playerId, stationId);

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(holder);
        when(view.getTopInventory()).thenReturn(topInventory);

        when(itemPolicy.checkItem(any(Player.class), any(), any())).thenReturn(ForgeItemPolicy.PolicyResult.allow());
        when(menuService.rerender(any(Player.class))).thenReturn(ForgeMenuService.MenuResult.opened(menuId));

        inputService.requestInsertOne(player, holder, sourceInventory, 5, sourceItem);

        verify(sourceInventory).setItem(eq(5), any(ItemStack.class));
        verify(messageService).send(player, "menu.item-inserted");
        verify(menuService).rerender(player);
        verify(view, never()).getItem(anyInt());
        verify(view, never()).setItem(anyInt(), any(ItemStack.class));
    }

    @Test
    void occupiedInputLeavesSourceUntouched() {
        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station1";

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        ItemStack sourceItem = mock(ItemStack.class);
        when(sourceItem.getType()).thenReturn(Material.DIAMOND);
        when(sourceItem.clone()).thenReturn(sourceItem);
        when(sourceItem.isSimilar(any(ItemStack.class))).thenReturn(true);

        Inventory sourceInventory = mock(Inventory.class);
        when(sourceInventory.getSize()).thenReturn(54);
        when(sourceInventory.getItem(5)).thenReturn(sourceItem);

        InventoryView view = mock(InventoryView.class);
        when(view.getBottomInventory()).thenReturn(sourceInventory);
        when(player.getOpenInventory()).thenReturn(view);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn(stationId);

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        context.tryInsert(new ItemStack(Material.DIAMOND, 1));
        registry.replace(context);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(menuId, playerId, stationId);

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(holder);
        when(view.getTopInventory()).thenReturn(topInventory);

        inputService.requestInsertOne(player, holder, sourceInventory, 5, sourceItem);

        verify(messageService).send(player, "menu.input-occupied");
        verify(menuService, never()).rerender(any(Player.class));
        verify(sourceInventory, never()).setItem(anyInt(), any(ItemStack.class));
    }

    @Test
    void rerenderFailureRemovesContextAndReturnsCustody() {
        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station1";

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        ItemStack sourceItem = mock(ItemStack.class);
        when(sourceItem.getType()).thenReturn(Material.DIAMOND);
        when(sourceItem.clone()).thenReturn(sourceItem);
        when(sourceItem.isSimilar(any(ItemStack.class))).thenReturn(true);

        Inventory sourceInventory = mock(Inventory.class);
        when(sourceInventory.getSize()).thenReturn(54);
        when(sourceInventory.getItem(5)).thenReturn(sourceItem);

        InventoryView view = mock(InventoryView.class);
        when(view.getBottomInventory()).thenReturn(sourceInventory);
        when(player.getOpenInventory()).thenReturn(view);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn(stationId);

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        registry.replace(context);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(menuId, playerId, stationId);

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(holder);
        when(view.getTopInventory()).thenReturn(topInventory);

        when(itemPolicy.checkItem(any(Player.class), any(), any())).thenReturn(ForgeItemPolicy.PolicyResult.allow());
        when(menuService.rerender(any(Player.class))).thenReturn(
            ForgeMenuService.MenuResult.renderFailed(menuId.toString(), "render failed")
        );

        inputService.requestInsertOne(player, holder, sourceInventory, 5, sourceItem);

        assertFalse(registry.get(playerId).isPresent());
        verify(settlementService).settleOnlineOrQueue(any(ForgeMenuContext.class), eq(player));
        verify(player).closeInventory();
        verify(messageService).send(eq(player), eq("open.menu-open-failed"), any());
    }

    @Test
    void inventoryCloseSettlesExactlyOnce() {
        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station1";

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn(stationId);

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        context.tryInsert(new ItemStack(Material.DIAMOND, 1));
        registry.replace(context);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(menuId, playerId, stationId);

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(holder);
        when(topInventory.getSize()).thenReturn(54);

        Inventory bottomInventory = mock(Inventory.class);
        when(bottomInventory.getHolder()).thenReturn(null);

        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(view.getBottomInventory()).thenReturn(bottomInventory);
        when(player.getOpenInventory()).thenReturn(view);

        inputService.handleInventoryClose(player, holder);

        assertFalse(registry.get(playerId).isPresent());
        verify(settlementService).settleOnlineOrQueue(any(ForgeMenuContext.class), eq(player));
        verify(settlementService, never()).settleOffline(any());

        reset(settlementService);
        inputService.handleInventoryClose(player, holder);
        verify(settlementService, never()).settleOnlineOrQueue(any(), any());
        verify(settlementService, never()).settleOffline(any());
    }

    @Test
    void policyDeniedSendsCorrectMessageKey() {
        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station1";

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        ItemStack sourceItem = mock(ItemStack.class);
        when(sourceItem.getType()).thenReturn(Material.DIAMOND);
        when(sourceItem.clone()).thenReturn(sourceItem);
        when(sourceItem.isSimilar(any(ItemStack.class))).thenReturn(true);

        Inventory sourceInventory = mock(Inventory.class);
        when(sourceInventory.getSize()).thenReturn(54);
        when(sourceInventory.getItem(5)).thenReturn(sourceItem);

        InventoryView view = mock(InventoryView.class);
        when(view.getBottomInventory()).thenReturn(sourceInventory);
        when(player.getOpenInventory()).thenReturn(view);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn(stationId);

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        registry.replace(context);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(menuId, playerId, stationId);

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(holder);
        when(view.getTopInventory()).thenReturn(topInventory);

        when(itemPolicy.checkItem(any(Player.class), any(), any()))
            .thenReturn(ForgeItemPolicy.PolicyResult.deny("menu.item-denied.customized"));

        inputService.requestInsertOne(player, holder, sourceInventory, 5, sourceItem);

        verify(messageService).send(player, "menu.item-denied.customized");
        verify(menuService, never()).rerender(any(Player.class));
    }

    @Test
    void quitQueuesInputOfflineAndRemovesContext() {
        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station1";

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn(stationId);

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        context.tryInsert(new ItemStack(Material.DIAMOND, 1));
        registry.replace(context);

        inputService.handlePlayerQuit(player);

        assertFalse(registry.get(playerId).isPresent());
        verify(settlementService).settleOffline(any(ForgeMenuContext.class));
        verify(settlementService, never()).settleOnlineOrQueue(any(), any());
    }

    @Test
    void shutdownDrainsAllContextsWithoutScheduling() {
        UUID playerId1 = UUID.randomUUID();
        UUID playerId2 = UUID.randomUUID();
        UUID menuId1 = UUID.randomUUID();
        UUID menuId2 = UUID.randomUUID();
        String stationId = "station1";

        PlayerForgeState session1 = mock(PlayerForgeState.class);
        when(session1.getActiveStationId()).thenReturn(stationId);
        PlayerForgeState session2 = mock(PlayerForgeState.class);
        when(session2.getActiveStationId()).thenReturn(stationId);

        ForgeMenuContext context1 = new ForgeMenuContext(menuId1, playerId1, stationId, session1, System.currentTimeMillis());
        context1.tryInsert(new ItemStack(Material.DIAMOND, 1));
        ForgeMenuContext context2 = new ForgeMenuContext(menuId2, playerId2, stationId, session2, System.currentTimeMillis());
        context2.tryInsert(new ItemStack(Material.GOLD_INGOT, 2));

        registry.replace(context1);
        registry.replace(context2);

        inputService.shutdown();

        assertEquals(0, registry.size());
        verify(settlementService).settleOffline(context1);
        verify(settlementService).settleOffline(context2);
        verify(scheduler, never()).runEntity(any(), any(), any());
        verify(scheduler, never()).runGlobal(any(), any());
        verify(scheduler, never()).runAsync(any(), any());
    }
}
