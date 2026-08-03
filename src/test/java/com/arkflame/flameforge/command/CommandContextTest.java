package com.arkflame.flameforge.command;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.TierRepository;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandContextTest {

    @Test
    void loadingAndUnavailableStatesHaveNoReadyServices() {
        CommandContext loadingCtx = CommandContext.loading();
        assertNull(loadingCtx.getReadyServices());
        assertFalse(loadingCtx.isReady());
        assertFalse(loadingCtx.isUnavailable());
        assertTrue(loadingCtx.isLoading());

        CommandContext unavailableCtx = CommandContext.unavailable();
        assertNull(unavailableCtx.getReadyServices());
        assertFalse(unavailableCtx.isReady());
        assertTrue(unavailableCtx.isUnavailable());
        assertFalse(unavailableCtx.isLoading());
    }

    @Test
    void readyRequiresAndExposesReadyServices() {
        ReadyServices services = mock(ReadyServices.class);
        CommandContext ctx = CommandContext.ready(services);

        assertEquals(CommandContext.State.READY, ctx.getState());
        assertTrue(ctx.isReady());
        assertFalse(ctx.isLoading());
        assertFalse(ctx.isUnavailable());
        assertSame(services, ctx.getReadyServices());
    }

    @Test
    void failedStateExposesSafeSummary() {
        StartupFailure emptyFailure = StartupFailure.create(
            StartupFailure.Component.CONFIGURATION,
            new RuntimeException("Initialization failed."),
            1L);
        CommandContext empty = CommandContext.failed(emptyFailure);
        assertEquals(CommandContext.State.FAILED, empty.getState());
        assertTrue(empty.isFailed());
        assertEquals("Initialization failed.", empty.getStartupFailure().getReason());
        assertNull(empty.getReadyServices());
        assertFalse(empty.isReady());

        StartupFailure multilineFailure = StartupFailure.create(
            StartupFailure.Component.CONFIGURATION,
            new RuntimeException(" first\nsecond\rthird "),
            2L);
        CommandContext multiline = CommandContext.failed(multilineFailure);
        assertEquals("first second third", multiline.getStartupFailure().getReason());

        StartupFailure longFailure = StartupFailure.create(
            StartupFailure.Component.CONFIGURATION,
            new RuntimeException(new String(new char[300]).replace('\0', 'x')),
            3L);
        CommandContext longSummary = CommandContext.failed(longFailure);
        assertEquals(240, longSummary.getStartupFailure().getReason().length());
    }

    @Test
    void commandLifecycleTransitionsInstanceOwnedContextAndIsReady() {
        ConfigService configService = mock(ConfigService.class);
        TierRepository tierRepository = mock(TierRepository.class);
        FlameForgeCommand command = new FlameForgeCommand(
            mock(JavaPlugin.class),
            mock(SchedulerBridge.class),
            null,
            configService,
            tierRepository,
            new CommandSuggestionIndex(tierRepository)
        );

        assertEquals(CommandContext.State.LOADING, command.snapshot().getState());

        command.markUnavailable();
        assertEquals(CommandContext.State.UNAVAILABLE, command.snapshot().getState());
        assertTrue(command.snapshot().isUnavailable());

        command.markLoading();
        assertEquals(CommandContext.State.LOADING, command.snapshot().getState());
        assertTrue(command.snapshot().isLoading());

        ReadyServices services = mock(ReadyServices.class);
        command.markReady(services);
        assertEquals(CommandContext.State.READY, command.snapshot().getState());
        assertTrue(command.snapshot().isReady());
        assertSame(services, command.snapshot().getReadyServices());

        command.markFailed(StartupFailure.create(
            StartupFailure.Component.CONFIGURATION,
            new RuntimeException("test failure"),
            4L));
        assertEquals(CommandContext.State.FAILED, command.snapshot().getState());
        assertTrue(command.snapshot().isFailed());
        assertEquals("test failure", command.snapshot().getStartupFailure().getReason());
    }
}
