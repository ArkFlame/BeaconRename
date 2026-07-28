package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.model.PlayerForgeState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ForgeInventoryHolder implements InventoryHolder {
    private final Player player;
    private final PlayerForgeState session;
    private final int page;
    private Inventory inventory;

    public ForgeInventoryHolder(Player player, PlayerForgeState session, int page) {
        this.player = player;
        this.session = session;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public PlayerForgeState getSession() {
        return session;
    }

    public int getPage() {
        return page;
    }

    public ForgeInventoryHolder withPage(int newPage) {
        return new ForgeInventoryHolder(player, session, newPage);
    }

    public ForgeInventoryHolder withSession(PlayerForgeState newSession) {
        return new ForgeInventoryHolder(player, newSession, page);
    }
}
