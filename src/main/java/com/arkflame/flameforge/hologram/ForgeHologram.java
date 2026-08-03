package com.arkflame.flameforge.hologram;

import org.bukkit.Location;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ForgeHologram {
    private final String id;
    private final Location location;
    private final List<String> miniMessageLines;
    private final List<String> legacyLines;
    private final boolean transparentBackground;

    public ForgeHologram(String id, Location location, List<String> miniMessageLines,
                          List<String> legacyLines, boolean transparentBackground) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.location = Objects.requireNonNull(location, "location must not be null").clone();
        this.miniMessageLines = miniMessageLines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(miniMessageLines);
        this.legacyLines = legacyLines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(legacyLines);
        this.transparentBackground = transparentBackground;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location.clone();
    }

    public List<String> getMiniMessageLines() {
        return miniMessageLines;
    }

    public List<String> getLegacyLines() {
        return legacyLines;
    }

    public boolean isTransparentBackground() {
        return transparentBackground;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ForgeHologram that = (ForgeHologram) o;
        return transparentBackground == that.transparentBackground
                && Objects.equals(id, that.id)
                && Objects.equals(location, that.location)
                && Objects.equals(miniMessageLines, that.miniMessageLines)
                && Objects.equals(legacyLines, that.legacyLines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, location, miniMessageLines, legacyLines, transparentBackground);
    }

    @Override
    public String toString() {
        return "ForgeHologram{" +
                "id='" + id + '\'' +
                ", location=" + location +
                ", miniMessageLines=" + miniMessageLines +
                ", legacyLines=" + legacyLines +
                ", transparentBackground=" + transparentBackground +
                '}';
    }
}
