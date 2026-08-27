package com.arkflame.flameforge.compat.effect.particle;

public final class ParticleColor {
    private final int red;
    private final int green;
    private final int blue;
    private final int alpha;

    public ParticleColor(int red, int green, int blue) {
        this(red, green, blue, 255);
    }

    public ParticleColor(int red, int green, int blue, int alpha) {
        this.red = clamp(red);
        this.green = clamp(green);
        this.blue = clamp(blue);
        this.alpha = clamp(alpha);
    }

    public static ParticleColor fromRgb(int red, int green, int blue) {
        return new ParticleColor(red, green, blue);
    }

    public static ParticleColor fromArgb(int alpha, int red, int green, int blue) {
        return new ParticleColor(red, green, blue, alpha);
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

    public int getAlpha() {
        return alpha;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
