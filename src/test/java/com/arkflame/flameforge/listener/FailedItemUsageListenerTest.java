package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import org.bukkit.Material;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FailedItemUsageListenerTest {

    private final ItemIdentityService identity = mock(ItemIdentityService.class);
    private final FailedItemUsageListener listener = new FailedItemUsageListener(identity);

    private ItemStack failedItem(String variantId) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(identity.readForgeIdentity(item)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
            ItemIdentityService.ForgeIdentityStatus.VALID,
            ItemIdentityCodec.Identity.empty().withFailureVariantId(variantId)));
        return item;
    }

    private ItemStack plainItem() {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.DIAMOND_SWORD);
        return item;
    }

    @Test
    void craftingWithFailedIngredientClearsResultAndCancels() {
        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        CraftingInventory inventory = mock(CraftingInventory.class);
        when(event.getInventory()).thenReturn(inventory);
        ItemStack failed = failedItem("break");
        ItemStack plain = plainItem();
        when(inventory.getMatrix()).thenReturn(new ItemStack[]{failed, plain});

        listener.onPrepareCraft(event);

        verify(inventory).setResult(null);
    }

    @Test
    void craftingWithoutFailedIngredientIsUntouched() {
        PrepareItemCraftEvent event = mock(PrepareItemCraftEvent.class);
        CraftingInventory inventory = mock(CraftingInventory.class);
        when(event.getInventory()).thenReturn(inventory);
        ItemStack plain = plainItem();
        when(inventory.getMatrix()).thenReturn(new ItemStack[]{plain, null});

        listener.onPrepareCraft(event);

        verify(inventory, never()).setResult(null);
    }

    @Test
    void anvilCombineWithFailedIngredientClearsResult() {
        PrepareAnvilEvent event = mock(PrepareAnvilEvent.class);
        AnvilInventory inventory = mock(AnvilInventory.class);
        when(event.getInventory()).thenReturn(inventory);
        ItemStack plain = plainItem();
        ItemStack failed = failedItem("curse");
        when(inventory.getItem(0)).thenReturn(plain);
        when(inventory.getItem(1)).thenReturn(failed);

        listener.onPrepareAnvil(event);

        verify(event).setResult(null);
    }

    @Test
    void furnaceSmeltingFailedSourceIsCancelled() {
        FurnaceSmeltEvent event = mock(FurnaceSmeltEvent.class);
        ItemStack failed = failedItem("shatter");
        when(event.getSource()).thenReturn(failed);

        listener.onFurnaceSmelt(event);

        verify(event).setResult(null);
        verify(event).setCancelled(true);
    }
}
