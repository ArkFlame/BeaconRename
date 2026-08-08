package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.forge.*;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.text.MessageService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeMenuForgeServiceTest {

    private ForgeMenuRegistry registry;
    private ForgeMenuViewResolver viewResolver;
    private ForgeService forgeService;
    private ForgeMenuSettlementService settlementService;
    private ForgeMenuService menuService;
    private SchedulerBridge scheduler;
    private MessageService messageService;

    private ForgeMenuForgeService forgeMenuService;

    @BeforeEach
    void setUp() {
        registry = new ForgeMenuRegistry();
        viewResolver = new ForgeMenuViewResolver(registry);
        forgeService = mock(ForgeService.class);
        settlementService = mock(ForgeMenuSettlementService.class);
        menuService = mock(ForgeMenuService.class);
        messageService = mock(MessageService.class);

        scheduler = mock(SchedulerBridge.class);
        when(scheduler.runEntity(any(Player.class), any(Runnable.class), any(Runnable.class)))
            .thenAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(1);
                runnable.run();
                return mock(TaskHandle.class);
            });

        forgeMenuService = new ForgeMenuForgeService(
            registry, viewResolver, forgeService, settlementService,
            menuService, scheduler, messageService, Logger.getLogger("test")
        );
    }

    @Test
    void planFailureLeavesContextInput() {
        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station1";

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        ItemStack inputItem = mock(ItemStack.class);
        when(inputItem.getType()).thenReturn(Material.DIAMOND);
        when(inputItem.clone()).thenReturn(inputItem);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn(stationId);

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        context.tryInsert(inputItem);
        registry.replace(context);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(menuId, playerId, stationId);

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(holder);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(player.getOpenInventory()).thenReturn(view);

        when(forgeService.createPlan(any(Player.class), any(), any())).thenReturn(ForgePlanResult.nextTierMissing());

        forgeMenuService.requestConfirm(player, holder);

        assertTrue(registry.getCurrent(playerId, menuId).isPresent());
        verify(messageService).send(eq(player), eq("menu.item-denied.no-tier"));
        verify(menuService).rerender(player);
    }

    @Test
    void unaffordableLeavesContextInput() {
        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station1";

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        ItemStack inputItem = mock(ItemStack.class);
        when(inputItem.getType()).thenReturn(Material.DIAMOND);
        when(inputItem.clone()).thenReturn(inputItem);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn(stationId);

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        context.tryInsert(inputItem);
        registry.replace(context);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(menuId, playerId, stationId);

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(holder);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(player.getOpenInventory()).thenReturn(view);

        ForgePlan plan = mock(ForgePlan.class);
        ForgePlanResult planResult = ForgePlanResult.ready(plan);
        when(forgeService.createPlan(any(Player.class), any(), any())).thenReturn(planResult);
        when(plan.isAffordable()).thenReturn(false);

        forgeMenuService.requestConfirm(player, holder);

        assertTrue(registry.getCurrent(playerId, menuId).isPresent());
        verify(messageService).send(eq(player), eq("menu.requirements-not-met"));
        verify(menuService).rerender(player);
    }

    @Test
    void successfulConfirmationRemovesRegistryClaimsOnceSubmitsOnce() {
        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station1";

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        ItemStack inputItem = mock(ItemStack.class);
        when(inputItem.getType()).thenReturn(Material.DIAMOND);
        when(inputItem.clone()).thenReturn(inputItem);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn(stationId);

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        context.tryInsert(inputItem);
        registry.replace(context);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(menuId, playerId, stationId);

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(holder);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(player.getOpenInventory()).thenReturn(view);

        ForgePlan plan = mock(ForgePlan.class);
        ForgePlanResult planResult = ForgePlanResult.ready(plan);
        when(forgeService.createPlan(any(Player.class), any(), any())).thenReturn(planResult);
        when(plan.isAffordable()).thenReturn(true);

        AtomicReference<Consumer<ForgeResolution>> capturedCallback = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedCallback.set(invocation.getArgument(4));
            return null;
        }).when(forgeService).confirmAndExecute(any(Player.class), any(), any(), any(), any());

        forgeMenuService.requestConfirm(player, holder);

        assertFalse(registry.getCurrent(playerId, menuId).isPresent());
        verify(player).closeInventory();
        verify(forgeService).confirmAndExecute(eq(player), eq(session), any(), eq(plan), any());
    }

    @Test
    void staleHolderCannotConfirmNewerSameStationContext() {
        UUID playerId = UUID.randomUUID();
        UUID menuIdA = UUID.randomUUID();
        UUID menuIdB = UUID.randomUUID();
        String stationId = "station1";

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn(stationId);

        ForgeMenuContext contextB = new ForgeMenuContext(menuIdB, playerId, stationId, session, System.currentTimeMillis());
        registry.replace(contextB);

        ForgeInventoryHolder holderA = new ForgeInventoryHolder(menuIdA, playerId, stationId);

        forgeMenuService.requestConfirm(player, holderA);

        verify(forgeService, never()).confirmAndExecute(any(), any(), any(), any(), any());
        assertTrue(registry.getCurrent(playerId, menuIdB).isPresent());
    }

    @Test
    void completionFeedbackReturnsToEntitySchedulerAndDoesNotReopen() {
        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station1";

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        ItemStack inputItem = new ItemStack(Material.DIAMOND, 1);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn(stationId);

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        context.tryInsert(inputItem);
        registry.replace(context);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(menuId, playerId, stationId);

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(holder);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(player.getOpenInventory()).thenReturn(view);

        ForgePlan plan = mock(ForgePlan.class);
        ForgePlanResult planResult = ForgePlanResult.ready(plan);
        when(forgeService.createPlan(any(Player.class), any(), any())).thenReturn(planResult);
        when(plan.isAffordable()).thenReturn(true);

        AtomicReference<Consumer<ForgeResolution>> capturedCallback = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedCallback.set(invocation.getArgument(4));
            return null;
        }).when(forgeService).confirmAndExecute(any(Player.class), any(), any(), any(), any());

        forgeMenuService.requestConfirm(player, holder);

        assertNotNull(capturedCallback.get());

        verify(messageService, never()).send(player, "forge.confirm.complete");

        Consumer<ForgeResolution> callback = capturedCallback.get();
        ForgeResolution successResolution = ForgeResolution.success(
            UUID.randomUUID(),
            ForgeOutcomeCategory.SUCCESS,
            null,
            "SUCCESS",
            null,
            null,
            null,
            null,
            null
        );
        callback.accept(successResolution);

        verify(messageService).send(player, "forge.confirm.complete");
        verify(scheduler, never()).runGlobal(any(), any());
        verify(player, never()).openInventory(any(Inventory.class));
    }

    @Test
    void confirmAndExecuteThrowsRestoresClaimedItemAndSettlesOnce() {
        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station1";

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        ItemStack inputItem = mock(ItemStack.class);
        when(inputItem.getType()).thenReturn(Material.DIAMOND);
        when(inputItem.clone()).thenReturn(inputItem);

        PlayerForgeState session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn(stationId);

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        context.tryInsert(inputItem);
        registry.replace(context);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(menuId, playerId, stationId);

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(holder);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(player.getOpenInventory()).thenReturn(view);

        ForgePlan plan = mock(ForgePlan.class);
        ForgePlanResult planResult = ForgePlanResult.ready(plan);
        when(forgeService.createPlan(any(Player.class), any(), any())).thenReturn(planResult);
        when(plan.isAffordable()).thenReturn(true);

        doThrow(new RuntimeException("forge service error"))
            .when(forgeService).confirmAndExecute(any(Player.class), any(), any(), any(), any());

        forgeMenuService.requestConfirm(player, holder);

        assertFalse(registry.getCurrent(playerId, menuId).isPresent());
        verify(settlementService, never()).settleOnlineOrQueue(any(ForgeMenuContext.class), eq(player));
        verify(settlementService, never()).settleOffline(any());
        verify(player).closeInventory();
    }
}
