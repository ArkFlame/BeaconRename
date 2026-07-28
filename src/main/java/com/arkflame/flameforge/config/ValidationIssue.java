package com.arkflame.flameforge.config;

import java.util.Objects;

public class ValidationIssue {
    private final String path;
    private final String field;
    private final Severity severity;
    private final String message;

    public enum Severity {
        ERROR,
        WARNING
    }

    public ValidationIssue(String path, String field, Severity severity, String message) {
        this.path = Objects.requireNonNull(path, "path");
        this.field = Objects.requireNonNull(field, "field");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.message = Objects.requireNonNull(message, "message");
    }

    public String getPath() {
        return path;
    }

    public String getField() {
        return field;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getFullPath() {
        if (path.isEmpty()) {
            return field;
        }
        return path + "." + field;
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public boolean isWarning() {
        return severity == Severity.WARNING;
    }

    @Override
    public String toString() {
        return "[" + severity + "] " + getFullPath() + ": " + message;
    }
}
