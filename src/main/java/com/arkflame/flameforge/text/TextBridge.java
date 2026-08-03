package com.arkflame.flameforge.text;

import net.kyori.adventure.platform.AudienceProvider;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class TextBridge {
    private final AudienceProvider audienceProvider;
    private final TextRenderer renderer;
    private volatile boolean closed = false;

    protected TextBridge(final AudienceProvider audienceProvider, final TextRenderer renderer) {
        this.audienceProvider = audienceProvider;
        this.renderer = renderer;
    }

    public static TextBridge create(final Object plugin, final TextRenderer renderer) {
        if (!(plugin instanceof org.bukkit.plugin.java.JavaPlugin)) {
            throw new IllegalArgumentException("Plugin must be a JavaPlugin");
        }
        final AudienceProvider provider = BukkitAudiences.create((org.bukkit.plugin.java.JavaPlugin) plugin);
        return new TextBridge(provider, renderer);
    }

    public AudienceProvider getAudienceProvider() {
        return audienceProvider;
    }

    public Component render(final String template) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }
        return renderer.renderToComponent(template);
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

    public void broadcast(final Component component) {
        sendAll(component);
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

    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (audienceProvider instanceof AutoCloseable) {
            try {
                ((AutoCloseable) audienceProvider).close();
            } catch (Exception e) {
            }
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
