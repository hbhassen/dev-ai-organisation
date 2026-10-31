package org.organisation.ods;

import java.net.URI;
import java.net.http.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.organisation.ods.contracts.Json;
import org.organisation.ods.integration.LocalAnalysisService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Checks the actual HTTP converter used by the embedded gateway before frontend remediation. Exigences : DEVAI-093, DEVAI-103, DEVAI-177. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:fingerprint-http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","ods.embedded=true","ods.local-token=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","server.address=127.0.0.1","logging.level.root=WARN"})
class FingerprintHttpTest {
    @LocalServerPort int port;
    @MockitoBean LocalAnalysisService local;
    /** Preserves the fingerprint field through Spring serialization and retains the local authentication boundary. Exigences : DEVAI-093, DEVAI-168, DEVAI-177. */
    @Test void fingerprintIsAJsonObjectOverHttp() throws Exception {
        String digest="b".repeat(64);
        when(local.fingerprint(any(),eq(2L))).thenReturn(Json.MAPPER.valueToTree(Map.of("fingerprint",digest)));
        var client=HttpClient.newHttpClient();
        var uri=URI.create("http://127.0.0.1:"+port+"/api/v1/workspace/fingerprint?projectId=2");
        var response=client.send(HttpRequest.newBuilder(uri).header("X-ODS-Local-Token","a".repeat(64)).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode());
        assertEquals(Json.MAPPER.valueToTree(Map.of("fingerprint",digest)),Json.MAPPER.readTree(response.body()));
        var denied=client.send(HttpRequest.newBuilder(uri).build(),HttpResponse.BodyHandlers.ofString());
        assertTrue(denied.statusCode()==401||denied.statusCode()==403);
        verify(local,times(1)).fingerprint(any(),eq(2L));
    }
}
