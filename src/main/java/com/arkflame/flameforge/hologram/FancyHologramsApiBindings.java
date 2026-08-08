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
    final Class<?> fancyHologramsClass;
    final Class<?> hologramManagerClass;
    final Class<?> hologramDataClass;
    final Class<?> textHologramDataClass;
    final Class<?> hologramClass;

    final Method getPluginMethod;
    final Method createMethod;
    final Method addHologramMethod;
    final Method removeHologramStringMethod;
    final Method isLoadedMethod;

    final Constructor<?> textHologramDataCtor;
    final Method textHologramDataSetText;
    final Method textHologramDataSetBackground;
    final Method hologramDataSetPersistent;

    final Color transparentColor;

    FancyHologramsApiBindings(Plugin providerPlugin, Logger logger) {
        ClassLoader classLoader = providerPlugin.getClass().getClassLoader();

        Class<?> fpClass = null;
        Class<?> fhClass = null;
        Class<?> hmClass = null;
        Class<?> hdClass = null;
        Class<?> thdClass = null;
        Class<?> hClass = null;

        Method getPlugin = null;
        Method create = null;
        Method addHologram = null;
        Method removeHologramString = null;
        Method isLoaded = null;

        Constructor<?> thdCtor = null;
        Method setText = null;
        Method setBackground = null;
        Method setPersistent = null;

        Color transparent = null;

        StringBuilder unavailableReason = new StringBuilder();

        try {
            fpClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin", false, classLoader);
        } catch (ClassNotFoundException e) {
            try {
                fhClass = Class.forName("de.oliver.fancyholograms.FancyHolograms", false, classLoader);
            } catch (ClassNotFoundException e2) {
                unavailableReason.append("FancyHologramsPlugin and FancyHolograms classes not found");
            }
        }

        if (fpClass == null && fhClass == null) {
            this.available = false;
            this.unavailReason = unavailableReason.toString();
            this.fancyHologramsPluginClass = null;
            this.fancyHologramsClass = null;
            this.hologramManagerClass = null;
            this.hologramDataClass = null;
            this.textHologramDataClass = null;
            this.hologramClass = null;
            this.getPluginMethod = null;
            this.createMethod = null;
            this.addHologramMethod = null;
            this.removeHologramStringMethod = null;
            this.isLoadedMethod = null;
            this.textHologramDataCtor = null;
            this.textHologramDataSetText = null;
            this.textHologramDataSetBackground = null;
            this.hologramDataSetPersistent = null;
            this.transparentColor = null;
            return;
        }

        this.fancyHologramsPluginClass = fpClass;
        this.fancyHologramsClass = fhClass;

        try {
            hmClass = Class.forName("de.oliver.fancyholograms.api.HologramManager", false, classLoader);
            hdClass = Class.forName("de.oliver.fancyholograms.api.data.HologramData", false, classLoader);
            thdClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData", false, classLoader);
            hClass = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram", false, classLoader);
        } catch (ClassNotFoundException e) {
            unavailableReason.append("; required API classes not found: ").append(e.getMessage());
            this.available = false;
            this.unavailReason = unavailableReason.toString();
            this.hologramManagerClass = null;
            this.hologramDataClass = null;
            this.textHologramDataClass = null;
            this.hologramClass = null;
            this.getPluginMethod = null;
            this.createMethod = null;
            this.addHologramMethod = null;
            this.removeHologramStringMethod = null;
            this.isLoadedMethod = null;
            this.textHologramDataCtor = null;
            this.textHologramDataSetText = null;
            this.textHologramDataSetBackground = null;
            this.hologramDataSetPersistent = null;
            this.transparentColor = null;
            return;
        }

        this.hologramManagerClass = hmClass;
        this.hologramDataClass = hdClass;
        this.textHologramDataClass = thdClass;
        this.hologramClass = hClass;

        try {
            if (fpClass != null) {
                getPlugin = fpClass.getMethod("get");
            } else if (fhClass != null) {
                getPlugin = fhClass.getMethod("get");
            }

            create = hmClass.getMethod("create", hdClass);
            addHologram = hmClass.getMethod("addHologram", hClass);
            removeHologramString = resolveRemoveHologramByNameMethod(hmClass);
            isLoaded = hmClass.getMethod("isLoaded");

            thdCtor = thdClass.getConstructor(String.class, Location.class);
            setText = thdClass.getMethod("setText", List.class);
            setBackground = thdClass.getMethod("setBackground", Color.class);
            setPersistent = hdClass.getMethod("setPersistent", boolean.class);

            Field transparentField = hClass.getField("TRANSPARENT");
            if (Color.class.isAssignableFrom(transparentField.getType())) {
                transparent = (Color) transparentField.get(null);
            } else {
                unavailableReason.append("; TRANSPARENT field type incompatible");
            }
        } catch (NoSuchMethodException | NoSuchFieldException | IllegalAccessException e) {
            unavailableReason.append("; method/field resolution failed: ").append(e.getMessage());
        } catch (ClassCastException e) {
            unavailableReason.append("; TRANSPARENT field type incompatible");
        } catch (RuntimeException e) {
            unavailableReason.append("; runtime error: ").append(e.getClass().getName()).append(": ").append(e.getMessage());
        } catch (LinkageError e) {
            unavailableReason.append("; linkage error: ").append(e.getClass().getName()).append(": ").append(e.getMessage());
            this.available = false;
            this.unavailReason = unavailableReason.toString();
            this.getPluginMethod = null;
            this.createMethod = null;
            this.addHologramMethod = null;
            this.removeHologramStringMethod = null;
            this.isLoadedMethod = null;
            this.textHologramDataCtor = null;
            this.textHologramDataSetText = null;
            this.textHologramDataSetBackground = null;
            this.hologramDataSetPersistent = null;
            this.transparentColor = null;
            return;
        }

        this.getPluginMethod = getPlugin;
        this.createMethod = create;
        this.addHologramMethod = addHologram;
        this.removeHologramStringMethod = removeHologramString;
        this.isLoadedMethod = isLoaded;
        this.textHologramDataCtor = thdCtor;
        this.textHologramDataSetText = setText;
        this.textHologramDataSetBackground = setBackground;
        this.hologramDataSetPersistent = setPersistent;
        this.transparentColor = transparent;

        Object manager = null;
        try {
            Object pluginOrHolograms = getPlugin.invoke(null);
            if (fpClass != null) {
                Method getHologramManager = fpClass.getMethod("getHologramManager");
                manager = getHologramManager.invoke(pluginOrHolograms);
            } else if (fhClass != null) {
                Method getHologramManager = fhClass.getMethod("getHologramManager");
                manager = getHologramManager.invoke(pluginOrHolograms);
            }
        } catch (Exception e) {
            unavailableReason.append("; could not get HologramManager: ").append(e.getMessage());
            this.available = false;
            this.unavailReason = unavailableReason.toString();
            return;
        }

        boolean loaded;
        try {
            loaded = (boolean) isLoadedMethod.invoke(manager);
        } catch (Exception e) {
            unavailableReason.append("; could not check manager loaded status: ").append(e.getMessage());
            this.available = false;
            this.unavailReason = unavailableReason.toString();
            return;
        }

        if (!loaded) {
            unavailableReason.append("; HologramManager is not loaded");
            this.available = false;
            this.unavailReason = unavailableReason.toString();
            return;
        }

        this.available = true;
        this.unavailReason = null;
    }

    static Method resolveRemoveHologramByNameMethod(Class<?> managerClass)
            throws NoSuchMethodException {
        return managerClass.getMethod("removeHologram", String.class);
    }

    boolean isAvailable() {
        return available;
    }

    String getUnavailReason() {
        return unavailReason;
    }
}
