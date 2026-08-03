package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.model.PlayerForgeState;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ForgeMenuContext {
    public enum State {
        OPEN,
        FORGING,
        RETIRED
    }

    private final UUID menuId;
    private final UUID playerId;
    private final String stationId;
    private final PlayerForgeState session;
    private ItemStack input;
    private final long generation;
    private State state = State.OPEN;

    public ForgeMenuContext(UUID menuId, UUID playerId, String stationId,
                            PlayerForgeState session, long generation) {
        this.menuId = Objects.requireNonNull(menuId);
        this.playerId = Objects.requireNonNull(playerId);
        this.stationId = Objects.requireNonNull(stationId);
        this.session = Objects.requireNonNull(session);
        this.input = null;
        this.generation = generation;
    }

    public synchronized boolean tryInsert(ItemStack item) {
        if (state != State.OPEN) {
            return false;
        }
        if (input != null) {
            return false;
        }
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            return false;
        }
        this.input = item.clone();
        return true;
    }

    public synchronized Optional<ItemStack> peekInput() {
        return input != null ? Optional.of(input.clone()) : Optional.empty();
    }

    public synchronized Optional<ItemStack> extractInput() {
        if (input == null) {
            return Optional.empty();
        }
        ItemStack result = input.clone();
        input = null;
        return Optional.of(result);
    }

    public synchronized Optional<ItemStack> retireAndExtract() {
        state = State.RETIRED;
        if (input == null) {
            return Optional.empty();
        }
        ItemStack result = input.clone();
        input = null;
        return Optional.of(result);
    }

    public synchronized boolean beginForge() {
        if (state != State.OPEN) {
            return false;
        }
        state = State.FORGING;
        return true;
    }

    public boolean isOpen() {
        return state == State.OPEN;
    }

    public boolean isForging() {
        return state == State.FORGING;
    }

    public boolean isRetired() {
        return state == State.RETIRED;
    }

    public UUID getMenuId() {
        return menuId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getStationId() {
        return stationId;
    }

    public PlayerForgeState getSession() {
        return session;
    }

    public long getGeneration() {
        return generation;
    }

    public State getState() {
        return state;
    }

    public boolean isCurrent(UUID menuId) {
        return this.menuId.equals(menuId);
    }
}
