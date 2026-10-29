package org.organisation.ods;

import org.organisation.ods.contracts.*;
import org.organisation.ods.integration.*;
import org.organisation.ods.data.Store;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real report normalization and freshness decisions with isolated HTTP contract responses. Exigences : DEVAI-088, DEVAI-089. */
class ScanServiceTest {
    /** Provides a configured connector without sending credentials or requests to a real service. Exigences : DEVAI-086. */
    private ScanService service(RemoteHttp http) {
        return new ScanService(new Settings(new MockEnvironment().withProperty("ods.sonar-url","https://sonar.invalid").withProperty("ods.sonar-token","test-placeholder").withProperty("ods.sonar-project-1","project-key")),http,mock(GitLabClient.class),mock(Store.class));
    }
    /** Normalizes component paths and binds the quality gate to the reported immutable analysis. Exigences : DEVAI-088, DEVAI-089. */
    @Test void normalizedReportHasRevisionAndPaths() {
        RemoteHttp http=mock(RemoteHttp.class);
        var analysis=Json.parse("{\"analyses\":[{\"key\":\"a1\",\"revision\":\"abc123\",\"date\":\"2026-10-29T00:00:00Z\"}]}");
        when(http.json(anyString(),argThat(uri->uri!=null&&uri.getPath().contains("project_analyses")),anyMap(),isNull())).thenReturn(analysis);
        when(http.json(anyString(),argThat(uri->uri!=null&&uri.getPath().contains("issues")),anyMap(),isNull())).thenReturn(Json.parse("{\"issues\":[{\"key\":\"i1\",\"rule\":\"java:S1\",\"component\":\"c1\",\"message\":\"Fix this\",\"line\":12,\"severity\":\"MAJOR\"}],\"components\":[{\"key\":\"c1\",\"path\":\"src/Main.java\"}],\"paging\":{\"total\":1}}"));
        when(http.json(anyString(),argThat(uri->uri!=null&&uri.getPath().contains("qualitygates")),anyMap(),isNull())).thenReturn(Json.parse("{\"projectStatus\":{\"status\":\"ERROR\"}}"));
        var report=service(http).sonar(mock(Jwt.class),"delegated",1,1);
        assertEquals("abc123",report.get("commit")); assertEquals("src/Main.java",Json.MAPPER.valueToTree(report).path("issues").get(0).path("path").asText());
        verify(http).json(eq("GET"),argThat(uri->uri!=null&&uri.getQuery().equals("analysisId=a1")),anyMap(),isNull());
    }
    /** Rejects a report assembled across two different analyses instead of labeling it current. Exigences : DEVAI-089, DEVAI-102. */
    @Test void concurrentAnalysisRefused() {
        RemoteHttp http=mock(RemoteHttp.class); when(http.json(anyString(),any(),anyMap(),isNull())).thenReturn(Json.parse("{\"analyses\":[{\"key\":\"a1\"}]}"),Json.parse("{\"issues\":[],\"paging\":{}}"),Json.parse("{}"),Json.parse("{\"analyses\":[{\"key\":\"a2\"}]}"));
        assertEquals("STALE_REPORT",assertThrows(OdsException.class,()->service(http).sonar(mock(Jwt.class),"delegated",1,1)).code());
    }
}
