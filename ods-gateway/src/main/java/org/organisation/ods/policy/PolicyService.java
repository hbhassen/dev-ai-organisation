package org.organisation.ods.policy;
import org.organisation.ods.*;
import org.organisation.ods.contracts.*;
import org.organisation.ods.security.*;
import org.organisation.ods.integration.GitLabClient;
import org.organisation.ods.data.Store;
import org.organisation.ods.ai.ManagedProviderRegistry;
import org.organisation.ods.ai.ModelSelection;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.*;

/** Retrieves signed policies at one GitLab commit and rechecks identity, action and provider/model selection on every sensitive call. Exigences : DEVAI-025, DEVAI-028, DEVAI-032, DEVAI-039, DEVAI-190, DEVAI-199. */
@Service
public class PolicyService {
    private final Settings settings; private final GitLabClient gitlab; private final Store store; private final ManagedProviderRegistry providers;
    private JsonNode cached; private Instant fetched=Instant.EPOCH;
    /** Injects server-owned trust and persistence dependencies. Exigences : DEVAI-003. */
    public PolicyService(Settings settings,GitLabClient gitlab,Store store,ManagedProviderRegistry providers) { this.settings=settings; this.gitlab=gitlab; this.store=store; this.providers=providers; }
    /** Refreshes atomically every 60 seconds and refuses stale or replayed snapshots. Exigences : DEVAI-028, DEVAI-031, DEVAI-032. */
    public synchronized JsonNode snapshot() {
        Instant now=Instant.now();
        if(cached!=null&&now.isBefore(fetched.plusSeconds(60))) { SignedDocuments.policy(cached,now,settings.require("gitlab-url")); return cached; }
        long project;
        try { project=Long.parseLong(settings.require("governance-project-id")); } catch(NumberFormatException e) { throw new OdsException("CONFIGURATION_INVALID","Identifiant de gouvernance invalide"); }
        String token=settings.require("governance-token"),sha=gitlab.commit(project,settings.get("governance-ref"),token);
        byte[] bytes=gitlab.file(project,"ai-project-policy.json",sha,token);
        String signature=new String(gitlab.file(project,"ai-project-policy.sig",sha,token),java.nio.charset.StandardCharsets.UTF_8).trim();
        JsonNode next=SignedDocuments.verify(bytes,signature,settings.require("policy-key")); SignedDocuments.policy(next,now,settings.require("gitlab-url"));
        store.watermark("ai-policy:"+settings.require("gitlab-url"),next.path("policyVersion").asLong(),SignedDocuments.sha256(bytes)); cached=next; fetched=now; return next;
    }
    /** Resolves signed Sonar mappings without requiring AI permission; only an absent mapping uses legacy configuration. Exigences : DEVAI-188. */
    public String sonarProjectKey(long project) {
        JsonNode policy=snapshot();
        for(JsonNode rule:policy.path("projects"))
            if(rule.path("projectId").asLong()==project && rule.has("sonarProjectKey")) return rule.path("sonarProjectKey").asText();
        return settings.require("sonar-project-"+project);
    }
    /** Checks the developer, signed project/action/model policy and records every decision. Exigences : DEVAI-026, DEVAI-039, DEVAI-043, DEVAI-064. */
    public JsonNode authorize(Jwt jwt,long project,String token,String action,String correlation) {
        return authorizeResolved(jwt,project,token,action,correlation).rule();
    }
    /** Authorizes the project and resolves its signed alias against the managed provider registry atomically. Exigences : DEVAI-039, DEVAI-064, DEVAI-190, DEVAI-191, DEVAI-193. */
    public Authorization authorizeResolved(Jwt jwt,long project,String token,String action,String correlation) {
        return authorizeResolved(jwt,project,token,action,correlation,null);
    }
    /** Revalidates a client-selected alias against the signed project allowlist on every transition. Exigences : DEVAI-039, DEVAI-199. */
    public Authorization authorizeResolved(Jwt jwt,long project,String token,String action,String correlation,String requestedAlias) {
        String subject=subject(jwt);
        try {
            gitlab.authorize(jwt,project,token); JsonNode policy=snapshot(); JsonNode rule=SignedDocuments.allow(policy,project,action);
            String alias=requestedAlias==null?Json.text(rule,"modelAlias"):requestedAlias;
            if(!modelAliases(rule).contains(alias)) throw new OdsException("MODEL_NOT_ALLOWED","Modèle absent des choix autorisés pour ce projet");
            ModelSelection model=providers.resolve(policy,alias);
            if(rule.has("allowedModelAliases")&&!model.tools()) throw new OdsException("MODEL_NOT_ALLOWED","Modèle déployé sans les outils requis pour les trois modes");
            if(action.equals("AGENT_RUN")&&!model.tools()) throw new OdsException("MODEL_NOT_ALLOWED","Modèle non qualifié pour les outils d'agent");
            store.audit(subject,project,action,"ALLOW",correlation,policy.path("policyVersion").asText(),model.providerId(),alias,model.model()); return new Authorization(rule,model,alias);
        } catch(OdsException e) { store.audit(subject,project,action,e.code(),correlation,""); throw e; }
    }
    /** Carries the matched signed rule and immutable deployment model to one provider call. Exigences : DEVAI-190, DEVAI-191, DEVAI-199. */
    public record Authorization(JsonNode rule,ModelSelection model,String alias) {
        /** Preserves server callers that use the historical default selection. Exigences : DEVAI-196, DEVAI-199. */
        public Authorization(JsonNode rule,ModelSelection model) { this(rule,model,Json.text(rule,"modelAlias")); }
    }
    /** Exposes only signed per-project aliases, using the legacy default when no choice list exists. Exigences : DEVAI-199. */
    private java.util.List<String> modelAliases(JsonNode rule) {
        if(!rule.has("allowedModelAliases")) return java.util.List.of(Json.text(rule,"modelAlias"));
        java.util.List<String> aliases=new java.util.ArrayList<>(); rule.path("allowedModelAliases").forEach(alias->aliases.add(alias.asText())); return aliases;
    }
    /** Publishes resolved model capabilities without endpoint or credential data. Exigences : DEVAI-192, DEVAI-193, DEVAI-199. */
    public java.util.List<java.util.Map<String,Object>> modelChoices(JsonNode policy,JsonNode rule) {
        java.util.List<java.util.Map<String,Object>> choices=new java.util.ArrayList<>();
        for(String alias:modelAliases(rule)) {
            ModelSelection model=providers.resolve(policy,alias);
            if(rule.has("allowedModelAliases")&&!model.tools()) throw new OdsException("MODEL_NOT_ALLOWED","Modèle déployé sans les outils requis pour les trois modes");
            choices.add(java.util.Map.of("modelAlias",alias,"provider",model.providerId(),"model",model.model(),"tools",model.tools(),"maxOutputTokens",model.maxOutputTokens()));
        }
        return choices;
    }
    /** Resolves the model displayed for a signed project rule without returning endpoint or credential data. Exigences : DEVAI-190, DEVAI-192. */
    public ModelSelection resolveModel(JsonNode policy,JsonNode rule) { return providers.resolve(policy,Json.text(rule,"modelAlias")); }
    /** Pseudonymizes the verified issuer/subject pair for persistence and audit. Exigences : DEVAI-043, DEVAI-121. */
    public static String subject(Jwt jwt) { if(jwt==null) throw new OdsException("AUTHENTICATION_REQUIRED","Connexion requise"); return SignedDocuments.sha256((jwt.getIssuer()+"|"+jwt.getSubject()).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
}
