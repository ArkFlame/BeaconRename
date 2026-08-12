package com.arkflame.flameforge.session;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class ForgeSessionServiceTest {
    private ForgeSessionService service;

    @BeforeEach
    void setUp() {
        service = new ForgeSessionService();
    }

    @Test
    void activeOpenSessionIsReused() {
        String playerId = UUID.randomUUID().toString();

        ForgeSession first = service.openSession(playerId);
        ForgeSession second = service.openSession(playerId);

        assertSame(first, second);
    }

    @Test
    void finishedForgeAllowsAnotherForgeForSamePlayer() {
        String playerId = UUID.randomUUID().toString();

        ForgeSession first = service.openSession(playerId);
        service.closeSession(playerId, closed -> {
            assertSame(first, closed);
            assertTrue(closed.isClosed());
        });

        ForgeSession reopened = service.openSession(playerId);

        assertNotSame(first, reopened);
        assertTrue(reopened.isOpen());
        assertFalse(reopened.isTerminal());
        assertTrue(service.hasActiveSession(playerId));
        assertSame(reopened, service.getExistingSession(playerId));
    }
}
