package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.menu.WeaponsMenuHolder;
import com.arkflame.flameforge.menu.WeaponsMenuService;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeaponsMenuListenerTest {

    private WeaponsMenuService service;
    private Player player;
    private WeaponsMenuHolder holder;
    private Inventory top;
    private Inventory bottom;
    private InventoryView view;
    private WeaponsMenuListener listener;

    @BeforeEach
    void setUp() {
        service = mock(WeaponsMenuService.class);
        listener = new WeaponsMenuListener(service);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        holder = new WeaponsMenuHolder(player.getUniqueId(), 0);
        top = mock(Inventory.class);
        bottom = mock(Inventory.class);
        when(top.getHolder()).thenReturn(holder);
        view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(bottom);
    }

    private InventoryClickEvent click(Inventory clickedInventory, int rawSlot,
                                      ClickType clickType, InventoryAction action) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.isCancelled()).thenReturn(false);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(clickedInventory);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(clickType);
        when(event.getAction()).thenReturn(action);
        return event;
    }

    @Test
    void ordinaryTopPreviewClickIsCancelledAndDelegatedOnce() {
        InventoryClickEvent event = click(top, 10, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(service).handleClick(player, 0, 10);
    }

    @Test
    void bottomInventoryClickIsCancelledWithoutDelegatingGrant() {
        InventoryClickEvent event = click(bottom, 40, ClickType.LEFT, InventoryAction.PICKUP_ALL);

        listener.onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(service, never()).handleClick(any(), anyInt(), anyInt());
    }

    @Test
    void shiftNumberDoubleAndCollectClicksWhileHolderOpenAreCancelled() {
        InventoryClickEvent shift = click(bottom, 40, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        InventoryClickEvent number = click(bottom, 40, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP);
        InventoryClickEvent doubleClick = click(bottom, 40, ClickType.DOUBLE_CLICK, InventoryAction.PICKUP_ALL);
        InventoryClickEvent collect = click(bottom, 40, ClickType.LEFT, InventoryAction.COLLECT_TO_CURSOR);

        listener.onInventoryClick(shift);
        listener.onInventoryClick(number);
        listener.onInventoryClick(doubleClick);
        listener.onInventoryClick(collect);

        verify(shift).setCancelled(true);
        verify(number).setCancelled(true);
        verify(doubleClick).setCancelled(true);
        verify(collect).setCancelled(true);
        verify(service, never()).handleClick(any(), anyInt(), anyInt());
    }

    @Test
    void dragWhileHolderOpenIsCancelled() {
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.isCancelled()).thenReturn(false);
        when(drag.getView()).thenReturn(view);

        listener.onInventoryDrag(drag);

        verify(drag).setCancelled(true);
    }

    @Test
    void unrelatedInventoryHolderIsUntouched() {
        Inventory unrelatedTop = mock(Inventory.class);
        when(unrelatedTop.getHolder()).thenReturn(mock(InventoryHolder.class));
        InventoryView otherView = mock(InventoryView.class);
        when(otherView.getTopInventory()).thenReturn(unrelatedTop);

        InventoryClickEvent clickEvent = mock(InventoryClickEvent.class);
        when(clickEvent.isCancelled()).thenReturn(false);
        when(clickEvent.getView()).thenReturn(otherView);
        listener.onInventoryClick(clickEvent);
        verify(clickEvent, never()).setCancelled(true);

        InventoryDragEvent dragEvent = mock(InventoryDragEvent.class);
        when(dragEvent.isCancelled()).thenReturn(false);
        when(dragEvent.getView()).thenReturn(otherView);
        listener.onInventoryDrag(dragEvent);
        verify(dragEvent, never()).setCancelled(true);

        verify(service, never()).handleClick(any(), anyInt(), anyInt());
    }

    @Test
    void alreadyCancelledClickIsNotReprocessed() {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.isCancelled()).thenReturn(true);
        when(event.getView()).thenReturn(view);

        listener.onInventoryClick(event);

        verify(service, never()).handleClick(any(), anyInt(), anyInt());
    }
}
