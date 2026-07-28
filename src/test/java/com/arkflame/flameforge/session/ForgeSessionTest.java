package com.arkflame.flameforge.session;

import com.arkflame.flameforge.forge.ForgeContext;
import com.arkflame.flameforge.forge.ForgeResolution;
import com.arkflame.flameforge.forge.ForgeTransaction;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.model.ForgeSessionState;
import com.arkflame.flameforge.model.PlayerForgeState;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ForgeSessionTest {

    @Test
    void newSession_isOpen() {
        ForgeSession session = new ForgeSession("player1");

        assertTrue(session.isOpen());
        assertFalse(session.isProcessing());
        assertFalse(session.isSettling());
        assertFalse(session.isClosed());
        assertFalse(session.isTerminal());
        assertEquals(ForgeSessionState.OPEN, session.getState());
    }

    @Test
    void atomicOpenToProcessing_validTransition_succeeds() {
        ForgeSession session = new ForgeSession("player1");
        ForgeContext ctx = ForgeContext.builder()
            .transactionId(UUID.randomUUID())
            .playerId("player1")
            .playerState(PlayerForgeState.of("player1"))
            .configSnapshot(ConfigSnapshot.builder().build())
            .build();
        ForgeTransaction tx = ForgeTransaction.builder()
            .transactionId(ctx.getTransactionId())
            .context(ctx)
            .build();

        boolean result = session.atomicOpenToProcessing(ctx, tx);

        assertTrue(result);
        assertTrue(session.isProcessing());
        assertEquals(ForgeSessionState.PROCESSING, session.getState());
        assertSame(tx, session.getCurrentTransaction());
        assertSame(ctx, session.getCurrentContext());
    }

    @Test
    void atomicOpenToProcessing_fromNonOpenState_fails() {
        ForgeSession session = new ForgeSession("player1");
        ForgeContext ctx = ForgeContext.builder()
            .transactionId(UUID.randomUUID())
            .playerId("player1")
            .playerState(PlayerForgeState.of("player1"))
            .configSnapshot(ConfigSnapshot.builder().build())
            .build();
        ForgeTransaction tx = ForgeTransaction.builder()
            .transactionId(ctx.getTransactionId())
            .context(ctx)
            .build();

        session.atomicOpenToProcessing(ctx, tx);
        boolean secondAttempt = session.atomicOpenToProcessing(ctx, tx);

        assertFalse(secondAttempt);
    }

    @Test
    void transitionToSettling_fromProcessing_succeeds() {
        ForgeSession session = new ForgeSession("player1");
        openToProcessing(session);

        boolean result = session.transitionToSettling();

        assertTrue(result);
        assertTrue(session.isSettling());
        assertEquals(ForgeSessionState.SETTLING, session.getState());
    }

    @Test
    void transitionToSettling_fromOpen_fails() {
        ForgeSession session = new ForgeSession("player1");

        boolean result = session.transitionToSettling();

        assertFalse(result);
        assertTrue(session.isOpen());
    }

    @Test
    void transitionToSettling_fromClosed_fails() {
        ForgeSession session = new ForgeSession("player1");

        session.transitionToClosed();
        boolean result = session.transitionToSettling();

        assertFalse(result);
    }

    @Test
    void transitionToClosed_fromProcessing_succeeds() {
        ForgeSession session = new ForgeSession("player1");
        openToProcessing(session);

        boolean result = session.transitionToClosed();

        assertTrue(result);
        assertTrue(session.isClosed());
        assertTrue(session.isTerminal());
        assertEquals(ForgeSessionState.CLOSED, session.getState());
    }

    @Test
    void transitionToClosed_fromSettling_succeeds() {
        ForgeSession session = new ForgeSession("player1");
        openToProcessing(session);
        session.transitionToSettling();

        boolean result = session.transitionToClosed();

        assertTrue(result);
        assertTrue(session.isClosed());
    }

    @Test
    void transitionToClosed_fromClosed_isIdempotent() {
        ForgeSession session = new ForgeSession("player1");

        session.transitionToClosed();
        boolean first = session.transitionToClosed();

        assertFalse(first);
        assertTrue(session.isClosed());
    }

    @Test
    void transitionToClosed_fromOpen_succeeds() {
        ForgeSession session = new ForgeSession("player1");

        boolean result = session.transitionToClosed();

        assertTrue(result);
        assertTrue(session.isClosed());
    }

    @Test
    void setTerminalResolution_andClaimTerminal_roundTrip() {
        ForgeSession session = new ForgeSession("player1");
        ForgeResolution resolution = ForgeResolution.failure(UUID.randomUUID(), "test error", true);
        AtomicReference<ForgeResolution> captured = new AtomicReference<>();

        session.setTerminalResolution(resolution);
        session.transitionToClosed();
        session.claimTerminal(captured::set);

        assertSame(resolution, captured.get());
    }

    @Test
    void claimTerminal_whenNotClosed_noOp() {
        ForgeSession session = new ForgeSession("player1");
        ForgeResolution resolution = ForgeResolution.failure(UUID.randomUUID(), "test error", true);
        AtomicBoolean called = new AtomicBoolean(false);

        session.setTerminalResolution(resolution);
        session.claimTerminal(r -> called.set(true));

        assertFalse(called.get());
    }

    @Test
    void claimTerminal_whenClosedNoResolution_noOp() {
        ForgeSession session = new ForgeSession("player1");
        session.transitionToClosed();
        AtomicBoolean called = new AtomicBoolean(false);

        session.claimTerminal(r -> called.set(true));

        assertFalse(called.get());
    }

    @Test
    void clearTransaction_nullsContextAndTransaction() {
        ForgeSession session = new ForgeSession("player1");
        openToProcessing(session);

        session.clearTransaction();

        assertNull(session.getCurrentTransaction());
        assertNull(session.getCurrentContext());
    }

    @Test
    void setPlayerStateSnapshot_storesSnapshot() {
        ForgeSession session = new ForgeSession("player1");
        PlayerForgeState snapshot = com.arkflame.flameforge.model.PlayerForgeState.of("player1");

        session.setPlayerStateSnapshot(snapshot);

        assertSame(snapshot, session.getPlayerStateSnapshot());
    }

    @Test
    void isTerminal_whenClosed_true() {
        ForgeSession session = new ForgeSession("player1");
        session.transitionToClosed();

        assertTrue(session.isTerminal());
    }

    @Test
    void isTerminal_whenOpen_false() {
        ForgeSession session = new ForgeSession("player1");

        assertFalse(session.isTerminal());
    }

    @Test
    void isTerminal_whenProcessing_false() {
        ForgeSession session = new ForgeSession("player1");
        openToProcessing(session);

        assertFalse(session.isTerminal());
    }

    private void openToProcessing(ForgeSession session) {
        ForgeContext ctx = ForgeContext.builder()
            .transactionId(UUID.randomUUID())
            .playerId(session.getPlayerId())
            .playerState(PlayerForgeState.of(session.getPlayerId()))
            .configSnapshot(ConfigSnapshot.builder().build())
            .build();
        ForgeTransaction tx = ForgeTransaction.builder()
            .transactionId(ctx.getTransactionId())
            .context(ctx)
            .build();
        session.atomicOpenToProcessing(ctx, tx);
    }
}
