package com.arkflame.flameforge.session;

import com.arkflame.flameforge.forge.ForgeContext;
import com.arkflame.flameforge.forge.ForgeResolution;
import com.arkflame.flameforge.forge.ForgeTransaction;
import com.arkflame.flameforge.model.ForgeSessionState;
import com.arkflame.flameforge.model.PlayerForgeState;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class ForgeSession {
    private final String playerId;
    private final UUID sessionId;
    private volatile ForgeSessionState state;
    private volatile ForgeTransaction currentTransaction;
    private volatile ForgeContext currentContext;
    private volatile ForgeResolution terminalResolution;
    private volatile PlayerForgeState playerStateSnapshot;

    public ForgeSession(String playerId) {
        this.playerId = Objects.requireNonNull(playerId);
        this.sessionId = UUID.randomUUID();
        this.state = ForgeSessionState.OPEN;
    }

    public String getPlayerId() { return playerId; }
    public UUID getSessionId() { return sessionId; }
    public ForgeSessionState getState() { return state; }
    public ForgeTransaction getCurrentTransaction() { return currentTransaction; }
    public ForgeContext getCurrentContext() { return currentContext; }
    public ForgeResolution getTerminalResolution() { return terminalResolution; }
    public PlayerForgeState getPlayerStateSnapshot() { return playerStateSnapshot; }

    public boolean isOpen() { return state == ForgeSessionState.OPEN; }
    public boolean isProcessing() { return state == ForgeSessionState.PROCESSING; }
    public boolean isSettling() { return state == ForgeSessionState.SETTLING; }
    public boolean isClosed() { return state == ForgeSessionState.CLOSED; }
    public boolean isTerminal() { return state == ForgeSessionState.CLOSED; }

    public synchronized boolean atomicOpenToProcessing(ForgeContext context, ForgeTransaction transaction) {
        if (state != ForgeSessionState.OPEN) {
            return false;
        }
        this.currentContext = Objects.requireNonNull(context);
        this.currentTransaction = Objects.requireNonNull(transaction);
        this.state = ForgeSessionState.PROCESSING;
        return true;
    }

    public synchronized boolean transitionToSettling() {
        if (state != ForgeSessionState.PROCESSING) {
            return false;
        }
        this.state = ForgeSessionState.SETTLING;
        return true;
    }

    public synchronized boolean transitionToClosed() {
        if (state == ForgeSessionState.CLOSED) {
            return false;
        }
        this.state = ForgeSessionState.CLOSED;
        return true;
    }

    public synchronized void setTerminalResolution(ForgeResolution resolution) {
        this.terminalResolution = Objects.requireNonNull(resolution);
    }

    public synchronized void setPlayerStateSnapshot(PlayerForgeState snapshot) {
        this.playerStateSnapshot = Objects.requireNonNull(snapshot);
    }

    public synchronized void clearTransaction() {
        this.currentTransaction = null;
        this.currentContext = null;
    }

    public synchronized void claimTerminal(Consumer<ForgeResolution> callback) {
        if (state != ForgeSessionState.CLOSED || terminalResolution == null) {
            return;
        }
        callback.accept(terminalResolution);
    }

    @Override
    public String toString() {
        return "ForgeSession{playerId=" + playerId + ", sessionId=" + sessionId +
               ", state=" + state + "}";
    }
}
