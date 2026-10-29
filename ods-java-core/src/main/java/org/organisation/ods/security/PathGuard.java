package org.organisation.ods.security;

import org.organisation.ods.contracts.OdsException;
import java.nio.file.*;
import java.io.IOException;
import java.util.Set;

/** Constrains reads and writes to one real workspace, including Windows junctions. Exigences : DEVAI-017, DEVAI-040, DEVAI-055. */
public final class PathGuard {
    private final Path root;
    private static final Set<String> BLOCKED = Set.of(".git", "node_modules", "target", "dist", "build", ".venv", ".ods-local", ".artifacts", "suiteinstall", "credentials.json", "secrets.properties", "token.txt", "application-secrets.yml");
    /** Resolves an existing directory once, without trusting an unvalidated client path. Exigences : DEVAI-040. */
    public PathGuard(Path directory) throws IOException {
        root = directory.toRealPath();
        if (!Files.isDirectory(root)) throw new OdsException("INVALID_WORKSPACE", "Dossier de travail requis");
    }
    /** Returns the verified root for process working directories, never for publication. Exigences : DEVAI-073. */
    public Path root() { return root; }
    /** Resolves a relative path and checks existing ancestors to prevent symlink escapes. Exigences : DEVAI-040, DEVAI-055. */
    public Path resolve(String relative) throws IOException {
        if (relative == null || relative.isBlank() || relative.contains("\\") || relative.contains(":") || relative.indexOf('\0') >= 0)
            throw new OdsException("CONTEXT_REJECTED", "Chemin relatif invalide");
        Path input = Path.of(relative);
        if (input.isAbsolute()) throw new OdsException("CONTEXT_REJECTED", "Chemin absolu interdit");
        for (Path part : input) {
            String name = part.toString().toLowerCase(java.util.Locale.ROOT);
            if (name.equals("..") || name.equals(".") || name.endsWith(".") || name.endsWith(" ") || BLOCKED.contains(name) || name.startsWith(".env") || name.endsWith(".key") || name.endsWith(".pem") || name.endsWith(".p12") || name.endsWith(".pfx"))
                throw new OdsException("CONTEXT_REJECTED", "Chemin exclu par la politique");
        }
        Path result = root.resolve(input).normalize();
        if (!result.startsWith(root)) throw new OdsException("CONTEXT_REJECTED", "Chemin hors projet");
        Path ancestor = result;
        while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.getParent();
        if (!ancestor.toRealPath().startsWith(root)) throw new OdsException("CONTEXT_REJECTED", "Lien hors projet interdit");
        Path cursor = Files.isDirectory(ancestor) ? ancestor : ancestor.getParent();
        while (cursor != null && !cursor.equals(root)) {
            if (Files.exists(cursor.resolve(".git"))) throw new OdsException("CONTEXT_REJECTED", "Dépôt imbriqué à autoriser séparément");
            cursor = cursor.getParent();
        }
        return result;
    }
}
