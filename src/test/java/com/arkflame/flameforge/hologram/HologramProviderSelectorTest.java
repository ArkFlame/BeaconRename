package com.arkflame.flameforge.hologram;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HologramProviderSelectorTest {

    @Test
    void selectorChoosesFirstAvailableConfiguredProviderOrNoOp() {
        Plugin fancy = mock(Plugin.class);
        when(fancy.getName()).thenReturn("FancyHolograms");
        when(fancy.isEnabled()).thenReturn(true);
        Plugin decent = mock(Plugin.class);
        when(decent.getName()).thenReturn("DecentHolograms");
        when(decent.isEnabled()).thenReturn(true);
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("FancyHolograms")).thenReturn(fancy);
        when(pluginManager.getPlugin("DecentHolograms")).thenReturn(decent);

        HologramProvider unavailable = mock(HologramProvider.class);
        when(unavailable.isAvailable()).thenReturn(false);
        HologramProvider available = mock(HologramProvider.class);
        when(available.isAvailable()).thenReturn(true);
        HologramProviderFactory factory = (plugin, logger) ->
            "FancyHolograms".equals(plugin.getName()) ? unavailable : available;
        HologramSettings settings = HologramSettings.fromConfig(
            Arrays.asList("FancyHolograms", "DecentHolograms"), true, 1.75, true,
            Collections.singletonList("Forge Station"));

        HologramProvider selected = new HologramProviderSelector(mock(Plugin.class), pluginManager,
            factory, Logger.getLogger("test")).select(settings);
        assertSame(available, selected);

        PluginManager emptyManager = mock(PluginManager.class);
        HologramProvider fallback = new HologramProviderSelector(mock(Plugin.class), emptyManager,
            factory, Logger.getLogger("test")).select(settings);
        assertTrue(fallback instanceof NoOpHologramProvider);
        assertFalse(fallback.isAvailable());
    }
}
