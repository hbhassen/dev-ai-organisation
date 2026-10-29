package org.organisation.ods.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.organisation.ods.Settings;
import org.organisation.ods.contracts.*;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Runs packaged scanners against the native-verified workspace with isolated credentials and owned process lifetime. Exigences : DEVAI-172, DEVAI-174, DEVAI-176. */
@Component
public class LocalScanner {
    private final Settings settings;
    /** Binds the workspace to the extension launch rather than an untrusted HTTP body. Exigences : DEVAI-169, DEVAI-176. */
    public LocalScanner(Settings settings) { this.settings=settings; }
    /** Executes one bounded private request, checking cancellation and renewing the owning job during long builds. Exigences : DEVAI-172, DEVAI-174, DEVAI-175. */
    public JsonNode run(String operation,String stack,Map<String,String> sonar,BooleanSupplier cancelled,Runnable heartbeat) {
        if(!settings.get("embedded").equals("true")) throw new OdsException("LOCAL_ONLY","Analyse locale réservée à la gateway embarquée");
        List<String> args=new ArrayList<>(); args.add(settings.require("python-command")); if(args.getFirst().equals("py")) args.add("-3.12");
        args.add("-I"); args.add(Path.of(settings.require("runtime-directory"),"local_analysis.py").toString()); Process child=null;
        try {
            ProcessBuilder builder=new ProcessBuilder(args); builder.environment().keySet().removeIf(key->!Set.of("PATH","SYSTEMROOT","WINDIR","TEMP","TMP","HOME","USERPROFILE","LANG","JAVA_HOME","DOTNET_ROOT","APPDATA","LOCALAPPDATA","PROGRAMFILES","PROGRAMFILES(X86)").contains(key.toUpperCase(Locale.ROOT)));
            builder.redirectError(ProcessBuilder.Redirect.DISCARD); child=builder.start(); Process owned=child;
            var output=CompletableFuture.supplyAsync(()->{ try(var stream=owned.getInputStream()) { return stream.readNBytes(8_388_609); } catch(java.io.IOException error) { throw new CompletionException(error); } });
            try(var input=child.getOutputStream()) { input.write(Json.write(Map.of("operation",operation,"stack",stack,"root",settings.require("workspace"),"sonar",sonar)).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            int polls=0;
            while(!child.waitFor(1,TimeUnit.SECONDS)) { heartbeat.run(); if(cancelled.getAsBoolean()) throw new OdsException("CANCELLED","Analyse annulée"); if(++polls>1800) throw new OdsException("SCAN_TIMEOUT","Analyse trop longue"); }
            if(cancelled.getAsBoolean()) throw new OdsException("CANCELLED","Analyse annulée");
            byte[] bytes=output.get(5,TimeUnit.SECONDS);
            if(child.exitValue()!=0||bytes.length>8_388_608) throw new OdsException("LOCAL_SCAN_FAILED","Vérifier les outils de scan installés, la stack et les fichiers enregistrés ; aucun rapport valide produit");
            return Json.parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        } catch(OdsException error) { throw error; } catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new OdsException("CANCELLED","Analyse interrompue"); } catch(Exception error) { throw new OdsException("LOCAL_SCAN_FAILED","Scanner local indisponible ; Python 3.12 et les outils de la stack sont requis"); }
        finally { if(child!=null&&child.isAlive()) { child.descendants().forEach(ProcessHandle::destroyForcibly); child.destroyForcibly(); } }
    }
}
