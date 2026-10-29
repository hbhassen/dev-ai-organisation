package org.organisation.ods.security;

import org.organisation.ods.contracts.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

/** Verifies immutable signed bytes and strict policy semantics before any IA decision. Exigences : DEVAI-026, DEVAI-029, DEVAI-030, DEVAI-031. */
public final class SignedDocuments {
    private static final Set<String> ACTIONS = Set.of("CHAT", "CODE_GENERATION", "SONAR_REMEDIATION", "XRAY_REMEDIATION", "MR_DESCRIPTION", "AGENT_RUN", "CHANGELOG", "RELEASE_NOTES");
    /** Prevents construction of the security utility. Exigences : DEVAI-029. */
    private SignedDocuments() { }
    /** Rejects malformed, future or expired signed recommendation intervals before their contents are used. Exigences : DEVAI-030, DEVAI-164. */
    public static void validity(JsonNode document, Instant now) {
        try {
            Instant issued=Instant.parse(Json.text(document,"issuedAt")), expires=Instant.parse(Json.text(document,"expiresAt"));
            if(!issued.isBefore(expires)||issued.isAfter(now.plusSeconds(60))) throw new OdsException("POLICY_INVALID","Intervalle de validité invalide");
            if(!now.isBefore(expires)) throw new OdsException("POLICY_EXPIRED","Document signé expiré");
        } catch(java.time.format.DateTimeParseException e) { throw new OdsException("POLICY_INVALID","Date du document invalide"); }
    }
    /** Computes the immutable identity of a file for signatures, provenance and conflict detection. Exigences : DEVAI-029, DEVAI-069. */
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (GeneralSecurityException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    /** Verifies detached Ed25519 over original bytes with a separately trusted DER public key. Exigences : DEVAI-029. */
    public static JsonNode verify(byte[] bytes, String signatureBase64, String publicKeyBase64) {
        if (bytes.length > 1_048_576) throw new OdsException("POLICY_INVALID", "Bundle trop volumineux");
        try {
            if(publicKeyBase64.stripLeading().startsWith("{")) {
                JsonNode trust=Json.parse(publicKeyBase64); Json.fields(trust,Set.of("keys"));
                if(!trust.path("keys").isArray()||trust.path("keys").isEmpty()||trust.path("keys").size()>8) throw new GeneralSecurityException();
                Set<String> ids=new HashSet<>(); List<String> eligible=new ArrayList<>(); Instant now=Instant.now();
                for(JsonNode entry:trust.path("keys")) {
                    Json.fields(entry,Set.of("keyId","publicKey","notBefore","notAfter","revoked"));
                    if(!ids.add(Json.text(entry,"keyId"))||!entry.path("revoked").isBoolean()) throw new GeneralSecurityException();
                    Instant start=Instant.parse(Json.text(entry,"notBefore")),end=Instant.parse(Json.text(entry,"notAfter"));
                    String key=Json.text(entry,"publicKey"); if(!start.isBefore(end)||key.startsWith("{")) throw new GeneralSecurityException();
                    if(!entry.path("revoked").asBoolean()&&!now.isBefore(start)&&now.isBefore(end)) eligible.add(key);
                }
                for(String candidate:eligible) try { return verify(bytes,signatureBase64,candidate); } catch(OdsException invalidCandidate) { /* Rotation overlaps keys without trusting a key delivered in the signed payload. */ }
                throw new GeneralSecurityException();
            }
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
            Signature verifier = Signature.getInstance("Ed25519"); verifier.initVerify(key); verifier.update(bytes);
            if (!verifier.verify(Base64.getDecoder().decode(signatureBase64))) throw new GeneralSecurityException();
            return Json.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) { throw new OdsException("POLICY_INVALID", "Signature ou document invalide"); }
    }
    /** Validates policy structure, all project rules, validity interval and initial provider restriction. Exigences : DEVAI-030, DEVAI-064. */
    public static void policy(JsonNode policy, Instant now, String expectedInstance) {
        Json.fields(policy, Set.of("schemaVersion", "policyVersion", "issuedAt", "expiresAt", "gitlabInstance", "defaultEffect", "allowedProviders", "models", "projects"));
        if (policy.path("schemaVersion").asInt() != 1 || !policy.path("policyVersion").canConvertToLong() || policy.path("policyVersion").asLong() < 1
            || !policy.path("defaultEffect").asText().equals("DENY") || !policy.path("gitlabInstance").asText().equals(expectedInstance))
            throw new OdsException("POLICY_INVALID", "Politique incompatible");
        try {
            Instant issued = Instant.parse(Json.text(policy, "issuedAt")), expires = Instant.parse(Json.text(policy, "expiresAt"));
            if (!issued.isBefore(expires) || issued.isAfter(now.plusSeconds(60)) || expires.isAfter(issued.plusSeconds(86400))) throw new IllegalArgumentException();
            if (!now.isBefore(expires)) throw new OdsException("POLICY_EXPIRED", "Politique expirée");
        } catch (OdsException e) { throw e; } catch (Exception e) { throw new OdsException("POLICY_INVALID", "Dates de politique invalides"); }
        if (!policy.path("allowedProviders").isArray() || policy.path("allowedProviders").size() != 1 || !policy.path("allowedProviders").get(0).asText().equals("MISTRAL"))
            throw new OdsException("MODEL_NOT_ALLOWED", "Seul Mistral est autorisé");
        if(!policy.path("models").isArray()||policy.path("models").isEmpty()) throw new OdsException("POLICY_INVALID","Catalogue de modèles signés requis");
        Set<String> aliases=new HashSet<>();
        for(JsonNode model:policy.path("models")) {
            Json.fields(model,Set.of("alias","provider","model","tools"));
            if(!aliases.add(Json.text(model,"alias"))||!model.path("provider").asText().equals("MISTRAL")||!model.path("tools").isBoolean()||!Json.text(model,"model").matches("[a-zA-Z0-9._:-]{1,128}")) throw new OdsException("MODEL_NOT_ALLOWED","Modèle signé invalide ou fournisseur interdit");
        }
        if (!policy.path("projects").isArray()) throw new OdsException("POLICY_INVALID", "Liste de projets requise");
        Set<Long> ids = new HashSet<>();
        for (JsonNode rule : policy.path("projects")) {
            Json.fields(rule, Set.of("projectId", "aiEnabled", "allowedActions", "modelAlias", "monthlyTokenQuota", "monthlyUserTokenQuota", "allowedAgents", "contextMode", "allowFreeText"));
            if(rule.has("monthlyUserTokenQuota")&&(!rule.path("monthlyUserTokenQuota").canConvertToLong()||rule.path("monthlyUserTokenQuota").asLong()<0)) throw new OdsException("POLICY_INVALID","Quota utilisateur invalide");
            if (!rule.path("projectId").canConvertToLong() || rule.path("projectId").asLong() < 1 || !ids.add(rule.path("projectId").asLong())
                || !rule.path("aiEnabled").isBoolean() || !rule.path("allowedActions").isArray() || !rule.path("monthlyTokenQuota").canConvertToLong() || rule.path("monthlyTokenQuota").asLong() < 0)
                throw new OdsException("POLICY_INVALID", "Règle de projet invalide");
            for (JsonNode action : rule.path("allowedActions")) if (!ACTIONS.contains(action.asText()) || !rule.path("aiEnabled").asBoolean())
                throw new OdsException("POLICY_INVALID", "Action non autorisée dans la règle");
            if (rule.path("aiEnabled").asBoolean() && (!Set.of("LOCAL_GOVERNED", "SERVER_VERIFIED").contains(rule.path("contextMode").asText()) || !aliases.contains(rule.path("modelAlias").asText())))
                throw new OdsException("POLICY_INVALID", "Contexte ou modèle absent");
        }
    }
    /** Finds an explicit permitted action; missing projects and actions always deny. Exigences : DEVAI-026, DEVAI-039. */
    public static JsonNode allow(JsonNode policy, long projectId, String action) {
        for (JsonNode rule : policy.path("projects")) if (rule.path("projectId").asLong() == projectId && rule.path("aiEnabled").asBoolean())
            for (JsonNode allowed : rule.path("allowedActions")) if (allowed.asText().equals(action)) return rule;
        throw new OdsException("ACTION_DENIED", "Action IA interdite pour ce projet");
    }
}
