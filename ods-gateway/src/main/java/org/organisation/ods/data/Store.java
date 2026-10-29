package org.organisation.ods.data;
import org.organisation.ods.contracts.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import java.time.*;

/** Persists audit, idempotent jobs and quota reservations using database transactions. Exigences : DEVAI-043, DEVAI-076, DEVAI-077, DEVAI-119, DEVAI-121. */
@Service
public class Store {
    private final JdbcTemplate db; private final TransactionTemplate tx; private final ContentCipher cipher; private final String instance=UUID.randomUUID().toString();
    /** Binds the same transactional database to jobs, quotas and anti-replay state. Exigences : DEVAI-121. */
    public Store(JdbcTemplate db,PlatformTransactionManager manager,ContentCipher cipher) { this.db=db; this.cipher=cipher; tx=new TransactionTemplate(manager); }
    /** Applies a monotonic version watermark with a locked row, rejecting same-version content substitution. Exigences : DEVAI-031. */
    public void watermark(String scope,long version,String hash) {
        try { db.update("INSERT INTO watermark(scope,version,hash) VALUES(?,0,'')",scope); }
        catch(org.springframework.dao.DuplicateKeyException expected) { /* Another instance initialized this durable scope. */ }
        tx.executeWithoutResult(s->{
            Map<String,Object> row=db.queryForMap("SELECT version,hash FROM watermark WHERE scope=? FOR UPDATE",scope);
            long prior=((Number)row.get("version")).longValue();
            if(version<prior||(version==prior&&!hash.equals(row.get("hash")))) throw new OdsException("POLICY_REPLAYED","Ancienne politique ou collision de version");
            db.update("UPDATE watermark SET version=?,hash=? WHERE scope=?",version,hash,scope);
        });
    }
    /** Appends a metadata-only audit event; no source or credential is accepted. Exigences : DEVAI-043. */
    public void audit(String subject,long project,String action,String decision,String correlation,String version) {
        db.update("INSERT INTO audit(id,created_at,subject,project_id,action,decision,correlation,policy_version) VALUES(?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),Instant.now().toString(),subject,project,action,decision,correlation,version);
    }
    /** Creates or retrieves an idempotent operation, refusing a key reused with another payload. Exigences : DEVAI-077, DEVAI-119. */
    public synchronized Map<String,Object> createJob(String subject,long project,String kind,String key,String hash) {
        if(key==null||!key.matches("[A-Za-z0-9_-]{8,128}")) throw new OdsException("INVALID_REQUEST","Clé d'idempotence requise");
        List<Map<String,Object>> existing=db.queryForList("SELECT * FROM job WHERE subject=? AND project_id=? AND kind=? AND request_key=?",subject,project,kind,key);
        if(!existing.isEmpty()) { if(!hash.equals(existing.getFirst().get("payload_hash"))) throw new OdsException("IDEMPOTENCY_CONFLICT","Clé déjà utilisée pour un autre contenu"); return job(existing.getFirst().get("id").toString(),subject); }
        String id=UUID.randomUUID().toString();
        try { db.update("INSERT INTO job(id,subject,project_id,kind,request_key,payload_hash,status,created_at,result) VALUES(?,?,?,?,?,?,?,?,?)",id,subject,project,kind,key,hash,"QUEUED",Instant.now().toString(),cipher.seal("{}",id)); }
        catch(org.springframework.dao.DuplicateKeyException expected) {
            var concurrent=db.queryForMap("SELECT id,payload_hash FROM job WHERE subject=? AND project_id=? AND kind=? AND request_key=?",subject,project,kind,key);
            if(!hash.equals(concurrent.get("payload_hash"))) throw new OdsException("IDEMPOTENCY_CONFLICT","Clé déjà utilisée pour un autre contenu");
            return job(concurrent.get("id").toString(),subject);
        }
        return job(id,subject);
    }
    /** Atomically claims a job, preventing a duplicate request from re-executing an external effect. Exigences : DEVAI-120. */
    public boolean claim(String id) { return guardedClaim(id,List.of("QUEUED")); }
    /** Claims a resumed turn through the database so two gateway instances cannot run it concurrently. Exigences : DEVAI-077, DEVAI-120. */
    public boolean claimTurn(String id) { return guardedClaim(id,List.of("WAITING_APPROVAL","SUCCEEDED","CANCELLED","SUSPENDED")); }
    /** Locks a shared subject guard before claiming work, enforcing rate and concurrency across JVMs. Exigences : DEVAI-079, DEVAI-120. */
    private boolean guardedClaim(String id,List<String> states) {
        String subject=db.queryForObject("SELECT subject FROM job WHERE id=?",String.class,id);
        try { db.update("INSERT INTO execution_guard(subject,period,calls) VALUES(?,0,0)",subject); } catch(org.springframework.dao.DuplicateKeyException expected) { /* Existing subject guard is locked below. */ }
        return Boolean.TRUE.equals(tx.execute(status->{
            var guard=db.queryForMap("SELECT period,calls FROM execution_guard WHERE subject=? FOR UPDATE",subject);
            String state=db.queryForObject("SELECT status FROM job WHERE id=?",String.class,id); if(!states.contains(state)) return false;
            long minute=System.currentTimeMillis()/60000; int calls=((Number)guard.get("period")).longValue()==minute?((Number)guard.get("calls")).intValue():0;
            Integer active=db.queryForObject("SELECT COUNT(*) FROM job WHERE subject=? AND status IN ('RUNNING','CANCEL_REQUESTED')",Integer.class,subject);
            if(calls>=60||active!=null&&active>=2) throw new OdsException("RATE_LIMITED","Limite de débit ou de tâches simultanées atteinte");
            db.update("UPDATE execution_guard SET period=?,calls=? WHERE subject=?",minute,calls+1,subject);
            return db.update("UPDATE job SET status='RUNNING',owner_instance=?,lease_until=? WHERE id=? AND status=?",instance,System.currentTimeMillis()+300000,id,state)==1;
        }));
    }
    /** Removes an owner's encrypted transcript and prevents later workers from republishing its content. Exigences : DEVAI-067, DEVAI-123. */
    public void deleteSession(String id,String subject) { job(id,subject); db.update("DELETE FROM conversation WHERE id=? AND subject=?",id,subject); db.update("UPDATE job SET status='DELETED',result=? WHERE id=? AND subject=?",cipher.seal("{}",id),id,subject); }
    /** Renews a bounded worker lease without changing cancellation intent. Exigences : DEVAI-120. */
    public void heartbeat(String id) { db.update("UPDATE job SET lease_until=? WHERE id=? AND owner_instance=? AND status IN ('RUNNING','CANCEL_REQUESTED')",System.currentTimeMillis()+300000,id,instance); }
    /** Reads a job only for its authenticated owner. Exigences : DEVAI-117. */
    public Map<String,Object> job(String id,String subject) {
        List<Map<String,Object>> rows=db.queryForList("SELECT * FROM job WHERE id=? AND subject=?",id,subject);
        if(rows.isEmpty()) throw new OdsException("NOT_FOUND","Opération inaccessible");
        Map<String,Object> row=rows.getFirst();
        return Map.of("jobId",id,"projectId",row.get("project_id"),"kind",row.get("kind"),"status",row.get("status"),"createdAt",row.get("created_at"),"result",Json.map(Json.parse(cipher.open(row.get("result").toString(),id))));
    }
    /** Stores a terminal outcome without confusing successful scanning with a passed quality gate. Exigences : DEVAI-119. */
    public void finish(String id,String status,Object result) { db.update("UPDATE job SET status=?,result=? WHERE id=? AND status<>'DELETED'",status,cipher.seal(Json.write(result),id),id); }
    /** Records cancellation intent for the owned operation; actual cancellation is reported by its worker. Exigences : DEVAI-024. */
    public void cancel(String id,String subject) { job(id,subject); db.update("UPDATE job SET status='CANCELLED' WHERE id=? AND status='WAITING_APPROVAL'",id); db.update("UPDATE job SET status='CANCEL_REQUESTED' WHERE id=? AND status IN ('QUEUED','RUNNING')",id); }
    /** Tests cancellation intent before each new agent/provider step. Exigences : DEVAI-024, DEVAI-074. */
    public boolean cancelled(String id) { return Set.of("CANCEL_REQUESTED","CANCELLED","DELETED").contains(db.queryForObject("SELECT status FROM job WHERE id=?",String.class,id)); }
    /** Persists an encrypted checkpoint with optimistic concurrency to reject conflicting tool submissions. Exigences : DEVAI-067, DEVAI-157. */
    public long saveSession(String id,String subject,Object content,long revision,long expires) {
        String payload=cipher.seal(Json.write(content),id+":"+subject);
        if(revision==0) { db.update("INSERT INTO conversation(id,subject,expires_at,revision,payload) VALUES(?,?,?,1,?)",id,subject,expires,payload); return 1; }
        if(db.update("UPDATE conversation SET payload=?,revision=revision+1 WHERE id=? AND subject=? AND revision=?",payload,id,subject,revision)!=1) throw new OdsException("CONFLICT","Checkpoint modifié ; relire la tâche avant de poursuivre");
        return revision+1;
    }
    /** Loads only an owned unexpired checkpoint; no sources are stored in clear text. Exigences : DEVAI-067, DEVAI-157. */
    public Map<String,Object> loadSession(String id,String subject) {
        var rows=db.queryForList("SELECT revision,payload FROM conversation WHERE id=? AND subject=? AND expires_at>?",id,subject,System.currentTimeMillis());
        if(rows.isEmpty()) throw new OdsException("NOT_FOUND","Checkpoint absent ou expiré");
        var row=rows.getFirst(); return Map.of("revision",row.get("revision"),"content",Json.parse(cipher.open(row.get("payload").toString(),id+":"+subject)));
    }
    /** Suspends abandoned workers without repeating external effects and expires conversation content. Exigences : DEVAI-120, DEVAI-123. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=30000)
    public void maintenance() {
        db.update("UPDATE job SET status='SUSPENDED' WHERE status IN ('RUNNING','CANCEL_REQUESTED') AND lease_until<?",System.currentTimeMillis());
        db.update("DELETE FROM conversation WHERE expires_at<?",System.currentTimeMillis());
        db.update("DELETE FROM audit WHERE created_at<?",Instant.now().minus(Duration.ofDays(90)).toString());
        for(var row:db.queryForList("SELECT id FROM job WHERE created_at<? AND status IN ('SUCCEEDED','FAILED','CANCELLED','SUSPENDED')",Instant.now().minus(Duration.ofDays(1)).toString())) finish(row.get("id").toString(),"EXPIRED",Map.of("expired",true));
    }
    /** Reserves a bounded project budget atomically, including other users' outstanding reservations. Exigences : DEVAI-076. */
    public void reserve(String id,long project,long amount,long limit) {
        reserve(id,project,null,amount,limit,limit);
    }
    /** Atomically checks both subject and project budgets under consistently ordered database locks. Exigences : DEVAI-075, DEVAI-076. */
    public void reserve(String id,long project,String subject,long amount,long limit,long subjectLimit) {
        String month=YearMonth.now(ZoneOffset.UTC).toString();
        try { db.update("INSERT INTO quota(project_id,period,consumed,reserved) VALUES(?,?,0,0)",project,month); }
        catch(org.springframework.dao.DuplicateKeyException expected) { /* A concurrent instance already initialized this UTC period. */ }
        if(subject!=null) try { db.update("INSERT INTO subject_quota(subject,project_id,period,consumed,reserved) VALUES(?,?,?,0,0)",subject,project,month); }
        catch(org.springframework.dao.DuplicateKeyException expected) { /* The existing subject row will be locked in the same transaction. */ }
        tx.executeWithoutResult(s->{
            Map<String,Object> row=db.queryForMap("SELECT consumed,reserved FROM quota WHERE project_id=? AND period=? FOR UPDATE",project,month);
            long used=((Number)row.get("consumed")).longValue(), reserved=((Number)row.get("reserved")).longValue();
            if(amount<=0||amount>limit||used>limit-amount||reserved>limit-amount-used) throw new OdsException("QUOTA_EXCEEDED","Quota insuffisant");
            if(subject!=null) {
                var person=db.queryForMap("SELECT consumed,reserved FROM subject_quota WHERE project_id=? AND subject=? AND period=? FOR UPDATE",project,subject,month);
                long personalUsed=((Number)person.get("consumed")).longValue(), personalReserved=((Number)person.get("reserved")).longValue();
                if(amount>subjectLimit||personalUsed>subjectLimit-amount||personalReserved>subjectLimit-amount-personalUsed) throw new OdsException("QUOTA_EXCEEDED","Quota utilisateur insuffisant");
                db.update("UPDATE subject_quota SET reserved=reserved+? WHERE project_id=? AND subject=? AND period=?",amount,project,subject,month);
            }
            db.update("INSERT INTO reservation(id,project_id,period,amount,state,subject) VALUES(?,?,?,?,'RESERVED',?)",id,project,month,amount,subject);
            db.update("UPDATE quota SET reserved=reserved+? WHERE project_id=? AND period=?",amount,project,month);
        });
    }
    /** Reconciles usage exactly once; uncertain provider outcomes retain their reservation. Exigences : DEVAI-075, DEVAI-076. */
    public void reconcile(String id,long input,long output) {
        tx.executeWithoutResult(s->{
            Map<String,Object> row=db.queryForMap("SELECT * FROM reservation WHERE id=? FOR UPDATE",id);
            if(!row.get("state").equals("RESERVED")) return;
            if(input<0||output<0) throw new OdsException("INVALID_USAGE","Mesure invalide");
            db.update("UPDATE quota SET reserved=reserved-?,consumed=consumed+? WHERE project_id=? AND period=?",row.get("amount"),Math.addExact(input,output),row.get("project_id"),row.get("period"));
            if(row.get("subject")!=null) db.update("UPDATE subject_quota SET reserved=reserved-?,consumed=consumed+? WHERE project_id=? AND subject=? AND period=?",row.get("amount"),Math.addExact(input,output),row.get("project_id"),row.get("subject"),row.get("period"));
            db.update("UPDATE reservation SET state='MEASURED',input_tokens=?,output_tokens=? WHERE id=?",input,output,id);
        });
    }
    /** Returns measured and reserved project counters for the current UTC month. Exigences : DEVAI-075. */
    public Map<String,Object> usage(long project) {
        String month=YearMonth.now(ZoneOffset.UTC).toString(); List<Map<String,Object>> rows=db.queryForList("SELECT consumed,reserved FROM quota WHERE project_id=? AND period=?",project,month);
        return Map.of("period",month,"consumed",rows.isEmpty()?0:rows.getFirst().get("consumed"),"reserved",rows.isEmpty()?0:rows.getFirst().get("reserved"));
    }
    /** Adds only the authenticated subject's counters to the authorized project view. Exigences : DEVAI-075. */
    public Map<String,Object> usage(long project,String subject) {
        Map<String,Object> result=new LinkedHashMap<>(usage(project));
        var rows=db.queryForList("SELECT consumed,reserved FROM subject_quota WHERE project_id=? AND subject=? AND period=?",project,subject,result.get("period"));
        result.put("subjectConsumed",rows.isEmpty()?0:rows.getFirst().get("consumed")); result.put("subjectReserved",rows.isEmpty()?0:rows.getFirst().get("reserved")); return result;
    }
}
