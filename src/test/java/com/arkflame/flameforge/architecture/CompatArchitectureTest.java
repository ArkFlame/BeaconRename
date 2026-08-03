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

import static org.junit.jupiter.api.Assertions.*;

class CompatArchitectureTest {

    private final String projectDir = System.getProperty("user.dir");

    @Test
    void legacyLoadedProductionClassesContainNoModernOnlyOrUnsafeTestLinkage() {
        File compatDir = new File(projectDir, "src/main/java/com/arkflame/flameforge/compat");
        if (!compatDir.exists()) return;

        List<File> files = listJavaFiles(compatDir);
        for (File f : files) {
            String content = readFile(f);
            assertFalse(content.contains("net.minecraft.server.level.ChunkProviderServer"),
                "Modern-only class ChunkProviderServer found in " + f.getName());
            assertFalse(content.contains("sun.misc.Unsafe"),
                "sun.misc.Unsafe found in " + f.getName());
        }

        File materialResolverFile = new File(projectDir,
            "src/main/java/com/arkflame/flameforge/compat/material/MaterialResolver.java");
        String materialResolverContent = readFile(materialResolverFile);

        File menuItemFactoryFile = new File(projectDir,
            "src/main/java/com/arkflame/flameforge/menu/MenuItemFactory.java");
        String menuItemFactoryContent = readFile(menuItemFactoryFile);

        for (File file : listJavaFiles(new File(projectDir, "src/main/java"))) {
            if (file.equals(materialResolverFile)) continue;
            String content = readFile(file);
            assertFalse(content.contains("Material.valueOf"),
                "Material.valueOf found outside MaterialResolver in " + file.getName());
        }

        assertFalse(scanForPatternMenu(menuItemFactoryContent, "static final Material\\s+[A-Z]"),
            "MenuItemFactory must not have static final Material fields");
    }

    @Test
    void productionContainsNoBlockingWaitsOrRawThreads() {
        File mainDir = new File(projectDir, "src/main/java");
        assertTrue(mainDir.exists(), "main source directory must exist");

        for (File file : listJavaFiles(mainDir)) {
            String content = readFile(file);
            assertFalse(content.contains(".join()"),
                ".join() blocking wait found in " + file.getName());
            assertFalse(content.contains("CountDownLatch"),
                "CountDownLatch blocking wait found in " + file.getName());
            assertFalse(content.contains(".await()"),
                ".await() blocking wait found in " + file.getName());
            assertFalse(content.contains("Thread.sleep("),
                "Thread.sleep() blocking wait found in " + file.getName());
            assertFalse(content.contains("new Thread("),
                "new Thread() found in " + file.getName());
        }
    }

    @Test
    void schedulerAndTeleportBridgesExposeAndImplementRequiredContracts() {
        File bridgeFile = new File(projectDir,
            "src/main/java/com/arkflame/flameforge/compat/scheduler/SchedulerBridge.java");
        assertTrue(bridgeFile.exists(), "SchedulerBridge.java must exist");

        String content = readFile(bridgeFile);
        assertTrue(content.contains("runGlobal"),
            "SchedulerBridge must have runGlobal method");
        assertTrue(content.contains("runEntity"),
            "SchedulerBridge must have runEntity method");
        assertTrue(content.contains("runRegion"),
            "SchedulerBridge must have runRegion method");
        assertTrue(content.contains("runAsync"),
            "SchedulerBridge must have runAsync method");
        assertTrue(content.contains("cancelAll"),
            "SchedulerBridge must have cancelAll method");
        assertTrue(content.contains("isFolia"),
            "SchedulerBridge must have isFolia method");

        File foliaFile = new File(projectDir,
            "src/main/java/com/arkflame/flameforge/compat/scheduler/FoliaSchedulerBridge.java");
        if (foliaFile.exists()) {
            String foliaContent = readFile(foliaFile);
            assertTrue(foliaContent.contains("implements SchedulerBridge"),
                "FoliaSchedulerBridge must implement SchedulerBridge");
        }

        File bukkitFile = new File(projectDir,
            "src/main/java/com/arkflame/flameforge/compat/scheduler/BukkitSchedulerBridge.java");
        if (bukkitFile.exists()) {
            String bukkitContent = readFile(bukkitFile);
            assertTrue(bukkitContent.contains("implements SchedulerBridge"),
                "BukkitSchedulerBridge must implement SchedulerBridge");
        }

        File teleportFile = new File(projectDir,
            "src/main/java/com/arkflame/flameforge/compat/scheduler/TeleportBridge.java");
        assertTrue(teleportFile.exists(), "TeleportBridge.java must exist");

        String teleportContent = readFile(teleportFile);
        assertTrue(teleportContent.contains("CompletableFuture<TeleportOutcome> teleportAsync"),
            "TeleportBridge must have teleportAsync method returning CompletableFuture<TeleportOutcome>");
        assertTrue(teleportContent.contains("player == null"),
            "TeleportBridge.teleportAsync must check for null player");
        assertTrue(teleportContent.contains("!player.isOnline()"),
            "TeleportBridge.teleportAsync must check player is online");

        File hologramDir = new File(projectDir, "src/main/java/com/arkflame/flameforge/hologram");
        assertTrue(hologramDir.exists(), "hologram directory must exist");
        assertFalse(scanForPattern(hologramDir, "import org.bukkit.plugin.Provider"),
            "Provider imports must not exist in hologram package");
        assertFalse(scanForPattern(hologramDir, "Provider<"),
            "Provider signature types must not exist in hologram package");
        assertTrue(scanForPattern(hologramDir, "providerPlugin.getClass().getClassLoader()"),
            "Provider classloader source must use providerPlugin.getClass().getClassLoader()");
    }

    @Test
    void hologramIntegrationUsesSupportedApiNamesSingleRendererAndNoStaticBridge() {
        File hologramDir = new File(projectDir, "src/main/java/com/arkflame/flameforge/hologram");
        assertTrue(hologramDir.exists(), "hologram directory must exist");

        assertFalse(scanForPattern(hologramDir, "org.incendio.hologram"),
            "org.incendio.hologram references must not exist");
        assertFalse(scanForPattern(hologramDir, "new TextRenderer("),
            "new TextRenderer( must not exist in hologram package");
        assertFalse(scanForPattern(hologramDir, "HologramBridge.java"),
            "HologramBridge.java must not exist");

        assertTrue(scanForPattern(hologramDir, "eu.decentsoftware.holograms.api.DHAPI"),
            "eu.decentsoftware.holograms.api.DHAPI (Decent provider) must be present");
        assertTrue(scanForPattern(hologramDir, "de.oliver.fancyholograms"),
            "de.oliver.fancyholograms (Fancy provider) must be present");
        assertTrue(scanForPattern(hologramDir, "HologramData"),
            "Fancy create lookup names HologramData must exist");
        assertTrue(scanForPattern(hologramDir, ".ifPresent"),
            "Optional unwrap using ifPresent must exist");
    }

    @Test
    void interactionMenuAndPagelessHelpRegressionGuardsRemain() throws IOException {
        File listenerFile = new File(projectDir,
            "src/main/java/com/arkflame/flameforge/listener/ForgeInventoryListener.java");
        assertTrue(listenerFile.exists(), "ForgeInventoryListener.java must exist");

        String content = readFile(listenerFile);
        assertFalse(content.contains("menuOpen"),
            "ForgeInventoryListener must not have menuOpen field");
        assertFalse(content.contains("markMenuOpen"),
            "ForgeInventoryListener must not have markMenuOpen method");
        assertFalse(content.contains("markMenuClosed"),
            "ForgeInventoryListener must not have markMenuClosed method");
        assertFalse(content.contains("isMenuOpen"),
            "ForgeInventoryListener must not have isMenuOpen method");

        File mainDir = new File(projectDir, "src/main/java");
        assertTrue(mainDir.exists(), "main source directory must exist");

        assertFalse(scanForPattern(mainDir, "HELP_PAGE_SIZE"),
            "HELP_PAGE_SIZE constant must not exist");
        assertFalse(scanForPattern(mainDir, "class PageArgument"),
            "PageArgument class must not exist");
        assertFalse(scanForPattern(mainDir, "getHelpPageSuggestions"),
            "getHelpPageSuggestions method must not exist");
        assertFalse(scanForPattern(mainDir, "getHelpPageCount"),
            "getHelpPageCount method must not exist");

        Path messagesPath = Paths.get(projectDir, "src/main/resources/messages.yml");
        assertTrue(messagesPath.toFile().exists(), "messages.yml must exist");
        String messagesContent = new String(Files.readAllBytes(messagesPath), StandardCharsets.UTF_8);

        assertFalse(messagesContent.contains("%page%"),
            "messages.yml must not contain %page% placeholder");
        assertFalse(messagesContent.contains("%total_pages%"),
            "messages.yml must not contain %total_pages% placeholder");

        Path configPath = Paths.get(projectDir, "src/main/resources/config.yml");
        assertTrue(configPath.toFile().exists(), "config.yml must exist");
        String configContent = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
        assertTrue(configContent.contains("holograms:") && configContent.contains("enabled:") && configContent.contains("provider-order:"),
            "Config holograms section with enabled and provider-order must exist");
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

    private boolean scanForPatternRegex(File dir, String regex) {
        if (dir == null || !dir.exists()) return false;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children == null) return false;
            for (File f : children) {
                if (f.isDirectory()) {
                    String name = f.getName();
                    if (!name.equals("test") && !name.equals("tests")) {
                        if (scanForPatternRegex(f, regex)) return true;
                    }
                } else if (f.getName().endsWith(".java")) {
                    if (readFile(f).matches("(?s).*" + regex + ".*")) return true;
                }
            }
        }
        return false;
    }

    private boolean scanForPatternMenu(String content, String regex) {
        return content.matches("(?s).*" + regex + ".*");
    }
}
