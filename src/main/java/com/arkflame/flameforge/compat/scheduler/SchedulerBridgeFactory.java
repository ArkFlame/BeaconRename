package com.arkflame.flameforge.compat.scheduler;

import com.arkflame.flameforge.compat.RuntimePlatform;
import org.bukkit.plugin.java.JavaPlugin;

public class SchedulerBridgeFactory {

    public SchedulerBridgeFactory() {
    }

    public static SchedulerBridge create(JavaPlugin plugin, RuntimePlatform platform) {
        if (platform.isFolia()) {
            return new FoliaSchedulerBridge(plugin);
        }
        return new BukkitSchedulerBridge();
    }
}
