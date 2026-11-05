package org.organisation.ods;

import org.junit.jupiter.api.Test;
import org.organisation.ods.ai.ManagedProviderRegistry;
import org.organisation.ods.contracts.Json;
import org.organisation.ods.contracts.OdsException;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies deployment and signed-policy intersection before a provider call. Exigences : DEVAI-189, DEVAI-190, DEVAI-191, DEVAI-196. */
class ManagedProviderRegistryTest {
    private static final String OPENAI="[{\"id\":\"OPENAI\",\"backend\":\"generic\",\"apiStyle\":\"openai\",\"apiBase\":\"https://api.openai.com/v1\",\"allowedHosts\":[\"api.openai.com\"],\"credentialRef\":\"ODS_OPENAI_API_KEY\",\"generation\":true,\"tools\":true,\"streaming\":false,\"images\":false,\"usage\":true}]";

    /** Produces a signed-policy-shaped model catalogue without external services. Exigences : DEVAI-190. */
    private com.fasterxml.jackson.databind.JsonNode policy(String providers,String provider,String model) {
        return Json.parse("{\"allowedProviders\":"+providers+",\"models\":[{\"alias\":\"approved-code\",\"provider\":\""+provider+"\",\"model\":\""+model+"\",\"tools\":true,\"maxContextTokens\":32768,\"maxOutputTokens\":4096}]}");
    }

    /** Resolves the exact OpenAI model and its signed limits only when policy and registry allow it. Exigences : DEVAI-189, DEVAI-190, DEVAI-191, DEVAI-193. */
    @Test void resolvesConfiguredOpenAiModel() {
        Settings settings=new Settings(new MockEnvironment().withProperty("ods.provider-registry",OPENAI).withProperty("ods.openai-api-key","synthetic-openai-key"));
        var selected=new ManagedProviderRegistry(settings).resolve(policy("[\"OPENAI\"]","OPENAI","gpt-5.1-codex"),"approved-code");
        assertEquals("OPENAI",selected.providerId()); assertEquals("gpt-5.1-codex",selected.model());
        assertEquals("generic",selected.backend()); assertEquals("openai",selected.apiStyle());
        assertEquals("ODS_OPENAI_API_KEY",selected.credentialRef()); assertTrue(selected.tools()); assertTrue(selected.usage());
        assertEquals(32768,selected.maxContextTokens()); assertEquals(4096,selected.maxOutputTokens());
    }

    /** Keeps a configured provider disabled when the signed project policy omits its authorization. Exigences : DEVAI-190, DEVAI-196. */
    @Test void policyAndRegistryMustBothAllowProvider() {
        Settings settings=new Settings(new MockEnvironment().withProperty("ods.provider-registry",OPENAI).withProperty("ods.openai-api-key","synthetic-openai-key"));
        assertEquals("MODEL_NOT_ALLOWED",assertThrows(OdsException.class,()->new ManagedProviderRegistry(settings).resolve(policy("[\"MISTRAL\"]","OPENAI","gpt-5.1-codex"),"approved-code")).code());
    }

    /** Fails closed for unregistered providers, models, API styles and unapproved endpoint hosts. Exigences : DEVAI-189, DEVAI-190, DEVAI-191. */
    @Test void rejectsUnmanagedModelsStylesAndEndpoints() {
        Settings absent=new Settings(new MockEnvironment());
        assertEquals("MODEL_NOT_ALLOWED",assertThrows(OdsException.class,()->new ManagedProviderRegistry(absent).resolve(policy("[\"OPENAI\"]","OPENAI","gpt-5.1-codex"),"approved-code")).code());
        assertEquals("MODEL_NOT_ALLOWED",assertThrows(OdsException.class,()->new ManagedProviderRegistry(new Settings(new MockEnvironment().withProperty("ods.provider-registry",OPENAI.replace("\"openai\"","\"anthropic\"")).withProperty("ods.openai-api-key","synthetic-openai-key"))).resolve(policy("[\"OPENAI\"]","OPENAI","gpt-5.1-codex"),"approved-code")).code());
        String endpoint=OPENAI.replace("api.openai.com/v1","api.attacker.invalid/v1");
        assertEquals("MODEL_NOT_ALLOWED",assertThrows(OdsException.class,()->new ManagedProviderRegistry(new Settings(new MockEnvironment().withProperty("ods.provider-registry",endpoint).withProperty("ods.openai-api-key","synthetic-openai-key"))).resolve(policy("[\"OPENAI\"]","OPENAI","gpt-5.1-codex"),"approved-code")).code());
        assertEquals("MODEL_NOT_ALLOWED",assertThrows(OdsException.class,()->new ManagedProviderRegistry(new Settings(new MockEnvironment().withProperty("ods.provider-registry",OPENAI).withProperty("ods.openai-api-key","synthetic-openai-key"))).resolve(policy("[\"OPENAI\"]","OPENAI","unapproved-model"),"missing-alias")).code());
    }

    /** Preserves the existing Mistral alias and deployment model without requiring an optional provider registry. Exigences : DEVAI-064, DEVAI-196. */
    @Test void preservesMistralOnlyDeployment() {
        Settings settings=new Settings(new MockEnvironment().withProperty("ods.mistral-model","approved-mistral").withProperty("ods.mistral-url","https://api.mistral.ai"));
        var selected=new ManagedProviderRegistry(settings).resolve(policy("[\"MISTRAL\"]","MISTRAL","approved-mistral"),"approved-code");
        assertEquals("MISTRAL",selected.providerId()); assertEquals("approved-mistral",selected.model());
        assertEquals("https://api.mistral.ai",selected.apiBase());
    }
}
