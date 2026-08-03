package com.arkflame.flameforge.architecture;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleLivenessArchitectureTest {

    private static final Pattern JOIN = Pattern.compile("\\.get\\(\\)\\.join\\(|\\.join\\(");
    private static final Pattern COUNT_DOWN_LATCH = Pattern.compile("\\bCountDownLatch\\b");
    private static final Pattern AWAIT = Pattern.compile("\\bawait\\s*\\(");
    private static final Pattern THREAD_SLEEP = Pattern.compile("\\bThread\\s*\\.\\s*sleep\\s*\\(");
    private static final Pattern LOCK_SUPPORT_PARK = Pattern.compile("\\bLockSupport\\s*\\.\\s*park(?:Nanos|Until)?\\s*\\(");
    private static final Pattern FUTURE_DECLARATION = Pattern.compile(
        "\\b(?:CompletableFuture|Future|[A-Za-z_$][A-Za-z0-9_$]*Future)"
            + "\\s*(?:<[^;{}()]+>)?\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:=|;)"
    );
    private static final Pattern FUTURE_GET = Pattern.compile(
        "\\b(?:CompletableFuture|Future)\\s*\\.\\s*get\\s*\\("
    );
    private static final Pattern BLOCKING_QUEUE_DECLARATION = Pattern.compile(
        "\\b(?:[A-Za-z_$][A-Za-z0-9_$]*\\.)*BlockingQueue\\s*"
            + "(?:<[^;{}()]+>)?\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:=|;)"
    );
    private static final Pattern STATIC_COMMAND_CONTEXT_FIELD = Pattern.compile(
        "(?m)^\\s*(?:(?:public|protected|private|final|volatile|transient)\\s+)*"
            + "static\\s+[^;{}\\n]*\\bCommandContext\\b(?:\\s*[>\\]])*\\s+"
            + "[A-Za-z_$][A-Za-z0-9_$]*"
            + "\\s*(?:=[^;{}\\n]*)?;"
    );

    private final String projectDir = System.getProperty("user.dir");

    @Test
    void requiredLifecycleAndGameplaySourcesExist() throws IOException {
        assertFalse(productionSources().isEmpty(), "Required lifecycle/gameplay source set must not be empty");
    }

    @Test
    void noForbiddenBlockingPrimitivesInLifecycleAndGameplayPaths() throws IOException {
        List<Pattern> forbidden = Arrays.asList(
            JOIN,
            COUNT_DOWN_LATCH,
            AWAIT,
            THREAD_SLEEP,
            LOCK_SUPPORT_PARK,
            FUTURE_GET
        );
        List<String> labels = Arrays.asList(
            ".join(",
            "CountDownLatch",
            "await(",
            "Thread.sleep(",
            "LockSupport.park",
            "Future.get"
        );

        for (File file : productionSources()) {
            String executable = stripCommentsAndLiterals(readSource(file));
            for (int i = 0; i < forbidden.size(); i++) {
                assertNoMatch(file, executable, forbidden.get(i), labels.get(i));
            }

            String futureViolation = futureGetViolation(file, executable);
            assertTrue(futureViolation == null, "Forbidden Future.get call at " + futureViolation);
        }

        File auditLog = requiredFile("src/main/java/com/arkflame/flameforge/persistence/AuditLogService.java");
        String auditSource = stripCommentsAndLiterals(readSource(auditLog));
        String queuePutViolation = typedMethodViolation(auditLog, auditSource,
            BLOCKING_QUEUE_DECLARATION, "put", "BlockingQueue.put");
        assertTrue(queuePutViolation == null, "Forbidden BlockingQueue.put at " + queuePutViolation);
    }

    @Test
    void noStaticCommandContextOwner() throws IOException {
        for (File file : productionSources()) {
            String executable = stripCommentsAndLiterals(readSource(file));
            String violation = matchEvidence(file, executable, STATIC_COMMAND_CONTEXT_FIELD,
                "static CommandContext field");
            assertTrue(violation == null, "Static CommandContext owner at " + violation);
        }
    }

    @Test
    void commandRegistrationPrecedesStartupLoadInvocation() throws IOException {
        File pluginFile = requiredFile("src/main/java/com/arkflame/flameforge/FlameForgePlugin.java");
        String source = stripCommentsAndLiterals(readSource(pluginFile));

        int markLoading = indexOf(source, "\\bcommand\\s*\\.\\s*markLoading\\s*\\(");
        int setExecutor = indexOf(source,
            "\\bgetPluginCommand\\s*\\([^;{}]*\\)\\s*\\.\\s*setExecutor\\s*\\(");
        int setTabCompleter = indexOf(source,
            "\\bgetPluginCommand\\s*\\([^;{}]*\\)\\s*\\.\\s*setTabCompleter\\s*\\(");
        int firstStartupLoad = firstIndex(source,
            "\\bconfigService\\s*\\.\\s*initialLoadAsync\\s*\\(",
            "\\bplayerStateRepository\\s*\\.\\s*loadAllAsync\\s*\\(",
            "\\bstationRepository\\s*\\.\\s*loadAsync\\s*\\(",
            "\\bpendingDeliveryRepository\\s*\\.\\s*loadAsync\\s*\\(");

        assertTrue(markLoading >= 0,
            "FlameForgePlugin.java must mark command loading before registration");
        assertTrue(setExecutor >= 0,
            "FlameForgePlugin.java must register command executor");
        assertTrue(setTabCompleter >= 0,
            "FlameForgePlugin.java must register command tab completer");
        assertTrue(firstStartupLoad >= 0,
            "FlameForgePlugin.java must invoke startup loads");
        assertTrue(markLoading < setExecutor && setExecutor < firstStartupLoad,
            orderEvidence(pluginFile, source, markLoading, setExecutor, firstStartupLoad)
                + ": markLoading, executor registration, startup load must stay ordered");
        assertTrue(markLoading < setTabCompleter && setTabCompleter < firstStartupLoad,
            orderEvidence(pluginFile, source, markLoading, setTabCompleter, firstStartupLoad)
                + ": markLoading, tab completer registration, startup load must stay ordered");
    }

    private List<File> productionSources() throws IOException {
        List<File> files = new ArrayList<>();
        files.add(requiredFile("src/main/java/com/arkflame/flameforge/FlameForgePlugin.java"));
        addRequiredDirectory(files, "src/main/java/com/arkflame/flameforge/command");
        addRequiredDirectory(files, "src/main/java/com/arkflame/flameforge/listener");
        addRequiredDirectory(files, "src/main/java/com/arkflame/flameforge/forge");
        addRequiredDirectory(files, "src/main/java/com/arkflame/flameforge/station");
        addRequiredDirectory(files, "src/main/java/com/arkflame/flameforge/persistence");
        files.sort(Comparator.comparing(File::getPath));
        return files;
    }

    private void addRequiredDirectory(List<File> files, String relativePath) throws IOException {
        File directory = requiredFile(relativePath);
        List<File> javaFiles;
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            javaFiles = paths
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .map(Path::toFile)
                .sorted(Comparator.comparing(File::getPath))
                .collect(Collectors.toList());
        }
        assertFalse(javaFiles.isEmpty(), "Required production package has no Java sources: " + directory.getPath());
        files.addAll(javaFiles);
    }

    private File requiredFile(String relativePath) {
        File file = Paths.get(projectDir, relativePath).toFile();
        assertTrue(file.exists(), "Required production path must exist: " + file.getPath());
        return file;
    }

    private String readSource(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private void assertNoMatch(File file, String source, Pattern pattern, String label) {
        String evidence = matchEvidence(file, source, pattern, label);
        assertTrue(evidence == null, "Forbidden " + label + " at " + evidence);
    }

    private String futureGetViolation(File file, String source) {
        Matcher declaration = FUTURE_DECLARATION.matcher(source);
        while (declaration.find()) {
            String variable = declaration.group(1);
            Matcher call = Pattern.compile("\\b" + Pattern.quote(variable) + "\\s*\\.\\s*get\\s*\\(")
                .matcher(source);
            if (call.find()) {
                return evidence(file, source, call.start(), "Future.get");
            }
        }
        return null;
    }

    private String typedMethodViolation(File file, String source, Pattern declarationPattern,
                                       String method, String label) {
        Matcher declaration = declarationPattern.matcher(source);
        while (declaration.find()) {
            String variable = declaration.group(1);
            Matcher call = Pattern.compile("\\b" + Pattern.quote(variable) + "\\s*\\.\\s*"
                    + Pattern.quote(method) + "\\s*\\(")
                .matcher(source);
            if (call.find()) {
                return evidence(file, source, call.start(), label);
            }
        }
        return null;
    }

    private String matchEvidence(File file, String source, Pattern pattern, String label) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? evidence(file, source, matcher.start(), label) : null;
    }

    private String evidence(File file, String source, int position, String label) {
        return file.getPath() + ":" + lineNumber(source, position) + " (" + label + ")";
    }

    private int indexOf(String source, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(source);
        return matcher.find() ? matcher.start() : -1;
    }

    private int firstIndex(String source, String... regexes) {
        int first = Integer.MAX_VALUE;
        for (String regex : regexes) {
            int index = indexOf(source, regex);
            if (index >= 0) {
                first = Math.min(first, index);
            }
        }
        return first == Integer.MAX_VALUE ? -1 : first;
    }

    private String orderEvidence(File file, String source, int first, int second, int third) {
        return file.getPath() + ":" + lineNumber(source, first) + ", "
            + lineNumber(source, second) + ", " + lineNumber(source, third);
    }

    private int lineNumber(String source, int position) {
        int line = 1;
        for (int i = 0; i < position; i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private String stripCommentsAndLiterals(String source) {
        StringBuilder executable = new StringBuilder(source.length());
        int length = source.length();
        int i = 0;
        while (i < length) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < length && source.charAt(i + 1) == '/') {
                executable.append("  ");
                i += 2;
                while (i < length && source.charAt(i) != '\n') {
                    executable.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < length && source.charAt(i + 1) == '*') {
                executable.append("  ");
                i += 2;
                while (i < length) {
                    c = source.charAt(i++);
                    if (c == '*' && i < length && source.charAt(i) == '/') {
                        executable.append(' ');
                        executable.append(' ');
                        i++;
                        break;
                    }
                    executable.append(c == '\n' ? '\n' : ' ');
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                char quote = c;
                executable.append(' ');
                i++;
                while (i < length) {
                    c = source.charAt(i++);
                    if (c == '\\' && i < length) {
                        executable.append(' ');
                        c = source.charAt(i++);
                        executable.append(c == '\n' ? '\n' : ' ');
                    } else if (c == quote) {
                        executable.append(' ');
                        break;
                    } else {
                        executable.append(c == '\n' ? '\n' : ' ');
                    }
                }
                continue;
            }
            executable.append(c);
            i++;
        }
        return executable.toString();
    }
}
