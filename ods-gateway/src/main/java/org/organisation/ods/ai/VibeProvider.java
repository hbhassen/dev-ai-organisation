package org.organisation.ods.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.organisation.ods.Settings;
import org.organisation.ods.contracts.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Executes the packaged Vibe CLI adapter with private stdin, isolated configuration and no direct workspace tools. Exigences : DEVAI-170, DEVAI-171, DEVAI-176. */
public final class VibeProvider implements ModelProvider {
    private final Settings settings;
    /** Binds Vibe to immutable managed settings, not user config or a shell command. Exigences : DEVAI-167, DEVAI-170. */
    public VibeProvider(Settings settings) { this.settings=settings; }
    /** Preserves the existing typed-tool protocol while Vibe handles the actual Mistral transport. Exigences : DEVAI-170, DEVAI-171. */
    @Override public JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput) {
        Path runtime=Path.of(settings.require("runtime-directory"));
        List<String> args=new ArrayList<>(); args.add(settings.require("python-command"));
        if(args.getFirst().equals("py")) args.add("-3.12");
        args.add("-I"); args.add(runtime.resolve("vibe_bridge.py").toString());
        String credential=settings.get("vibe-auth").equals("CLI_TOKEN")?settings.require("vibe-token"):settings.require("mistral-key");
        Map<String,Object> request=Map.of("url",settings.origin("vibe-url").toString(),"model",settings.require("mistral-model"),"credential",credential,"messages",messages,"tools",tools,"maxOutput",maxOutput);
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
            if(child.exitValue()!=0) throw new OdsException("DEPENDENCY_UNAVAILABLE","Vibe indisponible ; vérifier Python 3.12 et la configuration administrée");
            return Json.parse(new String(output.get(5,TimeUnit.SECONDS),java.nio.charset.StandardCharsets.UTF_8));
        } catch(OdsException e) { throw e; }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new OdsException("CANCELLED","Appel Vibe interrompu"); }
        catch(Exception e) { throw new OdsException("DEPENDENCY_UNAVAILABLE","Runtime Vibe embarqué indisponible"); }
        finally { if(child!=null&&child.isAlive()) { child.descendants().forEach(ProcessHandle::destroyForcibly); child.destroyForcibly(); } }
    }
}
