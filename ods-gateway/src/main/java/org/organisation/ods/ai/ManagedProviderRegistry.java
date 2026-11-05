package org.organisation.ods.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.organisation.ods.Settings;
import org.organisation.ods.contracts.Json;
import org.organisation.ods.contracts.OdsException;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

/**
 * Resolves a signed model through the deployment-owned provider registry and
 * rejects undeployed endpoints, credentials or capabilities. Exigences :
 * DEVAI-064, DEVAI-189, DEVAI-190, DEVAI-193, DEVAI-195, DEVAI-196.
 */
@Component
public final class ManagedProviderRegistry {
    private static final Set<String> CREDENTIAL_REFS = Set.of("ODS_OPENAI_API_KEY");
    private static final Set<String> API_STYLES = Set.of("openai");
    private final Settings settings;

    /** Binds provider resolution to Spring deployment settings. Exigences : DEVAI-189. */
    public ManagedProviderRegistry(Settings settings) { this.settings=settings; }

    /**
     * Resolves exactly one signed alias and rejects any provider absent from
     * either the project policy or this deployment. Exigences : DEVAI-064,
     * DEVAI-190, DEVAI-191, DEVAI-193.
     */
    public ModelSelection resolve(JsonNode policy, String alias) {
        JsonNode signedModel=null;
        for(JsonNode model:policy.path("models")) if(model.path("alias").asText().equals(alias)) signedModel=model;
        if(signedModel==null) throw denied();
        String id=signedModel.path("provider").asText();
        if(!policy.path("allowedProviders").isArray()||!contains(policy.path("allowedProviders"),id)) throw denied();
        String model=signedModel.path("model").asText();
        if(id.equals("MISTRAL")) return mistral(signedModel,model);
        JsonNode entry=entry(id);
        if(entry==null) throw denied();
        Json.fields(entry,Set.of("id","backend","apiStyle","apiBase","allowedHosts","credentialRef","generation","tools","streaming","images","usage"));
        String backend=Json.text(entry,"backend"),style=Json.text(entry,"apiStyle"),base=Json.text(entry,"apiBase"),reference=Json.text(entry,"credentialRef");
        if(!Json.text(entry,"id").equals(id)||!backend.equals("generic")||!API_STYLES.contains(style)
                ||!CREDENTIAL_REFS.contains(reference)||!entry.path("generation").isBoolean()||!entry.path("generation").asBoolean()
                ||!entry.path("tools").isBoolean()||!entry.path("streaming").isBoolean()||!entry.path("images").isBoolean()
                ||!entry.path("usage").isBoolean()||!entry.path("usage").asBoolean()) throw denied();
        requireApprovedEndpoint(base,entry.path("allowedHosts"));
        settings.require("openai-api-key");
        long providerQuota=signedModel.path("monthlyProviderTokenQuota").canConvertToLong()?signedModel.path("monthlyProviderTokenQuota").asLong():Long.MAX_VALUE;
        return new ModelSelection(id,model,backend,style,base,reference,true,entry.path("tools").asBoolean()&&signedModel.path("tools").asBoolean(),
                entry.path("streaming").asBoolean(),entry.path("images").asBoolean(),true,providerQuota,
                signedModel.path("maxContextTokens").asLong(),signedModel.path("maxOutputTokens").asInt());
    }

    /** Resolves the backwards-compatible Mistral deployment without changing its transport. Exigences : DEVAI-170, DEVAI-196. */
    private ModelSelection mistral(JsonNode signedModel,String model) {
        if(!model.equals(settings.require("mistral-model"))) throw denied();
        String transport=settings.get("llm-transport");
        String auth=settings.get("vibe-auth");
        String base=transport.equals("VIBE_CLI")?settings.origin("vibe-url").toString():settings.origin("mistral-url").toString();
        String credential=transport.equals("VIBE_CLI")&&auth.equals("CLI_TOKEN")?"ODS_VIBE_TOKEN":"ODS_MISTRAL_API_KEY";
        if(signedModel.path("tools").asBoolean()&&credential.equals("ODS_VIBE_TOKEN")&&settings.get("vibe-token").isBlank()) throw denied();
        long providerQuota=signedModel.path("monthlyProviderTokenQuota").canConvertToLong()?signedModel.path("monthlyProviderTokenQuota").asLong():Long.MAX_VALUE;
        return new ModelSelection("MISTRAL",model,"mistral","openai",base,credential,true,
                signedModel.path("tools").asBoolean(),true,false,true,providerQuota,
                signedModel.path("maxContextTokens").asLong(128_000),signedModel.path("maxOutputTokens").asInt(4_000));
    }

    /** Reads only exact provider IDs from the deployment-owned JSON registry. Exigences : DEVAI-189. */
    private JsonNode entry(String id) {
        String raw=settings.get("provider-registry");
        if(raw.isBlank()) return null;
        JsonNode entries;
        try { entries=Json.parse(raw); } catch(Exception error) { throw new OdsException("CONFIGURATION_INVALID","Registre de fournisseurs invalide"); }
        if(!entries.isArray()||entries.size()>8) throw new OdsException("CONFIGURATION_INVALID","Registre de fournisseurs invalide");
        Set<String> ids=new HashSet<>(); JsonNode match=null;
        for(JsonNode candidate:entries) {
            Json.fields(candidate,Set.of("id","backend","apiStyle","apiBase","allowedHosts","credentialRef","generation","tools","streaming","images","usage"));
            String candidateId=Json.text(candidate,"id");
            if(!candidateId.matches("[A-Z][A-Z0-9_-]{1,31}")||candidateId.equals("MISTRAL")||!ids.add(candidateId)) throw new OdsException("CONFIGURATION_INVALID","Identifiant fournisseur dupliqué ou réservé");
            if(candidateId.equals(id)) match=candidate;
        }
        return match;
    }

    /** Requires HTTPS and an exact host allowlist match before Vibe receives a destination. Exigences : DEVAI-189, DEVAI-191. */
    private void requireApprovedEndpoint(String base,JsonNode hosts) {
        try {
            URI uri=URI.create(base);
            if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
                    ||!hosts.isArray()||hosts.isEmpty()) throw denied();
            Set<String> approved=new HashSet<>();
            for(JsonNode host:hosts) {
                if(!host.isTextual()||!host.asText().matches("[A-Za-z0-9.-]{1,253}")||!approved.add(host.asText().toLowerCase(Locale.ROOT))) throw denied();
            }
            if(!approved.contains(uri.getHost().toLowerCase(Locale.ROOT))) throw denied();
        } catch(IllegalArgumentException error) { throw denied(); }
    }

    /** Checks provider membership without coercing malformed policy values. Exigences : DEVAI-190. */
    private boolean contains(JsonNode values,String expected) { for(JsonNode value:values) if(value.isTextual()&&value.asText().equals(expected)) return true; return false; }
    /** Returns the same safe denial for registry and signed-model failures. Exigences : DEVAI-190, DEVAI-191. */
    private OdsException denied() { return new OdsException("MODEL_NOT_ALLOWED","Le fournisseur ou modèle n’est pas autorisé par la politique et le déploiement"); }
}
