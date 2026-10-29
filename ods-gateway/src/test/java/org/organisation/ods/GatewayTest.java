package org.organisation.ods;
import org.organisation.ods.contracts.*;
import org.organisation.ods.data.Store;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs real Flyway migrations and HTTP security against an isolated H2 database. Exigences : DEVAI-003, DEVAI-076, DEVAI-121. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"spring.datasource.url=${ODS_TEST_DATABASE_URL:jdbc:h2:mem:gateway-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}","spring.datasource.username=${ODS_TEST_DATABASE_USER:sa}","spring.datasource.password=${ODS_TEST_DATABASE_PASSWORD:}","ods.oidc-issuer=","server.address=127.0.0.1","logging.level.root=WARN","logging.level.org.springframework=WARN","debug=false"})
class GatewayTest {
    @Autowired Store store;
    @Autowired org.springframework.jdbc.core.JdbcTemplate database;
    @Autowired org.organisation.ods.data.ContentCipher cipher;
    @LocalServerPort int port;
    /** Confirms health is public while business endpoints fail closed without SSO. Exigences : DEVAI-003, DEVAI-129. */
    @Test void anonymousBoundary() throws Exception {
        HttpClient client=HttpClient.newHttpClient();
        assertEquals(200,client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/health/live")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        int denied=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/capabilities?projectId=1")).build(),HttpResponse.BodyHandlers.ofString()).statusCode();
        assertTrue(denied==401||denied==403); assertEquals(503,client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/health/ready")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    /** Rejects policy replay and same-version content substitution after persistence. Exigences : DEVAI-031. */
    @Test void watermark() { String scope=UUID.randomUUID().toString(); store.watermark(scope,3,"abc"); store.watermark(scope,3,"abc"); assertThrows(OdsException.class,()->store.watermark(scope,2,"old")); assertThrows(OdsException.class,()->store.watermark(scope,3,"changed")); store.watermark(scope,4,"new"); }
    /** Ensures duplicate submissions cannot claim twice or read another subject's job. Exigences : DEVAI-077, DEVAI-117, DEVAI-120. */
    @Test void idempotenceAndOwnership() {
        String key=UUID.randomUUID().toString(); var first=store.createJob("alice",1,"CHAT",key,"payload");
        assertEquals(first.get("jobId"),store.createJob("alice",1,"CHAT",key,"payload").get("jobId"));
        assertThrows(OdsException.class,()->store.createJob("alice",1,"CHAT",key,"other"));
        String id=first.get("jobId").toString(); assertTrue(store.claim(id)); assertFalse(store.claim(id)); assertThrows(OdsException.class,()->store.job(id,"bob"));
    }
    /** Reconciles measured tokens once and retains unmeasured reservations. Exigences : DEVAI-075, DEVAI-076. */
    @Test void quotaAccounting() {
        long project=20001; String a=UUID.randomUUID().toString(), b=UUID.randomUUID().toString();
        store.reserve(a,project,70,100); assertThrows(OdsException.class,()->store.reserve(b,project,40,100));
        store.reconcile(a,20,10); store.reconcile(a,20,10); store.reserve(b,project,40,100);
        assertEquals(30,((Number)store.usage(project).get("consumed")).longValue()); assertEquals(40,((Number)store.usage(project).get("reserved")).longValue());
    }
    /** Prevents concurrent reservations from collectively exceeding a shared project budget. Exigences : DEVAI-076, DEVAI-121. */
    @Test void concurrentQuota() throws Exception {
        long project=20002; store.reserve(UUID.randomUUID().toString(),project,1,101);
        try(var pool=Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> calls=new ArrayList<>(); for(int i=0;i<20;i++) calls.add(()->{ try {store.reserve(UUID.randomUUID().toString(),project,10,101);return true;}catch(OdsException e){assertEquals("QUOTA_EXCEEDED",e.code());return false;} });
            int accepted=0; for(Future<Boolean> future:pool.invokeAll(calls)) if(future.get()) accepted++;
            assertEquals(10,accepted); assertEquals(101,((Number)store.usage(project).get("reserved")).longValue());
        }
    }
    /** Keeps subject budgets independent while preserving the shared project cap and once-only reconciliation. Exigences : DEVAI-075, DEVAI-076. */
    @Test void personalQuotaAccounting() {
        long project=20003; String id=UUID.randomUUID().toString();
        store.reserve(id,project,"alice",60,1000,100);
        assertThrows(OdsException.class,()->store.reserve(UUID.randomUUID().toString(),project,"alice",50,1000,100));
        store.reserve(UUID.randomUUID().toString(),project,"bob",80,1000,100);
        store.reconcile(id,10,20); store.reconcile(id,10,20);
        assertEquals(30,((Number)store.usage(project,"alice").get("subjectConsumed")).longValue());
        assertEquals(0,((Number)store.usage(project,"alice").get("subjectReserved")).longValue());
        assertEquals(80,((Number)store.usage(project,"bob").get("subjectReserved")).longValue());
    }
    /** Verifies encrypted storage, optimistic session revisions and identity binding of persisted checkpoints. Exigences : DEVAI-067, DEVAI-157. */
    @Test void encryptedCheckpoints() {
        String id=store.createJob("checkpoint-owner",30001,"CHAT",UUID.randomUUID().toString(),"payload").get("jobId").toString();
        long revision=store.saveSession(id,"checkpoint-owner",Map.of("content","private source example"),0,System.currentTimeMillis()+60000);
        assertEquals(1,revision); assertFalse(database.queryForObject("SELECT payload FROM conversation WHERE id=?",String.class,id).contains("private source example"));
        assertEquals("private source example",((com.fasterxml.jackson.databind.JsonNode)store.loadSession(id,"checkpoint-owner").get("content")).path("content").asText());
        assertThrows(OdsException.class,()->store.loadSession(id,"another-owner"));
        store.saveSession(id,"checkpoint-owner",Map.of("content","updated"),revision,System.currentTimeMillis()+60000);
        assertThrows(OdsException.class,()->store.saveSession(id,"checkpoint-owner",Map.of("content","stale"),revision,System.currentTimeMillis()+60000));
        String encrypted=cipher.seal("private","scope-a"); assertThrows(OdsException.class,()->cipher.open(encrypted,"scope-b"));
        store.deleteSession(id,"checkpoint-owner"); assertThrows(OdsException.class,()->store.loadSession(id,"checkpoint-owner"));
        store.finish(id,"SUCCEEDED",Map.of("content","late worker result")); assertEquals("DELETED",store.job(id,"checkpoint-owner").get("status"));
    }
    /** Recovers expired leases by suspension without claiming or replaying their external effect. Exigences : DEVAI-120. */
    @Test void workerLeaseRecovery() {
        String id=store.createJob("lease-owner",30002,"CHAT",UUID.randomUUID().toString(),"payload").get("jobId").toString();
        assertTrue(store.claim(id)); database.update("UPDATE job SET lease_until=0 WHERE id=?",id); store.maintenance();
        assertEquals("SUSPENDED",store.job(id,"lease-owner").get("status")); assertFalse(store.claim(id)); assertTrue(store.claimTurn(id));
    }
    /** Enforces a shared two-task concurrency cap before a third provider operation is claimed. Exigences : DEVAI-079, DEVAI-120. */
    @Test void activeJobLimit() {
        List<String> jobs=new ArrayList<>(); for(int i=0;i<3;i++) jobs.add(store.createJob("bounded-owner",30003,"CHAT",UUID.randomUUID().toString(),"payload").get("jobId").toString());
        assertTrue(store.claim(jobs.get(0))); assertTrue(store.claim(jobs.get(1)));
        assertEquals("RATE_LIMITED",assertThrows(OdsException.class,()->store.claim(jobs.get(2))).code());
        store.finish(jobs.get(0),"SUCCEEDED",Map.of()); assertTrue(store.claim(jobs.get(2)));
    }
}
