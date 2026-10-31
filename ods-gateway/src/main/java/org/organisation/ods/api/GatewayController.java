package org.organisation.ods.api;
import org.organisation.ods.*;
import org.organisation.ods.contracts.*;
import org.organisation.ods.security.*;
import org.organisation.ods.ai.AiService;
import org.organisation.ods.policy.PolicyService;
import org.organisation.ods.integration.*;
import org.organisation.ods.data.Store;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;

/** Versioned gateway facade; every project route verifies server-side ownership and action permissions. Exigences : DEVAI-003, DEVAI-117. */
@RestController
public class GatewayController {
    private final Settings settings; private final PolicyService policies; private final AiService ai; private final GitLabClient gitlab; private final ScanService scans; private final Store store;
    private final org.organisation.ods.ai.JobEvents events; private final LocalAnalysisService local;
    /** Injects authorization services rather than accepting a client-computed permission flag. Exigences : DEVAI-003. */
    public GatewayController(Settings settings,PolicyService policies,AiService ai,GitLabClient gitlab,ScanService scans,Store store,org.organisation.ods.ai.JobEvents events,LocalAnalysisService local) { this.settings=settings; this.policies=policies; this.ai=ai; this.gitlab=gitlab; this.scans=scans; this.store=store; this.events=events; this.local=local; }
    /** Exposes process liveness without disclosing infrastructure or secrets. Exigences : DEVAI-129. */
    @GetMapping("/health/live") public Map<String,Object> live() { return Map.of("status","UP","version","0.4.0"); }
    /** Clearly separates a running local gateway from configured AI readiness. Exigences : DEVAI-129. */
    @GetMapping("/health/ready") public ResponseEntity<?> ready() { boolean configured=(settings.get("embedded").equals("true")||!settings.get("oidc-issuer").isBlank())&&!settings.get("mistral-key").isBlank()&&!settings.get("governance-project-id").isBlank(); return ResponseEntity.status(configured?200:503).body(Map.of("status",configured?"CONFIGURED":"CONFIGURATION_REQUIRED","externalServicesVerified",false)); }
    /** Supplies public native-client OIDC metadata, never service credentials. Exigences : DEVAI-020. */
    @GetMapping("/api/v1/auth/config") public Map<String,Object> authConfig() { return Map.of("issuer",settings.get("oidc-issuer"),"clientId",settings.get("oidc-client-id"),"audience",settings.get("oidc-audience")); }
    /** Resolves a remote repository path on the configured GitLab and checks the developer's access. Exigences : DEVAI-025. */
    @GetMapping("/api/v1/projects/resolve") public Map<String,Object> project(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestParam String path) { JsonNode p=gitlab.call("GET","projects/"+RemoteHttp.encode(path),gitlab.readToken(token),null); gitlab.authorize(jwt,p.path("id").asLong(),token); return Map.of("projectId",p.path("id").asLong(),"name",p.path("path_with_namespace").asText(),"gitlabInstance",settings.get("gitlab-url")); }
    /** Reports effective project actions; provider selection remains Mistral-only. Exigences : DEVAI-026, DEVAI-033, DEVAI-064. */
    @GetMapping("/api/v1/capabilities") public Map<String,Object> capabilities(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestParam long projectId) {
        gitlab.authorize(jwt,projectId,token); JsonNode policy=policies.snapshot();
        for(JsonNode rule:policy.path("projects")) if(rule.path("projectId").asLong()==projectId) return Map.of("projectId",projectId,"rule",Json.map(rule),"policyVersion",policy.path("policyVersion").asLong(),"validUntil",policy.path("expiresAt").asText(),"provider","MISTRAL","usage",store.usage(projectId,PolicyService.subject(jwt)));
        return Map.of("projectId",projectId,"aiEnabled",false,"reason","ACTION_DENIED","allowedActions",List.of());
    }
    /** Displays current mandatory instructions for authorized sessions. Exigences : DEVAI-166. */
    @GetMapping("/api/v1/prompts/effective") public Map<String,Object> prompts(@RequestParam(defaultValue="JAVA") List<String> stack,@RequestParam(defaultValue="code-generation") String task) throws Exception { return ai.prompts(stack,task); }
    /** Lists built-in and signed organizational task prompts for native selection. Exigences : DEVAI-066, DEVAI-166. */
    @GetMapping("/api/v1/prompts") public List<Map<String,String>> promptCatalog() { return ai.tasks(); }
    /** Submits an asynchronous chat/generation with mandatory idempotency. Exigences : DEVAI-063, DEVAI-077. */
    @PostMapping("/api/v1/ai/chat") public ResponseEntity<?> chat(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestHeader("Idempotency-Key") String key,@RequestBody Map<String,Object> body) { return ResponseEntity.accepted().body(ai.start(jwt,token,Json.MAPPER.valueToTree(body),key,false)); }
    /** Starts an approved development agent under its separate project permission. Exigences : DEVAI-071, DEVAI-153. */
    @PostMapping("/api/v1/ai/agents/run") public ResponseEntity<?> agent(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestHeader("Idempotency-Key") String key,@RequestBody Map<String,Object> body) { return ResponseEntity.accepted().body(ai.start(jwt,token,Json.MAPPER.valueToTree(body),key,true)); }
    /** Continues a run only through the server's expected tool-result sequence. Exigences : DEVAI-153. */
    @PostMapping("/api/v1/ai/agents/{id}/turns") public ResponseEntity<?> turn(@PathVariable String id,@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestHeader("Idempotency-Key") String key,@RequestBody Map<String,Object> body) { return ResponseEntity.accepted().body(ai.next(id,jwt,token,Json.MAPPER.valueToTree(body),key)); }
    /** Records filtered results for previously requested tools, without accepting system messages. Exigences : DEVAI-038, DEVAI-153. */
    @PostMapping("/api/v1/ai/agents/{id}/tool-results") public Map<String,Object> tool(@PathVariable String id,@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestBody Map<String,Object> body) { return ai.toolResult(id,jwt,token,Json.MAPPER.valueToTree(body)); }
    /** Returns only a job owned by the verified subject. Exigences : DEVAI-117, DEVAI-119. */
    @GetMapping("/api/v1/jobs/{id}") public Map<String,Object> job(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) { return store.job(id,PolicyService.subject(jwt)); }
    /** Streams presentation text only after verifying the authenticated owner of the persisted operation. Exigences : DEVAI-078, DEVAI-117. */
    @GetMapping(value="/api/v1/jobs/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public org.springframework.web.servlet.mvc.method.annotation.SseEmitter progress(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) { store.job(id,PolicyService.subject(jwt)); return events.subscribe(id); }
    /** Requests cancellation without pretending already billed tokens disappeared. Exigences : DEVAI-024, DEVAI-078. */
    @PostMapping("/api/v1/jobs/{id}/cancel") public Map<String,Object> cancel(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) { store.cancel(id,PolicyService.subject(jwt)); return Map.of("cancelRequested",true); }
    /** Deletes only the requesting subject's stored conversation and assistant content. Exigences : DEVAI-067, DEVAI-123. */
    @DeleteMapping("/api/v1/ai/sessions/{id}") public Map<String,Object> deleteSession(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) { store.deleteSession(id,PolicyService.subject(jwt)); return Map.of("deleted",true); }
    /** Returns current project consumption after checking real project access. Exigences : DEVAI-075. */
    @GetMapping("/api/v1/ai/usage") public Map<String,Object> usage(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestParam long projectId) { gitlab.authorize(jwt,projectId,token); return store.usage(projectId,PolicyService.subject(jwt)); }
    /** Dispatches a genuine Sonar scanner task without requiring an AI entitlement. Exigences : DEVAI-027, DEVAI-085. */
    @PostMapping("/api/v1/quality/scans") public ResponseEntity<?> sonarScan(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestHeader("Idempotency-Key") String key,@RequestBody Map<String,Object> body) { return ResponseEntity.accepted().body(settings.get("embedded").equals("true")?local.start("SONAR",jwt,Json.MAPPER.valueToTree(body),key):scans.start("SONAR_SCAN",jwt,token,Json.MAPPER.valueToTree(body),key)); }
    /** Dispatches a genuine Xray audit independently of AI availability. Exigences : DEVAI-027, DEVAI-096. */
    @PostMapping("/api/v1/security/scans") public ResponseEntity<?> xrayScan(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestHeader("Idempotency-Key") String key,@RequestBody Map<String,Object> body) { return ResponseEntity.accepted().body(settings.get("embedded").equals("true")?local.start("XRAY",jwt,Json.MAPPER.valueToTree(body),key):scans.start("XRAY_SCAN",jwt,token,Json.MAPPER.valueToTree(body),key)); }
    /** Generates downloadable CycloneDX from the saved local sources without a commit. Exigences : DEVAI-172. */
    @PostMapping("/api/v1/security/sbom") public ResponseEntity<?> sbom(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@RequestBody Map<String,Object> body) { return ResponseEntity.accepted().body(local.start("SBOM",jwt,Json.MAPPER.valueToTree(body),key)); }
    /** Refuses stale remediation by exposing only the verified workspace fingerprint. Exigences : DEVAI-175. */
    @GetMapping("/api/v1/workspace/fingerprint") public Map<String,Object> fingerprint(@AuthenticationPrincipal Jwt jwt,@RequestParam long projectId) { return Json.map(local.fingerprint(jwt,projectId)); }
    /** Reads actual Sonar issues and gate without pretending the API ran a scanner. Exigences : DEVAI-085, DEVAI-088. */
    @GetMapping("/api/v1/quality/reports") public Map<String,Object> sonarReport(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestParam long projectId,@RequestParam(defaultValue="1") int page) { return scans.sonar(jwt,token,projectId,page); }
    /** Reads an existing Xray build report with explicit build identity. Exigences : DEVAI-096, DEVAI-102. */
    @GetMapping("/api/v1/security/reports") public Map<String,Object> xrayReport(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestParam long projectId,@RequestParam String buildNumber) { return scans.xray(jwt,token,projectId,buildNumber); }
    /** Reads a complete bounded GitLab merge-base comparison, pinned to both published branch heads. Exigences : DEVAI-037, DEVAI-112, DEVAI-113. */
    @GetMapping("/api/v1/git/mr-context") public Map<String,Object> mrContext(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestParam long projectId,@RequestParam String sourceBranch,@RequestParam String targetBranch) {
        gitlab.authorize(jwt,projectId,token);
        String source=branchHead(projectId,sourceBranch,token),target=branchHead(projectId,targetBranch,token);
        JsonNode comparison=gitlab.call("GET","projects/"+projectId+"/repository/compare?from="+target+"&to="+source+"&straight=false",gitlab.readToken(token),null);
        JsonNode diffs=comparison.path("diffs");
        if(comparison.path("compare_timeout").asBoolean()||!diffs.isArray()||diffs.isEmpty()||diffs.size()>30) throw new OdsException("CONTEXT_REJECTED","Diff GitLab vide, incomplet ou supérieur à 30 fichiers ; réduire la MR");
        List<Map<String,String>> files=new ArrayList<>();
        for(JsonNode diff:diffs) {
            if(diff.path("collapsed").asBoolean()||diff.path("too_large").asBoolean()) throw new OdsException("CONTEXT_REJECTED","Diff GitLab tronqué ; réduire la MR avant la pré-revue");
            String path=Json.text(diff,"new_path"),old=Json.text(diff,"old_path");
            for(String name:List.of(path,old)) if(name.contains("..")||name.startsWith("/")||name.contains("\\")||name.contains(":")||name.contains(".env")||name.contains(".git/")||name.endsWith(".key")||name.endsWith(".pem")) throw new OdsException("CONTEXT_REJECTED","Fichier sensible exclu de la pré-revue");
            files.add(Map.of("path",path,"oldPath",old,"diff",diff.path("diff").asText(),"status",diff.path("deleted_file").asBoolean()?"deleted":diff.path("renamed_file").asBoolean()?"renamed":diff.path("new_file").asBoolean()?"added":"modified"));
        }
        Map<String,Object> result=Map.of("sourceCommit",source,"targetCommit",target,"files",files);
        String serialized=Json.write(result); Secrets.check(serialized);
        if(serialized.length()>12000) throw new OdsException("CONTEXT_REJECTED","Diff supérieur au budget de pré-revue (12 000 caractères) ; découper la MR");
        return result;
    }
    /** Resolves branch endpoints rather than ambiguous tag names and rejects non-immutable responses. Exigences : DEVAI-112, DEVAI-113. */
    private String branchHead(long project,String branch,String token) {
        if(branch.isBlank()||branch.length()>255||branch.startsWith("-")||branch.contains("..")) throw new OdsException("INVALID_REQUEST","Branche invalide");
        String sha=gitlab.call("GET","projects/"+project+"/repository/branches/"+RemoteHttp.encode(branch),gitlab.readToken(token),null).path("commit").path("id").asText();
        if(!sha.matches("[a-fA-F0-9]{40,64}")) throw new OdsException("INVALID_RESPONSE","Commit GitLab invalide");
        return sha;
    }
    /** Creates a user-owned MR after checking duplicates and optional reviewed heads; service credentials are never substituted. Exigences : DEVAI-023, DEVAI-112, DEVAI-113. */
    @PostMapping("/api/v1/git/merge-requests") public Map<String,Object> mr(@AuthenticationPrincipal Jwt jwt,@RequestHeader(value="X-ODS-GitLab-Token",required=false) String token,@RequestBody Map<String,Object> input) {
        gitlab.call("GET","user",token,null);
        JsonNode body=Json.MAPPER.valueToTree(input); Json.fields(body,Set.of("projectId","sourceBranch","targetBranch","title","description","sourceCommit","targetCommit")); long project=body.path("projectId").asLong(); gitlab.authorize(jwt,project,token);
        String source=Json.text(body,"sourceBranch"),target=Json.text(body,"targetBranch"); Secrets.check(body.path("description").asText());
        if(body.has("sourceCommit")||body.has("targetCommit")) {
            if(!branchHead(project,source,token).equals(Json.text(body,"sourceCommit"))||!branchHead(project,target,token).equals(Json.text(body,"targetCommit"))) throw new OdsException("CONTEXT_REJECTED","Les branches ont changé ; régénérer la pré-revue avant publication");
        }
        JsonNode prior=gitlab.call("GET","projects/"+project+"/merge_requests?state=opened&source_branch="+RemoteHttp.encode(source)+"&target_branch="+RemoteHttp.encode(target),token,null);
        if(prior.isArray()&&!prior.isEmpty()) { Map<String,Object> existing=new LinkedHashMap<>(Json.map(prior.get(0))); existing.put("alreadyExists",true); return existing; }
        return Json.map(gitlab.call("POST","projects/"+project+"/merge_requests",token,Map.of("source_branch",source,"target_branch",target,"title",Json.text(body,"title"),"description",body.path("description").asText())));
    }
}
