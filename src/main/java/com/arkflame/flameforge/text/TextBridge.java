package com.arkflame.flameforge.text;

import net.kyori.adventure.platform.AudienceProvider;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class TextBridge {
    private static TextBridge instance;
    private final AudienceProvider audienceProvider;
    private final TextPlaceholders placeholders;
    private volatile boolean closed = false;

    private TextBridge(final AudienceProvider audienceProvider, final TextPlaceholders placeholders) {
        this.audienceProvider = audienceProvider;
        this.placeholders = placeholders;
    }

    public static synchronized TextBridge create(final Object plugin, final TextPlaceholders placeholders) {
        if (instance != null && !instance.closed) {
            return instance;
        }
        if (!(plugin instanceof org.bukkit.plugin.java.JavaPlugin)) {
            throw new IllegalArgumentException("Plugin must be a JavaPlugin");
        }
        final AudienceProvider provider = BukkitAudiences.create((org.bukkit.plugin.java.JavaPlugin) plugin);
        instance = new TextBridge(provider, placeholders);
        return instance;
    }

    public static TextBridge getInstance() {
        if (instance == null) {
            throw new IllegalStateException("TextBridge not initialized. Call create() first.");
        }
        return instance;
    }

    public static boolean isInitialized() {
        return instance != null && !instance.closed;
    }

    public AudienceProvider getAudienceProvider() {
        return audienceProvider;
    }

    public Component parse(final String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        final String resolved = placeholders.resolve(input);
        return fromLegacy(resolved);
    }

    public Component parse(final String input, final TagResolver resolver) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        final String resolved = placeholders.resolve(input);
        try {
            return MiniMessage.miniMessage().deserialize(resolved, resolver);
        } catch (Exception e) {
            return fromLegacy(resolved);
        }
    }

    public String serialize(final Component component) {
        if (component == null) {
            return "";
        }
        return net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(component);
    }

    public Component style(final Component component, final Style style) {
        if (component == null || style == null) {
            return component != null ? component : Component.empty();
        }
        return component.style(style);
    }

    public Component color(final Component component, final TextColor color) {
        return style(component, Style.style(color));
    }

    public Component color(final Component component, final NamedTextColor color) {
        return color(component, (TextColor) color);
    }

    public Component bold(final Component component) {
        return style(component, Style.style().decoration(TextDecoration.BOLD, true).build());
    }

    public Component italic(final Component component) {
        return style(component, Style.style().decoration(TextDecoration.ITALIC, true).build());
    }

    public Component underline(final Component component) {
        return style(component, Style.style().decoration(TextDecoration.UNDERLINED, true).build());
    }

    public Component strikethrough(final Component component) {
        return style(component, Style.style().decoration(TextDecoration.STRIKETHROUGH, true).build());
    }

    public Component obfuscate(final Component component) {
        return style(component, Style.style().decoration(TextDecoration.OBFUSCATED, true).build());
    }

    public Component append(final Component... components) {
        return Component.join(JoinConfiguration.noSeparators(), Arrays.asList(components));
    }

    public Component join(final Collection<Component> components, final Component separator) {
        return Component.join(JoinConfiguration.separator(separator), components);
    }

    public Component join(final Collection<Component> components) {
        return Component.join(JoinConfiguration.noSeparators(), components);
    }

    public Component newline() {
        return Component.text("\n");
    }

    public Component space() {
        return Component.text(" ");
    }

    public Component empty() {
        return Component.empty();
    }

    public Component text(final String content) {
        return Component.text(content != null ? content : "");
    }

    public Component fromLegacy(final String legacyText) {
        if (legacyText == null || legacyText.isEmpty()) {
            return Component.empty();
        }
        final String translated = net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', legacyText);
        return Component.text(translated.replace(net.md_5.bungee.api.ChatColor.COLOR_CHAR, '\u00A7'));
    }

    public void send(final Player player, final Component component) {
        if (player == null || component == null || closed) {
            return;
        }
        audienceProvider.player(player.getUniqueId()).sendMessage(component);
    }

    public void send(final CommandSender sender, final Component component) {
        if (sender == null || component == null || closed) {
            return;
        }
        if (sender instanceof Player) {
            audienceProvider.player(((Player) sender).getUniqueId()).sendMessage(component);
        } else {
            audienceProvider.console().sendMessage(component);
        }
    }

    public void sendAll(final Component component) {
        if (component == null || closed) {
            return;
        }
        audienceProvider.all().sendMessage(component);
    }

    public void sendTitle(final Player player, final Component title, final Component subtitle,
                         final int fadeIn, final int stay, final int fadeOut) {
        if (player == null || closed) {
            return;
        }
        final Title.Times times = Title.Times.times(
                java.time.Duration.ofMillis(fadeIn * 50L),
                java.time.Duration.ofMillis(stay * 50L),
                java.time.Duration.ofMillis(fadeOut * 50L)
        );
        audienceProvider.player(player.getUniqueId()).showTitle(Title.title(title, subtitle, times));
    }

    public void sendTitle(final Player player, final Component title, final Component subtitle) {
        sendTitle(player, title, subtitle, 10, 70, 20);
    }

    public void sendTitle(final Player player, final String title, final String subtitle,
                         final int fadeIn, final int stay, final int fadeOut) {
        sendTitle(player, parse(title), parse(subtitle), fadeIn, stay, fadeOut);
    }

    public void sendTitle(final Player player, final String title, final String subtitle) {
        sendTitle(player, title, subtitle, 10, 70, 20);
    }

    public void sendSubtitle(final Player player, final Component subtitle) {
        sendTitle(player, Component.empty(), subtitle);
    }

    public void clearTitle(final Player player) {
        if (player == null || closed) {
            return;
        }
        audienceProvider.player(player.getUniqueId()).clearTitle();
    }

    public void sendActionBar(final Player player, final Component message) {
        if (player == null || message == null || closed) {
            return;
        }
        audienceProvider.player(player.getUniqueId()).sendActionBar(message);
    }

    public void sendActionBar(final Player player, final String message) {
        sendActionBar(player, parse(message));
    }

    public void sendChat(final Player player, final Component message) {
        if (player == null || message == null || closed) {
            return;
        }
        audienceProvider.player(player.getUniqueId()).sendMessage(message);
    }

    public void sendHelp(final Player player, final Component header, final List<Component> commands) {
        if (player == null || closed) {
            return;
        }
        final net.kyori.adventure.audience.Audience audience = audienceProvider.player(player.getUniqueId());
        if (header != null) {
            audience.sendMessage(header);
        }
        for (final Component cmd : commands) {
            audience.sendMessage(cmd);
        }
    }

    public Component forItem(final org.bukkit.inventory.ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return text("");
        }
        final org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta.hasDisplayName()) {
            return parse(meta.getDisplayName());
        }
        return text(item.getType().name());
    }

    public List<Component> forLore(final List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Component> result = new ArrayList<>(lore.size());
        for (final String line : lore) {
            final Component component = parse(line);
            result.add(component.style(Style.style().decoration(TextDecoration.ITALIC, false).build()));
        }
        return result;
    }

    public List<String> serializeLore(final List<Component> components) {
        if (components == null || components.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> result = new ArrayList<>(components.size());
        for (final Component component : components) {
            result.add(serialize(component));
        }
        return result;
    }

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (audienceProvider instanceof AutoCloseable) {
            try {
                ((AutoCloseable) audienceProvider).close();
            } catch (Exception e) {
                // ignore
            }
        }
        instance = null;
    }

    public boolean isClosed() {
        return closed;
    }
}
