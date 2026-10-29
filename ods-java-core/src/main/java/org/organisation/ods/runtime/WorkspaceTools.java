package org.organisation.ods.runtime;

import org.organisation.ods.contracts.*;
import org.organisation.ods.security.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.io.IOException;

/** Implements bounded workspace tools and transactional changes without relying on model assertions. Exigences : DEVAI-152, DEVAI-154, DEVAI-069. */
public final class WorkspaceTools {
    private final PathGuard guard;
    private final ProcessRunner runner;
    /** Binds tools to exactly one approved workspace and its owned processes. Exigences : DEVAI-017, DEVAI-040. */
    public WorkspaceTools(Path root, ProcessRunner runner) throws IOException { guard = new PathGuard(root); this.runner = runner; }
    /** Lists bounded relative paths while skipping generated, secret and foreign repositories. Exigences : DEVAI-037, DEVAI-152. */
    public List<String> files() throws IOException {
        List<String> result = new ArrayList<>();
        Files.walkFileTree(guard.root(), EnumSet.noneOf(FileVisitOption.class), 20, new SimpleFileVisitor<>() {
            /** Avoids traversing excluded trees or symbolic directories. Exigences : DEVAI-040. */
            @Override public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                if (dir.equals(guard.root())) return FileVisitResult.CONTINUE;
                try { guard.resolve(guard.root().relativize(dir).toString().replace('\\','/')); return FileVisitResult.CONTINUE; }
                catch (Exception e) { return FileVisitResult.SKIP_SUBTREE; }
            }
            /** Adds only verified, bounded files; truncation is explicit at the caller. Exigences : DEVAI-065. */
            @Override public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                try { String relative = guard.root().relativize(file).toString().replace('\\','/'); guard.resolve(relative); if (attrs.size() <= 262144) result.add(relative); }
                catch (Exception ignored) { /* Excluded paths must not enter tool results. */ }
                return result.size() >= 5000 ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(result); return result;
    }
    /** Reads a UTF-8 file after provenance, size and secret checks, with a base hash. Exigences : DEVAI-037, DEVAI-040, DEVAI-068. */
    public Map<String,Object> read(String relative) throws IOException {
        Path path = guard.resolve(relative);
        if (!Files.isRegularFile(path) || Files.size(path) > 262144) throw new OdsException("CONTEXT_REJECTED", "Fichier absent ou trop volumineux");
        byte[] bytes = Files.readAllBytes(path); String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.indexOf('\0') >= 0) throw new OdsException("CONTEXT_REJECTED", "Fichier binaire exclu");
        Secrets.check(content);
        return Map.of("path", relative, "content", content, "sha256", SignedDocuments.sha256(bytes));
    }
    /** Searches literal text without executing regex supplied by an untrusted model. Exigences : DEVAI-152. */
    public List<Map<String,Object>> search(String query) throws IOException {
        if (query == null || query.isBlank() || query.length() > 256) throw new OdsException("INVALID_REQUEST", "Recherche invalide");
        List<Map<String,Object>> results = new ArrayList<>();
        for (String name : files()) {
            try {
                String[] lines = read(name).get("content").toString().split("\\R");
                for (int i = 0; i < lines.length; i++) if (lines[i].contains(query)) { results.add(Map.of("path", name, "line", i+1, "text", lines[i].substring(0, Math.min(lines[i].length(), 500)))); if (results.size() >= 200) return results; }
            } catch (OdsException ignored) { /* Sensitive and binary files are never searched remotely. */ }
        }
        return results;
    }
    /** Detects stack manifests and Git state without running a project build. Exigences : DEVAI-017, DEVAI-084, DEVAI-106. */
    public Map<String,Object> inspect() throws IOException {
        Set<String> stacks = new TreeSet<>();
        for (String name : files()) {
            if (name.endsWith("pom.xml")) stacks.add("JAVA");
            if (name.endsWith("angular.json")) stacks.add("ANGULAR");
            if (name.endsWith("pyproject.toml") || name.endsWith("requirements.txt")) stacks.add("PYTHON");
            if (name.endsWith(".csproj")) stacks.add("DOTNET");
        }
        Map<String,Object> result = new LinkedHashMap<>(); result.put("stacks", stacks);
        for (Map.Entry<String,List<String>> command : Map.of("branch", List.of("git","branch","--show-current"), "commit", List.of("git","rev-parse","HEAD"), "remote", List.of("git","remote","get-url","origin"), "status", List.of("git","status","--short")).entrySet()) {
            Map<String,Object> process = runner.run(guard.root(), command.getValue(), 15);
            result.put(command.getKey(), Objects.equals(process.get("exitCode"), 0) ? process.get("output").toString().trim() : "");
        }
        return result;
    }
    /** Produces an immutable proposal identity, refusing stale, duplicated or forbidden file targets. Exigences : DEVAI-068, DEVAI-069. */
    public Map<String,Object> propose(JsonNode changes) throws IOException {
        if (!changes.isArray() || changes.isEmpty() || changes.size() > 40) throw new OdsException("INVALID_REQUEST", "Liste de changements invalide");
        Set<String> names = new HashSet<>();
        for (JsonNode change : changes) {
            Json.fields(change, Set.of("path", "baseHash", "content", "requirements"));
            String name = Json.text(change,"path"); Path path = guard.resolve(name);
            if (!names.add(path.toString())) throw new OdsException("INVALID_REQUEST", "Fichier dupliqué");
            String current = Files.exists(path) ? SignedDocuments.sha256(Files.readAllBytes(path)) : "MISSING";
            if (!current.equals(Json.text(change,"baseHash"))) throw new OdsException("STALE_BASE", "Le fichier a changé : " + name);
            if (!change.path("content").isNull() && (!change.path("content").isTextual() || change.path("content").asText().length() > 262144)) throw new OdsException("INVALID_REQUEST", "Contenu invalide");
            Secrets.check(change.path("content").asText(""));
        }
        return Map.of("planHash", SignedDocuments.sha256(Json.write(changes).getBytes(StandardCharsets.UTF_8)), "changes", changes);
    }
    /** Applies an approved proposal with rollback snapshots and repeated stale-base verification. Exigences : DEVAI-069, DEVAI-154. */
    public synchronized Map<String,Object> apply(JsonNode changes, String approvedHash) throws IOException {
        if (!propose(changes).get("planHash").equals(approvedHash)) throw new OdsException("APPROVAL_REQUIRED", "Le plan approuvé a changé");
        Path backup = guard.root().resolve(".ods-local/backups/" + UUID.randomUUID()); Files.createDirectories(backup);
        if(!backup.toRealPath().startsWith(guard.root())) throw new OdsException("CONTEXT_REJECTED","Dossier de sauvegarde hors projet");
        Map<Path,byte[]> originals = new LinkedHashMap<>();
        try {
            for (JsonNode change : changes) {
                Path path = guard.resolve(Json.text(change,"path")); byte[] prior = Files.exists(path) ? Files.readAllBytes(path) : null; originals.put(path, prior);
                if (prior != null) { Path copy = backup.resolve("files").resolve(guard.root().relativize(path)); Files.createDirectories(copy.getParent()); Files.write(copy, prior); }
                if (change.path("content").isNull()) Files.deleteIfExists(path);
                else { Files.createDirectories(path.getParent()); Path temp = Files.createTempFile(path.getParent(), ".ods-", ".tmp"); Files.writeString(temp, change.path("content").asText(), StandardCharsets.UTF_8); Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            }
        } catch (Exception e) {
            for (Map.Entry<Path,byte[]> original : originals.entrySet()) { if (original.getValue() == null) Files.deleteIfExists(original.getKey()); else Files.write(original.getKey(), original.getValue()); }
            throw new OdsException("PATCH_FAILED", "Échec du patch ; contenus précédents restaurés");
        }
        Files.writeString(backup.resolve("checkpoint.json"),Json.write(Map.of("changes",changes,"appliedHash",approvedHash)),StandardCharsets.UTF_8);
        return Map.of("applied", changes.size(), "backup", guard.root().relativize(backup).toString().replace('\\','/'));
    }
    /** Restores one owned checkpoint only when no affected file changed after its application. Exigences : DEVAI-069, DEVAI-157. */
    public synchronized Map<String,Object> restore(String id) throws IOException {
        if(!id.matches("[a-f0-9-]{36}")) throw new OdsException("INVALID_REQUEST","Identifiant de checkpoint invalide");
        Path directory=guard.root().resolve(".ods-local/backups/"+id).toRealPath(); if(!directory.startsWith(guard.root())) throw new OdsException("CONTEXT_REJECTED","Checkpoint hors projet");
        JsonNode checkpoint=Json.parse(Files.readString(directory.resolve("checkpoint.json"))); List<Map<String,Object>> reversed=new ArrayList<>();
        for(JsonNode change:checkpoint.path("changes")) {
            String name=Json.text(change,"path"); Path current=guard.resolve(name);
            String expected=change.path("content").isNull()?"MISSING":SignedDocuments.sha256(change.path("content").asText().getBytes(StandardCharsets.UTF_8));
            String actual=Files.exists(current)?SignedDocuments.sha256(Files.readAllBytes(current)):"MISSING";
            if(!expected.equals(actual)) throw new OdsException("STALE_BASE","Modification utilisateur depuis le checkpoint : "+name);
            Path old=directory.resolve("files").resolve(name); Map<String,Object> undo=new LinkedHashMap<>(); undo.put("path",name); undo.put("baseHash",actual); undo.put("content",change.path("baseHash").asText().equals("MISSING")?null:Files.readString(old)); undo.put("requirements",List.of("DEVAI-069")); reversed.add(undo);
        }
        JsonNode changes=Json.MAPPER.valueToTree(reversed); return apply(changes,propose(changes).get("planHash").toString());
    }
    /** Reads instruction files through the same secret and provenance checks as ordinary source context. Exigences : DEVAI-038, DEVAI-152. */
    public List<Map<String,Object>> instructions() throws IOException {
        List<Map<String,Object>> result=new ArrayList<>(); for(String name:files()) if(name.equals("AGENTS.md")||name.endsWith("/AGENTS.md")||name.equals(".github/copilot-instructions.md")) result.add(read(name)); return result;
    }
    /** Starts a requested development command from a narrow executable allowlist. Exigences : DEVAI-073, DEVAI-155. */
    public String execute(List<String> arguments) throws IOException {
        if (arguments.isEmpty() || !Set.of("git","java","python","dotnet","node","npm","mvnw").contains(arguments.getFirst())) throw new OdsException("ACTION_DENIED", "Exécutable non autorisé");
        if (arguments.stream().anyMatch(a -> a.matches("(?s).*[&|<>^%!\\r\\n].*"))) throw new OdsException("ACTION_DENIED", "Métacaractères interdits");
        if(arguments.getFirst().equals("git")) {
            if(arguments.size()<2||!Set.of("status","diff","fetch","pull","push","switch","branch","add","commit","log","show","rev-parse","remote").contains(arguments.get(1))) throw new OdsException("ACTION_DENIED","Sous-commande Git non autorisée");
            if(arguments.stream().anyMatch(a->a.startsWith("--force")||a.startsWith("+")||Set.of("--amend","--mirror","--delete").contains(a))) throw new OdsException("ACTION_DENIED","Réécriture ou suppression Git non autorisée");
            if(arguments.stream().anyMatch(a->Set.of("--force","-f","--force-with-lease","--hard","clean","reset","restore","-D").contains(a))) throw new OdsException("ACTION_DENIED","Opération Git destructive non disponible dans l'agent");
            if(arguments.size()>1&&arguments.get(1).equals("add")) {
                int separator=arguments.indexOf("--"); if(separator<2||separator==arguments.size()-1) throw new OdsException("ACTION_DENIED","Git add exige une sélection explicite après --");
                for(String name:arguments.subList(separator+1,arguments.size())) read(name);
            }
            if(arguments.size()>1&&Set.of("commit","push").contains(arguments.get(1))) {
                String branch=runner.run(guard.root(),List.of("git","branch","--show-current"),15).get("output").toString().trim();
                if(branch.isBlank()||Set.of("main","master").contains(branch)) throw new OdsException("ACTION_DENIED","Créer une branche de développement avant commit/push");
                var staged=runner.run(guard.root(),List.of("git","diff","--cached","--name-only","-z"),15);
                if(!Objects.equals(staged.get("exitCode"),0)) throw new OdsException("OPERATION_FAILED","Index Git illisible");
                for(String name:staged.get("output").toString().split("\0")) if(!name.isBlank()) { guard.resolve(name); var blob=runner.run(guard.root(),List.of("git","show",":"+name),15); if(Boolean.TRUE.equals(blob.get("sensitive"))||Boolean.TRUE.equals(blob.get("truncated"))) throw new OdsException("CONTEXT_REJECTED","Fichier staged sensible ou trop volumineux"); }
            }
        }
        List<String> command = new ArrayList<>(arguments);
        if (command.getFirst().equals("mvnw")) command.set(0, System.getProperty("os.name").startsWith("Windows") ? "mvnw.cmd" : "./mvnw");
        if (System.getProperty("os.name").startsWith("Windows") && Set.of("npm","mvnw.cmd").contains(command.getFirst())) { command.addAll(0, List.of("cmd.exe","/d","/c")); }
        return runner.start(guard.root(), command, Duration.ofMinutes(10), Map.of());
    }
}
