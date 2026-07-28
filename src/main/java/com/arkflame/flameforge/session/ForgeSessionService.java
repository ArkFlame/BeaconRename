package com.arkflame.flameforge.session;

import com.arkflame.flameforge.forge.ForgeContext;
import com.arkflame.flameforge.forge.ForgeTransaction;
import com.arkflame.flameforge.model.ForgeSessionState;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public final class ForgeSessionService {
    private final Map<String, ForgeSession> sessionsByPlayerId = new ConcurrentHashMap<>();
    private volatile boolean shutdown;

    public ForgeSessionService() {
    }

    public ForgeSession openSession(String playerId) {
        Objects.requireNonNull(playerId);
        if (shutdown) {
            throw new IllegalStateException("Service is shut down");
        }
        ForgeSession existing = sessionsByPlayerId.get(playerId);
        if (existing != null && !existing.isTerminal()) {
            return existing;
        }
        ForgeSession newSession = new ForgeSession(playerId);
        ForgeSession raced = sessionsByPlayerId.putIfAbsent(playerId, newSession);
        return raced != null ? raced : newSession;
    }

    public ForgeSession getSession(String playerId) {
        return sessionsByPlayerId.get(playerId);
    }

    public ForgeSession getOrOpenSession(String playerId) {
        ForgeSession session = sessionsByPlayerId.get(playerId);
        if (session != null) {
            return session;
        }
        return openSession(playerId);
    }

    public ForgeSession getExistingSession(String playerId) {
        ForgeSession session = sessionsByPlayerId.get(playerId);
        if (session != null && !session.isTerminal()) {
            return session;
        }
        return null;
    }

    public ForgeSession computeIfAbsent(String playerId, BiFunction<String, ForgeSessionState, ForgeSession> function) {
        return sessionsByPlayerId.computeIfAbsent(playerId, k -> {
            ForgeSession newSession = new ForgeSession(k);
            return newSession;
        });
    }

    public boolean hasActiveSession(String playerId) {
        ForgeSession session = sessionsByPlayerId.get(playerId);
        return session != null && !session.isTerminal();
    }

    public void settleSession(String playerId, Consumer<ForgeSession> onSettle) {
        ForgeSession session = sessionsByPlayerId.get(playerId);
        if (session == null) {
            return;
        }
        synchronized (session) {
            if (session.isOpen()) {
                session.transitionToSettling();
                onSettle.accept(session);
            }
        }
    }

    public void closeSession(String playerId, Consumer<ForgeSession> onClose) {
        ForgeSession session = sessionsByPlayerId.get(playerId);
        if (session == null) {
            return;
        }
        synchronized (session) {
            if (session.isOpen()) {
                session.transitionToSettling();
            }
            if (session.isSettling()) {
                session.transitionToClosed();
                onClose.accept(session);
            }
        }
    }

    public void closeAllSessions(Consumer<ForgeSession> onClose) {
        sessionsByPlayerId.values().forEach(session -> {
            synchronized (session) {
                if (!session.isClosed()) {
                    session.transitionToClosed();
                    onClose.accept(session);
                }
            }
        });
    }

    public void shutdown() {
        shutdown = true;
        closeAllSessions(session -> {});
    }

    public Collection<ForgeSession> getAllSessions() {
        return sessionsByPlayerId.values();
    }

    public int getActiveSessionCount() {
        return (int) sessionsByPlayerId.values().stream()
            .filter(s -> !s.isTerminal())
            .count();
    }

    public void removeSession(String playerId) {
        sessionsByPlayerId.remove(playerId);
    }

    public boolean isShutdown() {
        return shutdown;
    }
}
