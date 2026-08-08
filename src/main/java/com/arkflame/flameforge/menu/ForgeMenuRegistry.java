package com.arkflame.flameforge.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class ForgeMenuRegistry {
    private final ConcurrentHashMap<UUID, ForgeMenuContext> contexts;

    public ForgeMenuRegistry() {
        this.contexts = new ConcurrentHashMap<>();
    }

    public Optional<ForgeMenuContext> replace(ForgeMenuContext context) {
        Objects.requireNonNull(context);
        UUID playerId = context.getPlayerId();
        return Optional.ofNullable(contexts.put(playerId, context));
    }

    public Optional<ForgeMenuContext> get(UUID playerId) {
        return Optional.ofNullable(contexts.get(playerId));
    }

    public Optional<ForgeMenuContext> getCurrent(UUID playerId, UUID menuId) {
        ForgeMenuContext context = contexts.get(playerId);
        if (context == null) {
            return Optional.empty();
        }
        if (!context.getMenuId().equals(menuId)) {
            return Optional.empty();
        }
        return Optional.of(context);
    }

    public boolean isCurrent(UUID playerId, UUID menuId) {
        ForgeMenuContext context = contexts.get(playerId);
        if (context == null) {
            return false;
        }
        return context.getMenuId().equals(menuId);
    }

    public Optional<ForgeMenuContext> removeIfCurrent(UUID playerId, UUID menuId) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(menuId);
        AtomicReference<ForgeMenuContext> removed = new AtomicReference<>();
        contexts.compute(playerId, (key, current) -> {
            if (current != null && current.getMenuId().equals(menuId)) {
                removed.set(current);
                return null;
            }
            return current;
        });
        return Optional.ofNullable(removed.get());
    }

    public Optional<ForgeMenuContext> remove(UUID playerId) {
        return Optional.ofNullable(contexts.remove(playerId));
    }

    public List<ForgeMenuContext> drain() {
        List<ForgeMenuContext> drained = new ArrayList<>();
        contexts.forEach((key, value) -> {
            if (contexts.remove(key, value)) {
                drained.add(value);
            }
        });
        return drained;
    }

    public int size() {
        return contexts.size();
    }
}
