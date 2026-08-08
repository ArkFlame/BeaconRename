package com.arkflame.flameforge.config;

import java.util.Collections;
import java.util.List;

public class ConfigurationValidationException extends RuntimeException {

    private static final int MAX_MESSAGE_LENGTH = 500;

    private final List<ValidationIssue> issues;

    public ConfigurationValidationException(String message, ValidationReport validationReport) {
        super(buildMessage(message, validationReport));
        this.issues = validationReport != null
            ? Collections.unmodifiableList(new java.util.ArrayList<>(validationReport.getIssues()))
            : Collections.emptyList();
    }

    public ConfigurationValidationException(String message, Throwable cause, ValidationReport validationReport) {
        super(buildMessage(message, validationReport), cause);
        this.issues = validationReport != null
            ? Collections.unmodifiableList(new java.util.ArrayList<>(validationReport.getIssues()))
            : Collections.emptyList();
    }

    private static String buildMessage(String baseMessage, ValidationReport report) {
        StringBuilder sb = new StringBuilder(baseMessage);
        sb.append("; errors=");
        if (report == null || report.getErrors().isEmpty()) {
            sb.append("0");
        } else {
            sb.append(report.getErrors().size());
            ValidationIssue firstError = report.getErrors().get(0);
            sb.append("; first=");
            sb.append(firstError.getFullPath());
            sb.append(": ");
            sb.append(firstError.getMessage());
        }
        String fullMessage = sb.toString();
        if (fullMessage.length() > MAX_MESSAGE_LENGTH) {
            return fullMessage.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }
        return fullMessage;
    }

    public List<ValidationIssue> getIssues() {
        return issues;
    }
}
