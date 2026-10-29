package org.organisation.ods.integration;
import org.organisation.ods.Settings;
import org.organisation.ods.contracts.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.jwt.Jwt;
import java.net.URI;
import java.util.*;

/** Separates managed read credentials from developer credentials used for MR publication. Exigences : DEVAI-022, DEVAI-023, DEVAI-112. */
@Component
public final class GitLabClient {
    private final Settings settings; private final RemoteHttp http;
    /** Injects only deployment-owned service configuration. Exigences : DEVAI-036. */
    public GitLabClient(Settings settings,RemoteHttp http) { this.settings=settings; this.http=http; }
    /** Calls one GitLab v4 route on the configured instance, without redirects. Exigences : DEVAI-025, DEVAI-036. */
    public JsonNode call(String method,String route,String token,Object body) {
        if(token==null||token.isBlank()) throw new OdsException("AUTHENTICATION_REQUIRED","Connecter le compte GitLab utilisateur");
        URI base=settings.origin("gitlab-url");
        return http.json(method,URI.create(base.toString().replaceAll("/$","")+"/api/v4/"+route),Map.of("Authorization","Bearer "+token),body);
    }
    /** Binds managed reads to the Git-verified repository; preserves delegated checks for standalone servers. Exigences : DEVAI-022, DEVAI-169. */
    public JsonNode authorize(Jwt jwt,long project,String token) {
        if(jwt==null||project<1) throw new OdsException("PROJECT_ACCESS_DENIED","Projet inaccessible");
        if(settings.get("embedded").equals("true")&&Boolean.TRUE.equals(jwt.getClaim("ods_embedded"))) {
            JsonNode metadata=call("GET","projects/"+project,settings.require("governance-token"),null);
            if(!metadata.path("path_with_namespace").asText().equals(settings.require("repository"))) throw new OdsException("PROJECT_ACCESS_DENIED","Projet différent du dépôt Git vérifié");
            return metadata;
        }
        JsonNode user=call("GET","user",token,null); Object claim=jwt.getClaims().get("gitlab_user_id");
        if(claim==null||!claim.toString().equals(user.path("id").asText())) throw new OdsException("PROJECT_ACCESS_DENIED","Association OIDC/GitLab non vérifiée");
        return call("GET","projects/"+project,token,null);
    }
    /** Selects the build-time read account only for governed metadata in the embedded runtime. Exigences : DEVAI-169. */
    public String readToken(String developerToken) { return settings.get("embedded").equals("true")?settings.require("governance-token"):developerToken; }
    /** Reads an immutable repository file and returns exactly the source bytes used by the signature. Exigences : DEVAI-028. */
    public byte[] file(long project,String path,String commit,String token) {
        token=readToken(token);
        JsonNode response=call("GET","projects/"+project+"/repository/files/"+RemoteHttp.encode(path)+"?ref="+RemoteHttp.encode(commit),token,null);
        try { return Base64.getMimeDecoder().decode(response.path("content").asText()); } catch(Exception e) { throw new OdsException("INVALID_RESPONSE","Fichier GitLab invalide"); }
    }
    /** Resolves a protected governance reference once; subsequent reads use only its immutable SHA. Exigences : DEVAI-028. */
    public String commit(long project,String ref,String token) { token=readToken(token); return Json.text(call("GET","projects/"+project+"/repository/commits/"+RemoteHttp.encode(ref),token,null),"id"); }
    /** Detects stack manifests from an immutable server tree so clients cannot suppress mandatory profiles. Exigences : DEVAI-040, DEVAI-162. */
    public Set<String> stacks(long project,String token) {
        token=readToken(token);
        JsonNode metadata=call("GET","projects/"+project,token,null); String ref=commit(project,Json.text(metadata,"default_branch"),token); Set<String> result=new TreeSet<>();
        for(int page=1;page<=20;page++) {
            JsonNode tree=call("GET","projects/"+project+"/repository/tree?recursive=true&per_page=100&page="+page+"&ref="+RemoteHttp.encode(ref),token,null);
            if(!tree.isArray()) throw new OdsException("INVALID_RESPONSE","Arbre GitLab invalide");
            for(JsonNode entry:tree) { String path=entry.path("path").asText();
                if(path.endsWith("pom.xml")||path.endsWith(".java")) result.add("JAVA");
                if(path.endsWith("angular.json")) result.add("ANGULAR");
                if(path.endsWith("pyproject.toml")||path.endsWith(".py")) result.add("PYTHON");
                if(path.endsWith(".csproj")||path.endsWith(".cs")) result.add("DOTNET");
            }
            if(tree.size()<100) return result;
        }
        throw new OdsException("CONTEXT_REJECTED","Arbre trop volumineux ; configurer un périmètre de modules dédié");
    }
}
