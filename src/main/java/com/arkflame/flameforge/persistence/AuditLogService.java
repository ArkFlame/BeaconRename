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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AuditLogService implements AutoCloseable {

    private enum RecordKind {
        LOG, FLUSH, CLOSE
    }

    private static final class AuditRecord {
        private final RecordKind kind;
        private final String action;
        private final String actor;
        private final String target;
        private final String details;
        private final CompletableFuture<Void> acknowledgement;

        private AuditRecord(RecordKind kind, String action, String actor, String target, String details,
                            CompletableFuture<Void> acknowledgement) {
            this.kind = kind;
            this.action = action;
            this.actor = actor;
            this.target = target;
            this.details = details;
            this.acknowledgement = acknowledgement;
        }
    }

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final Path auditFolder;
    private final BlockingQueue<AuditRecord> queue;
    private final Object queueLock = new Object();
    private final AtomicBoolean queueFullWarningLogged = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final TaskHandle writerTask;
    private CompletableFuture<Void> closeFuture;

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
            while (!Thread.currentThread().isInterrupted()) {
                AuditRecord record = queue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (record == null) {
                    continue;
                }

                if (record.kind == RecordKind.CLOSE) {
                    acknowledgeClose(record, writer);
                    break;
                }

                if (record.kind == RecordKind.FLUSH) {
                    acknowledgeFlush(record, writer);
                    continue;
                }

                String entry = buildEntry(record.action, record.actor, record.target, record.details);
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
        }
    }

    public boolean logAsync(String action, String actor, String target, String details) {
        AuditRecord record = new AuditRecord(RecordKind.LOG, action, actor, target, details, null);
        synchronized (queueLock) {
            if (closed.get()) {
                return false;
            }
            if (!queue.offer(record)) {
                warnQueueFull();
                return false;
            }
            return true;
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

    public CompletableFuture<Void> flushAsync() {
        CompletableFuture<Void> acknowledgement = new CompletableFuture<>();
        synchronized (queueLock) {
            if (closed.get()) {
                return failedFuture(new IllegalStateException("Audit log service is closed"));
            }
            if (!queue.offer(new AuditRecord(RecordKind.FLUSH, null, null, null, null, acknowledgement))) {
                warnQueueFull();
                acknowledgement.completeExceptionally(new IllegalStateException("Audit queue is full"));
            }
        }
        return acknowledgement;
    }

    public void flush() {
        flushAsync();
    }

    @Override
    public void close() {
        closeAsync();
    }

    public CompletableFuture<Void> closeAsync() {
        synchronized (queueLock) {
            if (closeFuture != null) {
                return closeFuture;
            }

            closeFuture = new CompletableFuture<>();
            closed.set(true);
            if (!queue.offer(new AuditRecord(RecordKind.CLOSE, null, null, null, null, closeFuture))) {
                warnQueueFull();
                closeFuture.completeExceptionally(new IllegalStateException("Audit queue is full"));
            }
            return closeFuture;
        }
    }

    private void acknowledgeFlush(AuditRecord record, BufferedWriter writer) {
        try {
            if (writer != null) {
                writer.flush();
            }
            record.acknowledgement.complete(null);
        } catch (IOException e) {
            record.acknowledgement.completeExceptionally(e);
        }
    }

    private void acknowledgeClose(AuditRecord record, BufferedWriter writer) {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
            record.acknowledgement.complete(null);
        } catch (IOException e) {
            record.acknowledgement.completeExceptionally(e);
        }
    }

    private void warnQueueFull() {
        if (queueFullWarningLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning("Audit queue full; dropping audit record.");
        }
    }

    private CompletableFuture<Void> failedFuture(Throwable failure) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        future.completeExceptionally(failure);
        return future;
    }
}
