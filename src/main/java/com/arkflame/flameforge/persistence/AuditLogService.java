package com.arkflame.flameforge.persistence;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AuditLogService implements AutoCloseable {
    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final Path auditFolder;
    private final BlockingQueue<String> queue;
    private final AtomicBoolean dropsLogged = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final TaskHandle writerTask;
    private final CountDownLatch writerLatch = new CountDownLatch(1);
    private final AtomicInteger droppedCount = new AtomicInteger(0);

    public AuditLogService(JavaPlugin plugin, SchedulerBridge scheduler, Path dataFolder, int queueCapacity) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.auditFolder = dataFolder.resolve("audit");
        this.queue = new LinkedBlockingQueue<>(queueCapacity);

        writerTask = scheduler.runAsync(plugin, this::writerLoop);
    }

    private void writerLoop() {
        BufferedWriter writer = null;
        Path currentFile = null;
        try {
            while (!Thread.currentThread().isInterrupted() && !closed.get()) {
                String entry = queue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (entry == null) {
                    continue;
                }
                Path todayFile = auditFolder.resolve(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) + ".jsonl");
                if (!todayFile.equals(currentFile)) {
                    if (writer != null) {
                        try { writer.flush(); writer.close(); } catch (IOException ignored) {}
                    }
                    try {
                        Files.createDirectories(auditFolder);
                        writer = Files.newBufferedWriter(todayFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        plugin.getLogger().severe("Failed to open audit writer: " + e.getMessage());
                        continue;
                    }
                    currentFile = todayFile;
                }
                try {
                    writer.write(entry);
                    writer.newLine();
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to write audit entry: " + e.getMessage());
                }
            }
            if (writer != null) {
                try { writer.flush(); writer.close(); } catch (IOException ignored) {}
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (writer != null) {
                try { writer.close(); } catch (IOException ignored) {}
            }
            writerLatch.countDown();
        }
    }

    public void logAsync(String action, String actor, String target, String details) {
        if (closed.get()) {
            return;
        }
        String entry = buildEntry(action, actor, target, details);
        if (!queue.offer(entry)) {
            int dropped = droppedCount.incrementAndGet();
            if (dropsLogged.compareAndSet(false, true)) {
                plugin.getLogger().warning("Audit queue full, dropping entries. First drop at: " + dropped);
            }
        }
    }

    private String buildEntry(String action, String actor, String target, String details) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"timestamp\":").append(System.currentTimeMillis());
        sb.append(",\"action\":").append(escapeJson(action));
        sb.append(",\"actor\":").append(escapeJson(actor));
        sb.append(",\"target\":").append(escapeJson(target));
        sb.append(",\"details\":").append(escapeJson(details));
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return "\"" + sb.toString() + "\"";
    }

    public void flush() {
        try {
            queue.put("__FLUSH__");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            flush();
            try {
                writerLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
