package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.text.MessageArguments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Renders lore templates with expandable line placeholders.
 * - line exactly "%key%" AND args has lineValues[key] -> splice list
 * - null line -> omit
 * - empty expansion line -> omit
 * - literal " " -> retain
 * - other lines -> return unchanged to TextRenderer
 */
public final class LoreTemplateRenderer {

    public List<String> render(final List<String> templates, final MessageArguments arguments) {
        if (templates == null || templates.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<String>> lineValues = arguments != null ? arguments.getLineValues() : Collections.emptyMap();

        List<String> result = new ArrayList<>();
        for (String line : templates) {
            if (line == null) {
                continue;
            }
            if (line.startsWith("%") && line.endsWith("%") && line.length() > 2) {
                String key = line.substring(1, line.length() - 1);
                if (lineValues.containsKey(key)) {
                    List<String> expansion = lineValues.get(key);
                    if (expansion != null) {
                        for (String expLine : expansion) {
                            if (expLine != null && !expLine.isEmpty()) {
                                result.add(expLine);
                            }
                        }
                    }
                    continue;
                }
            }
            if (line.isEmpty()) {
                continue;
            } else if (" ".equals(line)) {
                result.add(line);
            } else {
                result.add(line);
            }
        }
        return Collections.unmodifiableList(result);
    }
}