package com.arkflame.flameforge.compat.effect.particle.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ParticleStyle {
    private final ParticleStyleId id;
    private final int red;
    private final int green;
    private final int blue;
    private final List<String> candidates;

    public ParticleStyle(ParticleStyleId id, int red, int green, int blue,
                         List<String> candidates) {
        if (id == null || candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("Style id and candidates are required");
        }
        this.id = id;
        this.red = channel(red);
        this.green = channel(green);
        this.blue = channel(blue);
        List<String> copy = new ArrayList<String>(candidates.size());
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                throw new IllegalArgumentException("Particle candidates must be non-empty");
            }
            copy.add(candidate);
        }
        this.candidates = Collections.unmodifiableList(copy);
    }

    public ParticleStyleId getId() {
        return id;
    }

    public ParticleStyleId id() {
        return id;
    }

    public int getRed() {
        return red;
    }

    public int getGreen() {
        return green;
    }

    public int getBlue() {
        return blue;
    }

    public int red() {
        return red;
    }

    public int green() {
        return green;
    }

    public int blue() {
        return blue;
    }

    public int[] getRgb() {
        return new int[] {red, green, blue};
    }

    public List<String> getCandidates() {
        return candidates;
    }

    public List<String> candidates() {
        return candidates;
    }

    private static int channel(int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("RGB channels must be between 0 and 255");
        }
        return value;
    }
}
