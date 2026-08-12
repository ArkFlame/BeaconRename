package com.arkflame.flameforge.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
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
            Object manager = getManager();
            if (manager == null) {
                return;
            }

            String id = hologram.getId();
            Optional<Object> existing = findHologram(manager, id);
            if (existing.isPresent()) {
                try {
                    bindings.removeHologramMethod.invoke(manager, existing.get());
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException("Failed to remove hologram " + id, e);
                }
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
            Object manager = getManager();
            if (manager == null) {
                return;
            }

            findHologram(manager, hologramId).ifPresent(h -> {
                try {
                    bindings.removeHologramMethod.invoke(manager, h);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to remove hologram " + hologramId, e);
                }
            });
        } catch (Exception e) {
            logFailureOnce(hologramId, "remove", e);
        }
    }

    private Object getManager() throws Exception {
        Object plugin = bindings.getPluginMethod.invoke(null);
        return bindings.getHologramManagerMethod.invoke(plugin);
    }

    @SuppressWarnings("unchecked")
    private Optional<Object> findHologram(Object manager, String id) {
        try {
            Object result = bindings.getHologramMethod.invoke(manager, id);
            if (result instanceof Optional) {
                return (Optional<Object>) result;
            }
            return Optional.ofNullable(result);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to get hologram " + id, e);
        }
    }

    Object resolveManager() {
        try {
            return getManager();
        } catch (Exception e) {
            return null;
        }
    }

    private void logFailureOnce(String id, String operation, Throwable t) {
        String key = "FancyHolograms:" + operation + ":" + t.getClass().getName() + ":" + t.getMessage();
        if (loggedFailureKeys.add(key)) {
            logger.log(Level.WARNING, "FancyHolograms " + operation + " failed for " + id, t);
        }
    }
}
