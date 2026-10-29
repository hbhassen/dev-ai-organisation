package org.organisation.ods.starter;

import org.organisation.ods.contracts.*;
import org.organisation.ods.runtime.ProcessRunner;
import org.organisation.ods.security.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.net.http.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Creates standard projects in a temporary sibling, preserving any existing destination. Exigences : DEVAI-045, DEVAI-046, DEVAI-048, DEVAI-049, DEVAI-050, DEVAI-051, DEVAI-056. */
public final class StarterService {
    private final ProcessRunner runner;
    /** Injects the bounded process service used for official CLI generators. Exigences : DEVAI-047. */
    public StarterService(ProcessRunner runner) { this.runner = runner; }
    /** Returns the four initial standard providers with explicit prerequisites. Exigences : DEVAI-046. */
    public List<Map<String,String>> catalog() {
        return List.of(Map.of("stack","JAVA","provider","Spring Initializr / Maven","prerequisite","Java 21"),Map.of("stack","ANGULAR","provider","Angular CLI 21.2.5","prerequisite","Node 20.19+, 22.12+ ou 24+"),Map.of("stack","PYTHON","provider","PyPA package","prerequisite","Python 3.11+"),Map.of("stack","DOTNET","provider","dotnet new","prerequisite",".NET SDK installé"));
    }
    /** Creates a project only after approval, returning its provenance and actual generator result. Exigences : DEVAI-045, DEVAI-055, DEVAI-056, DEVAI-059. */
    public Map<String,Object> create(Path parent, String name, String stack) throws Exception {
        if (!name.matches("[a-z][a-z0-9-]{2,48}")) throw new OdsException("INVALID_REQUEST", "Nom attendu : lettres minuscules, chiffres, tirets (3–49 caractères)");
        Path safeParent = parent.toRealPath(), destination = safeParent.resolve(name);
        if (Files.exists(destination)) throw new OdsException("DESTINATION_EXISTS", "La destination existe déjà");
        Path work = Files.createTempDirectory(safeParent, ".ods-starter-"); Path generated = work.resolve(name); Files.createDirectories(generated);
        StarterOverrides overrides=StarterOverrides.configured(); String provenance="";
        if(overrides==null||!overrides.replaces(stack)) switch (stack) {
            case "JAVA" -> {
                String query = "type=maven-project&language=java&bootVersion=4.1.2&javaVersion=21&groupId=org.example&artifactId=" + name + "&name=" + name + "&packageName=org.example.app&dependencies=web";
                HttpResponse<byte[]> response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(java.time.Duration.ofSeconds(15)).build().send(HttpRequest.newBuilder(URI.create("https://start.spring.io/starter.zip?"+query)).timeout(java.time.Duration.ofSeconds(90)).build(), HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode()!=200 || response.body().length>20_000_000) throw new OdsException("GENERATOR_UNAVAILABLE", "Spring Initializr indisponible");
                extract(response.body(), generated); provenance = SignedDocuments.sha256(response.body());
                documentJava(generated);
            }
            case "ANGULAR" -> {
                List<String> command = new ArrayList<>(List.of("npx","--yes","--package=@angular/cli@21.2.5","ng","new",name,"--directory",name,"--skip-git","--skip-install","--defaults","--interactive=false"));
                if (System.getProperty("os.name").startsWith("Windows")) command.addAll(0,List.of("cmd.exe","/d","/c"));
                requireSuccess(runner.run(work,command,300)); provenance="@angular/cli@21.2.5";
                documentAngular(generated);
                List<String> lock=new ArrayList<>(List.of("npm","install","--package-lock-only","--ignore-scripts","--no-audit","--no-fund"));
                if(System.getProperty("os.name").startsWith("Windows")) lock.addAll(0,List.of("cmd.exe","/d","/c"));
                requireSuccess(runner.run(generated,lock,300)); provenance+=";lock="+SignedDocuments.sha256(Files.readAllBytes(generated.resolve("package-lock.json")));
            }
            case "PYTHON" -> { python(generated,name); provenance="ods-pypa-1.0.0"; }
            case "DOTNET" -> {
                var version=runner.run(generated,List.of("dotnet","--version"),30); requireSuccess(version); String sdk=version.get("output").toString().trim();
                if(!sdk.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new OdsException("PREREQUISITE_MISSING","SDK .NET stable requis");
                requireSuccess(runner.run(generated,List.of("dotnet","new","webapi","--name",name,"--output",".","--no-restore"),120));
                Files.writeString(generated.resolve("global.json"),Json.write(Map.of("sdk",Map.of("version",sdk,"rollForward","latestPatch"))));
                documentDotnet(generated); dotnetTests(generated,name,sdk); provenance="dotnet-sdk-"+sdk;
            }
            default -> throw new OdsException("INVALID_REQUEST", "Stack inconnue");
        }
        if(overrides!=null) { String organizational=overrides.apply(stack,generated,name); if(!organizational.isBlank()) provenance=organizational; }
        Files.createDirectories(generated.resolve(".ods"));
        Map<String,Object> lock = Map.of("schemaVersion",1,"stack",stack,"starterVersion","1.0.0","origin",provenance.startsWith("ORGANIZATION:")?"ORGANIZATION":"STANDARD","source",provenance,"requirements",List.of("DEVAI-059","DEVAI-137"));
        Files.writeString(generated.resolve(".ods/starter-lock.json"),Json.write(lock));
        Files.writeString(generated.resolve(".gitignore"),"target/\nnode_modules/\ndist/\nbin/\nobj/\nTestResults/\n.venv/\n__pycache__/\n.env\n.ods-local/\nsuiteInstall/\nsuiteInstall-*.zip\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        Files.writeString(generated.resolve("README.md"),"\n# Projet "+name+"\n\nGénéré par ODS, profil "+stack+". Voir `.ods/starter-lock.json`. Les credentials sont externes.\n\nValidation à exécuter : "+validation(stack)+". Génération de fichiers ne signifie pas build validé.\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        Files.writeString(generated.resolve("AGENTS.md"),"\n# Instructions de développement\n\nAnalyser le projet et préserver le travail existant. Commenter chaque classe et méthode avec ses exigences réelles. Mettre à jour tests et documentation. Exécuter les validations documentées ; ne jamais inventer un résultat. Ne commiter aucun secret ni artefact généré.\n\nExigences : DEVAI-058, DEVAI-137.\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        Files.move(generated,destination);
        return Map.of("created",name,"stack",stack,"validation","NOT_RUN","provenance",lock);
    }
    /** Rejects CLI failures instead of reporting a successful generation. Exigences : DEVAI-060. */
    private void requireSuccess(Map<String,Object> result) { if (!Objects.equals(result.get("exitCode"),0)) throw new OdsException("GENERATION_FAILED","Le générateur a échoué : "+result.get("output")); }
    /** Extracts archives with traversal, duplicate-entry and expansion limits. Exigences : DEVAI-054. */
    public static void extract(byte[] archive, Path root) throws IOException {
        long size=0; Set<Path> seen=new HashSet<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                String name=entry.getName(); if(name.startsWith("/")||name.contains("\\")||name.contains(":")||Arrays.asList(name.split("/")).contains("..")) throw new OdsException("INVALID_ARCHIVE","Chemin d'archive interdit");
                Path target=root.resolve(name).normalize();
                if(!target.startsWith(root)||!seen.add(Path.of(target.toString().toLowerCase(Locale.ROOT)))) throw new OdsException("INVALID_ARCHIVE","Collision dans l'archive");
                if(entry.isDirectory()) { Files.createDirectories(target); continue; }
                Files.createDirectories(target.getParent());
                try(OutputStream out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer=new byte[8192]; int n;
                    while((n=zip.read(buffer))>=0) { size+=n; if(size>50_000_000) throw new OdsException("INVALID_ARCHIVE","Archive trop volumineuse"); out.write(buffer,0,n); }
                }
            }
        }
    }
    /** Adds requirement-linked documentation to the standard Initializr entry points and tests. Exigences : DEVAI-048, DEVAI-137. */
    private void documentJava(Path root) throws IOException {
        try(var files=Files.walk(root)) {
            for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()) {
                String text=Files.readString(file);
                text=text.replaceAll("(?m)^(public )?class ","/** Point d'entrée ou test du starter Spring Boot. Exigences : DEVAI-048, DEVAI-137. */\n$1class ");
                text=text.replace("\tpublic static void main", "\t/** Démarre l'application Spring Boot. Exigences : DEVAI-048, DEVAI-137. */\n\tpublic static void main");
                text=text.replace("\t@Test", "\t/** Vérifie le chargement du contexte Spring. Exigences : DEVAI-048, DEVAI-060. */\n\t@Test");
                Files.writeString(file,text);
            }
        }
    }
    /** Documents editable Angular component classes emitted by the pinned official CLI. Exigences : DEVAI-049, DEVAI-137. */
    private void documentAngular(Path root) throws IOException {
        try(var files=Files.walk(root)) {
            for(Path file:files.filter(p->p.toString().endsWith(".ts")).toList()) {
                String text=Files.readString(file).replaceAll("(?m)^(export (?:default )?class )","/** Composant du starter Angular standard. Exigences : DEVAI-049, DEVAI-137. */\n$1"); Files.writeString(file,text);
            }
        }
    }
    /** Documents the editable records/classes from the official minimal API template. Exigences : DEVAI-051, DEVAI-137. */
    private void documentDotnet(Path root) throws IOException {
        try(var files=Files.walk(root)) {
            for(Path file:files.filter(p->p.toString().endsWith(".cs")).toList()) {
                String text=Files.readString(file).replaceAll("(?m)^((?:public |internal )?(?:record|class) )","/// <summary>Contrat du starter API standard. Exigences : DEVAI-051, DEVAI-137.</summary>\n$1"); Files.writeString(file,text);
            }
        }
    }
    /** Adds real HTTP integration tests to the official .NET starter, preserving its SDK and ASP.NET package line. Exigences : DEVAI-051, DEVAI-058, DEVAI-060, DEVAI-137. */
    private void dotnetTests(Path root,String name,String sdk) throws IOException {
        String project=Files.readString(root.resolve(name+".csproj"));
        var version=java.util.regex.Pattern.compile("Microsoft.AspNetCore.OpenApi\" Version=\"([^\"]+)\"").matcher(project);
        if(!version.find()) throw new OdsException("GENERATION_FAILED","Version ASP.NET du template non détectée");
        String testingVersion=version.group(1);
        Files.writeString(root.resolve(name+".csproj"),project.replace("</Project>","  <ItemGroup><Compile Remove=\"tests/**/*.cs\" /><Content Remove=\"tests/**\" /><None Remove=\"tests/**\" /></ItemGroup>\n</Project>"));
        Files.writeString(root.resolve("Program.cs"),"\n/// <summary>Point d'entrée exposé aux tests HTTP d'intégration. Exigences : DEVAI-051, DEVAI-060.</summary>\npublic partial class Program { }\n",StandardOpenOption.APPEND);
        String test="tests/OdsStarter.Tests/OdsStarter.Tests.csproj";
        requireSuccess(runner.run(root,List.of("dotnet","new","xunit","--name","OdsStarter.Tests","--output","tests/OdsStarter.Tests","--no-restore"),120));
        requireSuccess(runner.run(root,List.of("dotnet","add",test,"reference",name+".csproj"),60));
        Path testProject=root.resolve(test); Files.writeString(testProject,Files.readString(testProject).replace("</Project>","  <ItemGroup><PackageReference Include=\"Microsoft.AspNetCore.Mvc.Testing\" Version=\""+testingVersion+"\" /></ItemGroup>\n</Project>"));
        Files.writeString(root.resolve("tests/OdsStarter.Tests/UnitTest1.cs"),"""
            using Microsoft.AspNetCore.Mvc.Testing;
            using System.Net;
            using System.Text.Json;
            namespace OdsStarter.Tests;
            /// <summary>Vérifie le contrat HTTP réel de l'API standard. Exigences : DEVAI-051, DEVAI-060.</summary>
            public sealed class ApiTests : IClassFixture<WebApplicationFactory<Program>>
            {
                private readonly WebApplicationFactory<Program> factory;
                /// <summary>Injecte l'application hébergée par le serveur de test. Exigences : DEVAI-060.</summary>
                public ApiTests(WebApplicationFactory<Program> factory) { this.factory = factory; }
                /// <summary>Vérifie les cinq prévisions et leurs températures contractuelles. Exigences : DEVAI-051, DEVAI-060.</summary>
                [Fact]
                public async Task ForecastReturnsFiveValidEntries()
                {
                    using var client = factory.CreateClient(new WebApplicationFactoryClientOptions { BaseAddress = new Uri("https://localhost") });
                    using var response = await client.GetAsync("/weatherforecast");
                    Assert.Equal(HttpStatusCode.OK, response.StatusCode);
                    using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
                    Assert.Equal(5, document.RootElement.GetArrayLength());
                    foreach (var forecast in document.RootElement.EnumerateArray())
                        Assert.InRange(forecast.GetProperty("temperatureC").GetInt32(), -20, 54);
                }
                /// <summary>Vérifie qu'une route absente ne produit pas un faux succès. Exigences : DEVAI-060.</summary>
                [Fact]
                public async Task UnknownRouteReturnsNotFound()
                {
                    using var client = factory.CreateClient(new WebApplicationFactoryClientOptions { BaseAddress = new Uri("https://localhost") });
                    using var response = await client.GetAsync("/missing-route");
                    Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
                }
            }
            """);
        List<String> solution=new ArrayList<>(List.of("dotnet","new","sln","--name",name)); if(Integer.parseInt(sdk.split("\\.")[0])>=9) solution.addAll(List.of("--format","sln"));
        requireSuccess(runner.run(root,solution,60)); requireSuccess(runner.run(root,List.of("dotnet","sln",name+".sln","add",name+".csproj",test),60));
    }
    /** Writes a portable Python package and meaningful standard-library tests. Exigences : DEVAI-050, DEVAI-137. */
    private void python(Path root,String name) throws IOException {
        String module=name.replace('-','_'); Files.createDirectories(root.resolve("src/"+module)); Files.createDirectories(root.resolve("tests"));
        Files.writeString(root.resolve("pyproject.toml"),"[build-system]\nrequires = [\"setuptools==80.9.0\"]\nbuild-backend = \"setuptools.build_meta\"\n[project]\nname = \""+name+"\"\nversion = \"0.1.0\"\nrequires-python = \">=3.11\"\n[tool.setuptools.packages.find]\nwhere = [\"src\"]\n");
        Files.writeString(root.resolve("src/"+module+"/__init__.py"),"\"\"\"Package standard ODS. Exigences : DEVAI-050, DEVAI-137.\"\"\"\n\ndef greet(name: str) -> str:\n    \"\"\"Valide le nom puis produit une salutation. Exigences : DEVAI-050, DEVAI-137.\"\"\"\n    if not name.strip():\n        raise ValueError('Nom vide')\n    return f'Bonjour {name.strip()}'\n");
        Files.writeString(root.resolve("tests/test_greeting.py"),"\"\"\"Recette du starter. Exigences : DEVAI-050, DEVAI-060.\"\"\"\nimport unittest\nfrom "+module+" import greet\n\nclass GreetingTest(unittest.TestCase):\n    \"\"\"Vérifie le contrat de salutation. Exigences : DEVAI-050.\"\"\"\n    def test_greeting(self):\n        \"\"\"Accepte un nom valide. Exigences : DEVAI-050.\"\"\"\n        self.assertEqual(greet('Monde'), 'Bonjour Monde')\n    def test_empty(self):\n        \"\"\"Refuse les noms vides. Exigences : DEVAI-050.\"\"\"\n        with self.assertRaises(ValueError):\n            greet(' ')\n");
    }
    /** Describes real validation commands without claiming they already ran. Exigences : DEVAI-060. */
    private String validation(String stack) { return switch(stack) { case "JAVA"->"mvnw clean verify"; case "ANGULAR"->"npm install, npm run build, npm test -- --watch=false"; case "PYTHON"->"python -m pip install -e ., python -m unittest discover -s tests, python -m build"; default->"dotnet restore, dotnet build, dotnet test"; }; }
}
