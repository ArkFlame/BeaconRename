package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.item.ItemIdentityService;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class ForgeAnvilListenerTest {

    private static final class Harness {
        final ItemIdentityService identity = mock(ItemIdentityService.class);
        final ForgeAnvilListener listener = new ForgeAnvilListener(identity);
        final PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        final AnvilInventory inventory = mock(AnvilInventory.class);
        final ItemStack left = mock(ItemStack.class);
        final ItemStack vanilla = mock(ItemStack.class);
        final ItemStack clone = mock(ItemStack.class);
        final ItemMeta clonedMeta = mock(ItemMeta.class, withSettings().extraInterfaces(Repairable.class));
        final ItemMeta vanillaMeta = mock(ItemMeta.class);

        Harness() {
            when(event.getInventory()).thenReturn(inventory);
            when(event.getResult()).thenReturn(vanilla);
            when(vanilla.getItemMeta()).thenReturn(vanillaMeta);
            when(left.hasItemMeta()).thenReturn(true);
            when(left.clone()).thenReturn(clone);
            when(clone.getItemMeta()).thenReturn(clonedMeta);
            when(identity.hasForgeIdentityMarker(left)).thenReturn(true);
            when(inventory.getItem(0)).thenReturn(left);
        }
    }

    @Test
    void pureRenameOfForgedItemKeepsOriginalStackAndOnlyChangesName() {
        Harness h = new Harness();
        when(h.inventory.getItem(1)).thenReturn(null);
        when(h.vanillaMeta.hasDisplayName()).thenReturn(true);
        when(h.vanillaMeta.getDisplayName()).thenReturn("Renamed");

        h.listener.onPrepareAnvil(h.event);

        // Result is built from the original stack, never from the vanilla result.
        verify(h.left).clone();
        verify(h.vanilla, never()).clone();
        verify(h.vanilla, never()).setItemMeta(any());
        // Only the display name is taken from vanilla; no enchant/lore/PDC writes.
        verify(h.clonedMeta).setDisplayName("Renamed");
        verify(h.clonedMeta, never()).setLore(any());
        verify(h.clonedMeta, never()).addEnchant(any(), anyInt(), anyBoolean());
        verify(h.clonedMeta, never()).addItemFlags(any());
        verify(h.clone).setItemMeta(h.clonedMeta);
        verify(h.event).setResult(h.clone);
    }

    @Test
    void renameToEmptyClearsNameWithoutTouchingOtherData() {
        Harness h = new Harness();
        when(h.inventory.getItem(1)).thenReturn(new ItemStack(org.bukkit.Material.AIR));
        when(h.vanillaMeta.hasDisplayName()).thenReturn(false);

        h.listener.onPrepareAnvil(h.event);

        verify(h.clonedMeta).setDisplayName(null);
        verify(h.clonedMeta, never()).setLore(any());
        verify(h.event).setResult(h.clone);
    }

    @Test
    void repairCostIsCopiedFromVanillaResult() {
        Harness h = new Harness();
        when(h.inventory.getItem(1)).thenReturn(null);
        when(h.vanillaMeta.hasDisplayName()).thenReturn(false);
        ItemMeta vanillaRepairable = mock(ItemMeta.class, withSettings().extraInterfaces(Repairable.class));
        when(h.vanilla.getItemMeta()).thenReturn(vanillaRepairable);
        when(((Repairable) vanillaRepairable).getRepairCost()).thenReturn(7);

        h.listener.onPrepareAnvil(h.event);

        verify((Repairable) h.clonedMeta).setRepairCost(7);
        verify(h.event).setResult(h.clone);
    }

    @Test
    void nonForgedItemIsLeftUntouched() {
        Harness h = new Harness();
        when(h.identity.hasForgeIdentityMarker(h.left)).thenReturn(false);
        when(h.inventory.getItem(1)).thenReturn(null);

        h.listener.onPrepareAnvil(h.event);

        verify(h.event, never()).setResult(any());
        verify(h.left, never()).clone();
    }

    @Test
    void twoSlotCombinationIsLeftToVanilla() {
        Harness h = new Harness();
        ItemStack book = mock(ItemStack.class);
        when(book.getType()).thenReturn(org.bukkit.Material.ENCHANTED_BOOK);
        when(h.inventory.getItem(1)).thenReturn(book);

        h.listener.onPrepareAnvil(h.event);

        verify(h.event, never()).setResult(any());
        verify(h.left, never()).clone();
    }

    @Test
    void missingResultOrMetaIsIgnored() {
        Harness h = new Harness();
        when(h.inventory.getItem(1)).thenReturn(null);
        when(h.clone.getItemMeta()).thenReturn(null);

        h.listener.onPrepareAnvil(h.event);

        verify(h.event, never()).setResult(any());
    }
}
