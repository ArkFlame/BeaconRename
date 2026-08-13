package com.arkflame.flameforge.effect;

import java.util.Objects;

/** Immutable visual palette for one forge result. */
public final class ForgeAnimationTheme {
    private final String id;
    private final int auraRed;
    private final int auraGreen;
    private final int auraBlue;
    private final int starRed;
    private final int starGreen;
    private final int starBlue;
    private final String auraParticle;
    private final String starParticle;

    public ForgeAnimationTheme(String id, int auraRed, int auraGreen, int auraBlue,
                               int starRed, int starGreen, int starBlue,
                               String auraParticle, String starParticle) {
        this.id = Objects.requireNonNull(id, "id");
        this.auraRed = clampChannel(auraRed);
        this.auraGreen = clampChannel(auraGreen);
        this.auraBlue = clampChannel(auraBlue);
        this.starRed = clampChannel(starRed);
        this.starGreen = clampChannel(starGreen);
        this.starBlue = clampChannel(starBlue);
        this.auraParticle = auraParticle != null ? auraParticle : "flame";
        this.starParticle = starParticle != null ? starParticle : "firework";
    }

    public String getId() { return id; }
    public int getAuraRed() { return auraRed; }
    public int getAuraGreen() { return auraGreen; }
    public int getAuraBlue() { return auraBlue; }
    public int getStarRed() { return starRed; }
    public int getStarGreen() { return starGreen; }
    public int getStarBlue() { return starBlue; }
    public String getAuraParticle() { return auraParticle; }
    public String getStarParticle() { return starParticle; }

    private static int clampChannel(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
