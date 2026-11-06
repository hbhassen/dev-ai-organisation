package org.organisation.ods;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.organisation.ods.ai.*;
import org.organisation.ods.contracts.*;
import org.organisation.ods.data.Store;
import org.organisation.ods.integration.GitLabClient;
import org.organisation.ods.policy.PolicyService;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies project-scoped selection, public capabilities and immediate revocation without network calls. Exigences : DEVAI-199. */
class ModelChoiceTest {
    /** Builds a resolved model whose private details must never enter the capability contract. Exigences : DEVAI-195, DEVAI-199. */
    private ModelSelection model(String alias,boolean tools) { return new ModelSelection("OPENAI",alias+"-model","generic","openai","https://api.openai.com/v1","ODS_OPENAI_API_KEY",true,tools,false,false,true); }
    /** Selects each signed choice and rejects globally known but project-forbidden aliases before resolution. Exigences : DEVAI-039, DEVAI-199. */
    @Test void projectChoicesAndRevocation() {
        var rule=(ObjectNode)Json.parse("{\"projectId\":42,\"aiEnabled\":true,\"allowedActions\":[\"CHAT\",\"CODE_GENERATION\",\"AGENT_RUN\"],\"modelAlias\":\"first\",\"allowedModelAliases\":[\"first\",\"second\",\"third\"]}");
        var document=Json.MAPPER.createObjectNode(); document.put("policyVersion",7); document.putArray("projects").add(rule);
        ManagedProviderRegistry registry=mock(ManagedProviderRegistry.class); Store store=mock(Store.class);
        when(registry.resolve(any(),anyString())).thenAnswer(call->model(call.getArgument(1),true));
        PolicyService service=spy(new PolicyService(new Settings(new MockEnvironment()),mock(GitLabClient.class),store,registry)); doReturn(document).when(service).snapshot();
        Jwt jwt=Jwt.withTokenValue("synthetic").header("alg","RS256").issuer("https://identity.invalid").subject("test").build();
        for(int count=1;count<=3;count++) {
            var aliases=List.of("first","second","third").subList(0,count); rule.set("allowedModelAliases",Json.MAPPER.valueToTree(aliases));
            var choices=service.modelChoices(document,rule); assertEquals(count,choices.size());
            assertFalse(Json.write(choices).contains("https://")); assertFalse(Json.write(choices).contains("ODS_OPENAI_API_KEY"));
            for(String alias:aliases) for(String action:List.of("CHAT","CODE_GENERATION","AGENT_RUN")) {
                var selected=service.authorizeResolved(jwt,42,"delegated",action,"request",alias);
                assertEquals(alias,selected.alias()); assertEquals(alias+"-model",selected.model().model());
            }
        }
        assertEquals("MODEL_NOT_ALLOWED",assertThrows(OdsException.class,()->service.authorizeResolved(jwt,42,"delegated","CHAT","request","foreign-project-alias")).code());
        verify(registry,never()).resolve(any(),eq("foreign-project-alias"));
        rule.set("allowedModelAliases",Json.MAPPER.valueToTree(List.of("first")));
        assertEquals("MODEL_NOT_ALLOWED",assertThrows(OdsException.class,()->service.authorizeResolved(jwt,42,"delegated","CHAT","request","second")).code());
        rule.remove("allowedModelAliases"); assertEquals("first",service.authorizeResolved(jwt,42,"delegated","CHAT","legacy").alias());
        assertEquals(1,service.modelChoices(document,rule).size());
    }
    /** Rejects a model whose deployment removes tools even when its signed catalogue advertises them. Exigences : DEVAI-193, DEVAI-199. */
    @Test void deployedToolsAreRequiredForSelectableModels() {
        var rule=(ObjectNode)Json.parse("{\"modelAlias\":\"first\",\"allowedModelAliases\":[\"first\"]}");
        ManagedProviderRegistry registry=mock(ManagedProviderRegistry.class); when(registry.resolve(any(),eq("first"))).thenReturn(model("first",false));
        PolicyService service=new PolicyService(new Settings(new MockEnvironment()),mock(GitLabClient.class),mock(Store.class),registry);
        assertEquals("MODEL_NOT_ALLOWED",assertThrows(OdsException.class,()->service.modelChoices(Json.parse("{}"),rule)).code());
        rule.remove("allowedModelAliases"); assertEquals(false,service.modelChoices(Json.parse("{}"),rule).getFirst().get("tools"));
    }
}
