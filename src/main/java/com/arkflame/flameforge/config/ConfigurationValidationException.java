package com.arkflame.flameforge.config;

public class ConfigurationValidationException extends RuntimeException {

    private final ValidationReport validationReport;

    public ConfigurationValidationException(String message, ValidationReport validationReport) {
        super(message);
        this.validationReport = validationReport;
    }

    public ConfigurationValidationException(String message, Throwable cause, ValidationReport validationReport) {
        super(message, cause);
        this.validationReport = validationReport;
    }

    public ValidationReport getValidationReport() {
        return validationReport;
    }
}
