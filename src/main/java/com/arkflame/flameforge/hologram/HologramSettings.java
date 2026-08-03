package com.arkflame.flameforge.hologram;

import com.arkflame.flameforge.config.ConfigSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class HologramSettings {
    private static final List<String> DEFAULT_LINES = Collections.unmodifiableList(
            Collections.singletonList("Forge Station"));

    private final boolean enabled;
    private final List<String> providerOrder;
    private final double offsetY;
    private final boolean transparentBackground;
    private final List<String> lineTemplates;

    private HologramSettings(boolean enabled, List<String> providerOrder, double offsetY,
                             boolean transparentBackground, List<String> lineTemplates) {
        this.enabled = enabled;
        this.providerOrder = Collections.unmodifiableList(providerOrder);
        this.offsetY = offsetY;
        this.transparentBackground = transparentBackground;
        this.lineTemplates = lineTemplates == null || lineTemplates.isEmpty()
                ? DEFAULT_LINES
                : Collections.unmodifiableList(lineTemplates);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<String> getProviderOrder() {
        return providerOrder;
    }

    public double getOffsetY() {
        return offsetY;
    }

    public boolean isTransparentBackground() {
        return transparentBackground;
    }

    public List<String> getLineTemplates() {
        return lineTemplates;
    }

    public static HologramSettings fromConfig(List<?> rawProviderOrder, boolean enabled,
                                               double offsetY, boolean transparentBackground,
                                               List<?> rawLineTemplates) {
        List<String> providerOrder = normalizeProviderOrder(rawProviderOrder);
        double finiteOffsetY = Double.isFinite(offsetY) ? offsetY : 0.0;
        List<String> lineTemplates = normalizeLines(rawLineTemplates);
        return new HologramSettings(enabled, providerOrder, finiteOffsetY, transparentBackground, lineTemplates);
    }

    public static HologramSettings fromSnapshot(ConfigSnapshot snapshot) {
        boolean enabled = snapshot.getRootBoolean("holograms.enabled", true);
        List<String> providerOrder = snapshot.getRootStringList("holograms.provider-order");
        if (providerOrder == null || providerOrder.isEmpty()) {
            providerOrder = Arrays.asList("FancyHolograms", "DecentHolograms");
        }
        double offsetY = snapshot.getRootDouble("holograms.offset-y", 1.75);
        boolean transparentBackground = snapshot.getRootBoolean("holograms.transparent-background", true);
        List<String> lines = snapshot.getRootStringList("holograms.lines");
        return new HologramSettings(enabled, providerOrder, offsetY, transparentBackground, lines);
    }

    private static List<String> normalizeProviderOrder(List<?> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Object item : raw) {
            if (item != null) {
                String trimmed = item.toString().trim();
                if (!trimmed.isEmpty()) {
                    seen.add(trimmed);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    private static List<String> normalizeLines(List<?> raw) {
        if (raw == null || raw.isEmpty()) {
            return DEFAULT_LINES;
        }
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            if (item != null) {
                String trimmed = item.toString().trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result.isEmpty() ? DEFAULT_LINES : result;
    }
}
