package com.arkflame.flameforge.testfakes;

import org.bukkit.plugin.java.JavaPlugin;
import org.mockito.Mockito;

import java.io.File;
import java.util.logging.Logger;

public final class MockJavaPlugin {

    private MockJavaPlugin() {
    }

    @SuppressWarnings("deprecation")
    public static JavaPlugin createMockPlugin() {
        JavaPlugin mockPlugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(mockPlugin.getDataFolder()).thenReturn(new File("/tmp/test-flameforge"));
        Mockito.when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("test-flameforge"));
        Mockito.when(mockPlugin.getServer()).thenReturn(Mockito.mock(org.bukkit.Server.class));
        return mockPlugin;
    }
}