package com.arkflame.flameforge.hologram;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;

class FancyHologramsApiBindings {
    private final boolean available;
    private final String unavailReason;

    final Class<?> fancyHologramsPluginClass;
    final Class<?> hologramManagerClass;
    final Class<?> hologramDataClass;
    final Class<?> textHologramDataClass;
    final Class<?> hologramClass;

    final Method getPluginMethod;
    final Method getHologramManagerMethod;
    final Method getHologramMethod;
    final Method createMethod;
    final Method addHologramMethod;
    final Method removeHologramMethod;
    final Method isLoadedMethod;

    final Constructor<?> textHologramDataCtor;
    final Method textHologramDataSetText;
    final Method textHologramDataSetBackground;
    final Method hologramDataSetPersistent;

    final Color transparentColor;

    static Method resolveGetHologramMethod(Class<?> managerClass) throws NoSuchMethodException {
        return managerClass.getMethod("getHologram", String.class);
    }

    static Method resolveRemoveHologramMethod(Class<?> managerClass, Class<?> hologramClass) throws NoSuchMethodException {
        return managerClass.getMethod("removeHologram", hologramClass);
    }

    FancyHologramsApiBindings(Plugin providerPlugin, Logger logger) {
        ClassLoader classLoader = providerPlugin.getClass().getClassLoader();

        StringBuilder reason = new StringBuilder();
        boolean isAvailable = false;

        Class<?> fpClass = null;
        Class<?> hmClass = null;
        Class<?> hdClass = null;
        Class<?> thdClass = null;
        Class<?> hClass = null;

        Method getPluginM = null;
        Method getHologramManagerM = null;
        Method getHologramM = null;
        Method createM = null;
        Method addHologramM = null;
        Method removeHologramM = null;
        Method isLoadedM = null;

        Constructor<?> thdCtor = null;
        Method setTextM = null;
        Method setBackgroundM = null;
        Method setPersistentM = null;

        Color transparentC = null;

        try {
            fpClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin", false, classLoader);
        } catch (ClassNotFoundException e) {
            reason.append("FancyHologramsPlugin class not found");
        }

        if (fpClass == null) {
            this.available = false;
            this.unavailReason = reason.toString();
            this.fancyHologramsPluginClass = null;
            this.hologramManagerClass = null;
            this.hologramDataClass = null;
            this.textHologramDataClass = null;
            this.hologramClass = null;
            this.getPluginMethod = null;
            this.getHologramManagerMethod = null;
            this.getHologramMethod = null;
            this.createMethod = null;
            this.addHologramMethod = null;
            this.removeHologramMethod = null;
            this.isLoadedMethod = null;
            this.textHologramDataCtor = null;
            this.textHologramDataSetText = null;
            this.textHologramDataSetBackground = null;
            this.hologramDataSetPersistent = null;
            this.transparentColor = null;
            return;
        }

        try {
            hmClass = Class.forName("de.oliver.fancyholograms.api.HologramManager", false, classLoader);
            hdClass = Class.forName("de.oliver.fancyholograms.api.data.HologramData", false, classLoader);
            thdClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData", false, classLoader);
            hClass = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram", false, classLoader);
        } catch (ClassNotFoundException e) {
            reason.append("; required API classes not found: ").append(e.getMessage());
            this.available = false;
            this.unavailReason = reason.toString();
            this.fancyHologramsPluginClass = fpClass;
            this.hologramManagerClass = null;
            this.hologramDataClass = null;
            this.textHologramDataClass = null;
            this.hologramClass = null;
            this.getPluginMethod = null;
            this.getHologramManagerMethod = null;
            this.getHologramMethod = null;
            this.createMethod = null;
            this.addHologramMethod = null;
            this.removeHologramMethod = null;
            this.isLoadedMethod = null;
            this.textHologramDataCtor = null;
            this.textHologramDataSetText = null;
            this.textHologramDataSetBackground = null;
            this.hologramDataSetPersistent = null;
            this.transparentColor = null;
            return;
        }

        try {
            getPluginM = fpClass.getMethod("get");
            getHologramManagerM = fpClass.getMethod("getHologramManager");
            getHologramM = resolveGetHologramMethod(hmClass);
            createM = hmClass.getMethod("create", hdClass);
            addHologramM = hmClass.getMethod("addHologram", hClass);
            removeHologramM = resolveRemoveHologramMethod(hmClass, hClass);
            isLoadedM = hmClass.getMethod("isLoaded");

            thdCtor = thdClass.getConstructor(String.class, Location.class);
            setTextM = thdClass.getMethod("setText", List.class);
            setBackgroundM = thdClass.getMethod("setBackground", Color.class);
            setPersistentM = hdClass.getMethod("setPersistent", boolean.class);

            Field transparentField = hClass.getField("TRANSPARENT");
            if (Color.class.isAssignableFrom(transparentField.getType())) {
                transparentC = (Color) transparentField.get(null);
            } else {
                reason.append("; TRANSPARENT field type incompatible");
            }
        } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException e) {
            reason.append("; method/field resolution failed: ").append(e.getMessage());
        } catch (ClassCastException e) {
            reason.append("; TRANSPARENT field type incompatible");
        } catch (LinkageError e) {
            reason.append("; linkage error: ").append(e.getClass().getName()).append(": ").append(e.getMessage());
        }

        if (reason.length() > 0) {
            this.available = false;
            this.unavailReason = reason.toString();
            this.fancyHologramsPluginClass = fpClass;
            this.hologramManagerClass = hmClass;
            this.hologramDataClass = hdClass;
            this.textHologramDataClass = thdClass;
            this.hologramClass = hClass;
            this.getPluginMethod = null;
            this.getHologramManagerMethod = null;
            this.getHologramMethod = null;
            this.createMethod = null;
            this.addHologramMethod = null;
            this.removeHologramMethod = null;
            this.isLoadedMethod = null;
            this.textHologramDataCtor = null;
            this.textHologramDataSetText = null;
            this.textHologramDataSetBackground = null;
            this.hologramDataSetPersistent = null;
            this.transparentColor = null;
            return;
        }

        Object manager;
        try {
            Object plugin = getPluginM.invoke(null);
            manager = getHologramManagerM.invoke(plugin);
        } catch (Exception e) {
            reason.append("; could not get HologramManager: ").append(e.getMessage());
            this.available = false;
            this.unavailReason = reason.toString();
            this.fancyHologramsPluginClass = fpClass;
            this.hologramManagerClass = hmClass;
            this.hologramDataClass = hdClass;
            this.textHologramDataClass = thdClass;
            this.hologramClass = hClass;
            this.getPluginMethod = getPluginM;
            this.getHologramManagerMethod = getHologramManagerM;
            this.getHologramMethod = getHologramM;
            this.createMethod = createM;
            this.addHologramMethod = addHologramM;
            this.removeHologramMethod = removeHologramM;
            this.isLoadedMethod = isLoadedM;
            this.textHologramDataCtor = thdCtor;
            this.textHologramDataSetText = setTextM;
            this.textHologramDataSetBackground = setBackgroundM;
            this.hologramDataSetPersistent = setPersistentM;
            this.transparentColor = transparentC;
            return;
        }

        boolean loaded;
        try {
            loaded = (boolean) isLoadedM.invoke(manager);
        } catch (Exception e) {
            reason.append("; could not check manager loaded status: ").append(e.getMessage());
            this.available = false;
            this.unavailReason = reason.toString();
            this.fancyHologramsPluginClass = fpClass;
            this.hologramManagerClass = hmClass;
            this.hologramDataClass = hdClass;
            this.textHologramDataClass = thdClass;
            this.hologramClass = hClass;
            this.getPluginMethod = getPluginM;
            this.getHologramManagerMethod = getHologramManagerM;
            this.getHologramMethod = getHologramM;
            this.createMethod = createM;
            this.addHologramMethod = addHologramM;
            this.removeHologramMethod = removeHologramM;
            this.isLoadedMethod = isLoadedM;
            this.textHologramDataCtor = thdCtor;
            this.textHologramDataSetText = setTextM;
            this.textHologramDataSetBackground = setBackgroundM;
            this.hologramDataSetPersistent = setPersistentM;
            this.transparentColor = transparentC;
            return;
        }

        if (!loaded) {
            reason.append("; HologramManager is not loaded");
            this.available = false;
            this.unavailReason = reason.toString();
            this.fancyHologramsPluginClass = fpClass;
            this.hologramManagerClass = hmClass;
            this.hologramDataClass = hdClass;
            this.textHologramDataClass = thdClass;
            this.hologramClass = hClass;
            this.getPluginMethod = getPluginM;
            this.getHologramManagerMethod = getHologramManagerM;
            this.getHologramMethod = getHologramM;
            this.createMethod = createM;
            this.addHologramMethod = addHologramM;
            this.removeHologramMethod = removeHologramM;
            this.isLoadedMethod = isLoadedM;
            this.textHologramDataCtor = thdCtor;
            this.textHologramDataSetText = setTextM;
            this.textHologramDataSetBackground = setBackgroundM;
            this.hologramDataSetPersistent = setPersistentM;
            this.transparentColor = transparentC;
            return;
        }

        this.available = true;
        this.unavailReason = null;
        this.fancyHologramsPluginClass = fpClass;
        this.hologramManagerClass = hmClass;
        this.hologramDataClass = hdClass;
        this.textHologramDataClass = thdClass;
        this.hologramClass = hClass;
        this.getPluginMethod = getPluginM;
        this.getHologramManagerMethod = getHologramManagerM;
        this.getHologramMethod = getHologramM;
        this.createMethod = createM;
        this.addHologramMethod = addHologramM;
        this.removeHologramMethod = removeHologramM;
        this.isLoadedMethod = isLoadedM;
        this.textHologramDataCtor = thdCtor;
        this.textHologramDataSetText = setTextM;
        this.textHologramDataSetBackground = setBackgroundM;
        this.hologramDataSetPersistent = setPersistentM;
        this.transparentColor = transparentC;
    }

    boolean isAvailable() {
        return available;
    }

    String getUnavailReason() {
        return unavailReason;
    }
}
