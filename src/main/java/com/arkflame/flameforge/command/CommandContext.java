package com.arkflame.flameforge.command;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public final class CommandContext {

    public enum State {
        LOADING,
        READY,
        FAILED,
        UNAVAILABLE
    }

    private final State state;
    private final ReadyServices readyServices;
    private final StartupFailure startupFailure;
    private final Set<StartupFailure.Component> pendingComponents;

    private CommandContext(State state, ReadyServices readyServices, StartupFailure startupFailure,
            Set<StartupFailure.Component> pendingComponents) {
        this.state = state;
        this.readyServices = readyServices;
        this.startupFailure = startupFailure;
        this.pendingComponents = pendingComponents;
    }

    public static CommandContext loading() {
        Set<StartupFailure.Component> allRequired = EnumSet.of(
            StartupFailure.Component.CONFIGURATION,
            StartupFailure.Component.PLAYER_DATA,
            StartupFailure.Component.STATION_DATA,
            StartupFailure.Component.PENDING_DELIVERIES
        );
        return new CommandContext(State.LOADING, null, null, Collections.unmodifiableSet(allRequired));
    }

    public static CommandContext loading(Set<StartupFailure.Component> pending) {
        return new CommandContext(State.LOADING, null, null, Collections.unmodifiableSet(pending));
    }

    public static CommandContext ready(ReadyServices readyServices) {
        return new CommandContext(State.READY, readyServices, null, Collections.emptySet());
    }

    public static CommandContext failed(StartupFailure failure) {
        return new CommandContext(State.FAILED, null, failure, Collections.emptySet());
    }

    public static CommandContext unavailable() {
        return new CommandContext(State.UNAVAILABLE, null, null, Collections.emptySet());
    }

    public State getState() {
        return state;
    }

    public ReadyServices getReadyServices() {
        return readyServices;
    }

    public StartupFailure getStartupFailure() {
        return startupFailure;
    }

    public Set<StartupFailure.Component> getPendingComponents() {
        return pendingComponents;
    }

    public boolean isReady() {
        return state == State.READY && readyServices != null;
    }

    public boolean isUnavailable() {
        return state == State.UNAVAILABLE;
    }

    public boolean isFailed() {
        return state == State.FAILED;
    }

    public boolean isLoading() {
        return state == State.LOADING;
    }
}