package com.arkflame.flameforge.hologram;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HologramProviderSelectorTest {

    private org.mockito.MockedStatic<Bukkit> mockedBukkit;

    @BeforeEach
    void setUp() {
        mockedBukkit = mockStatic(Bukkit.class);
        mockedBukkit.when(Bukkit::getLogger).thenReturn(Logger.getLogger("test"));
    }

    @AfterEach
    void tearDown() {
        mockedBukkit.close();
    }

    @Test
    void noProviderOrDisabledConfigurationReturnsUnavailableNoOpWithReason() {
        HologramSettings disabledSettings = HologramSettings.fromConfig(
            Arrays.asList("FancyHolograms"), false, 1.75, true, Collections.singletonList("Line 1"));
        Plugin mockHostPlugin = mock(Plugin.class);
        HologramProviderSelector selector1 = new HologramProviderSelector(
            mockHostPlugin, mock(PluginManager.class), new HologramProviderFactory.Default(), Logger.getLogger("test"));
        HologramProvider provider1 = selector1.select(disabledSettings);
        assertNotNull(provider1);
        assertTrue(provider1 instanceof NoOpHologramProvider);
        assertFalse(provider1.isAvailable());
        assertEquals("disabled by configuration", provider1.getUnavailableReason());

        PluginManager emptyManager = mock(PluginManager.class);
        when(emptyManager.getPlugin(anyString())).thenReturn(null);
        HologramSettings emptySettings = HologramSettings.fromConfig(
            Collections.emptyList(), true, 1.75, true, Collections.singletonList("Line 1"));
        HologramProviderSelector selector2 = new HologramProviderSelector(mockHostPlugin, emptyManager, new HologramProviderFactory.Default(), Logger.getLogger("test"));
        HologramProvider provider2 = selector2.select(emptySettings);
        assertNotNull(provider2);
        assertTrue(provider2 instanceof NoOpHologramProvider);
        assertFalse(provider2.isAvailable());
    }

    @Test
    void configuredProviderOrderSelectsFirstEnabledCompatibleProvider() {
        Plugin fancyPlugin = mock(Plugin.class);
        when(fancyPlugin.isEnabled()).thenReturn(true);
        Plugin decentPlugin = mock(Plugin.class);
        when(decentPlugin.isEnabled()).thenReturn(true);
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("FancyHolograms")).thenReturn(fancyPlugin);
        when(pluginManager.getPlugin("DecentHolograms")).thenReturn(decentPlugin);

        Plugin mockHostPlugin = mock(Plugin.class);
        List<String> providerOrder = Arrays.asList("FancyHolograms", "DecentHolograms");
        HologramSettings settings = HologramSettings.fromConfig(
            providerOrder, true, 1.75, true, Collections.singletonList("Line 1"));

        HologramProviderSelector selector = new HologramProviderSelector(mockHostPlugin, pluginManager, new HologramProviderFactory.Default(), Logger.getLogger("test"));
        HologramProvider provider = selector.select(settings);

        assertNotNull(provider);
        assertTrue(provider instanceof NoOpHologramProvider);
    }

    @Test
    void disabledOrWrongApiProviderIsSkippedWithDiagnostic() {
        Plugin fancyPlugin = mock(Plugin.class);
        when(fancyPlugin.isEnabled()).thenReturn(false);
        org.bukkit.plugin.PluginDescriptionFile mockDesc = mock(org.bukkit.plugin.PluginDescriptionFile.class);
        when(mockDesc.getVersion()).thenReturn("1.0.0");
        when(fancyPlugin.getDescription()).thenReturn(mockDesc);
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPlugin("FancyHolograms")).thenReturn(fancyPlugin);

        Logger testLogger = Logger.getLogger("test-diag");
        testLogger.setUseParentHandlers(false);
        java.util.logging.LogRecord[] capturedRecord = new java.util.logging.LogRecord[1];
        java.util.logging.Handler captureHandler = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { capturedRecord[0] = record; }
            @Override public void flush() {}
            @Override public void close() {}
        };
        captureHandler.setLevel(java.util.logging.Level.INFO);
        testLogger.addHandler(captureHandler);

        Plugin mockHostPlugin = mock(Plugin.class);
        List<String> providerOrder = Arrays.asList("FancyHolograms");
        HologramSettings settings = HologramSettings.fromConfig(
            providerOrder, true, 1.75, true, Collections.singletonList("Line 1"));

        HologramProviderSelector selector = new HologramProviderSelector(mockHostPlugin, pluginManager, new HologramProviderFactory.Default(), testLogger);
        HologramProvider provider = selector.select(settings);

        assertNotNull(provider);
        assertTrue(provider instanceof NoOpHologramProvider);
        NoOpHologramProvider noOp = (NoOpHologramProvider) provider;
        assertEquals("FancyHolograms disabled (version 1.0.0)", noOp.getUnavailableReason());
        assertTrue(capturedRecord[0].getMessage().contains("Hologram provider: no supported provider"));

        testLogger.removeHandler(captureHandler);

        Plugin wrongApiPlugin = mock(Plugin.class);
        PluginManager wrongManager = mock(PluginManager.class);
        when(wrongManager.getPlugin(anyString())).thenReturn(null);

        HologramProviderSelector selector2 = new HologramProviderSelector(mockHostPlugin, wrongManager, new HologramProviderFactory.Default(), Logger.getLogger("test"));
        HologramProvider provider2 = selector2.select(settings);

        assertNotNull(provider2);
        assertTrue(provider2 instanceof NoOpHologramProvider);
        NoOpHologramProvider noOp2 = (NoOpHologramProvider) provider2;
        assertEquals("FancyHolograms not found", noOp2.getUnavailableReason());
    }

    @Test
    void fancyRemoveBindingSelectsStringOverload() throws Exception {
        Method method = FancyHologramsApiBindings.resolveRemoveHologramByNameMethod(
            FancyManagerOverloadFixture.class
        );
        FancyManagerOverloadFixture manager = new FancyManagerOverloadFixture();

        method.invoke(manager, "flameforge_test");

        assertArrayEquals(new Class<?>[]{String.class}, method.getParameterTypes());
        assertEquals("flameforge_test", manager.removedId);
        assertFalse(manager.objectOverloadCalled);
    }

    @Test
    void selectorNeverReturnsNullAndNoOpIsUnavailable() {
        HologramSettings settings = HologramSettings.fromConfig(
            Collections.emptyList(), true, 1.75, true, Collections.singletonList("Line 1"));
        Plugin mockHostPlugin = mock(Plugin.class);
        HologramProviderSelector selector = new HologramProviderSelector(
            mockHostPlugin, mock(PluginManager.class), new HologramProviderFactory.Default(), Logger.getLogger("test"));

        HologramProvider provider = selector.select(settings);

        assertNotNull(provider);
        assertFalse(provider.isAvailable());
        assertNotNull(provider.getUnavailableReason());
        assertTrue(provider instanceof HologramProvider);
    }

    public static final class FancyManagerOverloadFixture {
        private String removedId;
        private boolean objectOverloadCalled;

        public Object removeHologram(String hologramId) {
            this.removedId = hologramId;
            return null;
        }

        public void removeHologram(Object hologram) {
            this.objectOverloadCalled = true;
        }
    }
}
