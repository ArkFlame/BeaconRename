package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.config.ConfigSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WeaponParticleSettingsTest {
    @Test
    public void snapshotUsesDocumentedDefaults() {
        WeaponParticleSettings settings = WeaponParticleSettings.fromSnapshot(ConfigSnapshot.builder().build());

        assertTrue(settings.isEnabled());
        assertTrue(settings.isHeldEnabled());
        assertTrue(settings.isHolderOnly());
        assertTrue(settings.isHitEnabled());
    }

    @Test
    public void snapshotReadsAllSwitches() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
            .putRoot("forge.weapon-particles.enabled", false)
            .putRoot("forge.weapon-particles.held.enabled", false)
            .putRoot("forge.weapon-particles.held.holder-only", false)
            .putRoot("forge.weapon-particles.hit.enabled", false)
            .build();

        WeaponParticleSettings settings = WeaponParticleSettings.fromSnapshot(snapshot);

        assertFalse(settings.isEnabled());
        assertFalse(settings.isHeldEnabled());
        assertFalse(settings.isHolderOnly());
        assertFalse(settings.isHitEnabled());
    }

    @Test
    public void valueSemanticsIncludeEverySwitch() {
        WeaponParticleSettings first = WeaponParticleSettings.of(true, false, true, false);
        WeaponParticleSettings same = WeaponParticleSettings.of(true, false, true, false);
        WeaponParticleSettings different = WeaponParticleSettings.of(true, false, false, false);

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertFalse(first.equals(different));
        assertTrue(first.toString().contains("heldEnabled=false"));
    }
}
