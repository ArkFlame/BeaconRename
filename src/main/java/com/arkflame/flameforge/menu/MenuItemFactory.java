package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.text.MessageArguments;
import com.arkflame.flameforge.text.TextRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class MenuItemFactory {
    private final MaterialResolver materialResolver;
    private final TextRenderer textRenderer;

    public MenuItemFactory(final MaterialResolver materialResolver, final TextRenderer textRenderer) {
        this.materialResolver = materialResolver;
        this.textRenderer = textRenderer;
    }

    public ItemStack background(final List<String> materials, final String nameTemplate) {
        ItemStack item = materialResolver.itemOrThrow(1, materials.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        if (nameTemplate != null && !nameTemplate.isEmpty()) {
            Component component = textRenderer.renderComponent(nameTemplate, null, null);
            component = component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
            meta.setDisplayName(textRenderer.toLegacy(component));
        } else {
            meta.setDisplayName(" ");
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack build(final List<String> materials, final String nameTemplate,
                          final List<String> loreTemplates, final MessageArguments arguments,
                          final boolean glow, final String renderKey) {
        ItemStack item = materialResolver.itemOrThrow(1, materials.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        if (nameTemplate != null) {
            Component component = textRenderer.renderComponent(nameTemplate, arguments, renderKey);
            component = component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
            meta.setDisplayName(textRenderer.toLegacy(component));
        }
        if (loreTemplates != null && !loreTemplates.isEmpty()) {
            List<String> renderedLore = textRenderer.renderItemLore(loreTemplates, arguments, renderKey);
            meta.setLore(renderedLore);
        }
        if (glow) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }
}