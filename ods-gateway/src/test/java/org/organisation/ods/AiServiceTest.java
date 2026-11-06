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

/** Qualifies the actual orchestration with isolated provider and GitLab contract doubles. Exigences : DEVAI-039, DEVAI-077, DEVAI-153, DEVAI-190, DEVAI-194, DEVAI-199. */
class AiServiceTest {
    /** Creates an authenticated test identity, never a production bypass configuration. Exigences : DEVAI-022. */
    private Jwt identity() { return Jwt.withTokenValue("test-token").header("alg","RS256").issuer("https://identity.example").subject("developer").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("gitlab_user_id","7").build(); }
    /** Waits for the asynchronous provider result with a bounded deadline and fails on an absent transition. Exigences : DEVAI-119, DEVAI-153. */
    private void awaitStatus(AtomicReference<String> status,String expected) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos(); while(!expected.equals(status.get())&&System.nanoTime()<deadline) Thread.sleep(10); assertEquals(expected,status.get());
    }
    /** Reads a tool result, resumes Question mode and proves provider-resolved, idempotent continuation invokes the model once. Exigences : DEVAI-063, DEVAI-077, DEVAI-151, DEVAI-153, DEVAI-190, DEVAI-191, DEVAI-194. */
    @Test void questionToolLoopAndIdempotence() throws Exception { toolLoop("QUESTION","MISTRAL"); }
    /** Qualifies each selected provider in all three modes through persisted tool continuation. Exigences : DEVAI-199. */
    @Test void selectedProvidersSupportAllModes() throws Exception { for(String provider:List.of("MISTRAL","OPENAI")) for(String mode:List.of("QUESTION","MODIFICATION","AGENT")) toolLoop(mode,provider); }
    /** Runs the actual asynchronous service with selected identity, tools and immutable session provenance. Exigences : DEVAI-039, DEVAI-077, DEVAI-199. */
    private void toolLoop(String mode,String providerId) throws Exception {
        PolicyService policy=mock(PolicyService.class); Store store=mock(Store.class); ModelProvider provider=mock(ModelProvider.class); GitLabClient gitlab=mock(GitLabClient.class);
        doAnswer(call->{ ModelSelection selection=call.getArgument(0); assertEquals(providerId,selection.providerId()); assertEquals("selected-model",selection.model()); return provider.complete(call.getArgument(1),call.getArgument(2),call.getArgument(3)); }).when(provider).stream(any(ModelSelection.class),anyList(),anyList(),anyInt(),any());
        Settings settings=new Settings(new MockEnvironment()); Jwt jwt=identity(); String subject=PolicyService.subject(jwt);
        var rule=Json.parse("{\"monthlyTokenQuota\":1000000,\"contextMode\":\"LOCAL_GOVERNED\",\"allowFreeText\":true,\"allowedAgents\":[\"implement-requirement\"],\"modelAlias\":\"approved\"}");
        when(policy.authorizeResolved(any(),eq(1042L),anyString(),anyString(),anyString(),nullable(String.class))).thenReturn(new PolicyService.Authorization(rule,new ModelSelection(providerId,"selected-model","mistral","openai","https://api.mistral.ai","ODS_MISTRAL_API_KEY",true,true,true,false,true),"selected")); when(gitlab.stacks(1042,"delegated")).thenReturn(Set.of("JAVA"));
        when(store.createJob(anyString(),anyLong(),anyString(),anyString(),anyString())).thenReturn(Map.of("jobId","run-1")); when(store.claim("run-1")).thenReturn(true);
        AtomicReference<String> status=new AtomicReference<>("QUEUED"); when(store.job("run-1",subject)).thenAnswer(call->Map.of("jobId","run-1","status",status.get()));
        AtomicReference<com.fasterxml.jackson.databind.JsonNode> checkpoint=new AtomicReference<>(); java.util.concurrent.atomic.AtomicLong revision=new java.util.concurrent.atomic.AtomicLong();
        when(store.saveSession(anyString(),anyString(),any(),anyLong(),anyLong())).thenAnswer(call->{checkpoint.set(Json.MAPPER.valueToTree(call.getArgument(2)));return revision.incrementAndGet();});
        when(store.loadSession("run-1",subject)).thenAnswer(call->Map.of("revision",revision.get(),"content",checkpoint.get()));
        when(store.claimTurn("run-1")).thenReturn(true);
        doAnswer(call->{status.set(call.getArgument(1));return null;}).when(store).finish(eq("run-1"),anyString(),any());
        when(provider.complete(anyList(),anyList(),eq(4000))).thenReturn(Json.parse("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call1\",\"type\":\"function\",\"function\":{\"name\":\"files_read\",\"arguments\":\"{\\\"path\\\":\\\"pom.xml\\\"}\"}}]}}],\"usage\":{\"prompt_tokens\":20,\"completion_tokens\":10}}"),Json.parse("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"The project uses Maven.\"}}],\"usage\":{\"prompt_tokens\":30,\"completion_tokens\":8}}"));
        AiService service=new AiService(policy,store,provider,settings,gitlab,new JobEvents());
        service.start(jwt,"delegated",Json.MAPPER.valueToTree(Map.of("projectId",1042,"mode",mode,"modelAlias","selected","task","code-review","message","Inspect the build","context",List.of(),"stacks",List.of())),"request-1",mode.equals("AGENT"));
        awaitStatus(status,"WAITING_APPROVAL");
        var result=Json.parse("{\"toolCallId\":\"call1\",\"result\":{\"path\":\"pom.xml\",\"content\":\"<project/>\"}}");
        service.toolResult("run-1",jwt,"delegated",result); service.toolResult("run-1",jwt,"delegated",result);
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(()->assertDoesNotThrow(()->service.next("run-1",jwt,"delegated",Json.parse("{}"),"turn-key-1")));
        awaitStatus(status,"SUCCEEDED"); service.next("run-1",jwt,"delegated",Json.parse("{}"),"turn-key-1");
        verify(provider,times(2)).complete(anyList(),anyList(),eq(4000)); verify(policy,never()).authorize(any(),anyLong(),anyString(),eq("AGENT_RUN"),anyString());
        verify(store,times(2)).reserve(anyString(),eq(1042L),eq(subject),eq(providerId),eq("selected"),eq("selected-model"),anyLong(),eq(1000000L),eq(1000000L),anyLong());
        assertEquals("selected",checkpoint.get().path("modelAlias").asText()); assertEquals(providerId,checkpoint.get().path("provider").asText());
        when(policy.authorizeResolved(any(),eq(1042L),anyString(),anyString(),anyString(),eq("selected"))).thenReturn(new PolicyService.Authorization(rule,new ModelSelection(providerId,"changed-model","mistral","openai","https://api.mistral.ai","ODS_MISTRAL_API_KEY",true,true,true,false,true),"selected"));
        assertEquals("MODEL_NOT_ALLOWED",assertThrows(OdsException.class,()->service.next("run-1",jwt,"delegated",Json.parse("{}"),"changed-model-turn")).code());
        verify(provider,times(2)).complete(anyList(),anyList(),eq(4000));
    }
    /** Prevents a direct client from requesting an agent mode through the chat route. Exigences : DEVAI-039, DEVAI-151. */
    @Test void directModeBypassDenied() {
        AiService service=new AiService(mock(PolicyService.class),mock(Store.class),mock(ModelProvider.class),new Settings(new MockEnvironment()),mock(GitLabClient.class),new JobEvents());
        assertEquals("INVALID_REQUEST",assertThrows(OdsException.class,()->service.start(identity(),"delegated",Json.parse("{\"projectId\":1,\"mode\":\"AGENT\",\"message\":\"change files\"}"),"request-1",false)).code());
    }
    /** Migrates only legacy checkpoints with verified job provenance and rejects ambiguous history. Exigences : DEVAI-196, DEVAI-199. */
    @Test void legacyCheckpointRequiresModelProvenance() {
        PolicyService policy=mock(PolicyService.class); Store store=mock(Store.class); ModelProvider provider=mock(ModelProvider.class);
        Jwt jwt=identity(); String subject=PolicyService.subject(jwt);
        var state=Json.parse("{\"project\":1042,\"mode\":\"QUESTION\",\"task\":\"code-review\",\"action\":\"CHAT\",\"pending\":[\"call1\"],\"messages\":[],\"stacks\":[]}");
        when(store.loadSession("legacy",subject)).thenReturn(Map.of("revision",1L,"content",state));
        when(store.job("legacy",subject)).thenReturn(Map.of("result",Map.of("modelAlias","approved","provider","MISTRAL","model","legacy-model")));
        var rule=Json.parse("{\"modelAlias\":\"approved\"}");
        when(policy.authorizeResolved(any(),eq(1042L),anyString(),eq("CHAT"),anyString(),eq("approved"))).thenReturn(new PolicyService.Authorization(rule,new ModelSelection("MISTRAL","legacy-model","mistral","openai","https://api.mistral.ai","ODS_MISTRAL_API_KEY",true,true,true,false,true)));
        AiService service=new AiService(policy,store,provider,new Settings(new MockEnvironment()),mock(GitLabClient.class),new JobEvents());
        service.toolResult("legacy",jwt,"delegated",Json.parse("{\"toolCallId\":\"call1\",\"result\":{\"content\":\"test\"}}"));
        verify(store).saveSession(eq("legacy"),eq(subject),argThat(saved->"legacy-model".equals(((Map<?,?>)saved).get("model"))),eq(1L),anyLong());
        when(store.job("legacy",subject)).thenReturn(Map.of("result",Map.of()));
        assertEquals("MODEL_NOT_ALLOWED",assertThrows(OdsException.class,()->service.next("legacy",jwt,"delegated",Json.parse("{}"),"legacy-turn")).code());
        verifyNoInteractions(provider);
    }
    /** Denies unsupported Modification and Agent before job creation or paid model traffic. Exigences : DEVAI-193, DEVAI-199. */
    @Test void textOnlyModelCannotStartWriteModes() {
        PolicyService policy=mock(PolicyService.class); Store store=mock(Store.class); ModelProvider provider=mock(ModelProvider.class);
        var rule=Json.parse("{\"modelAlias\":\"smoke\"}");
        when(policy.authorizeResolved(any(),anyLong(),anyString(),anyString(),anyString(),nullable(String.class))).thenReturn(new PolicyService.Authorization(rule,new ModelSelection("OPENAI","text-model","generic","openai","https://api.openai.com/v1","ODS_OPENAI_API_KEY",true,false,false,false,true)));
        AiService service=new AiService(policy,store,provider,new Settings(new MockEnvironment()),mock(GitLabClient.class),new JobEvents());
        for(String mode:List.of("MODIFICATION","AGENT")) assertEquals("MODEL_NOT_ALLOWED",assertThrows(OdsException.class,()->service.start(identity(),"delegated",Json.MAPPER.valueToTree(Map.of("projectId",1042,"mode",mode,"message","test")),"text-request",mode.equals("AGENT"))).code());
        verifyNoInteractions(provider,store);
    }
    /** Classifies a remediation by its governed task instead of trusting a weaker supplied action. Exigences : DEVAI-039, DEVAI-090. */
    @Test void remediationCannotTunnelThroughChat() {
        PolicyService policy=mock(PolicyService.class); ModelProvider provider=mock(ModelProvider.class);
        when(policy.authorizeResolved(any(),anyLong(),anyString(),eq("SONAR_REMEDIATION"),anyString(),nullable(String.class))).thenThrow(new OdsException("ACTION_DENIED","denied"));
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
