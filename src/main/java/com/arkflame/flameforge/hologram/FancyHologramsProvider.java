package com.arkflame.flameforge.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

class FancyHologramsProvider implements HologramProvider {
    private final Plugin providerPlugin;
    private final FancyHologramsApiBindings bindings;
    private final Logger logger;
    private final Set<String> loggedFailureKeys;

    FancyHologramsProvider(Plugin providerPlugin, Logger logger) {
        this.providerPlugin = providerPlugin;
        this.bindings = new FancyHologramsApiBindings(providerPlugin, logger);
        this.logger = logger;
        this.loggedFailureKeys = ConcurrentHashMap.newKeySet();
    }

    @Override
    public String getName() {
        return "FancyHolograms";
    }

    @Override
    public String getVersion() {
        return providerPlugin.getDescription().getVersion();
    }

    @Override
    public boolean isAvailable() {
        return bindings.isAvailable();
    }

    @Override
    public String getUnavailableReason() {
        return bindings.getUnavailReason();
    }

    @Override
    public void upsert(ForgeHologram hologram) {
        if (!isAvailable()) {
            return;
        }
        try {
            Object manager = getHologramManager();
            if (manager == null) {
                return;
            }

            String id = hologram.getId();

            Optional<Object> existingOpt = getExistingHologram(manager, id);
            if (existingOpt.isPresent()) {
                bindings.removeHologramMethod.invoke(manager, existingOpt.get());
            }

            Location clonedLocation = hologram.getLocation().clone();

            Object data = bindings.textHologramDataCtor.newInstance(id, clonedLocation);
            bindings.textHologramDataSetText.invoke(data, hologram.getMiniMessageLines());

            if (hologram.isTransparentBackground()) {
                bindings.textHologramDataSetBackground.invoke(data, bindings.transparentColor);
            }

            bindings.hologramDataSetPersistent.invoke(data, false);

            Object created = bindings.createMethod.invoke(manager, data);
            bindings.addHologramMethod.invoke(manager, created);
        } catch (InvocationTargetException e) {
            logFailureOnce(hologram.getId(), "upsert", e.getCause());
        } catch (Exception e) {
            logFailureOnce(hologram.getId(), "upsert", e);
        }
    }

    @Override
    public void remove(String hologramId) {
        if (!isAvailable()) {
            return;
        }
        try {
            Object manager = getHologramManager();
            if (manager == null) {
                return;
            }

            getExistingHologram(manager, hologramId).ifPresent(h -> {
                try {
                    bindings.removeHologramMethod.invoke(manager, h);
                } catch (Exception e) {
                    logFailureOnce(hologramId, "remove", e);
                }
            });
        } catch (Exception e) {
            logFailureOnce(hologramId, "remove", e);
        }
    }

    private Object getHologramManager() throws Exception {
        Object pluginOrHolograms = bindings.getPluginMethod.invoke(null);
        if (bindings.fancyHologramsPluginClass != null) {
            Method getHologramManager = bindings.fancyHologramsPluginClass.getMethod("getHologramManager");
            return getHologramManager.invoke(pluginOrHolograms);
        } else {
            Method getHologramManager = bindings.fancyHologramsClass.getMethod("getHologramManager");
            return getHologramManager.invoke(pluginOrHolograms);
        }
    }

    private Optional<Object> getExistingHologram(Object manager, String id) {
        try {
            Object hologram = bindings.getHologramMethod.invoke(manager, id);
            if (hologram != null) {
                return Optional.of(hologram);
            }
        } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            logFailureOnce(id, "getExisting", e);
        } catch (RuntimeException e) {
            logFailureOnce(id, "getExisting", e);
        }
        return Optional.empty();
    }

    private void logFailureOnce(String id, String operation, Throwable t) {
        String key = "FancyHolograms:" + operation + ":" + t.getClass().getName() + ":" + t.getMessage();
        if (loggedFailureKeys.add(key)) {
            logger.warning("[FlameForge] FancyHolograms " + operation + " failed for " + id + ": " + t.getMessage());
        }
    }
}
