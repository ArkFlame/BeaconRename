package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.menu.ForgeInventoryHolder;
import com.arkflame.flameforge.menu.ForgeMenuForgeService;
import com.arkflame.flameforge.menu.ForgeMenuInputService;
import com.arkflame.flameforge.menu.ForgeMenuViewResolver;
import com.arkflame.flameforge.menu.MenuLayout;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForgeInventoryListenerTest {

    @Test
    void forgeInventoryRoutesInputConfirmAndCloseWithoutLosingItem() {
        ForgeMenuViewResolver viewResolver = mock(ForgeMenuViewResolver.class);
        ForgeMenuInputService inputService = mock(ForgeMenuInputService.class);
        ForgeMenuForgeService forgeService = mock(ForgeMenuForgeService.class);
        ForgeInventoryListener listener = new ForgeInventoryListener(
            viewResolver, inputService, forgeService
        );

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        ForgeInventoryHolder holder = new ForgeInventoryHolder(
            UUID.randomUUID(), playerId, "station"
        );
        Inventory topInventory = mock(Inventory.class);
        Inventory bottomInventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(view.getBottomInventory()).thenReturn(bottomInventory);

        ForgeMenuViewResolver.ResolvedView current = resolved(
            ForgeMenuViewResolver.Status.CURRENT, holder, topInventory, bottomInventory
        );
        when(viewResolver.resolve(player, view)).thenReturn(current);

        ItemStack item = new ItemStack(Material.DIAMOND, 2);
        InventoryClickEvent inputClick = click(
            topInventory, view, player, MenuLayout.SLOT_INPUT, item
        );
        InventoryClickEvent confirmClick = click(
            topInventory, view, player, MenuLayout.SLOT_CONFIRM, item
        );
        InventoryClickEvent itemClick = click(bottomInventory, view, player, 4, item);
        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getInventory()).thenReturn(topInventory);
        when(close.getPlayer()).thenReturn(player);
        when(topInventory.getHolder()).thenReturn(holder);

        listener.onInventoryClick(inputClick);
        listener.onInventoryClick(confirmClick);
        listener.onInventoryClick(itemClick);
        listener.onInventoryClose(close);

        verify(inputService).requestReturnInput(player, holder);
        verify(forgeService).requestConfirm(player, holder);
        verify(inputService).requestInsertOne(player, holder, bottomInventory, 4, item);
        verify(inputService).handleInventoryClose(player, holder);
    }

    @Test
    void staleOrNonForgeInventoryCannotMutateCurrentForge() {
        ForgeMenuViewResolver viewResolver = mock(ForgeMenuViewResolver.class);
        ForgeMenuInputService inputService = mock(ForgeMenuInputService.class);
        ForgeMenuForgeService forgeService = mock(ForgeMenuForgeService.class);
        ForgeInventoryListener listener = new ForgeInventoryListener(
            viewResolver, inputService, forgeService
        );

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        ForgeInventoryHolder staleHolder = new ForgeInventoryHolder(
            UUID.randomUUID(), playerId, "station"
        );
        Inventory topInventory = mock(Inventory.class);
        Inventory bottomInventory = mock(Inventory.class);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(view.getBottomInventory()).thenReturn(bottomInventory);

        ForgeMenuViewResolver.ResolvedView stale = resolved(
            ForgeMenuViewResolver.Status.STALE, staleHolder, topInventory, bottomInventory
        );
        ForgeMenuViewResolver.ResolvedView notForge = resolved(
            ForgeMenuViewResolver.Status.NOT_FORGE, null, null, bottomInventory
        );

        ItemStack item = new ItemStack(Material.DIAMOND);
        when(viewResolver.resolve(player, view)).thenReturn(stale);
        InventoryClickEvent staleClick = click(topInventory, view, player, 4, item);
        listener.onInventoryClick(staleClick);

        when(viewResolver.resolve(player, view)).thenReturn(notForge);
        InventoryClickEvent normalClick = click(bottomInventory, view, player, 4, item);
        listener.onInventoryClick(normalClick);

        when(topInventory.getHolder()).thenReturn(null);
        InventoryCloseEvent normalClose = mock(InventoryCloseEvent.class);
        when(normalClose.getInventory()).thenReturn(topInventory);
        when(normalClose.getPlayer()).thenReturn(player);
        listener.onInventoryClose(normalClose);

        verify(inputService).requestCloseStaleView(player, staleHolder);
        verify(inputService, never()).requestInsertOne(any(), any(), any(), anyInt(), any());
        verify(inputService, never()).requestReturnInput(any(), any());
        verify(inputService, never()).handleInventoryClose(any(), any());
        verify(forgeService, never()).requestConfirm(any(), any());
    }

    private ForgeMenuViewResolver.ResolvedView resolved(ForgeMenuViewResolver.Status status,
                                                        ForgeInventoryHolder holder,
                                                        Inventory topInventory,
                                                        Inventory bottomInventory) {
        ForgeMenuViewResolver.ResolvedView view = mock(ForgeMenuViewResolver.ResolvedView.class);
        when(view.getStatus()).thenReturn(status);
        when(view.getHolder()).thenReturn(holder);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(view.getBottomInventory()).thenReturn(bottomInventory);
        return view;
    }

    private InventoryClickEvent click(Inventory clickedInventory, InventoryView view,
                                      Player player, int rawSlot, ItemStack item) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getClickedInventory()).thenReturn(clickedInventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getSlot()).thenReturn(rawSlot);
        when(event.getClick()).thenReturn(ClickType.LEFT);
        when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
        when(event.getCurrentItem()).thenReturn(item);
        when(event.getCursor()).thenReturn(new ItemStack(Material.AIR));
        when(event.getView()).thenReturn(view);
        return event;
    }
}
