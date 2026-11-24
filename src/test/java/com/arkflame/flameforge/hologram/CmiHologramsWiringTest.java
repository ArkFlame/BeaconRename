package com.arkflame.flameforge.hologram;

import com.arkflame.flameforge.config.ConfigSnapshot;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CmiHologramsWiringTest {

    private static final Logger LOGGER = Logger.getLogger("test");

    @Test
    void defaultFactoryBuildsCmiProvider() {
        Plugin cmi = mock(Plugin.class);
        when(cmi.getName()).thenReturn("CMI");

        HologramProvider provider = new HologramProviderFactory.Default().create(cmi, LOGGER);

        assertEquals("CMI", provider.getName());
    }

    @Test
    void selectorAcceptsConfiguredCmiProvider() {
        Plugin cmi = mock(Plugin.class);
        when(cmi.getName()).thenReturn("CMI");
        when(cmi.isEnabled()).thenReturn(true);
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("CMI")).thenReturn(cmi);

        HologramProvider available = mock(HologramProvider.class);
        when(available.isAvailable()).thenReturn(true);
        when(available.getName()).thenReturn("CMI");
        HologramProviderFactory factory = (plugin, logger) -> available;
        HologramSettings settings = HologramSettings.fromConfig(Collections.singletonList("CMI"),
                true, 1.25, true, Collections.singletonList("line"));

        HologramProvider selected = new HologramProviderSelector(mock(Plugin.class), pluginManager,
                factory, LOGGER).select(settings);

        assertSame(available, selected);
    }

    @Test
    void settingsKeepCmiAndDefaultOrderAppendsItLast() {
        HologramSettings explicit = HologramSettings.fromConfig(
                Arrays.asList("FancyHolograms", "CMI"), true, 1.25, true,
                Collections.singletonList("line"));
        assertEquals(Arrays.asList("FancyHolograms", "CMI"), explicit.getProviderOrder());

        HologramSettings defaults = HologramSettings.fromSnapshot(ConfigSnapshot.builder().build());
        assertEquals(Arrays.asList("FancyHolograms", "DecentHolograms", "CMI"),
                defaults.getProviderOrder());
    }
}
