package com.arkflame.flameforge.effect;

import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyle;
import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyleCatalog;
import com.arkflame.flameforge.compat.effect.particle.style.ParticleStyleId;

import java.util.Objects;

/** Immutable visual palette for one forge result. */
public final class ForgeAnimationTheme {
    private final String id;
    private final ParticleStyleId styleId;

    public ForgeAnimationTheme(String id, ParticleStyleId styleId) {
        this.id = Objects.requireNonNull(id, "id");
        this.styleId = Objects.requireNonNull(styleId, "styleId");
    }

    public String getId() { return id; }
    public ParticleStyleId getStyleId() { return styleId; }
    public ParticleStyle getStyle() { return ParticleStyleCatalog.get(styleId); }
    public int getAuraRed() { return getStyle().getRed(); }
    public int getAuraGreen() { return getStyle().getGreen(); }
    public int getAuraBlue() { return getStyle().getBlue(); }
    public int getStarRed() { return getStyle().getRed(); }
    public int getStarGreen() { return getStyle().getGreen(); }
    public int getStarBlue() { return getStyle().getBlue(); }
}
