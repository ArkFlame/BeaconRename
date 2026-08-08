package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.model.PlayerForgeState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeMenuViewResolverTest {

    @Test
    void normalPlayerInventoryResolvesNotForge() {
        ForgeMenuRegistry registry = new ForgeMenuRegistry();
        ForgeMenuViewResolver resolver = new ForgeMenuViewResolver(registry);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);

        Inventory bottomInventory = mock(Inventory.class);
        when(bottomInventory.getHolder()).thenReturn(null);

        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(mock(Inventory.class));
        when(view.getBottomInventory()).thenReturn(bottomInventory);

        ForgeMenuViewResolver.ResolvedView result = resolver.resolve(player, view);

        assertEquals(ForgeMenuViewResolver.Status.NOT_FORGE, result.getStatus());
        assertNotNull(result.getBottomInventory());
        assertNull(result.getTopInventory());
    }

    @Test
    void exactTopHolderAndRegistryContextResolveCurrent() {
        ForgeMenuRegistry registry = new ForgeMenuRegistry();
        ForgeMenuViewResolver resolver = new ForgeMenuViewResolver(registry);

        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station-1";
        PlayerForgeState session = PlayerForgeState.of(playerId.toString());

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        registry.replace(context);

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(menuId, playerId, stationId);
        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(holder);
        when(topInventory.getHolder()).thenReturn(holder);

        Inventory bottomInventory = mock(Inventory.class);

        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(view.getBottomInventory()).thenReturn(bottomInventory);
        when(player.getOpenInventory()).thenReturn(view);

        ForgeMenuViewResolver.ResolvedView result = resolver.resolve(player, view);

        assertEquals(ForgeMenuViewResolver.Status.CURRENT, result.getStatus());
        assertTrue(resolver.isStillCurrent(player, holder));
    }

    @Test
    void foreignOrStaleHolderResolvesStale() {
        ForgeMenuRegistry registry = new ForgeMenuRegistry();
        ForgeMenuViewResolver resolver = new ForgeMenuViewResolver(registry);

        UUID playerId = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        String stationId = "station-1";
        PlayerForgeState session = PlayerForgeState.of(playerId.toString());

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(menuId, playerId, stationId);
        Inventory topInventory = mock(Inventory.class);
        when(topInventory.getHolder()).thenReturn(holder);

        Inventory bottomInventory = mock(Inventory.class);

        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(view.getBottomInventory()).thenReturn(bottomInventory);

        ForgeMenuViewResolver.ResolvedView caseA = resolver.resolve(player, view);
        assertEquals(ForgeMenuViewResolver.Status.STALE, caseA.getStatus());

        ForgeMenuContext context = new ForgeMenuContext(menuId, playerId, stationId, session, System.currentTimeMillis());
        registry.replace(context);

        ForgeInventoryHolder foreignHolder = new ForgeInventoryHolder(menuId, UUID.randomUUID(), stationId);
        when(topInventory.getHolder()).thenReturn(foreignHolder);

        ForgeMenuViewResolver.ResolvedView caseB = resolver.resolve(player, view);
        assertEquals(ForgeMenuViewResolver.Status.STALE, caseB.getStatus());

        ForgeInventoryHolder mismatchedStation = new ForgeInventoryHolder(menuId, playerId, "different-station");
        when(topInventory.getHolder()).thenReturn(mismatchedStation);

        ForgeMenuViewResolver.ResolvedView caseC = resolver.resolve(player, view);
        assertEquals(ForgeMenuViewResolver.Status.STALE, caseC.getStatus());
    }
}
