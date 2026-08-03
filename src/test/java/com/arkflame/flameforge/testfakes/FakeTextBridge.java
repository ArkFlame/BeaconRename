package com.arkflame.flameforge.testfakes;

import com.arkflame.flameforge.text.TextBridge;
import net.kyori.adventure.platform.AudienceProvider;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FakeTextBridge extends TextBridge {
    private boolean closed = false;

    public FakeTextBridge() {
        super(null, null);
    }

    @Override
    public Component render(String template) {
        return Component.text(template != null ? template : "");
    }

    @Override
    public void send(Player player, Component component) {
    }

    @Override
    public void send(CommandSender sender, Component component) {
    }

    @Override
    public void sendAll(Component component) {
    }

    @Override
    public void broadcast(Component component) {
    }

    @Override
    public void sendTitle(Player player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
    }

    @Override
    public void sendTitle(Player player, Component title, Component subtitle) {
    }

    @Override
    public void clearTitle(Player player) {
    }

    @Override
    public void sendActionBar(Player player, Component message) {
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
