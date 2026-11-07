package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.config.ConfigSnapshot;

import java.util.Objects;

/** Immutable weapon-particle configuration. */
public final class WeaponParticleSettings {
    private final boolean enabled;
    private final boolean heldEnabled;
    private final boolean holderOnly;
    private final boolean hitEnabled;

    private WeaponParticleSettings(boolean enabled, boolean heldEnabled, boolean holderOnly,
                                   boolean hitEnabled) {
        this.enabled = enabled;
        this.heldEnabled = heldEnabled;
        this.holderOnly = holderOnly;
        this.hitEnabled = hitEnabled;
    }

    public static WeaponParticleSettings fromSnapshot(ConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new WeaponParticleSettings(
            snapshot.getRootBoolean("forge.weapon-particles.enabled", true),
            snapshot.getRootBoolean("forge.weapon-particles.held.enabled", true),
            snapshot.getRootBoolean("forge.weapon-particles.held.holder-only", true),
            snapshot.getRootBoolean("forge.weapon-particles.hit.enabled", true));
    }

    public static WeaponParticleSettings of(boolean enabled, boolean heldEnabled, boolean holderOnly,
                                            boolean hitEnabled) {
        return new WeaponParticleSettings(enabled, heldEnabled, holderOnly, hitEnabled);
    }

    public boolean isEnabled() { return enabled; }
    public boolean isHeldEnabled() { return heldEnabled; }
    public boolean isHolderOnly() { return holderOnly; }
    public boolean isHitEnabled() { return hitEnabled; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WeaponParticleSettings)) return false;
        WeaponParticleSettings that = (WeaponParticleSettings) other;
        return enabled == that.enabled && heldEnabled == that.heldEnabled
            && holderOnly == that.holderOnly && hitEnabled == that.hitEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, heldEnabled, holderOnly, hitEnabled);
    }

    @Override
    public String toString() {
        return "WeaponParticleSettings{" +
            "enabled=" + enabled +
            ", heldEnabled=" + heldEnabled +
            ", holderOnly=" + holderOnly +
            ", hitEnabled=" + hitEnabled +
            '}';
    }
}
