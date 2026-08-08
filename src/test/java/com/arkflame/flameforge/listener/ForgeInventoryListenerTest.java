package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.menu.ForgeInventoryHolder;
import com.arkflame.flameforge.menu.ForgeMenuForgeService;
import com.arkflame.flameforge.menu.ForgeMenuInputService;
import com.arkflame.flameforge.menu.ForgeMenuViewResolver;
import com.arkflame.flameforge.menu.MenuLayout;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ForgeInventoryListenerTest {

    private ForgeMenuViewResolver viewResolver;
    private ForgeMenuInputService inputService;
    private ForgeMenuForgeService forgeService;
    private ForgeInventoryListener listener;

    private Player player;
    private Inventory topInventory;
    private Inventory bottomInventory;
    private InventoryView view;
    private ForgeInventoryHolder holder;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        viewResolver = mock(ForgeMenuViewResolver.class);
        inputService = mock(ForgeMenuInputService.class);
        forgeService = mock(ForgeMenuForgeService.class);

        listener = new ForgeInventoryListener(viewResolver, inputService, forgeService);

        player = mock(Player.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        topInventory = mock(Inventory.class);
        bottomInventory = mock(Inventory.class);
        view = mock(InventoryView.class);
        holder = new ForgeInventoryHolder(UUID.randomUUID(), playerId, "station");

        when(topInventory.getHolder()).thenReturn(holder);
        when(topInventory.getSize()).thenReturn(54);
        when(bottomInventory.getHolder()).thenReturn(null);
        when(bottomInventory.getType()).thenReturn(InventoryType.PLAYER);

        when(view.getTopInventory()).thenReturn(topInventory);
        when(view.getBottomInventory()).thenReturn(bottomInventory);
        when(player.getOpenInventory()).thenReturn(view);
    }

    @Test
    void normalPlayerInventoryClickIsUntouched() {
        ForgeMenuViewResolver.ResolvedView notForgeView = mockResolvedView(ForgeMenuViewResolver.Status.NOT_FORGE, null, null, null);
        when(viewResolver.resolve(any(Player.class), any(InventoryView.class))).thenReturn(notForgeView);

        InventoryClickEvent event = createClickEvent(bottomInventory, 0, ClickType.LEFT);

        listener.onInventoryClick(event);

        verify(inputService, never()).requestInsertOne(any(), any(), any(), anyInt(), any());
        verify(inputService, never()).requestReturnInput(any(), any());
        verify(forgeService, never()).requestConfirm(any(), any());
    }

    @Test
    void normalPlayerInventoryDragIsUntouched() {
        ForgeMenuViewResolver.ResolvedView notForgeView = mockResolvedView(ForgeMenuViewResolver.Status.NOT_FORGE, null, null, null);
        when(viewResolver.resolve(any(Player.class), any(InventoryView.class))).thenReturn(notForgeView);

        InventoryDragEvent event = mockDragEvent(bottomInventory, Collections.singleton(0));

        listener.onInventoryDrag(event);

        verify(inputService, never()).requestCloseStaleView(any(), any());
    }

    @Test
    void currentForgeBottomLeftClickCancelsAndRoutesInsert() {
        ForgeMenuViewResolver.ResolvedView currentView = mockResolvedView(ForgeMenuViewResolver.Status.CURRENT, holder, topInventory, bottomInventory);
        when(viewResolver.resolve(any(Player.class), any(InventoryView.class))).thenReturn(currentView);

        ItemStack currentItem = new ItemStack(org.bukkit.Material.DIAMOND);
        InventoryClickEvent event = createBottomClickEvent(currentItem, ClickType.LEFT, true);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(inputService).requestInsertOne(eq(player), eq(holder), eq(bottomInventory), eq(0), eq(currentItem));
    }

    @Test
    void currentForgeBottomShiftClickCancelsAndRoutesInsert() {
        ForgeMenuViewResolver.ResolvedView currentView = mockResolvedView(ForgeMenuViewResolver.Status.CURRENT, holder, topInventory, bottomInventory);
        when(viewResolver.resolve(any(Player.class), any(InventoryView.class))).thenReturn(currentView);

        ItemStack currentItem = new ItemStack(org.bukkit.Material.DIAMOND);
        InventoryClickEvent event = createBottomShiftClickEvent(currentItem);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(inputService).requestInsertOne(eq(player), eq(holder), eq(bottomInventory), eq(0), eq(currentItem));
    }

    @Test
    void currentForgeTopInputAndConfirmRouteToDedicatedServices() {
        ForgeMenuViewResolver.ResolvedView currentView = mockResolvedView(ForgeMenuViewResolver.Status.CURRENT, holder, topInventory, bottomInventory);
        when(viewResolver.resolve(any(Player.class), any(InventoryView.class))).thenReturn(currentView);

        InventoryClickEvent inputEvent = createClickEvent(topInventory, MenuLayout.SLOT_INPUT, ClickType.LEFT);
        listener.onInventoryClick(inputEvent);
        verify(inputEvent).setCancelled(true);
        verify(inputService).requestReturnInput(eq(player), eq(holder));
        verify(forgeService, never()).requestConfirm(any(), any());

        reset(inputService);

        InventoryClickEvent confirmEvent = createClickEvent(topInventory, MenuLayout.SLOT_CONFIRM, ClickType.LEFT);
        listener.onInventoryClick(confirmEvent);
        verify(confirmEvent).setCancelled(true);
        verify(forgeService).requestConfirm(eq(player), eq(holder));
    }

    @Test
    void currentForgeBottomOnlyDragRemainsAllowed() {
        ForgeMenuViewResolver.ResolvedView currentView = mockResolvedView(ForgeMenuViewResolver.Status.CURRENT, holder, topInventory, bottomInventory);
        when(viewResolver.resolve(any(Player.class), any(InventoryView.class))).thenReturn(currentView);

        InventoryDragEvent event = mockDragEvent(topInventory, Collections.singleton(54));

        listener.onInventoryDrag(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void currentForgeDragTouchingTopIsCancelled() {
        ForgeMenuViewResolver.ResolvedView currentView = mockResolvedView(ForgeMenuViewResolver.Status.CURRENT, holder, topInventory, bottomInventory);
        when(viewResolver.resolve(any(Player.class), any(InventoryView.class))).thenReturn(currentView);

        InventoryDragEvent event = mockDragEvent(topInventory, Collections.singleton(22));

        listener.onInventoryDrag(event);

        verify(event).setCancelled(true);
        verify(inputService, never()).requestCloseStaleView(any(), any());
    }

    @Test
    void staleForgeViewCancelsAndRequestsClose() {
        ForgeMenuViewResolver.ResolvedView staleView = mockResolvedView(ForgeMenuViewResolver.Status.STALE, holder, topInventory, bottomInventory);
        when(viewResolver.resolve(any(Player.class), any(InventoryView.class))).thenReturn(staleView);

        InventoryClickEvent event = createClickEvent(topInventory, 22, ClickType.LEFT);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(inputService).requestCloseStaleView(eq(player), eq(holder));
    }

    @Test
    void forgeCloseEventRoutesExactHolder() {
        ForgeMenuViewResolver.ResolvedView currentView = mockResolvedView(ForgeMenuViewResolver.Status.CURRENT, holder, topInventory, bottomInventory);
        when(viewResolver.resolve(any(Player.class), any(InventoryView.class))).thenReturn(currentView);

        InventoryCloseEvent closeEvent = mock(InventoryCloseEvent.class);
        when(closeEvent.getInventory()).thenReturn(topInventory);
        when(closeEvent.getPlayer()).thenReturn(player);

        listener.onInventoryClose(closeEvent);

        verify(inputService).handleInventoryClose(eq(player), eq(holder));
    }

    @Test
    void crossInventoryMoveToOtherRoutesInsert() {
        ForgeMenuViewResolver.ResolvedView currentView = mockResolvedView(ForgeMenuViewResolver.Status.CURRENT, holder, topInventory, bottomInventory);
        when(viewResolver.resolve(any(Player.class), any(InventoryView.class))).thenReturn(currentView);

        ItemStack currentItem = new ItemStack(org.bukkit.Material.DIAMOND);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClickedInventory()).thenReturn(bottomInventory);
        when(event.getInventory()).thenReturn(bottomInventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(0);
        when(event.getSlot()).thenReturn(0);
        when(event.getClick()).thenReturn(ClickType.SHIFT_LEFT);
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.getCurrentItem()).thenReturn(currentItem);
        when(event.getCursor()).thenReturn(new ItemStack(org.bukkit.Material.AIR));
        when(event.getView()).thenReturn(view);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(inputService).requestInsertOne(eq(player), eq(holder), eq(bottomInventory), eq(0), eq(currentItem));
    }

    private ForgeMenuViewResolver.ResolvedView mockResolvedView(ForgeMenuViewResolver.Status status, ForgeInventoryHolder h, Inventory top, Inventory bottom) {
        ForgeMenuViewResolver.ResolvedView resolvedView = mock(ForgeMenuViewResolver.ResolvedView.class);
        lenient().when(resolvedView.getStatus()).thenReturn(status);
        lenient().when(resolvedView.getHolder()).thenReturn(h);
        lenient().when(resolvedView.getTopInventory()).thenReturn(top);
        lenient().when(resolvedView.getBottomInventory()).thenReturn(bottom);
        return resolvedView;
    }

    private InventoryClickEvent createClickEvent(Inventory clickedInventory, int rawSlot, ClickType clickType) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClickedInventory()).thenReturn(clickedInventory);
        when(event.getInventory()).thenReturn(clickedInventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getSlot()).thenReturn(rawSlot);
        when(event.getClick()).thenReturn(clickType);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.getCurrentItem()).thenReturn(new ItemStack(org.bukkit.Material.DIAMOND));
        when(event.getCursor()).thenReturn(new ItemStack(org.bukkit.Material.AIR));
        when(event.getView()).thenReturn(view);
        return event;
    }

    private InventoryClickEvent createBottomClickEvent(ItemStack currentItem, ClickType clickType, boolean emptyCursor) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClickedInventory()).thenReturn(bottomInventory);
        when(event.getInventory()).thenReturn(bottomInventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(0);
        when(event.getSlot()).thenReturn(0);
        when(event.getClick()).thenReturn(clickType);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.getCurrentItem()).thenReturn(currentItem);
        when(event.getCursor()).thenReturn(emptyCursor ? new ItemStack(org.bukkit.Material.AIR) : new ItemStack(org.bukkit.Material.DIAMOND));
        when(event.getView()).thenReturn(view);
        return event;
    }

    private InventoryClickEvent createBottomShiftClickEvent(ItemStack currentItem) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClickedInventory()).thenReturn(bottomInventory);
        when(event.getInventory()).thenReturn(bottomInventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(0);
        when(event.getSlot()).thenReturn(0);
        when(event.getClick()).thenReturn(ClickType.SHIFT_LEFT);
        when(event.getAction()).thenReturn(InventoryAction.MOVE_TO_OTHER_INVENTORY);
        when(event.getCurrentItem()).thenReturn(currentItem);
        when(event.getCursor()).thenReturn(new ItemStack(org.bukkit.Material.AIR));
        when(event.getView()).thenReturn(view);
        return event;
    }

    private InventoryDragEvent mockDragEvent(Inventory inventory, java.util.Set<Integer> rawSlots) {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getRawSlots()).thenReturn(rawSlots);
        return event;
    }
}
