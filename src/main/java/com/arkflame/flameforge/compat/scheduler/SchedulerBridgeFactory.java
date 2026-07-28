package com.arkflame.flameforge.compat.scheduler;

public class SchedulerBridgeFactory {
    private static volatile SchedulerBridge bridge;
    private static volatile boolean initializationAttempted = false;
    private static final Object LOCK = new Object();

    private static boolean isFoliaPresent() {
        try {
            Class.forName("net.serveruller.Serveruller");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static SchedulerBridge getBridge() {
        if (bridge != null) {
            return bridge;
        }
        synchronized (LOCK) {
            if (bridge != null) {
                return bridge;
            }
            if (initializationAttempted) {
                return getFallbackBukkitBridge();
            }
            initializationAttempted = true;

            if (isFoliaPresent()) {
                try {
                    FoliaSchedulerBridge foliaBridge = new FoliaSchedulerBridge();
                    if (foliaBridge.isFolia()) {
                        bridge = foliaBridge;
                        return bridge;
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Folia detected but bridge initialization failed - blocking to prevent unstable state", e);
                }
            }

            bridge = getFallbackBukkitBridge();
            return bridge;
        }
    }

    private static SchedulerBridge getFallbackBukkitBridge() {
        return new BukkitSchedulerBridge();
    }

    public static boolean isFolia() {
        return getBridge().isFolia();
    }
}
