package com.arkflame.flameforge.compat.effect.particle.pattern;

import java.util.ArrayList;
import java.util.List;

public final class ParticlePatternBuilder {
    public ParticlePatternBuilder() {
    }

    public static ParticlePattern line(ParticlePoint start, ParticlePoint end, int points) {
        requirePoint(start);
        requirePoint(end);
        requireCount(points);
        List<ParticlePoint> result = new ArrayList<ParticlePoint>(points);
        for (int index = 0; index < points; index++) {
            double ratio = points == 1 ? 0.0 : index / (double) (points - 1);
            result.add(interpolate(start, end, ratio));
        }
        return new ParticlePattern(result);
    }

    public static ParticlePattern polyline(List<ParticlePoint> nodes, int pointsPerSegment) {
        if (nodes == null || nodes.size() < 2) {
            throw new IllegalArgumentException("Polyline requires at least two nodes");
        }
        requireCount(pointsPerSegment);
        int total = 1 + (nodes.size() - 1) * (pointsPerSegment - 1);
        requireCount(total);
        List<ParticlePoint> result = new ArrayList<ParticlePoint>(total);
        for (int segment = 0; segment < nodes.size() - 1; segment++) {
            ParticlePoint start = nodes.get(segment);
            ParticlePoint end = nodes.get(segment + 1);
            requirePoint(start);
            requirePoint(end);
            for (int index = segment == 0 ? 0 : 1; index < pointsPerSegment; index++) {
                double ratio = index / (double) (pointsPerSegment - 1);
                result.add(interpolate(start, end, ratio));
            }
        }
        return new ParticlePattern(result);
    }

    public static ParticlePattern circle(ParticlePoint center, double radius, int points) {
        requirePoint(center);
        requireFiniteNonNegative(radius, "Radius");
        requireCount(points);
        List<ParticlePoint> result = new ArrayList<ParticlePoint>(points);
        for (int index = 0; index < points; index++) {
            double angle = 2.0 * Math.PI * index / points;
            result.add(new ParticlePoint(center.x() + Math.cos(angle) * radius, center.y(),
                center.z() + Math.sin(angle) * radius));
        }
        return new ParticlePattern(result);
    }

    public static ParticlePattern helix(ParticlePoint center, double radius, double height,
                                        int turns, int pointsPerStrand) {
        requirePoint(center);
        requireFiniteNonNegative(radius, "Radius");
        requireFinite(height, "Height");
        if (turns < 1 || pointsPerStrand < 1) {
            throw new IllegalArgumentException("Helix turns and points must be positive");
        }
        if (pointsPerStrand > ParticlePattern.MAX_POINTS / 2) {
            throw new IllegalArgumentException("Helix exceeds 2048 points");
        }
        requireCount(pointsPerStrand * 2);
        List<ParticlePoint> result = new ArrayList<ParticlePoint>(pointsPerStrand * 2);
        for (int strand = 0; strand < 2; strand++) {
            for (int index = 0; index < pointsPerStrand; index++) {
                double ratio = pointsPerStrand == 1 ? 0.0
                    : index / (double) (pointsPerStrand - 1);
                double angle = 2.0 * Math.PI * turns * ratio + strand * Math.PI;
                result.add(new ParticlePoint(center.x() + Math.cos(angle) * radius,
                    center.y() + height * ratio, center.z() + Math.sin(angle) * radius));
            }
        }
        return new ParticlePattern(result);
    }

    public static ParticlePattern star(ParticlePoint center, double outerRadius,
                                        double innerRadius, int points) {
        requirePoint(center);
        requireFinitePositive(outerRadius, "Outer radius");
        requireFiniteNonNegative(innerRadius, "Inner radius");
        if (innerRadius > outerRadius) {
            throw new IllegalArgumentException("Inner radius cannot exceed outer radius");
        }
        requireCount(points);
        List<ParticlePoint> result = new ArrayList<ParticlePoint>(points);
        for (int index = 0; index < points; index++) {
            double radius = index % 2 == 0 ? outerRadius : innerRadius;
            double angle = -Math.PI / 2.0 + 2.0 * Math.PI * index / points;
            result.add(new ParticlePoint(center.x() + Math.cos(angle) * radius, center.y(),
                center.z() + Math.sin(angle) * radius));
        }
        return new ParticlePattern(result);
    }

    public static ParticlePattern star(ParticlePoint center, double outerRadius,
                                        double innerRadius) {
        return star(center, outerRadius, innerRadius, 10);
    }

    private static ParticlePoint interpolate(ParticlePoint start, ParticlePoint end,
                                             double ratio) {
        return new ParticlePoint(start.x() + (end.x() - start.x()) * ratio,
            start.y() + (end.y() - start.y()) * ratio,
            start.z() + (end.z() - start.z()) * ratio);
    }

    private static void requirePoint(ParticlePoint point) {
        if (point == null) {
            throw new IllegalArgumentException("Particle point is required");
        }
    }

    private static void requireCount(int count) {
        if (count < 1 || count > ParticlePattern.MAX_POINTS) {
            throw new IllegalArgumentException("Point count must be between 1 and 2048");
        }
    }

    private static void requireFinitePositive(double value, String name) {
        requireFinite(value, name);
        if (value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireFiniteNonNegative(double value, String name) {
        requireFinite(value, name);
        if (value < 0.0) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
    }

    private static void requireFinite(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
