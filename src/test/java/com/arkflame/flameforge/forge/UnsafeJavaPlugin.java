package com.arkflame.flameforge.forge;

import org.bukkit.plugin.java.JavaPlugin;
import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.logging.Logger;

public final class UnsafeJavaPlugin {
    private static final Unsafe UNSAFE;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T extends JavaPlugin> T createFakePlugin(Class<T> pluginClass) {
        try {
            T plugin = pluginClass.cast(UNSAFE.allocateInstance(pluginClass));
            Field loggerField = JavaPlugin.class.getDeclaredField("logger");
            long loggerOffset = UNSAFE.objectFieldOffset(loggerField);
            UNSAFE.putObject(plugin, loggerOffset, Logger.getLogger("test"));
            Field dataDirField = JavaPlugin.class.getDeclaredField("dataFolder");
            long dataDirOffset = UNSAFE.objectFieldOffset(dataDirField);
            UNSAFE.putObject(plugin, dataDirOffset, new java.io.File("/tmp"));
            return plugin;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create fake JavaPlugin", e);
        }
    }
}
