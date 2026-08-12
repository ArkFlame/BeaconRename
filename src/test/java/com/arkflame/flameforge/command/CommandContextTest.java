package com.arkflame.flameforge.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CommandContextTest {

    @Test
    void contextLifecycleExposesServicesOnlyWhenReady() {
        ReadyServices services = mock(ReadyServices.class);

        CommandContext loading = CommandContext.loading();
        CommandContext unavailable = CommandContext.unavailable();
        CommandContext failed = CommandContext.failed(StartupFailure.create(
            StartupFailure.Component.CONFIGURATION, new RuntimeException("startup failed"), 1L));
        CommandContext ready = CommandContext.ready(services);

        assertNull(loading.getReadyServices());
        assertNull(unavailable.getReadyServices());
        assertNull(failed.getReadyServices());
        assertFalse(loading.isReady());
        assertFalse(unavailable.isReady());
        assertFalse(failed.isReady());
        assertTrue(ready.isReady());
        assertSame(services, ready.getReadyServices());
    }
}
