package org.organisation.ods.policy;
import org.organisation.ods.*;
import org.organisation.ods.contracts.*;
import org.organisation.ods.security.*;
import org.organisation.ods.integration.GitLabClient;
import org.organisation.ods.data.Store;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.*;

/** Retrieves signed policies at one GitLab commit and rechecks identity and action on every sensitive call. Exigences : DEVAI-025, DEVAI-028, DEVAI-032, DEVAI-039. */
@Service
public class PolicyService {
    private final Settings settings; private final GitLabClient gitlab; private final Store store;
    private JsonNode cached; private Instant fetched=Instant.EPOCH;
    /** Injects server-owned trust and persistence dependencies. Exigences : DEVAI-003. */
    public PolicyService(Settings settings,GitLabClient gitlab,Store store) { this.settings=settings; this.gitlab=gitlab; this.store=store; }
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
    /** Checks the developer, signed project/action/model policy and records every decision. Exigences : DEVAI-026, DEVAI-039, DEVAI-043, DEVAI-064. */
    public JsonNode authorize(Jwt jwt,long project,String token,String action,String correlation) {
        String subject=subject(jwt);
        try {
            gitlab.authorize(jwt,project,token); JsonNode policy=snapshot(); JsonNode rule=SignedDocuments.allow(policy,project,action);
            if(!rule.path("modelAlias").asText().equals(settings.get("model-alias"))) throw new OdsException("MODEL_NOT_ALLOWED","Alias de modèle non autorisé");
            JsonNode model=null; for(JsonNode entry:policy.path("models")) if(entry.path("alias").asText().equals(rule.path("modelAlias").asText())) model=entry;
            if(model==null||!model.path("model").asText().equals(settings.require("mistral-model"))||!model.path("provider").asText().equals("MISTRAL")) throw new OdsException("MODEL_NOT_ALLOWED","Le modèle déployé n'est pas approuvé par le catalogue signé");
            if(action.equals("AGENT_RUN")&&!model.path("tools").asBoolean(false)) throw new OdsException("MODEL_NOT_ALLOWED","Modèle non qualifié pour les outils d'agent");
            store.audit(subject,project,action,"ALLOW",correlation,policy.path("policyVersion").asText()); return rule;
        } catch(OdsException e) { store.audit(subject,project,action,e.code(),correlation,""); throw e; }
    }
    /** Pseudonymizes the verified issuer/subject pair for persistence and audit. Exigences : DEVAI-043, DEVAI-121. */
    public static String subject(Jwt jwt) { if(jwt==null) throw new OdsException("AUTHENTICATION_REQUIRED","Connexion requise"); return SignedDocuments.sha256((jwt.getIssuer()+"|"+jwt.getSubject()).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
}
