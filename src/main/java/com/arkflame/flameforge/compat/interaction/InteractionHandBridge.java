package com.arkflame.flameforge.compat.interaction;

import org.bukkit.event.player.PlayerInteractEvent;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class InteractionHandBridge {
    private final Method getHandMethod;
    private final Logger logger;
    private boolean loggedUnknown;

    public InteractionHandBridge(Logger logger) {
        this.logger = logger;
        Method method;
        try {
            method = PlayerInteractEvent.class.getMethod("getHand");
        } catch (NoSuchMethodException e) {
            method = null;
        }
        this.getHandMethod = method;
    }

    public boolean isPrimary(PlayerInteractEvent event) {
        if (getHandMethod == null) {
            return true;
        }
        try {
            Object hand = getHandMethod.invoke(event);
            if (hand == null) {
                return false;
            }
            String name = hand.toString();
            if ("HAND".equals(name)) {
                return true;
            }
            if ("OFF_HAND".equals(name)) {
                return false;
            }
            if (!loggedUnknown) {
                loggedUnknown = true;
                logger.info("Unknown hand enum: " + name);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
