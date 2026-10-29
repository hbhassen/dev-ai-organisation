package org.organisation.ods;

import org.junit.jupiter.api.Test;
import org.organisation.ods.auth.LocalSessionFilter;
import org.organisation.ods.integration.*;
import org.organisation.ods.contracts.*;
import org.springframework.mock.web.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Tests the local private channel and project binding without SSO or real developer credentials. Exigences : DEVAI-168, DEVAI-169, DEVAI-177. */
class LocalAccessTest {
    /** Rejects wrong tokens and non-loopback calls while creating an opaque identity for the actual owned channel. Exigences : DEVAI-168, DEVAI-169. */
    @Test void localFilterRequiresPrivateTokenAndLoopback() throws Exception {
        String token="a".repeat(64);
        for(String remote:List.of("127.0.0.1","192.0.2.1")) for(String supplied:List.of(token,"wrong")) {
            SecurityContextHolder.clearContext();
            try {
                MockHttpServletRequest request=new MockHttpServletRequest("GET","/api/v1/prompts"); request.setRemoteAddr(remote); request.addHeader("X-ODS-Local-Token",supplied);
                new LocalSessionFilter(token).doFilter(request,new MockHttpServletResponse(),(req,res)->assertEquals(remote.equals("127.0.0.1")&&supplied.equals(token),SecurityContextHolder.getContext().getAuthentication()!=null));
            } finally { SecurityContextHolder.clearContext(); }
        }
        assertThrows(IllegalStateException.class,()->new LocalSessionFilter("weak"));
    }
    /** Prevents service credentials from widening access beyond the exact repository checked by the extension. Exigences : DEVAI-169. */
    @Test void managedReadsAreRepositoryBound() {
        var settings=new Settings(new MockEnvironment().withProperty("ods.embedded","true").withProperty("ods.gitlab-url","https://gitlab.invalid").withProperty("ods.repository","team/allowed").withProperty("ods.governance-token","synthetic-governance"));
        RemoteHttp http=mock(RemoteHttp.class); when(http.json(anyString(),any(),anyMap(),isNull())).thenReturn(Json.parse("{\"id\":1,\"path_with_namespace\":\"team/allowed\"}"),Json.parse("{\"id\":2,\"path_with_namespace\":\"team/other\"}"));
        Jwt jwt=Jwt.withTokenValue("local").header("alg","LOCAL").subject("test").claim("ods_embedded",true).build();
        GitLabClient client=new GitLabClient(settings,http); assertEquals(1,client.authorize(jwt,1,null).path("id").asInt());
        assertEquals("PROJECT_ACCESS_DENIED",assertThrows(OdsException.class,()->client.authorize(jwt,2,null)).code());
        verify(http,times(2)).json(eq("GET"),any(),eq(Map.of("Authorization","Bearer synthetic-governance")),isNull());
    }
}
