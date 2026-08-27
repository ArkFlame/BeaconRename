package com.arkflame.flameforge.compat.effect.particle.pattern;

public final class ParticlePoint {
    private final double x;
    private final double y;
    private final double z;

    public ParticlePoint(double x, double y, double z) {
        if (!finite(x) || !finite(y) || !finite(z)) {
            throw new IllegalArgumentException("Particle point coordinates must be finite");
        }
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ParticlePoint)) {
            return false;
        }
        ParticlePoint point = (ParticlePoint) other;
        return Double.compare(x, point.x) == 0 && Double.compare(y, point.y) == 0
            && Double.compare(z, point.z) == 0;
    }

    @Override
    public int hashCode() {
        long result = Double.doubleToLongBits(x);
        result = 31 * result + Double.doubleToLongBits(y);
        result = 31 * result + Double.doubleToLongBits(z);
        return (int) (result ^ (result >>> 32));
    }
}
