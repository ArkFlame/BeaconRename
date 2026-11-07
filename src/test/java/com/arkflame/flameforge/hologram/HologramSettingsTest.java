package com.arkflame.flameforge.hologram;

import com.arkflame.flameforge.config.ConfigSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HologramSettingsTest {

    @Test
    void fallbackLinesAndOffsetAreContractDefaults() {
        HologramSettings settings = HologramSettings.fromSnapshot(ConfigSnapshot.builder().build());

        assertEquals(Arrays.asList(
                "<gradient:#ff5f00:#ffd166><bold>FlameForge</bold></gradient>",
                "<gray>RIGHT CLICK"), settings.getLineTemplates());
        assertEquals(1.25, settings.getOffsetY());
    }

    @Test
    void suppliedLinesArePreservedAndImmutable() {
        List<String> lines = Arrays.asList("%forge_id%", " arbitrary <gray>%profile% ");
        HologramSettings settings = HologramSettings.fromConfig(Collections.emptyList(), true,
                1.25, true, lines);

        assertEquals(lines, settings.getLineTemplates());
        assertThrows(UnsupportedOperationException.class,
                () -> settings.getLineTemplates().set(0, "changed"));
    }
}
