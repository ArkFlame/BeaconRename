package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.forge.DeliveryService;
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
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeMenuInputServiceTest {
    private ForgeMenuRegistry registry;
    private ForgeMenuViewResolver viewResolver;
    private ForgeMenuService menuService;
    private ForgeItemPolicy itemPolicy;
    private DeliveryService delivery;
    private ForgeMenuInputService service;

    @BeforeEach
    void setUp() {
        registry = new ForgeMenuRegistry();
        viewResolver = new ForgeMenuViewResolver(registry);
        menuService = mock(ForgeMenuService.class);
        itemPolicy = mock(ForgeItemPolicy.class);
        delivery = mock(DeliveryService.class);
        MenuInputReturnService inputReturnService = new MenuInputReturnService(delivery);
        ForgeMenuSettlementService settlementService = new ForgeMenuSettlementService(inputReturnService);
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        when(scheduler.runEntity(any(Player.class), any(Runnable.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return mock(TaskHandle.class);
                });

        service = new ForgeMenuInputService(registry, viewResolver, menuService, itemPolicy,
                settlementService, scheduler, mock(MessageService.class));
    }

    @Test
    void insertRemoveAndCloseConserveOneInputItem() {
        Fixture fixture = fixture(2);
        when(menuService.rerender(fixture.player)).thenReturn(ForgeMenuService.MenuResult.opened(fixture.menuId));
        when(itemPolicy.checkItem(any(Player.class), eq(fixture.session), any(ItemStack.class)))
                .thenReturn(ForgeItemPolicy.PolicyResult.allow());
        when(delivery.generateDeliveryId(fixture.player, "menu_return")).thenReturn("menu-return");
        when(delivery.deliverItem(any(ItemStack.class), eq(fixture.player), isNull(), eq("menu-return")))
                .thenReturn(true);

        service.requestInsertOne(fixture.player, fixture.holder, fixture.sourceInventory, 5, fixture.sourceItem);

        assertEquals(1, fixture.sourceInventory.getItem(5).getAmount());
        assertEquals(1, fixture.context.peekInput().get().getAmount());

        service.handleInventoryClose(fixture.player, fixture.holder);

        ArgumentCaptor<ItemStack> returned = ArgumentCaptor.forClass(ItemStack.class);
        verify(delivery, times(1)).deliverItem(returned.capture(), eq(fixture.player), isNull(), eq("menu-return"));
        assertEquals(1, returned.getValue().getAmount());
        assertFalse(registry.get(fixture.playerId).isPresent());
    }

    @Test
    void rerenderOrReplacementFailureReturnsHeldInput() {
        Fixture fixture = fixture(2);
        when(menuService.rerender(fixture.player)).thenReturn(
                ForgeMenuService.MenuResult.renderFailed(fixture.menuId.toString(), "render failed"));
        when(itemPolicy.checkItem(any(Player.class), eq(fixture.session), any(ItemStack.class)))
                .thenReturn(ForgeItemPolicy.PolicyResult.allow());
        when(delivery.generateDeliveryId(fixture.player, "menu_return")).thenReturn("failed-return");
        when(delivery.deliverItem(any(ItemStack.class), eq(fixture.player), isNull(), eq("failed-return")))
                .thenReturn(true);

        service.requestInsertOne(fixture.player, fixture.holder, fixture.sourceInventory, 5, fixture.sourceItem);

        ArgumentCaptor<ItemStack> returned = ArgumentCaptor.forClass(ItemStack.class);
        verify(delivery, times(1)).deliverItem(returned.capture(), eq(fixture.player), isNull(), eq("failed-return"));
        assertEquals(Material.DIAMOND, returned.getValue().getType());
        assertEquals(1, returned.getValue().getAmount());
        assertFalse(registry.get(fixture.playerId).isPresent());
    }

    @Test
    void quitOrDisableReturnsOrQueuesHeldInput() {
        Fixture quitFixture = fixture(1);
        Fixture disableFixture = fixture(2);
        when(delivery.queuePendingDelivery(anyString(), any(UUID.class), any(ItemStack.class), isNull()))
                .thenReturn(true);

        assertTrue(quitFixture.context.tryInsert(quitFixture.sourceItem));
        assertTrue(disableFixture.context.tryInsert(disableFixture.sourceItem));

        service.handlePlayerQuit(quitFixture.player);
        service.shutdown();

        ArgumentCaptor<ItemStack> queued = ArgumentCaptor.forClass(ItemStack.class);
        verify(delivery, times(2)).queuePendingDelivery(anyString(), any(UUID.class), queued.capture(), isNull());
        List<Integer> amounts = Arrays.asList(queued.getAllValues().get(0).getAmount(),
                queued.getAllValues().get(1).getAmount());
        assertTrue(amounts.containsAll(Arrays.asList(1, 2)));
        assertFalse(registry.get(quitFixture.playerId).isPresent());
        assertFalse(registry.get(disableFixture.playerId).isPresent());
    }

    private Fixture fixture(int sourceAmount) {
        Fixture fixture = new Fixture();
        fixture.playerId = UUID.randomUUID();
        fixture.menuId = UUID.randomUUID();
        fixture.player = mock(Player.class);
        fixture.session = mock(PlayerForgeState.class);
        fixture.context = new ForgeMenuContext(fixture.menuId, fixture.playerId, "station",
                fixture.session, System.currentTimeMillis());
        registry.replace(fixture.context);
        fixture.holder = new ForgeInventoryHolder(fixture.menuId, fixture.playerId, "station");
        fixture.sourceItem = mockedItemStack(sourceAmount);

        Map<Integer, ItemStack> slots = new HashMap<>();
        slots.put(5, fixture.sourceItem);
        fixture.sourceInventory = mock(Inventory.class);
        when(fixture.sourceInventory.getSize()).thenReturn(36);
        when(fixture.sourceInventory.getItem(anyInt())).thenAnswer(invocation -> slots.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            slots.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(fixture.sourceInventory).setItem(anyInt(), any(ItemStack.class));

        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(fixture.holder);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(view.getBottomInventory()).thenReturn(fixture.sourceInventory);
        when(fixture.player.getOpenInventory()).thenReturn(view);
        when(fixture.player.getUniqueId()).thenReturn(fixture.playerId);
        when(fixture.player.isOnline()).thenReturn(true);
        return fixture;
    }

    private ItemStack mockedItemStack(int initialAmount) {
        ItemStack item = mock(ItemStack.class);
        int[] amount = {initialAmount};
        when(item.getType()).thenReturn(Material.DIAMOND);
        when(item.getAmount()).thenAnswer(invocation -> amount[0]);
        doAnswer(invocation -> {
            amount[0] = invocation.getArgument(0);
            return null;
        }).when(item).setAmount(anyInt());
        when(item.isSimilar(any(ItemStack.class))).thenReturn(true);
        when(item.clone()).thenAnswer(invocation -> mockedItemStack(amount[0]));
        return item;
    }

    private static final class Fixture {
        private UUID playerId;
        private UUID menuId;
        private Player player;
        private PlayerForgeState session;
        private ForgeMenuContext context;
        private ForgeInventoryHolder holder;
        private Inventory sourceInventory;
        private ItemStack sourceItem;
    }
}
