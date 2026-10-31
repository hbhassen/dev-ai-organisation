package org.organisation.ods.runtime;

import org.organisation.ods.contracts.*;
import org.organisation.ods.config.PromptCatalog;
import org.organisation.ods.starter.StarterService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Native stdio engine; stdout is reserved for bounded JSON-RPC replies. Exigences : DEVAI-014, DEVAI-015. */
public final class EngineMain {
    private final ProcessRunner processes=new ProcessRunner();
    private WorkspaceTools workspace;
    private boolean trusted;
    /** Starts the protocol reader or prints a minimal version for package smoke tests. Exigences : DEVAI-014, DEVAI-145. */
    public static void main(String[] args) throws Exception {
        if(args.length==1&&args[0].equals("--version")) { System.out.println("ODS Engine 0.3.2 / protocol 1 / Java 21"); return; }
        new EngineMain().serve(System.in,System.out);
    }
    /** Reads at most 8 MiB per frame and releases owned processes on EOF. Exigences : DEVAI-014, DEVAI-015. */
    public void serve(InputStream input,PrintStream output) throws IOException {
        try(processes; BufferedInputStream in=new BufferedInputStream(input)) {
            ByteArrayOutputStream frame=new ByteArrayOutputStream(); int b;
            while((b=in.read())!=-1) {
                if(b=='\n') { if(frame.size()>0) output.println(handle(frame.toString(StandardCharsets.UTF_8))); frame.reset(); }
                else { if(frame.size()>=8_388_608) throw new IOException("IPC frame too large"); frame.write(b); }
            }
        }
    }
    /** Maps domain errors into safe JSON-RPC envelopes and rejects batch/invalid requests. Exigences : DEVAI-014, DEVAI-118. */
    public String handle(String frame) {
        Object id=null;
        try {
            JsonNode request=Json.parse(frame); if(!request.isObject()||!request.path("jsonrpc").asText().equals("2.0")||!request.hasNonNull("id")) throw new OdsException("INVALID_REQUEST","Requête JSON-RPC invalide");
            id=Json.MAPPER.convertValue(request.get("id"),Object.class); Object result=dispatch(Json.text(request,"method"),request.path("params"));
            return Json.write(Map.of("jsonrpc","2.0","id",id,"result",result));
        } catch(Exception e) {
            String code=e instanceof OdsException o?o.code():"OPERATION_FAILED";
            String message=e instanceof OdsException?e.getMessage():"Opération impossible ; vérifier prérequis et droits";
            Map<String,Object> reply=new LinkedHashMap<>(); reply.put("jsonrpc","2.0"); reply.put("id",id); reply.put("error",Map.of("code",-32000,"message",message,"data",Map.of("code",code))); return Json.write(reply);
        }
    }
    /** Routes typed operations and requires native workspace trust before reading or executing project code. Exigences : DEVAI-016, DEVAI-152. */
    private Object dispatch(String method,JsonNode p) throws Exception {
        if(method.equals("initialize")) {
            if(p.path("protocolVersion").asInt()!=1) throw new OdsException("PROTOCOL_INCOMPATIBLE","Version IPC incompatible");
            return Map.of("protocolVersion",1,"engineVersion","0.3.2");
        }
        if(method.equals("workspace.open")) { trusted=p.path("trusted").asBoolean(false); workspace=new WorkspaceTools(Path.of(Json.text(p,"root")),processes); return Map.of("ready",true,"trusted",trusted); }
        if(method.equals("starters.list")) return new StarterService(processes).catalog();
        if(method.equals("git.clone")) {
            approved(p); if(!p.path("trusted").asBoolean(false)) throw new OdsException("WORKSPACE_UNTRUSTED","Contexte IDE approuvé requis");
            return GitClone.clone(processes,Path.of(Json.text(p,"parent")),Json.text(p,"name"),Json.text(p,"remote"));
        }
        if(method.equals("starters.create")) {
            approved(p); if(!p.path("trusted").asBoolean(false)) throw new OdsException("WORKSPACE_UNTRUSTED","La création exige un contexte IDE approuvé");
            return new StarterService(processes).create(Path.of(Json.text(p,"parent")),Json.text(p,"name"),Json.text(p,"stack"));
        }
        if(method.equals("prompts.preview")) return new PromptCatalog(null,"").compose(List.of(p.path("stack").asText("JAVA")),p.path("task").asText("code-generation"));
        if(!trusted||workspace==null) throw new OdsException("WORKSPACE_UNTRUSTED","Ouvrir et approuver un workspace avant cette action");
        return switch(method) {
            case "project.inspect" -> workspace.inspect();
            case "workspace.list", "search.files" -> workspace.files();
            case "files.read" -> workspace.read(Json.text(p,"path"));
            case "project.instructions" -> workspace.instructions();
            case "search.text" -> workspace.search(Json.text(p,"query"));
            case "changes.propose" -> workspace.propose(p.path("changes"));
            case "changes.apply" -> { approved(p); yield workspace.apply(p.path("changes"),Json.text(p,"planHash")); }
            case "checkpoints.restore" -> { approved(p); yield workspace.restore(Json.text(p,"checkpointId")); }
            case "process.start", "build.run", "tests.run" -> { approved(p); List<String> args=new ArrayList<>(); for(JsonNode arg:p.path("arguments")) args.add(arg.asText()); yield Map.of("processId",workspace.execute(args)); }
            case "process.output" -> processes.output(Json.text(p,"processId"));
            case "process.cancel" -> { processes.cancel(Json.text(p,"processId")); yield Map.of("cancelRequested",true); }
            default -> throw new OdsException("METHOD_NOT_FOUND","Outil non pris en charge");
        };
    }
    /** Requires the native extension to attach its explicit approval after displaying the operation. Exigences : DEVAI-010, DEVAI-158. */
    private void approved(JsonNode params) { if(!params.path("approved").asBoolean(false)) throw new OdsException("APPROVAL_REQUIRED","Autorisation explicite requise"); }
}
