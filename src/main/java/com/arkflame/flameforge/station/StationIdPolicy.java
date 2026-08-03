package com.arkflame.flameforge.station;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class StationIdPolicy {
    private static final Pattern VALID_EXPLICIT_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    public static String normalize(String id) {
        if (id == null) {
            return "";
        }
        return id.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidExplicit(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        return VALID_EXPLICIT_PATTERN.matcher(id).matches();
    }

    public static boolean isAutoToken(String id) {
        return "auto".equals(id);
    }

    public static String generateCandidate() {
        return "forge-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
