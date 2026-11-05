package org.organisation.ods.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.organisation.ods.Settings;
import org.organisation.ods.contracts.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Executes the packaged Vibe adapter with a signed-model selection, private
 * stdin, isolated configuration and no workspace tools. Exigences : DEVAI-170,
 * DEVAI-171, DEVAI-176, DEVAI-191.
 */
public final class VibeProvider implements ModelProvider {
    private final Settings settings;
    /** Binds Vibe to immutable managed settings, not user config or a shell command. Exigences : DEVAI-167, DEVAI-170, DEVAI-191. */
    public VibeProvider(Settings settings) { this.settings=settings; }

    /** Preserves the existing typed-tool protocol for configured Mistral transport. Exigences : DEVAI-170, DEVAI-171, DEVAI-196. */
    @Override public JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput) {
        String reference=settings.get("vibe-auth").equals("CLI_TOKEN")?"ODS_VIBE_TOKEN":"ODS_MISTRAL_API_KEY";
        ModelSelection selection=new ModelSelection("MISTRAL",settings.require("mistral-model"),"mistral","openai",settings.origin("vibe-url").toString(),reference,true,true,true,false,true);
        return complete(selection,messages,tools,maxOutput);
    }

    /** Sends only the already resolved managed provider and model through the private bridge. Exigences : DEVAI-190, DEVAI-191, DEVAI-193, DEVAI-176. */
    @Override public JsonNode complete(ModelSelection selection,List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput) {
        if(selection==null||!selection.generation()||(!tools.isEmpty()&&!selection.tools())||!selection.usage()) throw new OdsException("MODEL_NOT_ALLOWED","Le fournisseur ne couvre pas les capacités requises");
        Path runtime=Path.of(settings.require("runtime-directory"));
        List<String> args=new ArrayList<>(); args.add(settings.require("python-command"));
        if(args.getFirst().equals("py")) args.add("-3.12");
        args.add("-I"); args.add(runtime.resolve("vibe_bridge.py").toString());
        String credential=credential(selection.credentialRef());
        int boundedOutput=Math.min(maxOutput,selection.maxOutputTokens());
        Map<String,Object> request=Map.ofEntries(Map.entry("provider",selection.providerId()),Map.entry("backend",selection.backend()),Map.entry("apiStyle",selection.apiStyle()),
                Map.entry("url",selection.apiBase()),Map.entry("model",selection.model()),Map.entry("credential",credential),Map.entry("messages",messages),Map.entry("tools",tools),
                Map.entry("maxOutput",boundedOutput),Map.entry("maxContextTokens",selection.maxContextTokens()),Map.entry("maxOutputTokens",selection.maxOutputTokens()));
        Process child=null;
        try {
            ProcessBuilder builder=new ProcessBuilder(args); var env=builder.environment();
            env.keySet().removeIf(key->!Set.of("PATH","SYSTEMROOT","WINDIR","TEMP","TMP","HOME","USERPROFILE","LANG","APPDATA","LOCALAPPDATA").contains(key.toUpperCase(Locale.ROOT)));
            env.put("PYTHONIOENCODING","utf-8"); builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            child=builder.start(); Process owned=child;
            var output=CompletableFuture.supplyAsync(()->{
                try(var stream=owned.getInputStream()) { byte[] bytes=stream.readNBytes(8_388_609); if(bytes.length>8_388_608) throw new IOException(); return bytes; }
                catch(IOException e) { throw new CompletionException(e); }
            });
            try(var input=child.getOutputStream()) { input.write(Json.write(request).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            if(!child.waitFor(135,TimeUnit.SECONDS)) throw new OdsException("DEPENDENCY_TIMEOUT","Vibe a dépassé le délai autorisé");
            byte[] response=output.get(5,TimeUnit.SECONDS);
            if(child.exitValue()!=0) throw failure(response,selection.providerId());
            return Json.parse(new String(response,java.nio.charset.StandardCharsets.UTF_8));
        } catch(OdsException e) { throw e; }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new OdsException("CANCELLED","Appel Vibe interrompu"); }
        catch(Exception e) { throw new OdsException("DEPENDENCY_UNAVAILABLE","Runtime Vibe embarqué indisponible"); }
        finally { if(child!=null&&child.isAlive()) { child.descendants().forEach(ProcessHandle::destroyForcibly); child.destroyForcibly(); } }
    }

    /** Resolves only credential names explicitly permitted by the managed provider registry. Exigences : DEVAI-189, DEVAI-191, DEVAI-176. */
    private String credential(String reference) {
        return switch(reference) {
            case "ODS_OPENAI_API_KEY" -> settings.require("openai-api-key");
            case "ODS_MISTRAL_API_KEY" -> settings.require("mistral-key");
            case "ODS_VIBE_TOKEN" -> settings.require("vibe-token");
            default -> throw new OdsException("MODEL_NOT_ALLOWED","Référence de credential non autorisée");
        };
    }

    /** Maps only allowlisted bridge errors to provider-neutral diagnostics, never provider bodies or private child messages. Exigences : DEVAI-170, DEVAI-176, DEVAI-191. */
    private OdsException failure(byte[] response,String provider) {
        String code="";
        try { code=Json.parse(new String(response,java.nio.charset.StandardCharsets.UTF_8)).path("error").path("code").asText(); } catch(Exception ignored) { /* Broken bridges retain a secret-free generic failure. */ }
        return switch(code) {
            case "MODEL_AUTHENTICATION_FAILED" -> new OdsException(code,"Authentification "+provider+" refusée ; vérifier la clé administrée");
            case "MODEL_RATE_LIMITED" -> new OdsException(code,"Quota ou limite de débit "+provider+" atteint ; vérifier le compte fournisseur");
            case "MODEL_UNAVAILABLE" -> new OdsException(code,"Modèle "+provider+" indisponible pour cette configuration administrée");
            case "VIBE_RUNTIME_UNAVAILABLE" -> new OdsException("DEPENDENCY_UNAVAILABLE","Runtime Vibe incomplet ; vérifier Python 3.12 et réinstaller le VSIX administré");
            default -> new OdsException("DEPENDENCY_UNAVAILABLE","Appel Vibe échoué ; vérifier la connexion et la configuration administrée");
        };
    }
}
