package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.text.MessageArguments;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LoreTemplateRendererTest {

    private final LoreTemplateRenderer renderer = new LoreTemplateRenderer();

    @Test
    void testRenderEmptyTemplate() {
        List<String> result = renderer.render(Collections.emptyList(), MessageArguments.create());
        assertTrue(result.isEmpty());
    }

    @Test
    void testRenderNullArguments() {
        List<String> template = Arrays.asList("Hello", "World");
        List<String> result = renderer.render(template, null);
        assertEquals(2, result.size());
        assertEquals("Hello", result.get(0));
        assertEquals("World", result.get(1));
    }

    @Test
    void testRenderLineExpansionThroughLines() {
        List<String> template = Arrays.asList("Header", "%lines%", "Footer");
        MessageArguments args = MessageArguments.create();
        args.lines("lines", Arrays.asList("Line 1", "Line 2", "Line 3"));

        List<String> result = renderer.render(template, args);

        assertEquals(5, result.size());
        assertEquals("Header", result.get(0));
        assertEquals("Line 1", result.get(1));
        assertEquals("Line 2", result.get(2));
        assertEquals("Line 3", result.get(3));
        assertEquals("Footer", result.get(4));
    }

    @Test
    void testRenderScalarTokenPreservedForTextRenderer() {
        List<String> template = Arrays.asList("Hello %name%", "Value: %value%");
        MessageArguments args = MessageArguments.create();
        args.string("name", "World");
        args.string("value", "42");

        List<String> result = renderer.render(template, args);

        assertEquals(2, result.size());
        assertEquals("Hello %name%", result.get(0));
        assertEquals("Value: %value%", result.get(1));
    }

    @Test
    void testRenderEmptyExpansionOmitted() {
        List<String> template = Arrays.asList("Header", "%empty%", "Footer");
        MessageArguments args = MessageArguments.create();
        args.lines("empty", Collections.emptyList());

        List<String> result = renderer.render(template, args);

        assertEquals(2, result.size());
        assertEquals("Header", result.get(0));
        assertEquals("Footer", result.get(1));
    }

    @Test
    void testRenderLiteralBlankRetained() {
        List<String> template = Arrays.asList("Hello", " ", "World");
        MessageArguments args = MessageArguments.create();

        List<String> result = renderer.render(template, args);

        assertEquals(3, result.size());
        assertEquals("Hello", result.get(0));
        assertEquals(" ", result.get(1));
        assertEquals("World", result.get(2));
    }

    @Test
    void testRenderNullLineOmitted() {
        List<String> template = Arrays.asList("Hello", null, "World");
        MessageArguments args = MessageArguments.create();

        List<String> result = renderer.render(template, args);

        assertEquals(2, result.size());
        assertEquals("Hello", result.get(0));
        assertEquals("World", result.get(1));
    }

    @Test
    void testRenderNonMatchingPercentTokenPreserved() {
        List<String> template = Arrays.asList("Hello %unknown%");
        MessageArguments args = MessageArguments.create();

        List<String> result = renderer.render(template, args);

        assertEquals(1, result.size());
        assertEquals("Hello %unknown%", result.get(0));
    }

    @Test
    void testRenderEmptyExpansionListWithNullElements() {
        List<String> template = Arrays.asList("Header", "%items%", "Footer");
        MessageArguments args = MessageArguments.create();
        args.lines("items", Arrays.asList("Item 1", null, "Item 2"));

        List<String> result = renderer.render(template, args);

        assertEquals(4, result.size());
        assertEquals("Header", result.get(0));
        assertEquals("Item 1", result.get(1));
        assertEquals("Item 2", result.get(2));
        assertEquals("Footer", result.get(3));
    }
}
