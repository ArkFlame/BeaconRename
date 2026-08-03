package com.arkflame.flameforge.command;

public final class StartupFailure {
    public enum Component {
        CONFIGURATION("configuration", true),
        PLAYER_DATA("player data", true),
        STATION_DATA("station data", true),
        PENDING_DELIVERIES("pending deliveries", true),
        RUNTIME_SERVICES("runtime services", false),
        LISTENER_REGISTRATION("listener registration", false),
        GLOBAL_FINALIZATION("global startup finalization", false);

        private final String displayName;
        private final boolean retryable;

        Component(String displayName, boolean retryable) {
            this.displayName = displayName;
            this.retryable = retryable;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }

    private final Component component;
    private final String reason;
    private final String reference;
    private final String exceptionType;

    private StartupFailure(Component component, String reason, String reference, String exceptionType) {
        this.component = component;
        this.reason = reason;
        this.reference = reference;
        this.exceptionType = exceptionType;
    }

    public Component getComponent() {
        return component;
    }

    public String getReason() {
        return reason;
    }

    public String getReference() {
        return reference;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public boolean isRetryable() {
        return component.isRetryable();
    }

    public String getComponentDisplayName() {
        return component.getDisplayName();
    }

    public static StartupFailure create(Component component, Throwable root, long epoch) {
        String reason = sanitizeReason(root.getMessage());
        String reference = "FF-STARTUP-" + epoch + "-" + component.name();
        String exceptionType = root.getClass().getName();
        return new StartupFailure(component, reason, reference, exceptionType);
    }

    private static String sanitizeReason(String message) {
        if (message == null) return "unknown";
        String sanitized = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (sanitized.length() > 240) {
            sanitized = sanitized.substring(0, 237) + "...";
        }
        return sanitized.isEmpty() ? "unknown" : sanitized;
    }
}