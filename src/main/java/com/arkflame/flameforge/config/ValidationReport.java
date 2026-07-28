package com.arkflame.flameforge.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationReport {
    private final List<ValidationIssue> issues = new ArrayList<>();

    public void add(ValidationIssue issue) {
        issues.add(issue);
    }

    public void addError(String path, String field, String message) {
        issues.add(new ValidationIssue(path, field, ValidationIssue.Severity.ERROR, message));
    }

    public void addWarning(String path, String field, String message) {
        issues.add(new ValidationIssue(path, field, ValidationIssue.Severity.WARNING, message));
    }

    public boolean hasErrors() {
        for (ValidationIssue issue : issues) {
            if (issue.isError()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasWarnings() {
        for (ValidationIssue issue : issues) {
            if (issue.isWarning()) {
                return true;
            }
        }
        return false;
    }

    public List<ValidationIssue> getIssues() {
        return Collections.unmodifiableList(new ArrayList<>(issues));
    }

    public List<ValidationIssue> getErrors() {
        List<ValidationIssue> errors = new ArrayList<>();
        for (ValidationIssue issue : issues) {
            if (issue.isError()) {
                errors.add(issue);
            }
        }
        return Collections.unmodifiableList(errors);
    }

    public List<ValidationIssue> getWarnings() {
        List<ValidationIssue> warnings = new ArrayList<>();
        for (ValidationIssue issue : issues) {
            if (issue.isWarning()) {
                warnings.add(issue);
            }
        }
        return Collections.unmodifiableList(warnings);
    }

    public void merge(ValidationReport other) {
        issues.addAll(other.issues);
    }

    public int size() {
        return issues.size();
    }

    public void clear() {
        issues.clear();
    }

    public boolean isEmpty() {
        return issues.isEmpty();
    }
}
