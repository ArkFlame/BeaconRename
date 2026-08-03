package com.arkflame.flameforge.menu;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoreTemplateRendererTest {

    private final LoreTemplateRenderer renderer = new LoreTemplateRenderer();

    @Test
    void testRenderEmptyTemplate() {
        List<String> result = renderer.render(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
        assertTrue(result.isEmpty());
    }

    @Test
    void testRenderScalarReplacement() {
        List<String> template = Arrays.asList("Hello %name%", "Value: %value%");
        Map<String, String> scalar = new HashMap<>();
        scalar.put("name", "World");
        scalar.put("value", "42");

        List<String> result = renderer.render(template, scalar, Collections.emptyMap());

        assertEquals(2, result.size());
        assertEquals("Hello World", result.get(0));
        assertEquals("Value: 42", result.get(1));
    }

    @Test
    void testRenderExpandableToken() {
        List<String> template = Arrays.asList("Header", "%lines%", "Footer");
        Map<String, String> scalar = new HashMap<>();
        Map<String, List<String>> expandable = new HashMap<>();
        expandable.put("lines", Arrays.asList("Line 1", "Line 2", "Line 3"));

        List<String> result = renderer.render(template, scalar, expandable);

        assertEquals(5, result.size());
        assertEquals("Header", result.get(0));
        assertEquals("Line 1", result.get(1));
        assertEquals("Line 2", result.get(2));
        assertEquals("Line 3", result.get(3));
        assertEquals("Footer", result.get(4));
    }

    @Test
    void testRenderEmptyStringOmitted() {
        List<String> template = Arrays.asList("Hello", "", "World");
        Map<String, String> scalar = new HashMap<>();
        scalar.put("empty", "");

        List<String> result = renderer.render(template, scalar, Collections.emptyMap());

        assertEquals(2, result.size());
        assertEquals("Hello", result.get(0));
        assertEquals("World", result.get(1));
    }
}
