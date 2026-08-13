package com.arkflame.flameforge.compat.interaction;

import org.bukkit.event.player.PlayerInteractEvent;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class InteractionHandBridge {
    public enum Hand {
        MAIN,
        OFF,
        UNKNOWN
    }

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
        return getHand(event) == Hand.MAIN;
    }

    public Hand getHand(PlayerInteractEvent event) {
        if (event == null || getHandMethod == null) {
            return getHandMethod == null ? Hand.MAIN : Hand.UNKNOWN;
        }
        try {
            Object hand = getHandMethod.invoke(event);
            if (hand == null) {
                return Hand.UNKNOWN;
            }
            String name = hand.toString();
            if ("HAND".equals(name)) {
                return Hand.MAIN;
            }
            if ("OFF_HAND".equals(name)) {
                return Hand.OFF;
            }
            if (!loggedUnknown) {
                loggedUnknown = true;
                if (logger != null) {
                    logger.info("Unknown hand enum: " + name);
                }
            }
            return Hand.UNKNOWN;
        } catch (Exception e) {
            return Hand.UNKNOWN;
        }
    }
}
