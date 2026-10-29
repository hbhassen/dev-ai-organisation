package org.organisation.ods;

import org.junit.jupiter.api.Test;
import org.organisation.ods.contracts.*;
import org.organisation.ods.integration.*;
import org.organisation.ods.data.Store;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Verifies the exact Xray graph and HTTP contracts using synthetic service credentials and no external calls. Exigences : DEVAI-173, DEVAI-177. */
class LocalAnalysisTest {
    /** Supplies a resolved CycloneDX dependency with an impact edge and a disconnected component. Exigences : DEVAI-173. */
    private com.fasterxml.jackson.databind.JsonNode bom() { return Json.parse("""
        {"bomFormat":"CycloneDX","components":[{"bom-ref":"a","purl":"pkg:npm/%40scope/a@1.2.3"},{"bom-ref":"b","purl":"pkg:pypi/demo@2.0"},{"bom-ref":"c","purl":"pkg:maven/org.example/lib@3.0"}],"dependencies":[{"ref":"a","dependsOn":["b"]},{"ref":"b","dependsOn":["a"]}]}
        """); }
    /** Ensures cyclic and disconnected packages survive conversion with the same field names as the reference client. Exigences : DEVAI-173. */
    @Test void graphPreservesCyclesAndDisconnectedPackages() {
        String graph=Json.write(XrayGraph.convert(bom(),"ANGULAR"));
        assertTrue(graph.contains("npm://@scope/a:1.2.3")); assertTrue(graph.contains("pypi://demo:2.0")); assertTrue(graph.contains("gav://org.example:lib:3.0")); assertTrue(graph.contains("\"component_id\":\"root-node\"")); assertTrue(graph.contains("\"nodes\""));
        assertEquals("INVALID_SBOM",assertThrows(OdsException.class,()->XrayGraph.convert(Json.parse("{\"bomFormat\":\"CycloneDX\",\"components\":[{\"bom-ref\":\"x\",\"name\":\"x\"}]}"),"PYTHON")).code());
    }
    /** Checks watch encoding, both authentication modes, pending 202 and preserved remediation evidence. Exigences : DEVAI-173, DEVAI-177. */
    @Test void graphScanAuthenticatesPollsAndNormalizes() throws Exception {
        for(String mode:List.of("TOKEN","BASIC")) {
            RemoteHttp http=mock(RemoteHttp.class); Store store=mock(Store.class);
            var env=new MockEnvironment().withProperty("ods.jfrog-url","https://xray.invalid").withProperty("ods.jfrog-watch","watch team").withProperty("ods.jfrog-project-key","managed").withProperty("ods.jfrog-auth",mode).withProperty("ods.jfrog-username","svc-example").withProperty("ods.jfrog-password","synthetic-password").withProperty("ods.jfrog-token","synthetic-token");
            when(http.json(eq("POST"),any(),anyMap(),any())).thenReturn(Json.parse("{\"scan_id\":\"scan-123\"}"));
            when(http.exchange(eq("GET"),any(),anyMap(),isNull())).thenReturn(new RemoteHttp.Response(202,Json.parse("{}")),new RemoteHttp.Response(200,Json.parse("{\"vulnerabilities\":[{\"issue_id\":\"XRAY-1\",\"summary\":\"Upgrade dependency\",\"components\":{\"npm://a:1\":{\"fixed_versions\":[\"2\"]}},\"cves\":[{\"cve\":\"CVE-2026-0001\"}]}],\"violations\":[{\"issue_id\":\"XRAY-1\",\"watch_name\":\"watch team\",\"fail_build\":true}]}")));
            var service=new LocalAnalysisService(new Settings(env),http,mock(LocalScanner.class),mock(GitLabClient.class),store);
            var result=service.xray("job",bom(),"ANGULAR");
            assertEquals("scan-123",result.get("scanId")); assertEquals(2,((List<?>)result.get("issues")).size());
            String auth=mode.equals("TOKEN")?"Bearer synthetic-token":"Basic "+Base64.getEncoder().encodeToString("svc-example:synthetic-password".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            verify(http).json(eq("POST"),argThat(uri->uri.getRawQuery().equals("watch=watch%20team&scan_type=dependency&projectKey=managed")),eq(Map.of("Authorization",auth)),any());
            verify(http,times(2)).exchange(eq("GET"),argThat(uri->uri.getPath().equals("/xray/api/v1/scan/graph/scan-123")&&uri.getQuery().contains("include_licenses=true")),anyMap(),isNull());
        }
    }
    /** Rejects malformed replies and observes cancellation before any provider submission. Exigences : DEVAI-173, DEVAI-177. */
    @Test void invalidResponseAndCancellationFailClosed() {
        RemoteHttp http=mock(RemoteHttp.class); Store store=mock(Store.class);
        var settings=new Settings(new MockEnvironment().withProperty("ods.jfrog-url","https://xray.invalid").withProperty("ods.jfrog-watch","watch").withProperty("ods.jfrog-auth","TOKEN").withProperty("ods.jfrog-username","svc").withProperty("ods.jfrog-token","synthetic"));
        var service=new LocalAnalysisService(settings,http,mock(LocalScanner.class),mock(GitLabClient.class),store);
        when(store.cancelled("job")).thenReturn(true);
        assertEquals("CANCELLED",assertThrows(OdsException.class,()->service.xray("job",bom(),"ANGULAR")).code()); verifyNoInteractions(http);
        when(store.cancelled("job")).thenReturn(false); when(http.json(anyString(),any(),anyMap(),any())).thenReturn(Json.parse("{\"scan_id\":\"../unsafe\"}"));
        assertEquals("INVALID_RESPONSE",assertThrows(OdsException.class,()->service.xray("job",bom(),"ANGULAR")).code());
    }
    /** Verifies the Sonar task identity, pagination and analysis freshness before storing a local report. Exigences : DEVAI-174, DEVAI-175, DEVAI-177. */
    @Test void localSonarUsesScannerTaskAndRefusesReplacedAnalysis() throws Exception {
        for(boolean replaced:List.of(false,true)) {
            RemoteHttp http=mock(RemoteHttp.class); Store store=mock(Store.class); LocalScanner scanner=mock(LocalScanner.class);
            var env=new MockEnvironment().withProperty("ods.sonar-url","https://sonar.invalid").withProperty("ods.sonar-token","synthetic-sonar").withProperty("ods.sonar-project-1","project-one");
            when(store.createJob(anyString(),eq(1L),eq("LOCAL_SONAR"),anyString(),anyString())).thenReturn(Map.of("jobId","local-job")); when(store.claim("local-job")).thenReturn(true); when(store.job(anyString(),anyString())).thenReturn(Map.of("jobId","local-job","status","RUNNING"));
            when(scanner.run(eq("sonar"),eq("JAVA"),anyMap(),any(),any())).thenReturn(Json.parse("{\"ceTaskId\":\"ce-one\",\"fingerprint\":\"source-hash\"}"));
            when(http.json(eq("GET"),any(),anyMap(),isNull())).thenAnswer(call->{ String path=((java.net.URI)call.getArgument(1)).getPath(); return switch(path) {
                case "/api/ce/task"->Json.parse("{\"task\":{\"status\":\"SUCCESS\",\"componentKey\":\"project-one\",\"analysisId\":\"analysis-one\"}}");
                case "/api/project_analyses/search"->Json.parse("{\"analyses\":[{\"key\":\""+(replaced?"analysis-other":"analysis-one")+"\"}]}");
                case "/api/issues/search"->Json.parse("{\"issues\":[{\"key\":\"issue-one\",\"component\":\"component-one\",\"message\":\"Fix quality\"}],\"components\":[{\"key\":\"component-one\",\"path\":\"src/App.java\"}],\"paging\":{\"total\":1}}");
                case "/api/qualitygates/project_status"->Json.parse("{\"projectStatus\":{\"status\":\"ERROR\"}}");
                default->throw new AssertionError("Unexpected provider request");
            }; });
            var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("synthetic").header("alg","none").subject("test").build();
            new LocalAnalysisService(new Settings(env),http,scanner,mock(GitLabClient.class),store).start("SONAR",jwt,Json.parse("{\"projectId\":1,\"profile\":\"JAVA\"}"),"request-one");
            var result=org.mockito.ArgumentCaptor.forClass(Object.class); verify(store,timeout(5000)).finish(eq("local-job"),eq(replaced?"FAILED":"SUCCEEDED"),result.capture());
            var data=Json.MAPPER.valueToTree(result.getValue());
            if(replaced) assertEquals("STALE_REPORT",data.path("code").asText());
            else { assertEquals("analysis-one",data.path("analysisId").asText()); assertEquals("source-hash",data.path("fingerprint").asText()); assertEquals("src/App.java",data.path("issues").path(0).path("path").asText()); }
            verify(scanner).run(eq("sonar"),eq("JAVA"),argThat(config->config.get("token").equals("synthetic-sonar")),any(),any());
        }
    }
}
