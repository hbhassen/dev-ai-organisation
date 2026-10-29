package org.organisation.ods.runtime;

import org.organisation.ods.contracts.OdsException;
import org.organisation.ods.security.Secrets;
import java.nio.file.Path;
import java.time.Duration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Runs explicit argument lists with bounded output, deadlines and process-tree cancellation. Exigences : DEVAI-073, DEVAI-155. */
public final class ProcessRunner implements AutoCloseable {
    private final Map<String, Running> processes = new ConcurrentHashMap<>();
    /** Holds only the metadata and bounded output of one owned process. Exigences : DEVAI-155. */
    private static final class Running {
        private final Process process;
        private final Path directory;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private volatile Integer exit;
        private volatile boolean truncated;
        /** Associates an already started process with its observation state. Exigences : DEVAI-155. */
        private Running(Process process,Path directory) { this.process = process; this.directory=directory.toAbsolutePath().normalize(); }
    }
    /** Starts an owned process; credentials are removed from inherited environment. Exigences : DEVAI-073, DEVAI-155. */
    public String start(Path cwd, List<String> command, Duration timeout, Map<String,String> credentials) throws IOException {
        if (command.isEmpty() || command.size() > 64 || command.stream().anyMatch(s -> s.indexOf('\0') >= 0 || s.length() > 4096))
            throw new OdsException("INVALID_REQUEST", "Commande invalide");
        ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true);
        builder.environment().keySet().removeIf(k -> k.toUpperCase(Locale.ROOT).matches(".*(TOKEN|PASSWORD|SECRET|API_KEY|PRIVATE_KEY|CREDENTIAL).*"));
        builder.environment().putAll(credentials);
        Running running = new Running(builder.start(),cwd); String id = UUID.randomUUID().toString(); processes.put(id, running);
        Thread reader=Thread.ofVirtual().start(() -> {
            try (InputStream input = running.process.getInputStream()) {
                byte[] buffer = new byte[4096]; int length;
                while ((length = input.read(buffer)) >= 0) synchronized (running.output) {
                    int remaining = 262144 - running.output.size();
                    if (remaining > 0) running.output.write(buffer, 0, Math.min(length, remaining));
                    if (length > remaining) running.truncated = true;
                }
            } catch (IOException ignored) { /* Closed streams are expected during cancellation; exit status remains authoritative. */ }
        });
        Thread.ofVirtual().start(() -> {
            try {
                if (!running.process.waitFor(Math.min(timeout.toSeconds(), 1800), TimeUnit.SECONDS)) cancel(id);
                int code=running.process.waitFor(); reader.join(2000); running.exit=code;
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); cancel(id); }
        });
        return id;
    }
    /** Returns a redacted snapshot; absence of an exit code means still running. Exigences : DEVAI-155. */
    public Map<String,Object> output(String id) {
        Running r = processes.get(id); if (r == null) throw new OdsException("NOT_FOUND", "Processus inconnu");
        synchronized (r.output) {
            Map<String,Object> state = new LinkedHashMap<>(); state.put("processId", id); state.put("running", r.exit == null);
            String raw=r.output.toString(StandardCharsets.UTF_8); state.put("sensitive",Secrets.contains(raw));
            state.put("exitCode", r.exit); state.put("output", Secrets.redact(raw.replace(r.directory.toString(),"<WORKSPACE>").replace(r.directory.toString().replace('\\','/'),"<WORKSPACE>"))); state.put("truncated", r.truncated); return state;
        }
    }
    /** Stops only the owned process and its descendants, never an arbitrary PID. Exigences : DEVAI-073, DEVAI-155. */
    public void cancel(String id) { Running r = processes.get(id); if (r != null) { r.process.descendants().forEach(ProcessHandle::destroyForcibly); r.process.destroyForcibly(); } }
    /** Executes a short command while preserving its actual exit code and deadline. Exigences : DEVAI-106, DEVAI-155. */
    public Map<String,Object> run(Path cwd, List<String> command, int seconds) throws IOException {
        String id = start(cwd, command, Duration.ofSeconds(seconds), Map.of());
        try { while (processes.get(id).exit == null) Thread.sleep(20); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); cancel(id); throw new IOException("Interrupted"); }
        Map<String,Object> result=output(id); processes.remove(id); return result;
    }
    /** Terminates all processes when the owning engine/window is closed. Exigences : DEVAI-015. */
    @Override public void close() { processes.keySet().forEach(this::cancel); }
}
