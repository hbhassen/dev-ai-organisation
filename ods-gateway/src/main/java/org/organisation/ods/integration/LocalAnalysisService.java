package org.organisation.ods.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.organisation.ods.Settings;
import org.organisation.ods.contracts.*;
import org.organisation.ods.data.Store;
import org.organisation.ods.policy.PolicyService;
import org.organisation.ods.security.SignedDocuments;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Orchestrates saved-workspace scans and ties provider evidence to the exact local inputs, independently of AI entitlement. Exigences : DEVAI-172, DEVAI-173, DEVAI-174, DEVAI-175. */
@Service
public class LocalAnalysisService {
    private final Settings settings; private final RemoteHttp http; private final LocalScanner scanner; private final GitLabClient gitlab; private final Store store;
    /** Injects substitutable process and HTTP boundaries so every outgoing integration can be mocked. Exigences : DEVAI-177. */
    public LocalAnalysisService(Settings settings,RemoteHttp http,LocalScanner scanner,GitLabClient gitlab,Store store) { this.settings=settings; this.http=http; this.scanner=scanner; this.gitlab=gitlab; this.store=store; }
    /** Computes source provenance only for the launch-bound project after Git access was checked natively. Exigences : DEVAI-169, DEVAI-175. */
    public JsonNode fingerprint(Jwt jwt,long project) { gitlab.authorize(jwt,project,null); return scanner.run("fingerprint","",Map.of(),()->false,()->{}); }
    /** Creates an idempotent owned operation without requiring a commit or a GitLab pipeline. Exigences : DEVAI-172, DEVAI-174. */
    public Map<String,Object> start(String kind,Jwt jwt,JsonNode body,String key) {
        Json.fields(body,Set.of("projectId","profile","sbomJobId")); long project=body.path("projectId").asLong(); gitlab.authorize(jwt,project,null);
        String profile=Json.text(body,"profile"); if(!Set.of("JAVA","ANGULAR","PYTHON","DOTNET").contains(profile)) throw new OdsException("INVALID_REQUEST","Stack inconnue");
        String subject=PolicyService.subject(jwt); Map<String,Object> job=store.createJob(subject,project,"LOCAL_"+kind,key,SignedDocuments.sha256(Json.write(body).getBytes(StandardCharsets.UTF_8))); String id=job.get("jobId").toString();
        if(store.claim(id)) Thread.ofVirtual().start(()->{
            try {
                Map<String,Object> result;
                if(kind.equals("SBOM")) result=Json.map(scanner.run("sbom",profile,Map.of(),()->store.cancelled(id),()->store.heartbeat(id)));
                else if(kind.equals("SONAR")) result=sonar(id,project,profile);
                else if(kind.equals("XRAY")) {
                    JsonNode previous=Json.MAPPER.valueToTree(store.job(Json.text(body,"sbomJobId"),subject));
                    if(!previous.path("status").asText().equals("SUCCEEDED")||!previous.path("kind").asText().equals("LOCAL_SBOM")||previous.path("projectId").asLong()!=project||!previous.path("result").path("profile").asText().equals(profile)) throw new OdsException("INVALID_SBOM","Générer un SBOM pour ce projet et cette stack");
                    JsonNode saved=previous.path("result"),current=scanner.run("fingerprint",profile,Map.of(),()->store.cancelled(id),()->store.heartbeat(id));
                    if(!saved.path("fingerprint").equals(current.path("fingerprint"))) throw new OdsException("STALE_REPORT","Les sources ont changé ; régénérer le SBOM");
                    result=xray(id,saved.path("sbom"),profile); result.put("fingerprint",saved.path("fingerprint").asText());
                } else throw new OdsException("INVALID_REQUEST","Analyse inconnue");
                check(id); result.put("freshness","LOCAL_SAVED_SOURCES"); result.put("profile",profile); store.finish(id,"SUCCEEDED",result);
            } catch(Exception error) { if(error instanceof InterruptedException) Thread.currentThread().interrupt(); String code=error instanceof OdsException ods?ods.code():"SCAN_FAILED"; store.finish(id,code.equals("CANCELLED")?"CANCELLED":"FAILED",Map.of("code",code,"message",error instanceof OdsException?error.getMessage():"Analyse non terminée")); }
        });
        return store.job(id,subject);
    }
    /** Uses Basic service password or Bearer service token exactly as selected by the organisation. Exigences : DEVAI-173, DEVAI-176. */
    private Map<String,String> xrayHeaders() {
        settings.require("jfrog-username");
        return switch(settings.require("jfrog-auth")) { case "BASIC"->Map.of("Authorization","Basic "+Base64.getEncoder().encodeToString((settings.require("jfrog-username")+":"+settings.require("jfrog-password")).getBytes(StandardCharsets.UTF_8))); case "TOKEN"->Map.of("Authorization","Bearer "+settings.require("jfrog-token")); default->throw new OdsException("CONFIGURATION_INVALID","Mode JFrog invalide"); };
    }
    /** Submits the same Graph Scan API as scan-sbom-xray and polls 202 until a genuine completed report. Exigences : DEVAI-173. */
    public Map<String,Object> xray(String job,JsonNode bom,String profile) throws InterruptedException {
        String base=settings.origin("jfrog-url").toString().replaceAll("/$","");
        String endpoint=base+(base.endsWith("/xray/api")?"/v1/scan/graph":base.endsWith("/xray")?"/api/v1/scan/graph":"/xray/api/v1/scan/graph");
        String project=settings.get("jfrog-project-key").isBlank()?"":"&projectKey="+RemoteHttp.encode(settings.get("jfrog-project-key")); Map<String,String> headers=xrayHeaders();
        check(job); JsonNode submitted=http.json("POST",URI.create(endpoint+"?watch="+RemoteHttp.encode(settings.require("jfrog-watch"))+"&scan_type=dependency"+project),headers,XrayGraph.convert(bom,profile));
        String scan=Json.text(submitted,"scan_id"); if(!scan.matches("[A-Za-z0-9_-]{1,200}")) throw new OdsException("INVALID_RESPONSE","Identifiant Xray invalide");
        for(int attempt=0;attempt<180;attempt++) {
            check(job); RemoteHttp.Response response=http.exchange("GET",URI.create(endpoint+"/"+RemoteHttp.encode(scan)+"?include_vulnerabilities=true&include_licenses=true"+project),headers,null);
            if(response.status()==202) { Thread.sleep(1000); continue; }
            JsonNode report=response.body(); if(response.status()!=200||(!report.has("vulnerabilities")&&!report.has("violations"))) throw new OdsException("INVALID_RESPONSE","Rapport Xray incomplet");
            List<Map<String,Object>> issues=new ArrayList<>();
            for(String section:List.of("vulnerabilities","violations")) for(JsonNode issue:report.path(section)) { Map<String,Object> normalized=new LinkedHashMap<>(); normalized.put("key",issue.path("issue_id").asText()); normalized.put("message",issue.path("summary").asText()); normalized.put("severity",issue.path("severity").asText("UNKNOWN")); normalized.put("category",section); for(String field:List.of("cves","components","fixed_versions","impact_paths","watch_name","fail_build")) normalized.put(field,issue.path(field)); issues.add(normalized); }
            Map<String,Object> result=new LinkedHashMap<>(); result.put("source","XRAY"); result.put("scanId",scan); result.put("watch",settings.get("jfrog-watch")); result.put("issues",issues); result.put("report",report); return result;
        }
        throw new OdsException("SCAN_TIMEOUT","Xray n’a pas terminé le scan dans le délai autorisé");
    }
    /** Runs local SonarScanner then verifies Compute Engine identity before reading all corresponding issues. Exigences : DEVAI-174, DEVAI-175. */
    private Map<String,Object> sonar(String job,long project,String profile) throws InterruptedException {
        String projectKey=settings.require("sonar-project-"+project); String base=settings.origin("sonar-url").toString().replaceAll("/$","");
        JsonNode local=scanner.run("sonar",profile,Map.of("url",base,"token",settings.require("sonar-token"),"projectKey",projectKey),()->store.cancelled(job),()->store.heartbeat(job));
        Map<String,String> headers=Map.of("Authorization","Bearer "+settings.require("sonar-token")); String analysis="";
        for(int attempt=0;attempt<180;attempt++) { check(job); JsonNode task=http.json("GET",URI.create(base+"/api/ce/task?id="+RemoteHttp.encode(Json.text(local,"ceTaskId"))),headers,null).path("task");
            if(!task.path("componentKey").asText().equals(projectKey)) throw new OdsException("INVALID_RESPONSE","Projet Sonar incohérent");
            String status=task.path("status").asText(); if(status.equals("SUCCESS")) { analysis=Json.text(task,"analysisId"); break; } if(!Set.of("PENDING","IN_PROGRESS").contains(status)) throw new OdsException("SCAN_FAILED","Analyse Sonar refusée ou échouée"); Thread.sleep(1000); }
        if(analysis.isBlank()) throw new OdsException("SCAN_TIMEOUT","Sonar n’a pas terminé l’analyse");
        URI latest=URI.create(base+"/api/project_analyses/search?project="+RemoteHttp.encode(projectKey)+"&ps=1");
        if(!http.json("GET",latest,headers,null).path("analyses").path(0).path("key").asText().equals(analysis)) throw new OdsException("STALE_REPORT","Une autre analyse Sonar a remplacé ce résultat");
        List<Map<String,Object>> findings=new ArrayList<>();
        for(int page=1;page<=200;page++) {
            check(job); JsonNode data=http.json("GET",URI.create(base+"/api/issues/search?componentKeys="+RemoteHttp.encode(projectKey)+"&resolved=false&ps=100&p="+page),headers,null);
            Map<String,String> paths=new HashMap<>(); for(JsonNode component:data.path("components")) paths.put(component.path("key").asText(),component.path("path").asText());
            for(JsonNode issue:data.path("issues")) findings.add(Map.of("key",issue.path("key").asText(),"message",issue.path("message").asText(),"rule",issue.path("rule").asText(),"severity",issue.path("severity").asText("UNKNOWN"),"path",paths.getOrDefault(issue.path("component").asText(),""),"line",issue.path("line").asInt()));
            int total=data.path("paging").path("total").asInt(-1); if(total<0||total>20000) throw new OdsException("INVALID_RESPONSE","Pagination Sonar invalide ou trop volumineuse"); if(page*100>=total) break;
        }
        JsonNode gate=http.json("GET",URI.create(base+"/api/qualitygates/project_status?analysisId="+RemoteHttp.encode(analysis)),headers,null);
        if(!http.json("GET",latest,headers,null).path("analyses").path(0).path("key").asText().equals(analysis)) throw new OdsException("STALE_REPORT","Analyse Sonar remplacée pendant la lecture");
        Map<String,Object> result=new LinkedHashMap<>(Json.map(local)); result.put("source","SONAR"); result.put("analysisId",analysis); result.put("issues",findings); result.put("gate",gate); return result;
    }
    /** Renews job ownership and stops provider polling when the user requests cancellation. Exigences : DEVAI-024, DEVAI-173, DEVAI-174. */
    private void check(String id) { if(id==null) return; store.heartbeat(id); if(store.cancelled(id)) throw new OdsException("CANCELLED","Analyse annulée"); }
}
