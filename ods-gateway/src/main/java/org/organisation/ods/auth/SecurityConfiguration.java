package org.organisation.ods.auth;
import org.organisation.ods.Settings;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;

/** Makes the default unconfigured gateway fail closed and validates issuer/audience when configured. Exigences : DEVAI-003, DEVAI-020, DEVAI-127. */
@Configuration
public class SecurityConfiguration {
    /** Creates stateless bearer authentication; only health and public OIDC metadata are anonymous. Exigences : DEVAI-020, DEVAI-129. */
    @Bean
    public SecurityFilterChain security(HttpSecurity http,Settings settings) throws Exception {
        http.csrf(csrf->csrf.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        String issuer=settings.get("oidc-issuer");
        if(issuer.isBlank()||issuer.contains(".example")) {
            http.authorizeHttpRequests(a->a.requestMatchers("/health/live","/health/ready","/api/v1/auth/config").permitAll().anyRequest().denyAll());
        } else {
            settings.origin("oidc-issuer");
            NimbusJwtDecoder decoder=JwtDecoders.fromIssuerLocation(issuer);
            String audience=settings.require("oidc-audience");
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),jwt->jwt.getAudience().contains(audience)?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Audience incorrecte",null))));
            http.authorizeHttpRequests(a->a.requestMatchers("/health/live","/health/ready","/api/v1/auth/config").permitAll().anyRequest().authenticated()).oauth2ResourceServer(o->o.jwt(j->j.decoder(decoder)));
        }
        return http.build();
    }
}
