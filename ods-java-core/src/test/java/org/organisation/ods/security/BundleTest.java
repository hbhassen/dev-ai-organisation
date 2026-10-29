package org.organisation.ods.security;
import org.organisation.ods.contracts.*;
import org.organisation.ods.config.PromptCatalog;
import org.organisation.ods.starter.StarterOverrides;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

/** Qualifies organizational prompts and starter overlays with genuine signatures and collision checks. Exigences : DEVAI-052, DEVAI-164, DEVAI-165. */
class BundleTest {
    @TempDir Path root;
    /** Verifies the shipped document prompts bind distinct actions and preserve the reviewed patch workflow. Exigences : DEVAI-180, DEVAI-181. */
    @Test void documentPromptsArePackagedAndGoverned() throws Exception {
        var catalog=new PromptCatalog(null,"");
        assertEquals("CHANGELOG",catalog.action("git-changelog"));
        assertEquals("RELEASE_NOTES",catalog.action("git-release-notes"));
        for(var task:Map.of("git-changelog","CHANGELOG.md","git-release-notes","RELEASE_NOTES.md").entrySet()) {
            String system=catalog.compose(List.of("JAVA"),task.getKey()).get("system").toString();
            assertTrue(system.contains(task.getValue())); assertTrue(system.contains("changes_propose")); assertTrue(system.contains("MISSING"));
        }
    }
    /** Signs exact UTF-8 fixture bytes with a fresh ephemeral test key and returns only the public key. Exigences : DEVAI-029. */
    private String sign(String name,Object manifest) throws Exception {
        byte[] bytes=Json.write(manifest).getBytes(java.nio.charset.StandardCharsets.UTF_8); Files.write(root.resolve(name+".json"),bytes);
        KeyPair pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); Signature signer=Signature.getInstance("Ed25519"); signer.initSign(pair.getPrivate()); signer.update(bytes);
        Files.writeString(root.resolve(name+".sig"),Base64.getEncoder().encodeToString(signer.sign())); return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }
    /** Produces a manifest layer bound to the exact content fixture. Exigences : DEVAI-164. */
    private Map<String,Object> layer(String name,String content,String mode) throws Exception {
        Files.writeString(root.resolve(name),content); return Map.of("mode",mode,"files",List.of(Map.of("path",name,"sha256",SignedDocuments.sha256(content.getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
    }
    /** Builds a current organization manifest with explicit permission for untouched generic profiles. Exigences : DEVAI-164. */
    private Map<String,Object> manifest(Object global,Object stacks) {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("schemaVersion",1); result.put("bundleId","example"); result.put("bundleVersion",1); result.put("origin","ORGANIZATION"); result.put("issuedAt",Instant.now().minusSeconds(10).toString()); result.put("expiresAt",Instant.now().plusSeconds(3600).toString()); result.put("global",global); result.put("stacks",stacks); result.put("unconfiguredStacks",Map.of("useApprovedGeneric",true)); return result;
    }
    /** Replaces Java only, retains Python and appends a new governed task without an extension rebuild. Exigences : DEVAI-162, DEVAI-164, DEVAI-166. */
    @Test void replaceOneStackAndAddTask() throws Exception {
        var data=manifest(layer("global.md","Organisation global rule","APPEND"),Map.of("JAVA",layer("java.md","Organisation Java Maven rule","REPLACE")));
        data.put("tasks",Map.of("organisation-review",Map.of("action","CHAT","label","Revue organisationnelle","layer",layer("review.md","Review with the approved internal checklist.","REPLACE"))));
        String key=sign("bundle",data); PromptCatalog catalog=new PromptCatalog(root,key);
        String java=catalog.compose(List.of("JAVA"),"organisation-review").get("system").toString();
        assertTrue(java.contains("Organisation Java Maven rule")); assertTrue(java.contains("approved internal checklist"));
        String python=catalog.compose(List.of("PYTHON"),"code-generation").get("system").toString(); assertTrue(python.contains("pyproject.toml")); assertFalse(python.contains("Organisation Java Maven rule"));
        assertEquals("CHAT",catalog.action("organisation-review"));
    }
    /** Refuses mandatory constraint conflicts instead of allowing a task to weaken the global layer. Exigences : DEVAI-165. */
    @Test void mandatoryConflict() throws Exception {
        Map<String,Object> global=new LinkedHashMap<>(layer("global.md","Global standards","APPEND")); global.put("constraints",Map.of("tests.required","true"));
        Map<String,Object> java=new LinkedHashMap<>(layer("java.md","Java standards","APPEND")); java.put("constraints",Map.of("tests.required","false"));
        String key=sign("bundle",manifest(global,Map.of("JAVA",java)));
        assertEquals("POLICY_CONFLICT",assertThrows(OdsException.class,()->new PromptCatalog(root,key).compose(List.of("JAVA"),"code-generation")).code());
    }
    /** Rejects a modified file even though its manifest still carries a valid signature. Exigences : DEVAI-164. */
    @Test void promptDigestTampering() throws Exception {
        String key=sign("bundle",manifest(layer("global.md","Approved instructions","REPLACE"),Map.of())); Files.writeString(root.resolve("global.md"),"Tampered instructions");
        assertThrows(OdsException.class,()->new PromptCatalog(root,key));
    }
    /** Refuses a signed recommendation whose activation is future-dated or whose interval is reversed. Exigences : DEVAI-030, DEVAI-164. */
    @Test void futureAndReversedValidity() throws Exception {
        var data=manifest(layer("global.md","Approved instructions","REPLACE"),Map.of());
        data.put("issuedAt",Instant.now().plusSeconds(600).toString());
        String future=sign("bundle",data); assertThrows(OdsException.class,()->new PromptCatalog(root,future));
        data.put("issuedAt",Instant.now().plusSeconds(7200).toString());
        String reversed=sign("bundle",data); assertThrows(OdsException.class,()->new PromptCatalog(root,reversed));
    }
    /** Rejects missing required profiles and an expired organizational bundle without generic fallback. Exigences : DEVAI-164. */
    @Test void missingProfileAndExpiry() throws Exception {
        var data=manifest(layer("global.md","Approved global","REPLACE"),Map.of()); data.put("unconfiguredStacks",Map.of("useApprovedGeneric",false));
        String key=sign("bundle",data); assertThrows(OdsException.class,()->new PromptCatalog(root,key).compose(List.of("PYTHON"),"code-review"));
        data.put("expiresAt",Instant.now().minusSeconds(10).toString()); String expired=sign("bundle",data); assertThrows(OdsException.class,()->new PromptCatalog(root,expired));
    }
    /** Allows only explicitly signed collision paths in an organizational starter overlay. Exigences : DEVAI-053, DEVAI-054. */
    @Test void overlayCollision() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); try(ZipOutputStream zip=new ZipOutputStream(bytes)) { zip.putNextEntry(new ZipEntry("README.md")); zip.write("Project {{projectName}}".getBytes()); zip.closeEntry(); }
        Files.write(root.resolve("overlay.zip"),bytes.toByteArray());
        Map<String,Object> profile=new LinkedHashMap<>(Map.of("mode","OVERLAY","archive","overlay.zip","sha256",SignedDocuments.sha256(bytes.toByteArray()),"replacePaths",List.of()));
        var catalog=Map.of("schemaVersion",1,"version",1,"issuedAt",Instant.now().minusSeconds(1).toString(),"expiresAt",Instant.now().plusSeconds(3600).toString(),"profiles",Map.of("PYTHON",profile));
        Path generated=Files.createDirectory(root.resolve("generated")); Files.writeString(generated.resolve("README.md"),"Keep existing");
        String key=sign("starters",catalog); StarterOverrides rejected=new StarterOverrides(root,key);
        assertThrows(OdsException.class,()->rejected.apply("PYTHON",generated,"demo-app")); assertEquals("Keep existing",Files.readString(generated.resolve("README.md")));
        profile.put("replacePaths",List.of("README.md")); key=sign("starters",catalog); new StarterOverrides(root,key).apply("PYTHON",generated,"demo-app"); assertEquals("Project demo-app",Files.readString(generated.resolve("README.md")));
    }
}
