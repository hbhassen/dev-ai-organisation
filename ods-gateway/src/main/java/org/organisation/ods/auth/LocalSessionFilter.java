package org.organisation.ods.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Protects the owned loopback process after the native Git access gate, without SSO. Exigences : DEVAI-168, DEVAI-169. */
public final class LocalSessionFilter extends OncePerRequestFilter {
    private final byte[] token;
    /** Requires a strong ephemeral launch credential; it is never accepted from request bodies. Exigences : DEVAI-168. */
    public LocalSessionFilter(String configured) {
        if(!configured.matches("[a-f0-9]{64}")) throw new IllegalStateException("Private gateway launch credential required");
        token=configured.getBytes(StandardCharsets.UTF_8);
    }
    /** Authenticates only loopback calls with the launch credential and an opaque local subject. Exigences : DEVAI-168, DEVAI-169. */
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String supplied=request.getHeader("X-ODS-Local-Token");
        boolean local=List.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(request.getRemoteAddr());
        if(local&&supplied!=null&&MessageDigest.isEqual(token,supplied.getBytes(StandardCharsets.UTF_8))) {
            Jwt jwt=Jwt.withTokenValue("local-owned-session").header("alg","LOCAL").issuer("https://ods.local").subject("local-installation").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).claim("ods_embedded",true).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of()));
        }
        chain.doFilter(request,response);
    }
}
