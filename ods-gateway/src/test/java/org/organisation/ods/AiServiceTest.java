package org.organisation.ods;
import org.organisation.ods.ai.*;
import org.organisation.ods.contracts.*;
import org.organisation.ods.data.Store;
import org.organisation.ods.integration.*;
import org.organisation.ods.policy.PolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Qualifies the actual orchestration with isolated provider and GitLab contract doubles. Exigences : DEVAI-039, DEVAI-077, DEVAI-153. */
class AiServiceTest {
    /** Creates an authenticated test identity, never a production bypass configuration. Exigences : DEVAI-022. */
    private Jwt identity() { return Jwt.withTokenValue("test-token").header("alg","RS256").issuer("https://identity.example").subject("developer").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("gitlab_user_id","7").build(); }
    /** Waits for the asynchronous provider result with a bounded deadline and fails on an absent transition. Exigences : DEVAI-119, DEVAI-153. */
    private void awaitStatus(AtomicReference<String> status,String expected) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos(); while(!expected.equals(status.get())&&System.nanoTime()<deadline) Thread.sleep(10); assertEquals(expected,status.get());
    }
    /** Reads a tool result, resumes Question mode and proves an idempotent continuation invokes the model once. Exigences : DEVAI-063, DEVAI-077, DEVAI-151, DEVAI-153. */
    @Test void questionToolLoopAndIdempotence() throws Exception {
        PolicyService policy=mock(PolicyService.class); Store store=mock(Store.class); ModelProvider provider=mock(ModelProvider.class); GitLabClient gitlab=mock(GitLabClient.class);
        doCallRealMethod().when(provider).stream(anyList(),anyList(),anyInt(),any());
        Settings settings=new Settings(new MockEnvironment()); Jwt jwt=identity(); String subject=PolicyService.subject(jwt);
        var rule=Json.parse("{\"monthlyTokenQuota\":1000000,\"contextMode\":\"LOCAL_GOVERNED\",\"allowFreeText\":true,\"allowedAgents\":[]}");
        when(policy.authorize(any(),eq(1042L),anyString(),anyString(),anyString())).thenReturn(rule); when(gitlab.stacks(1042,"delegated")).thenReturn(Set.of("JAVA"));
        when(store.createJob(anyString(),anyLong(),anyString(),anyString(),anyString())).thenReturn(Map.of("jobId","run-1")); when(store.claim("run-1")).thenReturn(true);
        AtomicReference<String> status=new AtomicReference<>("QUEUED"); when(store.job("run-1",subject)).thenAnswer(call->Map.of("jobId","run-1","status",status.get()));
        AtomicReference<com.fasterxml.jackson.databind.JsonNode> checkpoint=new AtomicReference<>(); java.util.concurrent.atomic.AtomicLong revision=new java.util.concurrent.atomic.AtomicLong();
        when(store.saveSession(anyString(),anyString(),any(),anyLong(),anyLong())).thenAnswer(call->{checkpoint.set(Json.MAPPER.valueToTree(call.getArgument(2)));return revision.incrementAndGet();});
        when(store.loadSession("run-1",subject)).thenAnswer(call->Map.of("revision",revision.get(),"content",checkpoint.get()));
        when(store.claimTurn("run-1")).thenReturn(true);
        doAnswer(call->{status.set(call.getArgument(1));return null;}).when(store).finish(eq("run-1"),anyString(),any());
        when(provider.complete(anyList(),anyList(),eq(4000))).thenReturn(Json.parse("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call1\",\"type\":\"function\",\"function\":{\"name\":\"files_read\",\"arguments\":\"{\\\"path\\\":\\\"pom.xml\\\"}\"}}]}}],\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":10}}"),Json.parse("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"The project uses Maven.\"}}],\"usage\":{\"prompt_tokens\":30,\"completion_tokens\":8}}"));
        AiService service=new AiService(policy,store,provider,settings,gitlab,new JobEvents());
        service.start(jwt,"delegated",Json.parse("{\"projectId\":1042,\"mode\":\"QUESTION\",\"task\":\"code-review\",\"message\":\"Inspect the build\",\"context\":[],\"stacks\":[]}"),"request-1",false);
        awaitStatus(status,"WAITING_APPROVAL");
        var result=Json.parse("{\"toolCallId\":\"call1\",\"result\":{\"path\":\"pom.xml\",\"content\":\"<project/>\"}}");
        service.toolResult("run-1",jwt,"delegated",result); service.toolResult("run-1",jwt,"delegated",result);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(()->assertDoesNotThrow(()->service.next("run-1",jwt,"delegated",Json.parse("{}"),"turn-key-1")));
        awaitStatus(status,"SUCCEEDED"); service.next("run-1",jwt,"delegated",Json.parse("{}"),"turn-key-1");
        verify(provider,times(2)).complete(anyList(),anyList(),eq(4000)); verify(policy,never()).authorize(any(),anyLong(),anyString(),eq("AGENT_RUN"),anyString());
        verify(store,times(2)).reserve(anyString(),eq(1042L),eq(subject),anyLong(),eq(1000000L),eq(1000000L));
    }
    /** Prevents a direct client from requesting an agent mode through the chat route. Exigences : DEVAI-039, DEVAI-151. */
    @Test void directModeBypassDenied() {
        AiService service=new AiService(mock(PolicyService.class),mock(Store.class),mock(ModelProvider.class),new Settings(new MockEnvironment()),mock(GitLabClient.class),new JobEvents());
        assertEquals("INVALID_REQUEST",assertThrows(OdsException.class,()->service.start(identity(),"delegated",Json.parse("{\"projectId\":1,\"mode\":\"AGENT\",\"message\":\"change files\"}"),"request-1",false)).code());
    }
    /** Classifies a remediation by its governed task instead of trusting a weaker supplied action. Exigences : DEVAI-039, DEVAI-090. */
    @Test void remediationCannotTunnelThroughChat() {
        PolicyService policy=mock(PolicyService.class); ModelProvider provider=mock(ModelProvider.class);
        when(policy.authorize(any(),anyLong(),anyString(),eq("SONAR_REMEDIATION"),anyString())).thenThrow(new OdsException("ACTION_DENIED","denied"));
        AiService service=new AiService(policy,mock(Store.class),provider,new Settings(new MockEnvironment()),mock(GitLabClient.class),new JobEvents());
        assertThrows(OdsException.class,()->service.start(identity(),"delegated",Json.parse("{\"projectId\":1,\"mode\":\"QUESTION\",\"task\":\"sonar-remediation\",\"action\":\"CHAT\",\"message\":\"fix\"}"),"request-1",false));
        verifyNoInteractions(provider);
    }
    /** Uses only deployment model and endpoint configuration in the real Mistral adapter contract. Exigences : DEVAI-064. */
    @Test void mistralAdapterContract() {
        Settings settings=new Settings(new MockEnvironment().withProperty("ods.mistral-model","approved-model").withProperty("ods.mistral-url","https://api.mistral.ai").withProperty("ods.mistral-key","test-placeholder"));
        RemoteHttp http=mock(RemoteHttp.class); when(http.json(anyString(),any(),anyMap(),any())).thenReturn(Json.parse("{}"));
        new MistralProvider(settings,http).complete(List.of(Map.of("role","user","content","Hello")),List.of(),10000);
        verify(http).json(eq("POST"),eq(java.net.URI.create("https://api.mistral.ai/v1/chat/completions")),anyMap(),argThat(value->{Map<?,?> payload=(Map<?,?>)value;return payload.get("model").equals("approved-model")&&payload.get("max_tokens").equals(4000)&&!payload.containsKey("tools");}));
    }
}
