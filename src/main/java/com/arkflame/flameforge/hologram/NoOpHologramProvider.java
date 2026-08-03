package com.arkflame.flameforge.hologram;

public final class NoOpHologramProvider implements HologramProvider {
    private final String unavailableReason;

    public NoOpHologramProvider(String unavailableReason) {
        this.unavailableReason = unavailableReason != null ? unavailableReason : "no supported hologram provider available";
    }

    @Override
    public String getName() {
        return "NoOp";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getUnavailableReason() {
        return unavailableReason;
    }

    @Override
    public void upsert(ForgeHologram hologram) {
    }

    @Override
    public void remove(String hologramId) {
    }
}
