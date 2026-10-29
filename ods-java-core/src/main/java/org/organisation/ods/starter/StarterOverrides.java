package org.organisation.ods.starter;
import org.organisation.ods.contracts.*;
import org.organisation.ods.security.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.io.IOException;

/** Applies signed organizational replacements or explicit overlays without changing the extension. Exigences : DEVAI-052, DEVAI-053, DEVAI-054. */
public final class StarterOverrides {
    private final Path directory; private final JsonNode manifest;
    /** Verifies detached signature, validity, versions and archive digests before generation starts. Exigences : DEVAI-052, DEVAI-054. */
    public StarterOverrides(Path directory,String publicKey) throws IOException {
        this.directory=directory.toRealPath();
        manifest=SignedDocuments.verify(Files.readAllBytes(directory.resolve("starters.json")),Files.readString(directory.resolve("starters.sig")).trim(),publicKey);
        Json.fields(manifest,Set.of("schemaVersion","version","issuedAt","expiresAt","profiles"));
        if(manifest.path("schemaVersion").asInt()!=1||manifest.path("version").asLong()<1||!manifest.path("profiles").isObject()) throw new OdsException("POLICY_INVALID","Catalogue starter invalide");
        SignedDocuments.validity(manifest,Instant.now());
        var profiles=manifest.path("profiles").fields();
        while(profiles.hasNext()) {
            var entry=profiles.next(); JsonNode profile=entry.getValue();
            if(!Set.of("JAVA","ANGULAR","PYTHON","DOTNET").contains(entry.getKey())) throw new OdsException("POLICY_INVALID","Stack inconnue");
            Json.fields(profile,Set.of("mode","archive","sha256","replacePaths","allowStandardFallback"));
            if(!Set.of("REPLACE","OVERLAY").contains(profile.path("mode").asText())) throw new OdsException("POLICY_INVALID","Mode starter invalide");
            archive(profile);
        }
    }
    /** Loads a deployment-owned directory and trust key; absent configuration selects the standard catalog. Exigences : DEVAI-052. */
    public static StarterOverrides configured() throws IOException {
        String path=System.getenv("ODS_STARTER_DIRECTORY"),key=System.getenv("ODS_STARTER_PUBLIC_KEY");
        if(path==null||path.isBlank()) return null;
        if(key==null||key.isBlank()) throw new OdsException("POLICY_INVALID","Clé publique starter requise");
        return new StarterOverrides(Path.of(path),key);
    }
    /** Reads an immutable local archive after provenance, size and digest checks. Exigences : DEVAI-054. */
    private byte[] archive(JsonNode profile) throws IOException {
        Path path=new PathGuard(directory).resolve(Json.text(profile,"archive"));
        if(Files.size(path)>20_000_000) throw new OdsException("INVALID_ARCHIVE","Archive trop volumineuse");
        byte[] bytes=Files.readAllBytes(path);
        if(!SignedDocuments.sha256(bytes).equals(Json.text(profile,"sha256"))) throw new OdsException("POLICY_INVALID","Empreinte du starter incorrecte");
        return bytes;
    }
    /** Indicates whether a profile replaces the standard generator entirely. Exigences : DEVAI-052. */
    public boolean replaces(String stack) { return manifest.path("profiles").path(stack).path("mode").asText().equals("REPLACE"); }
    /** Applies only declared file collisions and records an immutable organizational provenance string. Exigences : DEVAI-053, DEVAI-059. */
    public String apply(String stack,Path generated,String name) throws IOException {
        JsonNode profile=manifest.path("profiles").path(stack); if(profile.isMissingNode()) return "";
        Path overlay=Files.createTempDirectory(generated.getParent(),"overlay-"); StarterService.extract(archive(profile),overlay);
        Set<String> replace=new HashSet<>(); for(JsonNode path:profile.path("replacePaths")) replace.add(path.asText());
        try(var paths=Files.walk(overlay)) {
            for(Path file:paths.filter(Files::isRegularFile).sorted().toList()) {
                String relative=overlay.relativize(file).toString().replace('\\','/'); Path target=new PathGuard(generated).resolve(relative);
                if(Files.exists(target)&&!replace.contains(relative)) throw new OdsException("OVERLAY_CONFLICT","Remplacement non déclaré : "+relative);
                byte[] bytes=Files.readAllBytes(file); String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8); Secrets.check(text);
                if(text.indexOf('\0')<0&&Arrays.equals(bytes,text.getBytes(java.nio.charset.StandardCharsets.UTF_8))) bytes=text.replace("{{projectName}}",name).replace("{{pythonModule}}",name.replace('-','_')).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Files.createDirectories(target.getParent()); Files.write(target,bytes);
            }
        }
        return "ORGANIZATION:"+manifest.path("version").asLong()+":"+profile.path("sha256").asText();
    }
}
