package com.arkflame.flameforge.hologram;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

class DecentHologramsProvider implements HologramProvider {
    private final Plugin providerPlugin;
    private final Logger logger;
    private final Method getHologramMethod;
    private final Method createHologramMethod;
    private final Method setLinesMethod;
    private final Method removeHologramMethod;
    private final boolean isJarPresent;
    private final String failureReason;
    private final Set<String> loggedFailureKeys;

    DecentHologramsProvider(Plugin providerPlugin, Logger logger) {
        this.providerPlugin = providerPlugin;
        this.logger = logger;
        this.loggedFailureKeys = ConcurrentHashMap.newKeySet();

        ClassLoader classLoader = providerPlugin.getClass().getClassLoader();
        boolean jarPresent;
        String computedFailureReason = null;
        try {
            Class.forName("eu.decentsoftware.holograms.api.DHAPI", false, classLoader);
            jarPresent = true;
        } catch (ClassNotFoundException e) {
            jarPresent = false;
            computedFailureReason = "DecentHolograms not installed";
        }
        this.isJarPresent = jarPresent;

        if (!jarPresent) {
            this.getHologramMethod = null;
            this.createHologramMethod = null;
            this.setLinesMethod = null;
            this.removeHologramMethod = null;
            this.failureReason = computedFailureReason;
            return;
        }

        Method getHologram = null;
        Method createHologram = null;
        Method setLines = null;
        Method removeHologram = null;
        try {
            Class<?> dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI", false, classLoader);
            Class<?> hologramClass = Class.forName("eu.decentsoftware.holograms.api.holograms.Hologram", false, classLoader);

            getHologram = dhapiClass.getMethod("getHologram", String.class);
            createHologram = dhapiClass.getMethod("createHologram", String.class,
                    org.bukkit.Location.class, boolean.class, java.util.List.class);
            setLines = dhapiClass.getMethod("setHologramLines", hologramClass, java.util.List.class);
            removeHologram = dhapiClass.getMethod("removeHologram", String.class);

        } catch (ClassNotFoundException e) {
            computedFailureReason = "DecentHolograms class not found";
        } catch (NoSuchMethodException e) {
            computedFailureReason = "DecentHolograms method not found";
        } catch (RuntimeException e) {
            computedFailureReason = "DecentHolograms runtime error: " + e.getMessage();
        } catch (LinkageError e) {
            computedFailureReason = "DecentHolograms linkage error: " + e.getMessage();
        } catch (Exception e) {
            computedFailureReason = "DecentHolograms error: " + e.getMessage();
        }

        this.getHologramMethod = getHologram;
        this.createHologramMethod = createHologram;
        this.setLinesMethod = setLines;
        this.removeHologramMethod = removeHologram;
        this.failureReason = computedFailureReason;
    }

    @Override
    public String getName() {
        return "DecentHolograms";
    }

    @Override
    public String getVersion() {
        if (!isJarPresent) {
            return "not installed";
        }
        try {
            Class<?> dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI", false,
                    providerPlugin.getClass().getClassLoader());
            Class<?> pluginClass = Class.forName("eu.decentsoftware.holograms.DecentHolograms", false,
                    providerPlugin.getClass().getClassLoader());
            Method getPlugin = pluginClass.getMethod("getPlugin");
            Object plugin = getPlugin.invoke(null);
            Method getDescription = plugin.getClass().getMethod("getDescription");
            Object desc = getDescription.invoke(plugin);
            return (String) desc.getClass().getMethod("getVersion").invoke(desc);
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    public boolean isAvailable() {
        return isJarPresent && getHologramMethod != null && createHologramMethod != null;
    }

    @Override
    public String getUnavailableReason() {
        return failureReason != null ? failureReason : "DecentHolograms not available";
    }

    @Override
    public void upsert(ForgeHologram hologram) {
        if (!isAvailable()) {
            return;
        }
        try {
            String id = hologram.getId();
            Object existing = getHologramMethod.invoke(null, id);

            if (existing != null) {
                setLinesMethod.invoke(null, existing, hologram.getLegacyLines());
            } else {
                createHologramMethod.invoke(null, id, hologram.getLocation().clone(),
                        hologram.isTransparentBackground(), hologram.getLegacyLines());
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
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
            Object existing = getHologramMethod.invoke(null, hologramId);
            if (existing != null) {
                removeHologramMethod.invoke(null, hologramId);
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            logFailureOnce(hologramId, "remove", e.getCause());
        } catch (Exception e) {
            logFailureOnce(hologramId, "remove", e);
        }
    }

    private void logFailureOnce(String id, String operation, Throwable t) {
        String key = "DecentHolograms:" + operation + ":" + t.getClass().getName() + ":" + t.getMessage();
        if (loggedFailureKeys.add(key)) {
            logger.warning("[FlameForge] DecentHolograms " + operation + " failed for " + id + ": " + t.getMessage());
        }
    }
}
