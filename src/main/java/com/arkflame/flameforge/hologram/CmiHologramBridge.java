package com.arkflame.flameforge.hologram;

/**
 * Narrow seam over the reflective CMI hologram calls so provider behaviour can be
 * unit tested without a running CMI server.
 */
interface CmiHologramBridge {
    boolean isAvailable();

    String getUnavailableReason();

    String getVersion();

    boolean exists(String hologramId) throws Exception;

    void create(ForgeHologram hologram) throws Exception;

    void updateLines(ForgeHologram hologram) throws Exception;

    void delete(String hologramId) throws Exception;
}
