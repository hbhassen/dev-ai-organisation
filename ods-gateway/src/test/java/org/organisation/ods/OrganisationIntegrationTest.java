package org.organisation.ods;

import org.organisation.ods.contracts.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.net.ssl.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real HTTP controllers, JWT validation, TLS adapters, signed policy and persistence with local provider doubles. Exigences : DEVAI-020, DEVAI-135, DEVAI-139. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=jdbc:h2:mem:organisation-integration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","server.address=127.0.0.1","logging.level.root=WARN","debug=false"})
class OrganisationIntegrationTest {
    private static HttpsServer services;
    private static SSLContext previousTls;
    private static SSLSocketFactory previousFactory;
    private static RSAKey oidcKey;
    private static String origin,publicPolicyKey,signedPolicy,policySignature;
    private static final String COMMIT="a".repeat(40);
    private static final AtomicInteger modelCalls=new AtomicInteger(),mrCreates=new AtomicInteger(),pipelines=new AtomicInteger();
    private static final AtomicReference<String> failure=new AtomicReference<>("");
    private static final List<String> contractErrors=new java.util.concurrent.CopyOnWriteArrayList<>();
    @LocalServerPort int port;

    /** Starts ephemeral HTTPS dependencies before Spring discovers the actual mocked OIDC issuer. Exigences : DEVAI-020, DEVAI-036, DEVAI-135. */
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) throws Exception {
        startServices();
        Map<String,String> properties=new LinkedHashMap<>();
        properties.put("oidc-issuer",origin+"/issuer"); properties.put("oidc-audience","ods-gateway"); properties.put("oidc-client-id","ods-test");
        properties.put("gitlab-url",origin); properties.put("governance-project-id","999"); properties.put("governance-ref","main"); properties.put("governance-token","synthetic-governance"); properties.put("policy-key",publicPolicyKey);
        properties.put("mistral-url",origin); properties.put("mistral-key","synthetic-mistral"); properties.put("mistral-model","synthetic-approved-model"); properties.put("model-alias","approved");
        properties.put("sonar-url",origin); properties.put("sonar-token","synthetic-sonar"); properties.put("sonar-project-1042","payment-service");
        properties.put("jfrog-url",origin); properties.put("jfrog-token","synthetic-xray"); properties.put("jfrog-build-1042","payment-service");
        properties.put("runner-project-id","888"); properties.put("runner-ref","main"); properties.put("runner-token","synthetic-runner");
        properties.forEach((key,value)->registry.add("ods."+key,()->value));
    }

    /** Creates temporary test trust without disabling certificate checks or changing production configuration. Exigences : DEVAI-036, DEVAI-135. */
    private static void startServices() throws Exception {
        if(services!=null) return;
        Path directory=Files.createTempDirectory("ods-https-test-"); Path store=directory.resolve("test.p12");
        String executable=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString();
        Process keytool=new ProcessBuilder(executable,"-genkeypair","-alias","localhost","-keyalg","RSA","-keysize","2048","-storetype","PKCS12","-keystore",store.toString(),"-storepass","isolated-test-only","-dname","CN=localhost","-ext","SAN=dns:localhost,ip:127.0.0.1","-validity","2","-noprompt").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if(!keytool.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)||keytool.exitValue()!=0) { keytool.destroyForcibly(); throw new IllegalStateException("Test certificate creation failed"); }
        KeyStore keys=KeyStore.getInstance("PKCS12"); try(var in=Files.newInputStream(store)) { keys.load(in,"isolated-test-only".toCharArray()); }
        KeyManagerFactory managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); managers.init(keys,"isolated-test-only".toCharArray());
        TrustManagerFactory trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trust.init(keys);
        SSLContext tls=SSLContext.getInstance("TLS"); tls.init(managers.getKeyManagers(),trust.getTrustManagers(),new SecureRandom());
        previousTls=SSLContext.getDefault(); previousFactory=HttpsURLConnection.getDefaultSSLSocketFactory(); SSLContext.setDefault(tls); HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());
        services=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0); services.setHttpsConfigurator(new HttpsConfigurator(tls));
        origin="https://localhost:"+services.getAddress().getPort();
        KeyPair rsa=KeyPairGenerator.getInstance("RSA").generateKeyPair(); oidcKey=new RSAKey.Builder((RSAPublicKey)rsa.getPublic()).privateKey((RSAPrivateKey)rsa.getPrivate()).keyID("isolated-oidc").build();
        KeyPair policy=KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); publicPolicyKey=Base64.getEncoder().encodeToString(policy.getPublic().getEncoded());
        signedPolicy=Json.write(Map.of("schemaVersion",1,"policyVersion",1,"issuedAt",Instant.now().minusSeconds(60).toString(),"expiresAt",Instant.now().plusSeconds(3600).toString(),"gitlabInstance",origin,"defaultEffect","DENY","allowedProviders",List.of("MISTRAL"),"models",List.of(Map.of("alias","approved","provider","MISTRAL","model","synthetic-approved-model","tools",true)),"projects",List.of(Map.of("projectId",1042,"aiEnabled",true,"allowedActions",List.of("CHAT","CODE_GENERATION","AGENT_RUN","MR_DESCRIPTION","SONAR_REMEDIATION","XRAY_REMEDIATION"),"modelAlias","approved","monthlyTokenQuota",1000000,"allowedAgents",List.of("implement-requirement"),"contextMode","LOCAL_GOVERNED","allowFreeText",true))));
        Signature signer=Signature.getInstance("Ed25519"); signer.initSign(policy.getPrivate()); signer.update(signedPolicy.getBytes(StandardCharsets.UTF_8)); policySignature=Base64.getEncoder().encodeToString(signer.sign());
        services.createContext("/",OrganisationIntegrationTest::route); services.start();
        Files.delete(store); Files.delete(directory);
    }

    /** Restores JVM trust after the mock service is stopped; no private test key is persisted. Exigences : DEVAI-035, DEVAI-135. */
    @AfterAll static void closeServices() throws Exception { if(services!=null) services.stop(0); if(previousTls!=null) SSLContext.setDefault(previousTls); if(previousFactory!=null) HttpsURLConnection.setDefaultSSLSocketFactory(previousFactory); }
    /** Resets only scenario state so separate integration cases cannot inherit failures. Exigences : DEVAI-135. */
    @BeforeEach void reset() { failure.set(""); modelCalls.set(0); mrCreates.set(0); pipelines.set(0); contractErrors.clear(); }
    /** Fails tests on unexpected provider requests rather than fabricating a generic success. Exigences : DEVAI-135. */
    @AfterEach void contracts() { assertEquals(List.of(),contractErrors); }

    /** Routes realistic provider contracts over TLS and records all unrecognized requests. Exigences : DEVAI-022, DEVAI-064, DEVAI-088, DEVAI-097, DEVAI-112, DEVAI-135. */
    private static void route(HttpExchange exchange) throws java.io.IOException {
        try {
            String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod(),query=Objects.toString(exchange.getRequestURI().getRawQuery(),"");
            if(path.endsWith("/.well-known/openid-configuration")||path.equals("/.well-known/openid-configuration/issuer")) { reply(exchange,200,Map.of("issuer",origin+"/issuer","jwks_uri",origin+"/jwks","authorization_endpoint",origin+"/authorize","token_endpoint",origin+"/token","response_types_supported",List.of("code"),"subject_types_supported",List.of("public"),"id_token_signing_alg_values_supported",List.of("RS256"))); return; }
            if(path.equals("/jwks")) { reply(exchange,200,new JWKSet(oidcKey.toPublicJWK()).toJSONObject()); return; }
            if(path.startsWith("/api/v4/")) {
                String auth=exchange.getRequestHeaders().getFirst("Authorization");
                if(auth==null||!auth.startsWith("Bearer synthetic-")) { reply(exchange,401,Map.of("error","missing synthetic credential")); return; }
                if(path.equals("/api/v4/user")) { reply(exchange,200,Map.of("id",7)); return; }
                if(path.equals("/api/v4/projects/1042")||path.equals("/api/v4/projects/demo/payment-service")) { reply(exchange,failure.get().equals("gitlab")?403:200,Map.of("id",1042,"path_with_namespace","demo/payment-service","default_branch","main")); return; }
                if(path.contains("/repository/commits/")) { reply(exchange,200,Map.of("id",COMMIT)); return; }
                if(path.contains("/repository/files/")) { String value=path.endsWith(".sig")?policySignature:signedPolicy; reply(exchange,200,Map.of("content",Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)))); return; }
                if(path.endsWith("/repository/tree")) { reply(exchange,200,List.of(Map.of("path","pom.xml","type","blob"))); return; }
                if(path.endsWith("/pipeline")&&method.equals("POST")) { JsonNode body=Json.parse(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)); if(!Json.write(body).contains(COMMIT)||!body.path("ref").asText().equals("main")) contractErrors.add("Runner lost immutable commit"); pipelines.incrementAndGet(); reply(exchange,201,Map.of("id",73)); return; }
                if(path.endsWith("/pipelines/73")) { reply(exchange,200,Map.of("status","success","web_url",origin+"/pipeline/73")); return; }
                if(path.endsWith("/merge_requests")) { if(method.equals("GET")) reply(exchange,200,List.of()); else { mrCreates.incrementAndGet(); JsonNode body=Json.parse(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)); reply(exchange,201,Map.of("iid",18,"title",body.path("title").asText(),"web_url",origin+"/mr/18")); } return; }
            }
            if(path.equals("/v1/chat/completions")) {
                modelCalls.incrementAndGet(); if(failure.get().equals("model")) { reply(exchange,503,Map.of("error","synthetic provider outage")); return; }
                JsonNode body=Json.parse(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                if(!body.path("model").asText().equals("synthetic-approved-model")||!body.path("messages").get(0).path("role").asText().equals("system")||!body.path("stream").asBoolean()) contractErrors.add("Mistral request contract mismatch");
                JsonNode last=body.path("messages").get(body.path("messages").size()-1);
                Map<String,Object> delta=last.path("role").asText().equals("tool")?Map.of("content","Le projet utilise Maven. Réponse simulée d’intégration."):Map.of("tool_calls",List.of(Map.of("index",0,"id","read-manifest","type","function","function",Map.of("name","files_read","arguments","{\"path\":\"pom.xml\"}"))));
                String data="data: "+Json.write(Map.of("choices",List.of(Map.of("delta",delta)),"usage",Map.of("prompt_tokens",20,"completion_tokens",10,"total_tokens",30)))+"\n\ndata: [DONE]\n\n";
                exchange.getResponseHeaders().set("Content-Type","text/event-stream"); byte[] bytes=data.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200,bytes.length); try(var out=exchange.getResponseBody()){out.write(bytes);} return;
            }
            if(path.startsWith("/api/project_analyses/")) { reply(exchange,200,Map.of("analyses",List.of(Map.of("key","analysis-1","revision",COMMIT,"date",Instant.parse("2026-01-01T00:00:00Z").toString())))); return; }
            if(path.equals("/api/issues/search")) { int page=query.contains("&p=2&")?2:1; reply(exchange,200,Map.of("issues",List.of(Map.of("key","issue-"+page,"rule","java:S106","message","Remplacer la sortie standard","severity","MAJOR","component","payment:Main.java","line",12)),"components",List.of(Map.of("key","payment:Main.java","path","src/main/java/Main.java")),"paging",Map.of("pageIndex",page,"pageSize",50,"total",51))); return; }
            if(path.equals("/api/qualitygates/project_status")) { reply(exchange,200,Map.of("projectStatus",Map.of("status","ERROR"))); return; }
            if(path.equals("/xray/api/v2/summary/build")) { if(failure.get().equals("xray")) reply(exchange,403,Map.of("error","denied")); else reply(exchange,200,Map.of("issues",List.of(Map.of("issue_id","XRAY-DEMO-1","summary","Vulnérabilité synthétique","severity","High","components",List.of(Map.of("component_id","gav://example:library:1.0")))),"licenses",List.of(Map.of("name","Apache-2.0")))); return; }
            contractErrors.add(method+" "+path); reply(exchange,404,Map.of("error","Unexpected integration mock route"));
        } catch(Exception error) { contractErrors.add(error.getClass().getSimpleName()); reply(exchange,500,Map.of("error","Mock contract failure")); }
    }

    /** Serializes bounded synthetic provider responses using the same JSON implementation as production. Exigences : DEVAI-135. */
    private static void reply(HttpExchange exchange,int code,Object value) throws java.io.IOException { byte[] bytes=Json.write(value).getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(code,bytes.length); try(var output=exchange.getResponseBody()){output.write(bytes);} }
    /** Signs a short-lived test JWT that the actual resource-server filter must validate. Exigences : DEVAI-020, DEVAI-022. */
    private String token(String audience,String gitlab) throws Exception { SignedJWT jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(oidcKey.getKeyID()).build(),new JWTClaimsSet.Builder().issuer(origin+"/issuer").subject("synthetic-user").audience(audience).issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("gitlab_user_id",gitlab).build()); jwt.sign(new RSASSASigner(oidcKey)); return jwt.serialize(); }
    /** Calls the running application over HTTP with an authenticated synthetic developer identity. Exigences : DEVAI-020, DEVAI-135. */
    private HttpResponse<String> request(String method,String route,Object body,String bearer) throws Exception { var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+route)).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+bearer).header("X-ODS-GitLab-Token","synthetic-user").header("Idempotency-Key",UUID.randomUUID().toString()).header("Content-Type","application/json"); return HttpClient.newHttpClient().send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(Json.write(body))).build(),HttpResponse.BodyHandlers.ofString()); }
    /** Asserts HTTP success before parsing the body, retaining actionable failure evidence. Exigences : DEVAI-135. */
    private JsonNode ok(String method,String route,Object body) throws Exception { var response=request(method,route,body,token("ods-gateway","7")); assertTrue(response.statusCode()>=200&&response.statusCode()<300,response.body()); return Json.parse(response.body()); }
    /** Waits for one asynchronous job transition without resubmitting the operation. Exigences : DEVAI-077, DEVAI-119. */
    private JsonNode awaitJob(String id,String state) throws Exception { JsonNode job=null; for(int attempt=0;attempt<100;attempt++){job=ok("GET","/api/v1/jobs/"+id,null); if(job.path("status").asText().equals(state)) return job; if(job.path("status").asText().equals("FAILED")) break; Thread.sleep(50);} assertEquals(state,job.path("status").asText(),job.toString()); return job; }

    /** Completes a real gateway agent tool loop with signed policy, SSE, encrypted checkpoint and measured quotas. Exigences : DEVAI-064, DEVAI-075, DEVAI-151, DEVAI-153. */
    @Test void agentTabThroughHttpsProviderAndTools() throws Exception {
        assertEquals(1042,ok("GET","/api/v1/projects/resolve?path=demo%2Fpayment-service",null).path("projectId").asInt());
        assertTrue(ok("GET","/api/v1/capabilities?projectId=1042",null).path("rule").path("aiEnabled").asBoolean());
        assertTrue(ok("GET","/api/v1/prompts",null).isArray());
        String id=ok("POST","/api/v1/ai/agents/run",Map.of("projectId",1042,"mode","AGENT","task","implement-requirement","message","Examiner le manifeste","context",List.of(),"stacks",List.of())).path("jobId").asText();
        assertEquals("files_read",awaitJob(id,"WAITING_APPROVAL").path("result").path("message").path("tool_calls").get(0).path("function").path("name").asText());
        ok("POST","/api/v1/ai/agents/"+id+"/tool-results",Map.of("toolCallId","read-manifest","result",Map.of("path","pom.xml","content","<project/>")));
        Thread.sleep(100); ok("POST","/api/v1/ai/agents/"+id+"/turns",Map.of());
        assertTrue(awaitJob(id,"SUCCEEDED").path("result").path("message").path("content").asText().contains("Maven")); assertEquals(2,modelCalls.get());
        assertTrue(ok("GET","/api/v1/ai/usage?projectId=1042",null).path("consumed").asLong()>=60);
        ok("DELETE","/api/v1/ai/sessions/"+id,null);
    }
    /** Qualifies both scan tabs, runner dispatch and Sonar pagination without calling an organisational service. Exigences : DEVAI-027, DEVAI-085, DEVAI-088, DEVAI-096, DEVAI-097. */
    @Test void qualityAndSecurityTabs() throws Exception {
        JsonNode sonar=ok("GET","/api/v1/quality/reports?projectId=1042&page=2",null); assertEquals(2,sonar.path("paging").path("pageIndex").asInt()); assertEquals(COMMIT,sonar.path("commit").asText()); assertEquals("src/main/java/Main.java",sonar.path("issues").get(0).path("path").asText());
        JsonNode xray=ok("GET","/api/v1/security/reports?projectId=1042&buildNumber=42",null); assertEquals("XRAY-DEMO-1",xray.path("issues").get(0).path("key").asText()); assertFalse(xray.path("commitVerified").asBoolean());
        for(String type:List.of("quality","security")) { String id=ok("POST","/api/v1/"+type+"/scans",Map.of("projectId",1042,"commit",COMMIT,"profile","JAVA")).path("jobId").asText(); assertEquals(COMMIT,awaitJob(id,"SUCCEEDED").path("result").path("commit").asText()); }
        assertEquals(2,pipelines.get()); assertEquals(0,modelCalls.get());
    }
    /** Publishes a reviewed MR through the delegated GitLab identity and verifies its provider result. Exigences : DEVAI-023, DEVAI-112. */
    @Test void gitflowTab() throws Exception { JsonNode mr=ok("POST","/api/v1/git/merge-requests",Map.of("projectId",1042,"sourceBranch","feat/test","targetBranch","develop","title","Synthetic MR","description","Integration test only")); assertEquals(18,mr.path("iid").asInt()); assertEquals(1,mrCreates.get()); }
    /** Checks real JWT audience, identity mismatch and forbidden providers without trusting mock success defaults. Exigences : DEVAI-020, DEVAI-022, DEVAI-026, DEVAI-125. */
    @Test void authenticationAndProviderFailures() throws Exception {
        assertEquals(401,request("GET","/api/v1/capabilities?projectId=1042",null,token("wrong-audience","7")).statusCode());
        assertEquals(403,request("GET","/api/v1/capabilities?projectId=1042",null,token("ods-gateway","9")).statusCode());
        failure.set("gitlab"); assertEquals(403,request("GET","/api/v1/quality/reports?projectId=1042",null,token("ods-gateway","7")).statusCode());
        failure.set("xray"); assertEquals(403,request("GET","/api/v1/security/reports?projectId=1042&buildNumber=42",null,token("ods-gateway","7")).statusCode());
        failure.set("model"); String id=ok("POST","/api/v1/ai/chat",Map.of("projectId",1042,"mode","QUESTION","task","code-review","message","Inspecter","context",List.of())).path("jobId").asText(); assertEquals("DEPENDENCY_UNAVAILABLE",awaitJob(id,"FAILED").path("result").path("code").asText());
    }
}
