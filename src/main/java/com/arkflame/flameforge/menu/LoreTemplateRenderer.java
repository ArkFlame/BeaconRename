package com.arkflame.flameforge.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders lore templates with scalar and expandable placeholders.
 * - line equal to one expandable token -> splice list
 * - ordinary line -> scalar replacement
 * - resolved "" -> omit
 * - resolved " " -> retain
 */
public final class LoreTemplateRenderer {

    public List<String> render(List<String> templates, Map<String, String> scalar, Map<String, List<String>> expandable) {
        if (templates == null || templates.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, String> safeScalar = scalar != null ? new HashMap<>(scalar) : new HashMap<>();
        Map<String, List<String>> safeExpandable = expandable != null ? new HashMap<>(expandable) : new HashMap<>();

        List<String> result = new ArrayList<>();
        for (String line : templates) {
            if (line == null) {
                continue;
            }
            String resolved = resolveScalar(line, safeScalar);
            String expandableKey = resolved.startsWith("%") && resolved.endsWith("%")
                ? resolved.substring(1, resolved.length() - 1)
                : resolved;
            if (safeExpandable.containsKey(expandableKey)) {
                List<String> expansion = safeExpandable.get(expandableKey);
                if (expansion != null) {
                    for (String expLine : expansion) {
                        result.add(expLine);
                    }
                }
            } else {
                if (resolved.isEmpty()) {
                    // omit empty
                } else if (" ".equals(resolved)) {
                    result.add(resolved);
                } else {
                    result.add(resolved);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    private String resolveScalar(String input, Map<String, String> scalar) {
        String result = input;
        for (Map.Entry<String, String> e : scalar.entrySet()) {
            String placeholder = "%" + e.getKey() + "%";
            String value = e.getValue() != null ? e.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
