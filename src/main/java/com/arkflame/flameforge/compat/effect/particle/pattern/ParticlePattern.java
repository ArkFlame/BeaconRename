package com.arkflame.flameforge.compat.effect.particle.pattern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ParticlePattern {
    public static final int MAX_POINTS = 2048;
    private final List<ParticlePoint> points;

    public ParticlePattern(List<ParticlePoint> points) {
        if (points == null || points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("Particle pattern cannot exceed 2048 points");
        }
        List<ParticlePoint> copy = new ArrayList<ParticlePoint>(points.size());
        for (ParticlePoint point : points) {
            if (point == null) {
                throw new IllegalArgumentException("Particle pattern points cannot be null");
            }
            copy.add(point);
        }
        this.points = Collections.unmodifiableList(copy);
    }

    public List<ParticlePoint> getPoints() {
        return points;
    }

    public List<ParticlePoint> points() {
        return points;
    }

    public int size() {
        return points.size();
    }
}
