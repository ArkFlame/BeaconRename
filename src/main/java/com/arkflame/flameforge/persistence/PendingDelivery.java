package com.arkflame.flameforge.persistence;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PendingDelivery {
    private final String deliveryId;
    private final UUID targetPlayer;
    private final long createdAt;
    private final Map<String, Object> itemSnapshot;
    private final List<String> deferredCommands;

    public PendingDelivery(String deliveryId, UUID targetPlayer, long createdAt,
                           Map<String, Object> itemSnapshot, List<String> deferredCommands) {
        this.deliveryId = Objects.requireNonNull(deliveryId);
        this.targetPlayer = Objects.requireNonNull(targetPlayer);
        this.createdAt = createdAt;
        this.itemSnapshot = itemSnapshot != null ? Collections.unmodifiableMap(new java.util.HashMap<>(itemSnapshot)) : Collections.emptyMap();
        this.deferredCommands = deferredCommands != null ? Collections.unmodifiableList(new java.util.ArrayList<>(deferredCommands)) : Collections.emptyList();
    }

    public String getDeliveryId() { return deliveryId; }
    public UUID getTargetPlayer() { return targetPlayer; }
    public long getCreatedAt() { return createdAt; }
    public Map<String, Object> getItemSnapshot() { return itemSnapshot; }
    public List<String> getDeferredCommands() { return deferredCommands; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PendingDelivery)) return false;
        PendingDelivery that = (PendingDelivery) o;
        return Objects.equals(deliveryId, that.deliveryId);
    }

    @Override
    public int hashCode() { return Objects.hash(deliveryId); }
}
