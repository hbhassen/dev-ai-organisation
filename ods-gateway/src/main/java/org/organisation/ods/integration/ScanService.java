package org.organisation.ods.integration;
import org.organisation.ods.*;
import org.organisation.ods.contracts.*;
import org.organisation.ods.data.Store;
import org.organisation.ods.policy.PolicyService;
import org.organisation.ods.security.SignedDocuments;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Dispatches real scans to an isolated runner and reads genuine Sonar/Xray reports. Exigences : DEVAI-085, DEVAI-096, DEVAI-104. */
@Service
public final class ScanService {
    private final Settings settings; private final RemoteHttp http; private final GitLabClient gitlab; private final Store store;
    /** Connects scans to project authorization and durable job tracking. Exigences : DEVAI-027, DEVAI-119. */
    public ScanService(Settings settings,RemoteHttp http,GitLabClient gitlab,Store store) { this.settings=settings; this.http=http; this.gitlab=gitlab; this.store=store; }
    /** Starts an authenticated remote runner operation; absent runners remain explicitly unavailable. Exigences : DEVAI-085, DEVAI-096. */
    public Map<String,Object> start(String kind,Jwt jwt,String token,JsonNode body,String key) {
        Json.fields(body,Set.of("projectId","commit","profile")); long project=body.path("projectId").asLong(); JsonNode targetProject=gitlab.authorize(jwt,project,token);
        String commit=Json.text(body,"commit"); if(!commit.matches("[a-f0-9]{40,64}")) throw new OdsException("INVALID_REQUEST","Commit immuable requis");
        String verifiedCommit=gitlab.commit(project,commit,token); if(!verifiedCommit.equals(commit)) throw new OdsException("INVALID_REQUEST","Révision non vérifiée");
        String profile=Json.text(body,"profile"); if(!Set.of("JAVA","ANGULAR","PYTHON","DOTNET").contains(profile)) throw new OdsException("INVALID_REQUEST","Profil inconnu");
        String runnerProject=settings.require("runner-project-id"), runnerToken=settings.require("runner-token"), runnerRef=settings.require("runner-ref");
        if(!runnerProject.matches("[1-9][0-9]*")) throw new OdsException("CONFIGURATION_INVALID","Projet runner invalide");
        Map<String,Object> job=store.createJob(PolicyService.subject(jwt),project,kind,key,SignedDocuments.sha256(Json.write(body).getBytes(StandardCharsets.UTF_8))); String id=job.get("jobId").toString();
        if(store.claim(id)) Thread.ofVirtual().start(()->{
            try {
                List<Map<String,String>> variables=new ArrayList<>();
                for(var entry:Map.of("ODS_JOB_ID",id,"ODS_TARGET_PROJECT",Long.toString(project),"ODS_TARGET_PATH",Json.text(targetProject,"path_with_namespace"),"ODS_TARGET_COMMIT",commit,"ODS_SCAN_KIND",kind,"ODS_PROFILE",profile).entrySet()) variables.add(Map.of("key",entry.getKey(),"value",entry.getValue()));
                JsonNode response=gitlab.call("POST","projects/"+runnerProject+"/pipeline",runnerToken,Map.of("ref",runnerRef,"variables",variables));
                long remoteId=response.path("id").asLong(); if(remoteId<1) throw new OdsException("INVALID_RESPONSE","Identifiant pipeline invalide");
                boolean cancelSent=false;
                for(int poll=0;poll<180;poll++) {
                    store.heartbeat(id);
                    if(store.cancelled(id)&&!cancelSent) { gitlab.call("POST","projects/"+runnerProject+"/pipelines/"+remoteId+"/cancel",runnerToken,Map.of()); cancelSent=true; }
                    JsonNode state=gitlab.call("GET","projects/"+runnerProject+"/pipelines/"+remoteId,runnerToken,null);
                    String status=state.path("status").asText();
                    if(Set.of("success","failed","canceled","skipped").contains(status)) { store.finish(id,status.equals("success")?"SUCCEEDED":status.equals("canceled")?"CANCELLED":"FAILED",Map.of("source","GITLAB_RUNNER","pipelineId",remoteId,"pipelineUrl",state.path("web_url").asText(),"commit",commit,"profile",profile,"qualityGate","CONSULT_PROVIDER_REPORT")); return; }
                    Thread.sleep(5000);
                }
                store.finish(id,"TIMED_OUT",Map.of("message","Délai du scan dépassé"));
            } catch(Exception e) { if(e instanceof InterruptedException) Thread.currentThread().interrupt(); store.finish(id,"FAILED",Map.of("code",e instanceof OdsException o?o.code():"SCAN_FAILED","message","Analyse distante non terminée")); }
        });
        return store.job(id,PolicyService.subject(jwt));
    }
    /** Retrieves actual Sonar issues and gate using an administratively configured project key. Exigences : DEVAI-088, DEVAI-089. */
    public Map<String,Object> sonar(Jwt jwt,String token,long project,int page) {
        if(page<1||page>10000) throw new OdsException("INVALID_REQUEST","Page invalide");
        gitlab.authorize(jwt,project,token); String projectKey=settings.require("sonar-project-"+project); URI base=settings.origin("sonar-url");
        String auth="Basic "+Base64.getEncoder().encodeToString((settings.require("sonar-token")+":").getBytes(StandardCharsets.UTF_8)); Map<String,String> headers=Map.of("Authorization",auth);
        URI analysesUri=URI.create(base+"/api/project_analyses/search?project="+RemoteHttp.encode(projectKey)+"&ps=1");
        JsonNode before=http.json("GET",analysesUri,headers,null).path("analyses").path(0);
        JsonNode issues=http.json("GET",URI.create(base+"/api/issues/search?componentKeys="+RemoteHttp.encode(projectKey)+"&p="+Math.max(1,page)+"&ps=50"),headers,null);
        JsonNode gate=http.json("GET",URI.create(base+"/api/qualitygates/project_status?"+(before.path("key").asText().isBlank()?"projectKey="+RemoteHttp.encode(projectKey):"analysisId="+RemoteHttp.encode(before.path("key").asText()))),headers,null);
        JsonNode after=http.json("GET",analysesUri,headers,null).path("analyses").path(0);
        if(!before.equals(after)) throw new OdsException("STALE_REPORT","Une analyse a changé pendant la lecture ; actualiser le rapport");
        Map<String,String> paths=new HashMap<>(); for(JsonNode component:issues.path("components")) paths.put(component.path("key").asText(),component.path("path").asText());
        List<Map<String,Object>> normalized=new ArrayList<>();
        for(JsonNode issue:issues.path("issues")) {
            Map<String,Object> finding=new LinkedHashMap<>(); finding.put("key",issue.path("key").asText()); finding.put("rule",issue.path("rule").asText()); finding.put("message",issue.path("message").asText()); finding.put("path",paths.getOrDefault(issue.path("component").asText(),"")); finding.put("line",issue.path("line").asInt(0)); finding.put("severity",issue.path("severity").asText(issue.path("impacts").path(0).path("severity").asText("UNKNOWN"))); finding.put("status",issue.path("issueStatus").asText(issue.path("status").asText("UNKNOWN"))); normalized.add(finding);
        }
        return Map.of("source","SONAR","issues",normalized,"paging",Json.map(issues.path("paging")),"gate",Json.map(gate),"commit",before.path("revision").asText(),"analysisId",before.path("key").asText(),"analyzedAt",before.path("date").asText(),"freshness",before.path("revision").asText().isBlank()?"UNKNOWN_REVISION":"PROVIDER_REVISION");
    }
    /** Retrieves a real Xray build summary for a deployment-owned project/build mapping. Exigences : DEVAI-096, DEVAI-097, DEVAI-102. */
    public Map<String,Object> xray(Jwt jwt,String token,long project,String buildNumber) {
        gitlab.authorize(jwt,project,token); if(!buildNumber.matches("[A-Za-z0-9._-]{1,100}")) throw new OdsException("INVALID_REQUEST","Numéro de build invalide");
        String name=settings.require("jfrog-build-"+project);
        JsonNode response=http.json("GET",URI.create(settings.origin("jfrog-url")+"/xray/api/v2/summary/build?build_name="+RemoteHttp.encode(name)+"&build_number="+RemoteHttp.encode(buildNumber)),Map.of("Authorization","Bearer "+settings.require("jfrog-token")),null);
        List<Map<String,Object>> findings=new ArrayList<>();
        if(response.path("issues").isArray()) for(JsonNode issue:response.path("issues")) {
            Map<String,Object> finding=new LinkedHashMap<>();
            finding.put("key",issue.path("issue_id").asText()); finding.put("message",issue.path("summary").asText()); finding.put("severity",issue.path("severity").asText("UNKNOWN"));
            finding.put("components",issue.path("components")); finding.put("cves",issue.path("cves")); finding.put("providerEvidence",Json.map(issue)); findings.add(finding);
        }
        return Map.of("source","XRAY","buildName",name,"buildNumber",buildNumber,"issues",findings,"report",Json.map(response),"freshness","BUILD_REPORT","commitVerified",false);
    }
}
