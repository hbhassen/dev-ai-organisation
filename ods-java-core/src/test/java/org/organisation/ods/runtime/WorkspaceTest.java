package org.organisation.ods.runtime;
import org.organisation.ods.contracts.*;
import org.organisation.ods.security.SignedDocuments;
import org.organisation.ods.config.PromptCatalog;
import org.organisation.ods.starter.StarterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises reversible edits, IPC trust, malicious archives and generated Python code. Exigences : DEVAI-014, DEVAI-054, DEVAI-069. */
class WorkspaceTest {
    @TempDir Path root;
    /** Keeps UTF-8 patches and approval hashes intact even with a Windows legacy stdout charset. Exigences : DEVAI-014, DEVAI-069. */
    @Test void utf8ProposalSurvivesLegacyConsoleEncoding() throws Exception {
        String content="// Méthode corrigée — 日本語\n";
        var change=Map.of("path","new.txt","baseHash","MISSING","content",content,"requirements",List.of("DEVAI-069"));
        String open=Json.write(Map.of("jsonrpc","2.0","id",1,"method","workspace.open","params",Map.of("root",root.toString(),"trusted",true)));
        String propose=Json.write(Map.of("jsonrpc","2.0","id",2,"method","changes.propose","params",Map.of("changes",List.of(change))));
        var bytes=new ByteArrayOutputStream();
        new EngineMain().serve(new ByteArrayInputStream((open+"\n"+propose+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)),new PrintStream(bytes,true,"windows-1252"));
        var result=Json.parse(bytes.toString(java.nio.charset.StandardCharsets.UTF_8).split("\n")[1]).path("result");
        assertEquals(content,result.path("changes").get(0).path("content").asText());
        try(ProcessRunner runner=new ProcessRunner()) {
            var applied=new WorkspaceTools(root,runner).apply(result.path("changes"),result.path("planHash").asText());
            assertEquals(1,applied.get("applied")); assertEquals(content,Files.readString(root.resolve("new.txt")));
        }
    }
    /** Ensures a stale proposal never overwrites a subsequent developer edit. Exigences : DEVAI-069. */
    @Test void stalePatchPreservesUserChanges() throws Exception {
        Files.writeString(root.resolve("a.txt"),"before");
        try(ProcessRunner runner=new ProcessRunner()) {
            WorkspaceTools tools=new WorkspaceTools(root,runner);
            var changes=Json.MAPPER.valueToTree(List.of(Map.of("path","a.txt","baseHash",tools.read("a.txt").get("sha256"),"content","after","requirements",List.of("DEVAI-069"))));
            String hash=tools.propose(changes).get("planHash").toString(); Files.writeString(root.resolve("a.txt"),"user edit");
            assertThrows(OdsException.class,()->tools.apply(changes,hash)); assertEquals("user edit",Files.readString(root.resolve("a.txt")));
        }
    }
    /** Verifies applied bytes, new-file handling and the exact saved original. Exigences : DEVAI-069, DEVAI-154. */
    @Test void appliedPatchHasRecoveryCopy() throws Exception {
        Files.writeString(root.resolve("a.txt"),"before");
        try(ProcessRunner runner=new ProcessRunner()) {
            WorkspaceTools tools=new WorkspaceTools(root,runner);
            var changes=Json.MAPPER.valueToTree(List.of(Map.of("path","a.txt","baseHash",tools.read("a.txt").get("sha256"),"content","after","requirements",List.of("DEVAI-069")),Map.of("path","src/new.txt","baseHash","MISSING","content","created","requirements",List.of("DEVAI-069"))));
            var result=tools.apply(changes,tools.propose(changes).get("planHash").toString());
            assertEquals("before",Files.readString(root.resolve(result.get("backup").toString()).resolve("files/a.txt")));
            assertEquals("after",Files.readString(root.resolve("a.txt"))); assertEquals("created",Files.readString(root.resolve("src/new.txt")));
            tools.restore(Path.of(result.get("backup").toString()).getFileName().toString());
            assertEquals("before",Files.readString(root.resolve("a.txt"))); assertFalse(Files.exists(root.resolve("src/new.txt")));
        }
    }
    /** Checks real process exit/output and denies a destructive Git operation before process creation. Exigences : DEVAI-073, DEVAI-114, DEVAI-155. */
    @Test void processAndDestructiveGitBoundary() throws Exception {
        try(ProcessRunner runner=new ProcessRunner()) {
            var output=runner.run(root,List.of("java","-version"),15); assertEquals(0,output.get("exitCode")); assertTrue(output.get("output").toString().contains("version"));
            WorkspaceTools tools=new WorkspaceTools(root,runner); assertThrows(OdsException.class,()->tools.execute(List.of("git","reset","--hard")));
            assertThrows(OdsException.class,()->tools.execute(List.of("git","add","-A")));
        }
    }
    /** Runs installed Maven through the actual agent boundary without requiring a project wrapper or permitting shell chaining. Exigences : DEVAI-073, DEVAI-155. */
    @Test void installedMavenWithoutWrapper() throws Exception {
        try(ProcessRunner runner=new ProcessRunner()) {
            WorkspaceTools tools=new WorkspaceTools(root,runner);
            assertFalse(Files.exists(root.resolve("mvnw"))); assertFalse(Files.exists(root.resolve("mvnw.cmd")));
            String id=tools.execute(List.of("mvn","--version"));
            long deadline=System.nanoTime()+java.time.Duration.ofSeconds(30).toNanos();
            Map<String,Object> result=runner.output(id);
            while(Boolean.TRUE.equals(result.get("running"))&&System.nanoTime()<deadline) { Thread.sleep(50); result=runner.output(id); }
            assertEquals(false,result.get("running")); assertEquals(0,result.get("exitCode"),result.get("output").toString());
            assertTrue(result.get("output").toString().contains("Apache Maven"));
            assertThrows(OdsException.class,()->tools.execute(List.of("mvn","--version","&","echo","injected")));
            assertThrows(OdsException.class,()->tools.execute(List.of("cmd.exe","/c","echo","injected")));
        }
    }
    /** Stops zip-slip before any file can escape the generation directory. Exigences : DEVAI-054. */
    @Test void rejectsArchiveTraversal() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)) { zip.putNextEntry(new ZipEntry("../escape.txt")); zip.write("bad".getBytes()); zip.closeEntry(); }
        assertThrows(OdsException.class,()->StarterService.extract(bytes.toByteArray(),root));
    }
    /** Rejects privileged operations before trust and malformed batch JSON-RPC. Exigences : DEVAI-014, DEVAI-016. */
    @Test void ipcTrustAndShape() {
        EngineMain engine=new EngineMain();
        assertEquals("WORKSPACE_UNTRUSTED",Json.parse(engine.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"files.read\",\"params\":{\"path\":\"a.txt\"}}")).path("error").path("data").path("code").asText());
        assertTrue(Json.parse(engine.handle("[]")).has("error"));
    }
    /** Refuses local clone transports, embedded credentials and existing destinations before invoking Git. Exigences : DEVAI-107. */
    @Test void cloneBoundary() throws Exception {
        try(ProcessRunner runner=new ProcessRunner()) {
            assertThrows(OdsException.class,()->GitClone.clone(runner,root,"project","file:///private/repository"));
            assertThrows(OdsException.class,()->GitClone.clone(runner,root,"project","https://token@gitlab.invalid/group/project.git"));
            Files.createDirectory(root.resolve("project"));
            assertEquals("DESTINATION_EXISTS",assertThrows(OdsException.class,()->GitClone.clone(runner,root,"project","https://gitlab.invalid/group/project.git")).code());
        }
    }
    /** Keeps global and stack instructions deterministic across caller order. Exigences : DEVAI-162, DEVAI-163. */
    @Test void mandatoryPromptComposition() throws Exception {
        PromptCatalog catalog=new PromptCatalog(null,"");
        var first=catalog.compose(List.of("PYTHON","JAVA"),"code-generation"); var second=catalog.compose(List.of("JAVA","PYTHON"),"code-generation");
        assertEquals(first.get("sha256"),second.get("sha256")); assertTrue(first.get("system").toString().contains("Maven")); assertEquals("GENERIC",first.get("origin"));
    }
    /** Generates a standard Python project and refuses to replace an existing destination. Exigences : DEVAI-050, DEVAI-056. */
    @Test void pythonStarterPreservesDestination() throws Exception {
        try(ProcessRunner runner=new ProcessRunner()) {
            StarterService service=new StarterService(runner); service.create(root,"example-app","PYTHON");
            assertTrue(Files.exists(root.resolve("example-app/pyproject.toml")));
            assertTrue(Files.readString(root.resolve("example-app/src/example_app/__init__.py")).contains("DEVAI-050"));
            assertThrows(OdsException.class,()->service.create(root,"example-app","PYTHON"));
            var tests=runner.run(root.resolve("example-app/src"),List.of("python","-m","unittest","discover","-s","../tests"),60);
            assertEquals(0,tests.get("exitCode"),tests.get("output").toString());
        }
    }
}
