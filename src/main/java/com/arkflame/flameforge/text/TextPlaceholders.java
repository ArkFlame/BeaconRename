package com.arkflame.flameforge.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEvent.Action;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class TextPlaceholders {
    private static TextPlaceholders instance;
    private final Map<String, Function<Player, String>> dynamicReplacements = new ConcurrentHashMap<>();
    private final Map<String, String> staticReplacements = new ConcurrentHashMap<>();
    private final Map<String, Function<Player, Component>> dynamicComponents = new ConcurrentHashMap<>();
    private final Map<String, Component> staticComponents = new ConcurrentHashMap<>();

    private TextPlaceholders() {
        initializeDefaults();
    }

    public static TextPlaceholders getInstance() {
        if (instance == null) {
            instance = new TextPlaceholders();
        }
        return instance;
    }

    public static synchronized TextPlaceholders create() {
        instance = new TextPlaceholders();
        return instance;
    }

    private void initializeDefaults() {
        dynamicReplacements.put("%player_name%", player -> player != null ? player.getName() : "???");
        dynamicReplacements.put("%player%", player -> player != null ? player.getName() : "???");
        dynamicReplacements.put("%display_name%", player -> player != null ? player.getDisplayName() : "???");
        dynamicReplacements.put("%world%", player -> player != null ? player.getWorld().getName() : "???");
        dynamicReplacements.put("%world_name%", player -> player != null ? player.getWorld().getName() : "???");
        dynamicReplacements.put("%online%", player -> player != null
                ? String.valueOf(player.getServer().getOnlinePlayers().size()) : "0");
        dynamicReplacements.put("%online_players%", player -> player != null
                ? String.valueOf(player.getServer().getOnlinePlayers().size()) : "0");
        dynamicReplacements.put("%max_players%", player -> player != null
                ? String.valueOf(player.getServer().getMaxPlayers()) : "0");
        dynamicComponents.put("%health%", player -> player != null
                ? Component.text(String.format("%.1f", player.getHealth())).hoverEvent(
                        HoverEvent.showText(Component.text("Health: " + String.format("%.1f", player.getHealth())))
                ) : Component.text("???"));
        dynamicComponents.put("%health_points%", player -> player != null
                ? Component.text(String.format("%.1f", player.getHealth())).hoverEvent(
                        HoverEvent.showText(Component.text("Health"))
                ) : Component.text("???"));
        dynamicComponents.put("%food%", player -> player != null
                ? Component.text(String.valueOf(player.getFoodLevel())).hoverEvent(
                        HoverEvent.showText(Component.text("Food Level"))
                ) : Component.text("???"));
        dynamicComponents.put("%xp_level%", player -> player != null
                ? Component.text(String.valueOf(player.getLevel())).hoverEvent(
                        HoverEvent.showText(Component.text("Experience Level"))
                ) : Component.text("???"));
        dynamicComponents.put("%item_name%", player -> {
            if (player != null && player.getItemInHand() != null) {
                final ItemStack item = player.getItemInHand();
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    return Component.text(item.getItemMeta().getDisplayName());
                }
                return Component.text(item.getType().name());
            }
            return Component.text("???");
        });
        dynamicComponents.put("%item_in_hand%", player -> {
            if (player != null && player.getItemInHand() != null) {
                final ItemStack item = player.getItemInHand();
                if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    return Component.text(item.getItemMeta().getDisplayName());
                }
                return Component.text(item.getType().name());
            }
            return Component.text("???");
        });
    }

    public String resolve(final String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        for (final Map.Entry<String, String> entry : staticReplacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public String resolve(final String input, final Player player) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = resolve(input);
        for (final Map.Entry<String, Function<Player, String>> entry : dynamicReplacements.entrySet()) {
            final String value = entry.getValue().apply(player);
            result = result.replace(entry.getKey(), value != null ? value : "");
        }
        return result;
    }

    public Component resolveToComponent(final String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        Component result = Component.text(input);
        for (final Map.Entry<String, Component> entry : staticComponents.entrySet()) {
            result = result.replaceText(builder -> builder.matchLiteral(entry.getKey())
                    .replacement(entry.getValue()));
        }
        for (final Map.Entry<String, Function<Player, Component>> entry : dynamicComponents.entrySet()) {
            result = result.replaceText(builder -> builder.matchLiteral(entry.getKey())
                    .replacement(entry.getValue().apply(null)));
        }
        return result;
    }

    public Component resolveToComponent(final String input, final Player player) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        Component result = Component.text(input);
        for (final Map.Entry<String, Component> entry : staticComponents.entrySet()) {
            result = result.replaceText(builder -> builder.matchLiteral(entry.getKey())
                    .replacement(entry.getValue()));
        }
        for (final Map.Entry<String, Function<Player, Component>> entry : dynamicComponents.entrySet()) {
            result = result.replaceText(builder -> builder.matchLiteral(entry.getKey())
                    .replacement(entry.getValue().apply(player)));
        }
        return result;
    }

    public void registerReplacement(final String placeholder, final String value) {
        if (placeholder != null && value != null) {
            staticReplacements.put(placeholder, value);
        }
    }

    public void registerReplacement(final String placeholder, final Function<Player, String> resolver) {
        if (placeholder != null && resolver != null) {
            dynamicReplacements.put(placeholder, resolver);
        }
    }

    public void registerComponent(final String placeholder, final Component component) {
        if (placeholder != null && component != null) {
            staticComponents.put(placeholder, component);
        }
    }

    public void registerComponent(final String placeholder, final Function<Player, Component> resolver) {
        if (placeholder != null && resolver != null) {
            dynamicComponents.put(placeholder, resolver);
        }
    }

    public void unregisterReplacement(final String placeholder) {
        staticReplacements.remove(placeholder);
        dynamicReplacements.remove(placeholder);
    }

    public void unregisterComponent(final String placeholder) {
        staticComponents.remove(placeholder);
        dynamicComponents.remove(placeholder);
    }

    public void clearAll() {
        staticReplacements.clear();
        dynamicReplacements.clear();
        staticComponents.clear();
        dynamicComponents.clear();
    }

    public Collection<String> getRegisteredPlaceholders() {
        final Collection<String> all = new java.util.HashSet<>();
        all.addAll(staticReplacements.keySet());
        all.addAll(dynamicReplacements.keySet());
        all.addAll(staticComponents.keySet());
        all.addAll(dynamicComponents.keySet());
        return java.util.Collections.unmodifiableCollection(all);
    }

    public Map<String, String> getStaticReplacements() {
        return new HashMap<>(staticReplacements);
    }

    public Map<String, String> getDynamicReplacements() {
        final Map<String, String> result = new HashMap<>();
        for (final Map.Entry<String, Function<Player, String>> entry : dynamicReplacements.entrySet()) {
            result.put(entry.getKey(), "<dynamic>");
        }
        return result;
    }
}
