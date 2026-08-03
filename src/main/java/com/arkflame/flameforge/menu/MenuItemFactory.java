package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.text.TextRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MenuItemFactory {
    private final MaterialResolver materialResolver;
    private final TextRenderer textRenderer;
    private final LegacyComponentSerializer legacySerializer;

    public MenuItemFactory(MaterialResolver materialResolver, TextRenderer textRenderer) {
        this.materialResolver = materialResolver;
        this.textRenderer = textRenderer;
        this.legacySerializer = LegacyComponentSerializer.legacySection();
    }

    public ItemStack background() {
        ItemStack item = materialResolver.itemOrThrow(1, "GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE:7", "GLASS_PANE");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack info(String name, List<String> lore) {
        ItemStack item = materialResolver.itemOrThrow(1, "DIAMOND", "GOLD_INGOT");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(renderItemText(name));
        meta.setLore(renderItemLore(lore));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack inputEmpty(String name, List<String> lore) {
        ItemStack item = materialResolver.itemOrThrow(1, "BLACK_STAINED_GLASS_PANE", "STAINED_GLASS_PANE:15", "GLASS_PANE");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(renderItemText(name));
        meta.setLore(renderItemLore(lore));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack confirmEmpty(String name, List<String> lore) {
        ItemStack item = materialResolver.itemOrThrow(1, "EMERALD_BLOCK", "EMERALD");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(renderItemText(name));
        meta.setLore(renderItemLore(lore));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack confirmBlocked(String name, List<String> lore) {
        ItemStack item = materialResolver.itemOrThrow(1, "REDSTONE_BLOCK", "REDSTONE");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(renderItemText(name));
        meta.setLore(renderItemLore(lore));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack confirmReady(String name, List<String> lore) {
        ItemStack item = materialResolver.itemOrThrow(1, "EMERALD_BLOCK", "EMERALD");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(renderItemText(name));
        meta.setLore(renderItemLore(lore));
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack close(String name, String lore) {
        ItemStack item = materialResolver.itemOrThrow(1, "BARRIER", "RED_STAINED_GLASS_PANE", "STAINED_GLASS_PANE:14", "REDSTONE_BLOCK");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(renderItemText(name));
        meta.setLore(Collections.singletonList(renderItemText(lore)));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private String renderItemText(String input) {
        if (input == null) return "";
        Component component = textRenderer.renderToComponent(input, Collections.emptyMap(), Collections.emptyMap(), null);
        component = component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        return legacySerializer.serialize(component);
    }

    private List<String> renderItemLore(List<String> input) {
        if (input == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String line : input) {
            result.add(renderItemText(line));
        }
        return result;
    }
}
