package org.organisation.ods.security;
import org.organisation.ods.contracts.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies signatures, fail-closed permissions and workspace isolation with real primitives. Exigences : DEVAI-026, DEVAI-029, DEVAI-040. */
class SecurityTest {
    @TempDir Path root;
    /** Supplies a bounded policy with one explicit authorized project. Exigences : DEVAI-030. */
    private String policy() { return """
        {"schemaVersion":1,"policyVersion":3,"issuedAt":"2026-01-01T00:00:00Z","expiresAt":"2026-01-02T00:00:00Z",
        "gitlabInstance":"https://gitlab.example","defaultEffect":"DENY","allowedProviders":["MISTRAL"],
        "models":[{"alias":"approved","provider":"MISTRAL","model":"qualified-test-model","tools":true}],
        "projects":[{"projectId":1042,"aiEnabled":true,"allowedActions":["CHAT"],"modelAlias":"approved",
        "monthlyTokenQuota":100000,"allowedAgents":[],"contextMode":"LOCAL_GOVERNED","allowFreeText":true}]}
        """; }
    /** Detects a one-byte modification using a real detached Ed25519 signature. Exigences : DEVAI-029. */
    @Test void signatureIntegrity() throws Exception {
        KeyPair pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); byte[] bytes=policy().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Signature signer=Signature.getInstance("Ed25519"); signer.initSign(pair.getPrivate()); signer.update(bytes);
        String signature=Base64.getEncoder().encodeToString(signer.sign()), key=Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        assertEquals(3,SignedDocuments.verify(bytes,signature,key).path("policyVersion").asInt());
        bytes[1]^=1; assertThrows(OdsException.class,()->SignedDocuments.verify(bytes,signature,key));
    }
    /** Allows a controlled rotation overlap while refusing revoked and expired trust roots. Exigences : DEVAI-029, DEVAI-031. */
    @Test void keyRotationAndRevocation() throws Exception {
        KeyPair pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); byte[] bytes=policy().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Signature signer=Signature.getInstance("Ed25519"); signer.initSign(pair.getPrivate()); signer.update(bytes);
        String signature=Base64.getEncoder().encodeToString(signer.sign()),publicKey=Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        var key=new java.util.LinkedHashMap<String,Object>(java.util.Map.of("keyId","rotation-1","publicKey",publicKey,"notBefore",Instant.now().minusSeconds(60).toString(),"notAfter",Instant.now().plusSeconds(3600).toString(),"revoked",false));
        assertEquals(3,SignedDocuments.verify(bytes,signature,Json.write(java.util.Map.of("keys",java.util.List.of(key)))).path("policyVersion").asInt());
        key.put("revoked",true); assertThrows(OdsException.class,()->SignedDocuments.verify(bytes,signature,Json.write(java.util.Map.of("keys",java.util.List.of(key)))));
        key.put("revoked",false); key.put("notAfter",Instant.now().minusSeconds(1).toString()); assertThrows(OdsException.class,()->SignedDocuments.verify(bytes,signature,Json.write(java.util.Map.of("keys",java.util.List.of(key)))));
    }
    /** Allows only the exact project/action and rejects an expired policy. Exigences : DEVAI-026, DEVAI-030. */
    @Test void explicitPermissionAndExpiry() {
        var p=Json.parse(policy()); SignedDocuments.policy(p,Instant.parse("2026-01-01T12:00:00Z"),"https://gitlab.example");
        assertEquals(1042,SignedDocuments.allow(p,1042,"CHAT").path("projectId").asInt());
        assertThrows(OdsException.class,()->SignedDocuments.allow(p,1043,"CHAT"));
        assertThrows(OdsException.class,()->SignedDocuments.allow(p,1042,"AGENT_RUN"));
        assertEquals("POLICY_EXPIRED",assertThrows(OdsException.class,()->SignedDocuments.policy(p,Instant.parse("2026-01-02T00:00:00Z"),"https://gitlab.example")).code());
    }
    /** Requires separate signed permissions for each GitFlow document rather than inferring rights from chat. Exigences : DEVAI-180, DEVAI-181. */
    @Test void documentActionsRequireExplicitPermission() {
        var original=Json.parse(policy());
        for(String action:java.util.List.of("CHANGELOG","RELEASE_NOTES")) {
            assertThrows(OdsException.class,()->SignedDocuments.allow(original,1042,action));
            var granted=Json.parse(policy().replace("[\"CHAT\"]","[\""+action+"\"]"));
            SignedDocuments.policy(granted,Instant.parse("2026-01-01T12:00:00Z"),"https://gitlab.example");
            assertEquals(1042,SignedDocuments.allow(granted,1042,action).path("projectId").asInt());
        }
    }
    /** Rejects a different GitLab instance and a non-Mistral provider. Exigences : DEVAI-030, DEVAI-064. */
    @Test void providerAndInstanceBoundaries() {
        Instant now=Instant.parse("2026-01-01T12:00:00Z");
        assertThrows(OdsException.class,()->SignedDocuments.policy(Json.parse(policy()),now,"https://another.example"));
        assertThrows(OdsException.class,()->SignedDocuments.policy(Json.parse(policy().replace("MISTRAL","OTHER")),now,"https://gitlab.example"));
        assertThrows(OdsException.class,()->SignedDocuments.policy(Json.parse(policy().replace("\"modelAlias\":\"approved\"","\"modelAlias\":\"unapproved\"")),now,"https://gitlab.example"));
    }
    /** Refuses traversal, credential and Git internals while accepting ordinary source files. Exigences : DEVAI-040. */
    @Test void protectedPaths() throws Exception {
        PathGuard guard=new PathGuard(root); Files.createDirectory(root.resolve("src"));
        assertEquals(root.toRealPath().resolve("src/App.java"),guard.resolve("src/App.java"));
        for(String path:new String[]{"../escape",".env",".git/config","src/../../escape","private.key"}) assertThrows(OdsException.class,()->guard.resolve(path));
    }
    /** Keeps files belonging to a nested repository out of the selected project. Exigences : DEVAI-040. */
    @Test void nestedRepository() throws Exception {
        Files.createDirectories(root.resolve("nested/.git")); Files.writeString(root.resolve("nested/private.txt"),"private");
        assertThrows(OdsException.class,()->new PathGuard(root).resolve("nested/private.txt"));
    }
    /** Blocks synthetic tokens without rejecting explicit documentation placeholders. Exigences : DEVAI-041. */
    @Test void credentials() {
        assertThrows(OdsException.class,()->Secrets.check("glpat-"+"x".repeat(30)));
        assertDoesNotThrow(()->Secrets.check("API_KEY=<API_KEY>"));
    }
}
