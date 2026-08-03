package com.arkflame.flameforge.hologram;

public interface HologramProvider {
    String getName();

    String getVersion();

    boolean isAvailable();

    String getUnavailableReason();

    void upsert(ForgeHologram hologram);

    void remove(String hologramId);
}
