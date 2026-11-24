package com.arkflame.flameforge.hologram;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

final class CmiHologramsProvider implements HologramProvider {
    private static final String NAME = "CMI";

    private final CmiHologramBridge bridge;
    private final Logger logger;
    private final Set<String> loggedFailureKeys;

    CmiHologramsProvider(Plugin providerPlugin, Logger logger) {
        this(new ReflectiveCmiHologramBridge(providerPlugin), logger);
    }

    CmiHologramsProvider(CmiHologramBridge bridge, Logger logger) {
        this.bridge = bridge;
        this.logger = logger;
        this.loggedFailureKeys = ConcurrentHashMap.newKeySet();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getVersion() {
        return bridge.getVersion();
    }

    @Override
    public boolean isAvailable() {
        return bridge.isAvailable();
    }

    @Override
    public String getUnavailableReason() {
        return bridge.getUnavailableReason();
    }

    @Override
    public void upsert(ForgeHologram hologram) {
        if (!bridge.isAvailable()) {
            return;
        }
        try {
            if (bridge.exists(hologram.getId())) {
                bridge.updateLines(hologram);
            } else {
                bridge.create(hologram);
            }
        } catch (InvocationTargetException e) {
            logFailureOnce(hologram.getId(), "upsert", e.getCause());
        } catch (Exception e) {
            logFailureOnce(hologram.getId(), "upsert", e);
        } catch (LinkageError e) {
            logFailureOnce(hologram.getId(), "upsert", e);
        }
    }

    @Override
    public void remove(String hologramId) {
        if (!bridge.isAvailable()) {
            return;
        }
        try {
            if (bridge.exists(hologramId)) {
                bridge.delete(hologramId);
            }
        } catch (InvocationTargetException e) {
            logFailureOnce(hologramId, "remove", e.getCause());
        } catch (Exception e) {
            logFailureOnce(hologramId, "remove", e);
        } catch (LinkageError e) {
            logFailureOnce(hologramId, "remove", e);
        }
    }

    private void logFailureOnce(String id, String operation, Throwable t) {
        if (t == null) {
            return;
        }
        String key = NAME + ":" + operation + ":" + t.getClass().getName() + ":" + t.getMessage();
        if (loggedFailureKeys.add(key)) {
            logger.warning("[FlameForge] CMI " + operation + " failed for " + id + ": " + t.getMessage());
        }
    }
}
