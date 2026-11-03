package org.organisation.ods;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.organisation.ods.contracts.*;
import org.organisation.ods.data.Store;
import org.organisation.ods.integration.GitLabClient;
import org.organisation.ods.policy.PolicyService;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Exercises signed project onboarding, compatibility and fail-closed resolution without external services. Exigences : DEVAI-188. */
class PolicyMappingTest {
    /** Creates a short-lived policy whose disabled AI rule still permits a Sonar mapping. Exigences : DEVAI-188. */
    private byte[] policy(long version,List<Map<String,Object>> projects) {
        return Json.write(Map.of("schemaVersion",1,"policyVersion",version,"issuedAt",Instant.now().minusSeconds(5).toString(),"expiresAt",Instant.now().plusSeconds(3600).toString(),"gitlabInstance","https://gitlab.invalid","defaultEffect","DENY","allowedProviders",List.of("MISTRAL"),"models",List.of(Map.of("alias","coding","provider","MISTRAL","model","test-model","tools",true)),"projects",projects)).getBytes(StandardCharsets.UTF_8);
    }
    /** Publishes exact bytes and their real detached signature at a mocked immutable GitLab commit. Exigences : DEVAI-029, DEVAI-188. */
    private void publish(GitLabClient git,KeyPair keys,byte[] bytes) throws Exception {
        Signature signer=Signature.getInstance("Ed25519"); signer.initSign(keys.getPrivate()); signer.update(bytes);
        when(git.commit(99,"main","synthetic")).thenReturn("immutable-sha");
        when(git.file(99,"ai-project-policy.json","immutable-sha","synthetic")).thenReturn(bytes);
        when(git.file(99,"ai-project-policy.sig","immutable-sha","synthetic")).thenReturn(Base64.getEncoder().encode(signer.sign()));
    }
    /** Proves new mappings are discovered after cache refresh with the same service and unchanged configuration. Exigences : DEVAI-188. */
    @Test void signedMappingsRefreshAndLegacyFallbackRemains() throws Exception {
        KeyPair keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        GitLabClient git=mock(GitLabClient.class); Store store=mock(Store.class);
        Settings settings=new Settings(new MockEnvironment().withProperty("ods.governance-project-id","99").withProperty("ods.governance-ref","main").withProperty("ods.governance-token","synthetic").withProperty("ods.policy-key",Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())).withProperty("ods.gitlab-url","https://gitlab.invalid").withProperty("ods.sonar-project-1","legacy-one").withProperty("ods.sonar-project-3","legacy-three"));
        PolicyService service=new PolicyService(settings,git,store);
        Map<String,Object> first=Map.of("projectId",1,"aiEnabled",false,"allowedActions",List.of(),"monthlyTokenQuota",0);
        publish(git,keys,policy(1,List.of(first)));
        assertEquals("legacy-one",service.sonarProjectKey(1));
        assertEquals("legacy-three",service.sonarProjectKey(3));
        assertEquals("DEPENDENCY_UNAVAILABLE",assertThrows(OdsException.class,()->service.sonarProjectKey(2)).code());
        Map<String,Object> updated=new HashMap<>(first); updated.put("sonarProjectKey","signed-one");
        Map<String,Object> added=new HashMap<>(first); added.put("projectId",2); added.put("sonarProjectKey","new-project");
        publish(git,keys,policy(2,List.of(updated,added)));
        assertEquals("legacy-one",service.sonarProjectKey(1));
        ReflectionTestUtils.setField(service,"fetched",Instant.EPOCH);
        assertEquals("signed-one",service.sonarProjectKey(1));
        assertEquals("new-project",service.sonarProjectKey(2));
        verify(store).watermark(anyString(),eq(2L),anyString());
        ReflectionTestUtils.setField(service,"fetched",Instant.EPOCH);
        when(git.file(99,"ai-project-policy.sig","immutable-sha","synthetic")).thenReturn("invalid".getBytes(StandardCharsets.UTF_8));
        assertEquals("POLICY_INVALID",assertThrows(OdsException.class,()->service.sonarProjectKey(1)).code());
        var expired=(com.fasterxml.jackson.databind.node.ObjectNode)Json.parse(new String(policy(3,List.of(updated)),StandardCharsets.UTF_8));
        expired.put("issuedAt",Instant.now().minusSeconds(7200).toString()); expired.put("expiresAt",Instant.now().minusSeconds(3600).toString());
        publish(git,keys,Json.write(expired).getBytes(StandardCharsets.UTF_8));
        assertEquals("POLICY_EXPIRED",assertThrows(OdsException.class,()->service.sonarProjectKey(1)).code());
        publish(git,keys,policy(3,List.of(updated)));
        doThrow(new OdsException("POLICY_REPLAYED","Replay")).when(store).watermark(anyString(),eq(3L),anyString());
        assertEquals("POLICY_REPLAYED",assertThrows(OdsException.class,()->service.sonarProjectKey(1)).code());
        when(git.commit(99,"main","synthetic")).thenThrow(new OdsException("DEPENDENCY_UNAVAILABLE","Unavailable"));
        assertEquals("DEPENDENCY_UNAVAILABLE",assertThrows(OdsException.class,()->service.sonarProjectKey(1)).code());
    }
}
