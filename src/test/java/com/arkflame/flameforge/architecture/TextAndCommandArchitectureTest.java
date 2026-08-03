package com.arkflame.flameforge.architecture;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class TextAndCommandArchitectureTest {

    private final String projectDir = System.getProperty("user.dir");

    @Test
    void textPipelineHasOneRendererAndNoRawConfiguredTemplateRendering() {
        File textBridgeFile = new File(projectDir,
            "src/main/java/com/arkflame/flameforge/text/TextBridge.java");
        assertTrue(textBridgeFile.exists(), "TextBridge.java must exist");
        String content = readFile(textBridgeFile);

        assertFalse(content.contains("fromLegacy"),
            "TextBridge must not use fromLegacy fallback");
        assertFalse(content.contains("return Component.text(input)"),
            "TextBridge parse methods must not use Component.text(input) as raw template fallback");

        File msgServiceFile = new File(projectDir,
            "src/main/java/com/arkflame/flameforge/text/MessageService.java");
        assertTrue(msgServiceFile.exists(), "MessageService.java must exist");
        String msgContent = readFile(msgServiceFile);

        assertFalse(msgContent.contains("private final HashMap"),
            "MessageService must not store mutable HashMap fields for explicitValues");
    }

    @Test
    void commandCatalogOwnsRootMetadataAndAllCommandMethodsReturnTrue() {
        File cmdDir = new File(projectDir,
            "src/main/java/com/arkflame/flameforge/command");
        assertTrue(cmdDir.exists(), "command package must exist");

        Pattern staticListField = Pattern.compile(
            "(?m)^\\s*(?:public|protected|private)?\\s*static\\s+(?:final\\s+)?List<[^;{]+[=;]");
        boolean commandNodeFound = false;
        for (File file : listJavaFiles(cmdDir)) {
            String content = readFile(file);
            if ("CommandNode.java".equals(file.getName())) {
                commandNodeFound = true;
                assertTrue(content.contains("permittedRootNames"),
                    "CommandNode must own root command suggestions");
            } else {
                assertFalse(staticListField.matcher(content).find(),
                    "Static command list found outside CommandNode: " + file.getName());
            }
        }
        assertTrue(commandNodeFound, "CommandNode.java must exist");

        File cmdFile = new File(projectDir,
            "src/main/java/com/arkflame/flameforge/command/FlameForgeCommand.java");
        assertTrue(cmdFile.exists(), "FlameForgeCommand.java must exist");
        String cmdContent = readFile(cmdFile);

        assertFalse(cmdContent.contains("Component.text("),
            "FlameForgeCommand must render text through MessageService");
        assertFalse(cmdContent.contains("NamedTextColor"),
            "FlameForgeCommand must not own text colors");
        assertFalse(cmdContent.contains("private TextBridge"),
            "FlameForgeCommand must not own a private TextBridge");
        assertFalse(cmdContent.contains("buildCommandList"),
            "FlameForgeCommand must not build a parallel command list");

        String onCommand = extractMethod(cmdContent, "public boolean onCommand");
        assertFalse(onCommand.isEmpty(), "onCommand method must exist");
        assertFalse(onCommand.contains("return false"),
            "onCommand must not return false - return true after sending usage");
    }

    @Test
    void stationTargetingAndRegistrationRemainServiceOwned() {
        File mainDir = new File(projectDir, "src/main/java");
        assertTrue(mainDir.exists(), "main source directory must exist");

        assertFalse(scanForPattern(mainDir, "resolveStationFromClick"),
            "resolveStationFromClick must not exist in production code");

        Pattern legacyAddStation = Pattern.compile(
            "addStation\\s*\\(\\s*String\\s+\\w+\\s*,\\s*Block\\b");
        for (File file : listJavaFiles(mainDir)) {
            assertFalse(legacyAddStation.matcher(readFile(file)).find(),
                "Legacy addStation(String, Block, ...) found in " + file.getName());
        }

        File bridgeFile = new File(projectDir,
            "src/main/java/com/arkflame/flameforge/station/TargetBlockBridge.java");
        assertTrue(bridgeFile.exists(), "TargetBlockBridge.java must exist");
        String bridgeContent = readFile(bridgeFile);
        assertFalse(bridgeContent.contains("validateBeaconSetup"),
            "TargetBlockBridge must not contain validateBeaconSetup");
        assertFalse(bridgeContent.contains("findTargetBlockSync"),
            "TargetBlockBridge must not contain findTargetBlockSync - use computeTargetBlockSync");
    }

    @Test
    void commandAndStationSourcesRemainJava8ApiCompatible() {
        File cmdDir = new File(projectDir, "src/main/java/com/arkflame/flameforge/command");
        assertTrue(cmdDir.exists(), "command package must exist");
        List<File> cmdFiles = listJavaFiles(cmdDir);

        for (File f : cmdFiles) {
            String content = readFile(f);
            assertFalse(content.contains("Optional.isEmpty()"),
                "Optional.isEmpty() found in " + f.getName() + " - use .isPresent() or other pattern");
        }

        File stationDir = new File(projectDir, "src/main/java/com/arkflame/flameforge/station");
        assertTrue(stationDir.exists(), "station package must exist");
        List<File> stationFiles = listJavaFiles(stationDir);

        for (File f : stationFiles) {
            String content = readFile(f);
            assertFalse(content.contains("Optional.isEmpty()"),
                "Optional.isEmpty() found in " + f.getName() + " - use .isPresent() or other pattern");
        }
    }

    @Test
    void userFacingResourcesExcludeBeaconAndRenameTerminology() {
        File mainDir = new File(projectDir, "src/main/java");
        assertTrue(mainDir.exists(), "main source directory must exist");

        for (File file : listJavaFiles(mainDir)) {
            if ("MaterialResolver.java".equals(file.getName())) {
                continue;
            }
            String content = readFile(file).toLowerCase(java.util.Locale.ROOT);
            assertFalse(content.contains("beacon"),
                "Product term 'beacon' found outside MaterialResolver: " + file.getName());
            assertFalse(content.contains("rename"),
                "Product term 'rename' found outside MaterialResolver: " + file.getName());
        }

        File resourcesDir = new File(projectDir, "src/main/resources");
        assertTrue(resourcesDir.exists(), "main resources directory must exist");
        try (Stream<Path> walk = Files.walk(resourcesDir.toPath())) {
            walk.filter(path -> path.toString().endsWith(".yml"))
                .forEach(path -> {
                    String content = readFile(path.toFile()).toLowerCase(java.util.Locale.ROOT);
                    assertFalse(content.contains("beacon"),
                        "Product term 'beacon' found in " + path.getFileName());
                    assertFalse(content.contains("rename"),
                        "Product term 'rename' found in " + path.getFileName());
                });
        } catch (IOException e) {
            fail("Could not scan main resources", e);
        }
    }

    @Test
    void persistenceAndHologramSourcesContainNoLegacyOrFabricatedAuthorities() {
        File persistDir = new File(projectDir, "src/main/java/com/arkflame/flameforge/persistence");
        assertTrue(persistDir.exists(), "persistence package must exist");
        List<File> persistFiles = listJavaFiles(persistDir);

        for (File f : persistFiles) {
            String content = readFile(f);
            assertFalse(content.contains("legacyCache"),
                "legacyCache found in " + f.getName());
        }

        File hologramDir = new File(projectDir, "src/main/java/com/arkflame/flameforge/hologram");
        assertTrue(hologramDir.exists(), "hologram directory must exist");
        assertFalse(scanForPattern(hologramDir, "fr.mrmicky.fastcompound"),
            "fr.mrmicky.fastcompound references found in hologram package");

        File mainDir = new File(projectDir, "src/main/java");
        assertFalse(scanForPattern(mainDir, "Executors.defaultThreadFactory()"),
            "Executors.defaultThreadFactory() found in production code");
    }

    private List<File> listJavaFiles(File dir) {
        if (dir == null || !dir.exists()) return Collections.emptyList();
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private boolean scanForPattern(File dir, String pattern) {
        if (dir == null || !dir.exists()) return false;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children == null) return false;
            for (File f : children) {
                if (f.isDirectory()) {
                    String name = f.getName();
                    if (!name.equals("test") && !name.equals("tests")) {
                        if (scanForPattern(f, pattern)) return true;
                    }
                } else if (f.getName().endsWith(".java")) {
                    if (readFile(f).contains(pattern)) return true;
                }
            }
        }
        return false;
    }

    private String readFile(File f) {
        if (f == null || !f.exists()) return "";
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            try {
                java.util.Scanner scanner = new java.util.Scanner(f).useDelimiter("\\\\A");
                return scanner.hasNext() ? scanner.next() : "";
            } catch (IOException ex) {
                return "";
            }
        }
    }

    private String extractMethod(String content, String methodSignature) {
        int start = content.indexOf(methodSignature);
        if (start < 0) return "";
        int braceStart = content.indexOf("{", start);
        if (braceStart < 0) return "";
        int braceCount = 1;
        int pos = braceStart + 1;
        while (pos < content.length() && braceCount > 0) {
            char c = content.charAt(pos);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
            pos++;
        }
        return content.substring(start, pos);
    }
}
