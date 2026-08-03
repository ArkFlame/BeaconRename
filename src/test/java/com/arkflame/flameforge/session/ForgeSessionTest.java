package com.arkflame.flameforge.session;

import com.arkflame.flameforge.forge.ForgeContext;
import com.arkflame.flameforge.forge.ForgePlan;
import com.arkflame.flameforge.forge.ForgeResolution;
import com.arkflame.flameforge.forge.ForgeTransaction;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeSessionState;
import com.arkflame.flameforge.model.PlayerForgeState;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ForgeSessionTest {

    @Test
    void newSessionStartsOpenAndNonTerminal() {
        ForgeSession session = new ForgeSession("player1");

        assertTrue(session.isOpen());
        assertFalse(session.isProcessing());
        assertFalse(session.isSettling());
        assertFalse(session.isClosed());
        assertFalse(session.isTerminal());
        assertEquals(ForgeSessionState.OPEN, session.getState());
    }

    @Test
    void openProcessingSettlingClosedTransitionMatrixIsEnforced() {
        ForgeSession session = new ForgeSession("player1");
        for (String transition : new String[] {"openToProcessing", "processingToSettling", "settlingToClosed", "closedIdempotent"}) {
            switch (transition) {
                case "openToProcessing":
                    assertTrue(session.isOpen());
                    openToProcessing(session);
                    assertTrue(session.isProcessing());
                    assertEquals(ForgeSessionState.PROCESSING, session.getState());
                    break;
                case "processingToSettling":
                    openToProcessing(session);
                    boolean toSettling = session.transitionToSettling();
                    assertTrue(toSettling);
                    assertTrue(session.isSettling());
                    assertEquals(ForgeSessionState.SETTLING, session.getState());
                    break;
                case "settlingToClosed":
                    openToProcessing(session);
                    session.transitionToSettling();
                    boolean toClosed = session.transitionToClosed();
                    assertTrue(toClosed);
                    assertTrue(session.isClosed());
                    assertTrue(session.isTerminal());
                    assertEquals(ForgeSessionState.CLOSED, session.getState());
                    break;
                case "closedIdempotent":
                    session.transitionToClosed();
                    boolean secondClose = session.transitionToClosed();
                    assertFalse(secondClose);
                    assertTrue(session.isClosed());
                    break;
            }
        }
    }

    @Test
    void closedTransitionIsIdempotent() {
        ForgeSession session = new ForgeSession("player1");

        boolean first = session.transitionToClosed();
        boolean second = session.transitionToClosed();

        assertTrue(first);
        assertFalse(second);
        assertTrue(session.isClosed());
        assertTrue(session.isTerminal());
    }

    @Test
    void terminalResolutionCanBeClaimedExactlyOnceOnlyAfterClose() {
        ForgeSession session = new ForgeSession("player1");
        ForgeResolution resolution = ForgeResolution.failure(UUID.randomUUID(), ForgeOutcomeCategory.BREAK, "test error", true);
        AtomicReference<ForgeResolution> captured = new AtomicReference<>();

        session.setTerminalResolution(resolution);
        session.claimTerminal(captured::set);
        assertNull(captured.get());

        session.transitionToClosed();
        session.claimTerminal(captured::set);
        assertSame(resolution, captured.get());

        session.claimTerminal(captured::set);
        assertSame(resolution, captured.get());
    }

    @Test
    void clearTransactionRemovesTransactionContext() {
        ForgeSession session = new ForgeSession("player1");
        openToProcessing(session);

        assertNotNull(session.getCurrentTransaction());
        assertNotNull(session.getCurrentContext());

        session.clearTransaction();

        assertNull(session.getCurrentTransaction());
        assertNull(session.getCurrentContext());
    }

    @Test
    void playerStateSnapshotRoundTrips() {
        ForgeSession session = new ForgeSession("player1");
        PlayerForgeState snapshot = PlayerForgeState.of("player1");

        session.setPlayerStateSnapshot(snapshot);

        assertSame(snapshot, session.getPlayerStateSnapshot());
    }

    private void openToProcessing(ForgeSession session) {
        ForgePlan plan = ForgePlan.builder()
            .input(new org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_SWORD))
            .currentTierLevel(0)
            .targetTierLevel(1)
            .build();
        ForgeContext ctx = ForgeContext.builder()
            .transactionId(UUID.randomUUID())
            .playerId(session.getPlayerId())
            .playerState(PlayerForgeState.of(session.getPlayerId()))
            .configSnapshot(ConfigSnapshot.builder().build())
            .plan(plan)
            .build();
        ForgeTransaction tx = ForgeTransaction.builder()
            .transactionId(ctx.getTransactionId())
            .context(ctx)
            .build();
        session.atomicOpenToProcessing(ctx, tx);
    }
}
