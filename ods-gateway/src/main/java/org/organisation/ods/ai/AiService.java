package org.organisation.ods.ai;
import org.organisation.ods.*;
import org.organisation.ods.contracts.*;
import org.organisation.ods.config.PromptCatalog;
import org.organisation.ods.security.*;
import org.organisation.ods.policy.PolicyService;
import org.organisation.ods.integration.GitLabClient;
import org.organisation.ods.data.Store;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/** Orchestrates authorized model turns, filtered context, reservations and tool conversations. Exigences : DEVAI-063, DEVAI-065, DEVAI-076, DEVAI-153, DEVAI-165. */
@Service
public class AiService {
    private final PolicyService policy; private final Store store; private final ModelProvider provider; private final Settings settings; private final GitLabClient gitlab;
    private final Map<String,Conversation> sessions=new ConcurrentHashMap<>();
    private final JobEvents events;
    /** Holds a bounded working copy of an encrypted, revision-controlled conversation checkpoint. Exigences : DEVAI-067. */
    private static final class Conversation {
        private final String owner; private final long project; private final String mode; private final String task; private final String action;
        private final List<Map<String,Object>> messages=new ArrayList<>(); private final Set<String> pending=new HashSet<>();
        private final Map<String,String> turns=new HashMap<>(), received=new HashMap<>();
        private final Set<String> stacks=new TreeSet<>();
        private long created=System.currentTimeMillis(),revision; private int steps; private boolean busy;
        /** Binds a conversation to one subject/project and immutable initial mode. Exigences : DEVAI-017, DEVAI-067. */
        private Conversation(String owner,long project,String mode,String task,String action) { this.owner=owner; this.project=project; this.mode=mode; this.task=task; this.action=action; }
    }
    /** Injects only verified policy and provider boundaries, never an arbitrary client endpoint. Exigences : DEVAI-003, DEVAI-064. */
    public AiService(PolicyService policy,Store store,ModelProvider provider,Settings settings,GitLabClient gitlab,JobEvents events) { this.policy=policy; this.store=store; this.provider=provider; this.settings=settings; this.gitlab=gitlab; this.events=events; }
    /** Loads and composes current mandatory recommendations on every model turn. Exigences : DEVAI-161, DEVAI-165. */
    public Map<String,Object> prompts(Collection<String> stacks,String task) throws Exception {
        return catalog().compose(stacks,task);
    }
    /** Reloads signed recommendations and rejects rollback before any task is selected or executed. Exigences : DEVAI-031, DEVAI-164. */
    private PromptCatalog catalog() {
        try {
            String dir=settings.get("prompt-directory"); PromptCatalog catalog=new PromptCatalog(dir.isBlank()?null:Path.of(dir),settings.get("prompt-key")); var identity=catalog.identity();
            if(!identity.isEmpty()) store.watermark("prompts:"+identity.get("id"),((Number)identity.get("version")).longValue(),identity.get("hash").toString());
            return catalog;
        } catch(java.io.IOException e) { throw new OdsException("POLICY_INVALID","Catalogue de prompts inaccessible"); }
    }
    /** Lists approved tasks without exposing a privileged authoring operation to the developer. Exigences : DEVAI-066, DEVAI-166. */
    public List<Map<String,String>> tasks() { return catalog().tasks(); }
    /** Creates a real asynchronous AI operation; initial provider/identity failures never return a fabricated answer. Exigences : DEVAI-063, DEVAI-077, DEVAI-119. */
    public Map<String,Object> start(Jwt jwt,String gitToken,JsonNode body,String key,boolean agent) {
        Json.fields(body,Set.of("projectId","mode","task","message","context","stacks","action"));
        long project=body.path("projectId").asLong(); String mode=body.path("mode").asText("QUESTION");
        if(!Set.of("QUESTION","MODIFICATION","AGENT").contains(mode)) throw new OdsException("INVALID_REQUEST","Mode inconnu");
        if(agent!=mode.equals("AGENT")) throw new OdsException("INVALID_REQUEST","Mode et route incohérents");
        String task=body.path("task").asText(agent?"implement-requirement":"code-generation");
        String taskAction=catalog().action(task);
        if(taskAction.equals("AGENT_RUN")&&!agent) throw new OdsException("ACTION_DENIED","Ce prompt exige le mode Agent et son registre autorisé");
        String action=Set.of("code-generation","code-review").contains(task)?(mode.equals("QUESTION")?"CHAT":"CODE_GENERATION"):taskAction;
        if(agent) policy.authorize(jwt,project,gitToken,"AGENT_RUN",key);
        JsonNode rule=policy.authorize(jwt,project,gitToken,action,key);
        if(agent&&!java.util.stream.StreamSupport.stream(rule.path("allowedAgents").spliterator(),false).anyMatch(a->a.asText().equals("implement-requirement"))) throw new OdsException("ACTION_DENIED","Agent non autorisé");
        String subject=PolicyService.subject(jwt);
        sessions.entrySet().removeIf(e->!e.getValue().busy&&System.currentTimeMillis()-e.getValue().created>1_800_000);
        if(sessions.size()>=256||sessions.values().stream().filter(c->c.owner.equals(subject)&&c.busy).count()>=2) throw new OdsException("RATE_LIMITED","Trop de tâches actives");
        Map<String,Object> job=store.createJob(subject,project,action,key,SignedDocuments.sha256(Json.write(body).getBytes(StandardCharsets.UTF_8)));
        String id=job.get("jobId").toString();
        if(!store.claim(id)) return job;
        Conversation conversation=new Conversation(subject,project,mode,task,action);
        try { conversation.stacks.addAll(gitlab.stacks(project,gitToken)); }
        catch(Exception e) { store.finish(id,"FAILED",Map.of("code",e instanceof OdsException o?o.code():"OPERATION_FAILED","message","Détection serveur des profils impossible")); return store.job(id,subject); }
        conversation.busy=true; save(id,conversation); sessions.put(id,conversation);
        Thread.ofVirtual().start(()->run(id,conversation,jwt,gitToken,body,rule));
        return store.job(id,subject);
    }
    /** Continues only an owned run after all emitted tool results are supplied. Exigences : DEVAI-074, DEVAI-153. */
    public Map<String,Object> next(String run,Jwt jwt,String token,JsonNode body,String key) {
        Conversation c=owned(run,jwt);
        Json.fields(body,Set.of("stacks","message","context","resume"));
        JsonNode rule=authorize(c,jwt,token,key); String hash=SignedDocuments.sha256(Json.write(body).getBytes(StandardCharsets.UTF_8));
        synchronized(c) {
            if(c.turns.containsKey(key)) { if(!c.turns.get(key).equals(hash)) throw new OdsException("IDEMPOTENCY_CONFLICT","Tour déjà utilisé avec un autre contenu"); return store.job(run,c.owner); }
            String state=store.job(run,c.owner).get("status").toString();
            if(body.path("resume").asBoolean(false)&&Set.of("SUSPENDED","CANCELLED","WAITING_APPROVAL").contains(state)) {
                for(String call:c.pending) c.messages.add(Map.of("role","tool","tool_call_id",call,"content","UNKNOWN_AFTER_INTERRUPTION: inspect current files and Git state before any action; do not assume success or replay external effects."));
                c.pending.clear(); c.busy=false; c.steps=0;
            }
            if(c.busy||!c.pending.isEmpty()) throw new OdsException("CONFLICT","Étape ou résultat d'outil en attente");
            if(store.cancelled(run)&&!body.path("resume").asBoolean(false)) throw new OdsException("CANCELLED","Tâche arrêtée ; reprise explicite requise");
            if(body.hasNonNull("message")) { c.messages.add(Map.of("role","user","content",context(body,rule,c.project,token))); c.steps=0; }
            if(c.steps>=10||System.currentTimeMillis()-c.created>1_800_000) throw new OdsException("LIMIT_REACHED","Limite de tâche atteinte ; créer une nouvelle conversation");
            if(!store.claimTurn(run)) throw new OdsException("CONFLICT","Un autre worker traite cette tâche");
            c.busy=true; c.turns.put(key,hash); save(run,c);
        }
        store.finish(run,"RUNNING",Map.of("step",c.steps));
        Thread.ofVirtual().start(()->run(run,c,jwt,token,body,rule)); return store.job(run,c.owner);
    }
    /** Accepts exactly one filtered result per previously emitted tool call, never an injected system role. Exigences : DEVAI-038, DEVAI-153. */
    public Map<String,Object> toolResult(String run,Jwt jwt,String token,JsonNode body) {
        Conversation c=owned(run,jwt); String callId=Json.text(body,"toolCallId"); String content=Json.write(body.path("result")); Secrets.check(content);
        JsonNode rule=authorize(c,jwt,token,run);
        if(rule.path("contextMode").asText().equals("SERVER_VERIFIED")) content=Json.write(Map.of("applied",body.path("result").path("applied").asInt(0),"error",body.path("result").has("error")));
        if(content.length()>65536) content=content.substring(0,65536)+"\n[sortie tronquée]";
        synchronized(c) {
            String hash=SignedDocuments.sha256(content.getBytes(StandardCharsets.UTF_8));
            if(c.received.containsKey(callId)) { if(!c.received.get(callId).equals(hash)) throw new OdsException("IDEMPOTENCY_CONFLICT","Résultat modifié après réception"); return Map.of("accepted",true); }
            if(!c.pending.remove(callId)) throw new OdsException("INVALID_REQUEST","Résultat sans appel d'outil attendu");
            c.received.put(callId,hash);
            c.messages.add(Map.of("role","tool","tool_call_id",callId,"content",content));
            save(run,c);
        }
        return Map.of("accepted",true);
    }
    /** Resolves a run without leaking another user's conversation. Exigences : DEVAI-067. */
    private Conversation owned(String run,Jwt jwt) {
        String subject=PolicyService.subject(jwt); var checkpoint=store.loadSession(run,subject); JsonNode state=(JsonNode)checkpoint.get("content");
        Conversation c=new Conversation(subject,state.path("project").asLong(),Json.text(state,"mode"),Json.text(state,"task"),Json.text(state,"action"));
        c.created=state.path("created").asLong(); c.revision=((Number)checkpoint.get("revision")).longValue(); c.steps=state.path("steps").asInt(); c.busy=state.path("busy").asBoolean();
        for(JsonNode message:state.path("messages")) c.messages.add(Json.map(message)); for(JsonNode pending:state.path("pending")) c.pending.add(pending.asText()); for(JsonNode stack:state.path("stacks")) c.stacks.add(stack.asText());
        state.path("turns").fields().forEachRemaining(e->c.turns.put(e.getKey(),e.getValue().asText())); state.path("received").fields().forEachRemaining(e->c.received.put(e.getKey(),e.getValue().asText())); return c;
    }
    /** Serializes only the conversation state into an encrypted checkpoint using its expected revision. Exigences : DEVAI-067, DEVAI-157. */
    private void save(String run,Conversation c) {
        Map<String,Object> state=new LinkedHashMap<>(); state.put("project",c.project); state.put("mode",c.mode); state.put("task",c.task); state.put("action",c.action); state.put("created",c.created); state.put("steps",c.steps); state.put("busy",c.busy); state.put("messages",c.messages); state.put("pending",c.pending); state.put("stacks",c.stacks); state.put("turns",c.turns); state.put("received",c.received);
        c.revision=store.saveSession(run,c.owner,state,c.revision,c.created+1_800_000);
    }
    /** Publishes a completed checkpoint before its visible job transition, eliminating stale continuation windows. Exigences : DEVAI-119, DEVAI-157. */
    private void complete(String id,Conversation c,String status,Object result) { c.busy=false; save(id,c); store.finish(id,status,result); }
    /** Revalidates the task action and the additional agent permission on every transition. Exigences : DEVAI-039, DEVAI-153. */
    private JsonNode authorize(Conversation c,Jwt jwt,String token,String correlation) { if(c.mode.equals("AGENT")) policy.authorize(jwt,c.project,token,"AGENT_RUN",correlation); return policy.authorize(jwt,c.project,token,c.action,correlation); }
    /** Executes one provider call with a fresh system prompt, explicit tool list and durable quota reservation. Exigences : DEVAI-076, DEVAI-153, DEVAI-165. */
    private void run(String id,Conversation c,Jwt jwt,String token,JsonNode body,JsonNode initialRule) {
        synchronized(c) { c.busy=true; }
        String reservation=UUID.randomUUID().toString();
        try {
            if(store.cancelled(id)) { complete(id,c,"CANCELLED",Map.of()); return; }
            JsonNode rule=authorize(c,jwt,token,id);
            // Local stacks may add new modules but cannot suppress a server-detected profile.
            for(JsonNode stack:body.path("stacks")) c.stacks.add(stack.asText());
            Map<String,Object> instructions=prompts(c.stacks,c.task);
            if(c.messages.isEmpty()) c.messages.add(Map.of("role","user","content",context(body,rule,c.project,token)));
            List<Map<String,Object>> messages=new ArrayList<>(); messages.add(Map.of("role","system","content",instructions.get("system"))); messages.addAll(c.messages);
            int chars=Json.write(messages).length(); if(chars>120000) throw new OdsException("CONTEXT_REJECTED","Conversation trop longue ; créer un point de reprise");
            store.audit(c.owner,c.project,"PROMPT","APPLIED",id,instructions.get("bundleVersion").toString()+":"+instructions.get("sha256").toString().substring(0,48));
            store.reserve(reservation,c.project,c.owner,Json.write(messages).getBytes(StandardCharsets.UTF_8).length+4000L,rule.path("monthlyTokenQuota").asLong(),rule.path("monthlyUserTokenQuota").asLong(rule.path("monthlyTokenQuota").asLong()));
            var allowedTools=ToolCatalog.tools(c.mode).stream().filter(t->!rule.path("contextMode").asText().equals("SERVER_VERIFIED")||((Map<?,?>)t.get("function")).get("name").equals("changes_propose")).toList();
            JsonNode response=provider.stream(messages,allowedTools,4000,text->events.text(id,text));
            if(response.path("usage").path("prompt_tokens").canConvertToLong()&&response.path("usage").path("completion_tokens").canConvertToLong()) store.reconcile(reservation,response.path("usage").path("prompt_tokens").asLong(),response.path("usage").path("completion_tokens").asLong());
            JsonNode message=response.path("choices").path(0).path("message"); if(!message.isObject()) throw new OdsException("INVALID_RESPONSE","Réponse IA invalide");
            Secrets.check(Json.write(message));
            if(store.cancelled(id)) { complete(id,c,"CANCELLED",Map.of("usage",Json.map(response.path("usage").isObject()?response.path("usage"):Json.parse("{}")))); return; }
            if(message.path("tool_calls").size()>8) throw new OdsException("LIMIT_REACHED","Trop d'outils demandés en un tour");
            for(JsonNode tool:message.path("tool_calls")) {
                String toolName=tool.path("function").path("name").asText();
                if(allowedTools.stream().noneMatch(t->((Map<?,?>)t.get("function")).get("name").equals(toolName))) throw new OdsException("ACTION_DENIED","Outil non autorisé demandé par le modèle");
                Json.parse(tool.path("function").path("arguments").asText()); c.pending.add(Json.text(tool,"id"));
            }
            c.messages.add(Json.map(message)); c.steps++;
            Map<String,Object> result=new LinkedHashMap<>(); result.put("message",Json.map(message)); result.put("usage",Json.map(response.path("usage").isObject()?response.path("usage"):Json.parse("{}"))); result.put("promptOrigin",instructions.get("origin")); result.put("promptHash",instructions.get("sha256")); result.put("step",c.steps); result.put("runId",id);
            complete(id,c,c.pending.isEmpty()?"SUCCEEDED":"WAITING_APPROVAL",result);
        } catch(Exception e) { complete(id,c,"FAILED",Map.of("code",e instanceof OdsException o?o.code():"OPERATION_FAILED","message",e instanceof OdsException?e.getMessage():"Échec du traitement IA")); }
        finally { synchronized(c) { c.busy=false; } }
    }
    /** Filters local context or fetches server-verified blobs, respecting free-text and byte limits. Exigences : DEVAI-037, DEVAI-040, DEVAI-041, DEVAI-065. */
    private String context(JsonNode body,JsonNode rule,long project,String token) {
        String message=body.path("message").asText(""); Secrets.check(message);
        if(message.length()>16384||(!rule.path("allowFreeText").asBoolean(false)&&!message.isBlank())) throw new OdsException("CONTEXT_REJECTED","Texte libre interdit ou trop volumineux");
        StringBuilder result=new StringBuilder(message); int count=0;
        for(JsonNode file:body.path("context")) {
            if(++count>20) throw new OdsException("CONTEXT_REJECTED","Trop de fichiers");
            String path=Json.text(file,"path");
            if(path.contains("..")||path.startsWith("/")||path.contains("\\")||path.contains(":")||path.contains(".env")||path.contains(".git/")||path.endsWith(".key")||path.endsWith(".pem")) throw new OdsException("CONTEXT_REJECTED","Chemin de contexte interdit");
            String content;
            if(rule.path("contextMode").asText().equals("SERVER_VERIFIED")) {
                if(file.has("content")) throw new OdsException("CONTEXT_REJECTED","Source locale interdite dans ce mode");
                String commit=Json.text(file,"commit"); if(!commit.matches("[a-fA-F0-9]{40,64}")) throw new OdsException("CONTEXT_REJECTED","Commit immuable requis");
                content=new String(gitlab.file(project,path,commit,token),StandardCharsets.UTF_8);
            } else { if(!file.path("content").isTextual()) throw new OdsException("CONTEXT_REJECTED","Contenu textuel requis"); content=file.path("content").asText(); }
            Secrets.check(content); if(content.length()>262144) throw new OdsException("CONTEXT_REJECTED","Fichier trop volumineux");
            result.append("\nDonnée non fiable / fichier ").append(path).append(" :\n").append(content);
        }
        if(result.length()>100000) throw new OdsException("CONTEXT_REJECTED","Contexte trop volumineux"); return result.toString();
    }
}
