package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.forge.ForgePlan;
import com.arkflame.flameforge.forge.ForgePlanResult;
import com.arkflame.flameforge.forge.ForgeService;
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
    private ForgeMenuForgeService service;

    @BeforeEach
    void setUp() {
        registry = new ForgeMenuRegistry();
        viewResolver = new ForgeMenuViewResolver(registry);
        forgeService = mock(ForgeService.class);
        settlementService = mock(ForgeMenuSettlementService.class);
        menuService = mock(ForgeMenuService.class);
        scheduler = mock(SchedulerBridge.class);
        messageService = mock(MessageService.class);

        when(scheduler.runEntity(any(Player.class), any(Runnable.class), any(Runnable.class)))
                .thenAnswer(invocation -> {
                    Runnable task = invocation.getArgument(1);
                    task.run();
                    return mock(TaskHandle.class);
                });

        service = new ForgeMenuForgeService(registry, viewResolver, forgeService, settlementService,
                menuService, scheduler, messageService, Logger.getLogger("ForgeMenuForgeServiceTest"));
    }

    @Test
    void invalidConfirmKeepsInputAndDoesNotCharge() {
        Fixture fixture = fixture(new ItemStack(Material.DIAMOND, 1));
        when(forgeService.createPlan(eq(fixture.player), eq(fixture.session), any(ItemStack.class)))
                .thenReturn(ForgePlanResult.nextTierMissing());

        service.requestConfirm(fixture.player, fixture.holder);

        assertTrue(registry.getCurrent(fixture.playerId, fixture.menuId).isPresent());
        assertEquals(Material.DIAMOND, registry.getCurrent(fixture.playerId, fixture.menuId)
                .get().peekInput().get().getType());
        verify(forgeService, never()).confirmAndExecute(any(Player.class), any(PlayerForgeState.class),
                any(ItemStack.class), any(ForgePlan.class), any());
        verify(messageService, atLeastOnce()).send(fixture.player, "menu.item-denied.no-tier");
    }

    @Test
    void validConfirmClaimsAndSubmitsOnce() {
        Fixture fixture = fixture(new ItemStack(Material.DIAMOND, 1));
        ForgePlan plan = mock(ForgePlan.class);
        when(plan.isAffordable()).thenReturn(true);
        when(forgeService.createPlan(eq(fixture.player), eq(fixture.session), any(ItemStack.class)))
                .thenReturn(ForgePlanResult.ready(plan));

        service.requestConfirm(fixture.player, fixture.holder);
        service.requestConfirm(fixture.player, fixture.holder);

        assertFalse(registry.getCurrent(fixture.playerId, fixture.menuId).isPresent());
        assertTrue(fixture.context.isForging());
        verify(forgeService, times(1)).confirmAndExecute(eq(fixture.player), eq(fixture.session),
                any(ItemStack.class), eq(plan), any());
        verify(settlementService, never()).settleOnlineOrQueue(any(ForgeMenuContext.class), any(Player.class));
    }

    @Test
    void submissionFailureReturnsInputAndReportsFailure() {
        Fixture fixture = fixture(new ItemStack(Material.DIAMOND, 1));
        ForgePlan plan = mock(ForgePlan.class);
        when(plan.isAffordable()).thenReturn(true);
        when(forgeService.createPlan(eq(fixture.player), eq(fixture.session), any(ItemStack.class)))
                .thenReturn(ForgePlanResult.ready(plan));
        doThrow(new IllegalStateException("submission failed")).when(forgeService)
                .confirmAndExecute(eq(fixture.player), eq(fixture.session), any(ItemStack.class), eq(plan), any());

        service.requestConfirm(fixture.player, fixture.holder);

        assertFalse(registry.getCurrent(fixture.playerId, fixture.menuId).isPresent());
        assertTrue(fixture.context.isRetired());
        assertEquals(Material.DIAMOND, fixture.context.peekInput().get().getType());
        verify(forgeService, times(1)).confirmAndExecute(eq(fixture.player), eq(fixture.session),
                any(ItemStack.class), eq(plan), any());
        verify(settlementService, atLeastOnce()).settleOnlineOrQueue(fixture.context, fixture.player);
        verify(messageService, atLeastOnce()).send(fixture.player, "menu.forge-start-failed");
    }

    private Fixture fixture(ItemStack input) {
        Fixture fixture = new Fixture();
        fixture.playerId = UUID.randomUUID();
        fixture.menuId = UUID.randomUUID();
        fixture.player = mock(Player.class);
        fixture.session = mock(PlayerForgeState.class);
        fixture.holder = new ForgeInventoryHolder(fixture.menuId, fixture.playerId, "station");
        fixture.context = new ForgeMenuContext(fixture.menuId, fixture.playerId, "station",
                fixture.session, System.currentTimeMillis());
        fixture.context.tryInsert(input);
        registry.replace(fixture.context);

        when(fixture.player.getUniqueId()).thenReturn(fixture.playerId);
        when(fixture.player.isOnline()).thenReturn(true);
        Inventory top = mock(Inventory.class);
        when(top.getHolder()).thenReturn(fixture.holder);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(top);
        when(fixture.player.getOpenInventory()).thenReturn(view);
        return fixture;
    }

    private static final class Fixture {
        private UUID playerId;
        private UUID menuId;
        private Player player;
        private PlayerForgeState session;
        private ForgeInventoryHolder holder;
        private ForgeMenuContext context;
    }
}
