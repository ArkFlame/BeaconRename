package com.arkflame.flameforge.hologram;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Reflective binding for the CMI hologram API. CMI is not a compile dependency:
 * FlameForge targets Spigot 1.8.8, so every class and member is resolved once at
 * construction and reused. Any resolution failure degrades the provider to
 * unavailable instead of escaping into the station service.
 */
final class ReflectiveCmiHologramBridge implements CmiHologramBridge {
    private static final String CMI_CLASS = "com.Zrips.CMI.CMI";
    private static final String MANAGER_CLASS = "com.Zrips.CMI.Modules.Holograms.HologramManager";
    private static final String HOLOGRAM_CLASS = "com.Zrips.CMI.Modules.Holograms.CMIHologram";
    private static final String PAGES_CLASS = "com.Zrips.CMI.Modules.Holograms.CMIHologramPages";
    private static final String SETTINGS_CLASS = "com.Zrips.CMI.Modules.Holograms.CMIHologramSettings";

    private final Plugin providerPlugin;
    private final Method getInstanceMethod;
    private final Method getHologramManagerMethod;
    private final Method getByNameMethod;
    private final Constructor<?> hologramConstructor;
    private final Method getPagesMethod;
    private final Method getSettingsMethod;
    private final Method setLinesMethod;
    private final Method setSaveToFileMethod;
    private final Method showMethod;
    private final Method updateMethod;
    private final Method deleteMethod;
    private final Method removeMethod;
    private final boolean available;
    private final String unavailableReason;

    ReflectiveCmiHologramBridge(Plugin providerPlugin) {
        this.providerPlugin = providerPlugin;

        Method getInstance = null;
        Method getHologramManager = null;
        Method getByName = null;
        Constructor<?> hologramConstructor = null;
        Method getPages = null;
        Method getSettings = null;
        Method setLines = null;
        Method setSaveToFile = null;
        Method show = null;
        Method update = null;
        Method delete = null;
        Method remove = null;
        boolean resolved = false;
        String failureReason = null;

        ClassLoader classLoader = providerPlugin.getClass().getClassLoader();
        try {
            Class<?> cmiClass = Class.forName(CMI_CLASS, false, classLoader);
            Class<?> managerClass = Class.forName(MANAGER_CLASS, false, classLoader);
            Class<?> hologramClass = Class.forName(HOLOGRAM_CLASS, false, classLoader);
            Class<?> pagesClass = Class.forName(PAGES_CLASS, false, classLoader);
            Class<?> settingsClass = Class.forName(SETTINGS_CLASS, false, classLoader);

            getInstance = cmiClass.getMethod("getInstance");
            getHologramManager = cmiClass.getMethod("getHologramManager");
            getByName = managerClass.getMethod("getByName", String.class);
            hologramConstructor = hologramClass.getConstructor(String.class, Location.class);
            getPages = hologramClass.getMethod("getPages");
            getSettings = hologramClass.getMethod("getSettings");
            show = hologramClass.getMethod("show");
            setLines = pagesClass.getMethod("setLines", List.class);
            setSaveToFile = settingsClass.getMethod("setSaveToFile", boolean.class);
            update = findOptional(hologramClass, "update");
            delete = findOptional(hologramClass, "delete");
            remove = findOptional(managerClass, "remove", hologramClass);

            if (delete == null && remove == null) {
                failureReason = "CMI delete/remove method not found";
            } else {
                resolved = true;
            }
        } catch (ClassNotFoundException e) {
            failureReason = "CMI not installed";
        } catch (NoSuchMethodException e) {
            failureReason = "CMI method not found";
        } catch (LinkageError e) {
            failureReason = "CMI linkage error: " + e.getMessage();
        } catch (RuntimeException e) {
            failureReason = "CMI runtime error: " + e.getMessage();
        } catch (Exception e) {
            failureReason = "CMI error: " + e.getMessage();
        }

        this.getInstanceMethod = getInstance;
        this.getHologramManagerMethod = getHologramManager;
        this.getByNameMethod = getByName;
        this.hologramConstructor = hologramConstructor;
        this.getPagesMethod = getPages;
        this.getSettingsMethod = getSettings;
        this.setLinesMethod = setLines;
        this.setSaveToFileMethod = setSaveToFile;
        this.showMethod = show;
        this.updateMethod = update;
        this.deleteMethod = delete;
        this.removeMethod = remove;
        this.available = resolved;
        this.unavailableReason = failureReason;
    }

    private static Method findOptional(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String getUnavailableReason() {
        return unavailableReason != null ? unavailableReason : "CMI not available";
    }

    @Override
    public String getVersion() {
        try {
            return providerPlugin.getDescription().getVersion();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    @Override
    public boolean exists(String hologramId) throws Exception {
        return getByNameMethod.invoke(manager(), hologramId) != null;
    }

    @Override
    public void create(ForgeHologram hologram) throws Exception {
        Object hologramHandle = hologramConstructor.newInstance(hologram.getId(),
                hologram.getLocation().clone());
        Object settings = getSettingsMethod.invoke(hologramHandle);
        // FlameForge owns and rebuilds station holograms, so they must not persist to CMI's file.
        setSaveToFileMethod.invoke(settings, false);
        Object pages = getPagesMethod.invoke(hologramHandle);
        // CMI takes legacy/plain lines and has no transparent-background concept.
        setLinesMethod.invoke(pages, hologram.getLegacyLines());
        showMethod.invoke(hologramHandle);
    }

    @Override
    public void updateLines(ForgeHologram hologram) throws Exception {
        Object hologramHandle = getByNameMethod.invoke(manager(), hologram.getId());
        if (hologramHandle == null) {
            create(hologram);
            return;
        }
        Object pages = getPagesMethod.invoke(hologramHandle);
        setLinesMethod.invoke(pages, hologram.getLegacyLines());
        if (updateMethod != null) {
            updateMethod.invoke(hologramHandle);
        } else {
            showMethod.invoke(hologramHandle);
        }
    }

    @Override
    public void delete(String hologramId) throws Exception {
        Object manager = manager();
        Object hologramHandle = getByNameMethod.invoke(manager, hologramId);
        if (hologramHandle == null) {
            return;
        }
        if (deleteMethod != null) {
            deleteMethod.invoke(hologramHandle);
        } else {
            removeMethod.invoke(manager, hologramHandle);
        }
    }

    private Object manager() throws Exception {
        Object cmi = getInstanceMethod.invoke(null);
        if (cmi == null) {
            throw new IllegalStateException("CMI instance unavailable");
        }
        Object manager = getHologramManagerMethod.invoke(cmi);
        if (manager == null) {
            throw new IllegalStateException("CMI hologram manager unavailable");
        }
        return manager;
    }
}
