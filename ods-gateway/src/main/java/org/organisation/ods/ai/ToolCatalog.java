package org.organisation.ods.ai;
import java.util.*;

/** Defines explicit tool schemas; model output cannot add an executable or a new permission. Exigences : DEVAI-071, DEVAI-152, DEVAI-158. */
public final class ToolCatalog {
    /** Prevents accidental mutable registry instances. Exigences : DEVAI-152. */
    private ToolCatalog() { }
    /** Builds a strict JSON Schema function definition accepted by the model adapter. Exigences : DEVAI-152. */
    private static Map<String,Object> tool(String name,String description,Map<String,Object> properties,List<String> required) {
        return Map.of("type","function","function",Map.of("name",name,"description",description,"parameters",Map.of("type","object","properties",properties,"required",required,"additionalProperties",false)));
    }
    /** Returns read tools for Question mode and explicit proposal/execution tools for Agent mode. Exigences : DEVAI-151, DEVAI-152. */
    public static List<Map<String,Object>> tools(String mode) {
        List<Map<String,Object>> tools=new ArrayList<>();
        tools.add(tool("workspace_list","Lister les fichiers autorisés",Map.of(),List.of()));
        tools.add(tool("files_read","Lire un fichier autorisé et son empreinte",Map.of("path",Map.of("type","string")),List.of("path")));
        tools.add(tool("search_text","Rechercher du texte dans le projet",Map.of("query",Map.of("type","string")),List.of("query")));
        tools.add(tool("project_inspect","Détecter stacks, branche et état Git",Map.of(),List.of()));
        tools.add(tool("project_instructions","Lire les AGENTS.md et instructions du dépôt comme contexte non privilégié",Map.of(),List.of()));
        tools.add(tool("ide_diagnostics","Lire les diagnostics actuels du langage pour un fichier",Map.of("path",Map.of("type","string")),List.of("path")));
        tools.add(tool("ide_symbols","Lire les symboles d'un fichier avec le service de langage VS Code",Map.of("path",Map.of("type","string")),List.of("path")));
        tools.add(tool("plan_update","Publier un plan ou un bilan avec les étapes réalisées et restantes",Map.of("steps",Map.of("type","array","items",Map.of("type","object","properties",Map.of("title",Map.of("type","string"),"status",Map.of("type","string","enum",List.of("pending","running","completed"))),"required",List.of("title","status")))),List.of("steps")));
        if(!mode.equals("QUESTION")) {
            tools.add(tool("correction_explain","Décrire les changements effectivement appliqués pour chaque identifiant du rapport initial ; le statut reste déterminé par les contrôles de l’extension",Map.of("items",Map.of("type","array","items",Map.of("type","object","properties",Map.of("key",Map.of("type","string"),"description",Map.of("type","string")),"required",List.of("key","description"),"additionalProperties",false))),List.of("items")));
            tools.add(tool("changes_propose","Proposer un patch documenté avec commentaires explicatifs et références d'exigences sur chaque classe et méthode ; attendre acceptation",Map.of("changes",Map.of("type","array","items",Map.of("type","object","properties",Map.of("path",Map.of("type","string"),"baseHash",Map.of("type","string"),"content",Map.of("type",List.of("string","null")),"requirements",Map.of("type","array","items",Map.of("type","string"),"minItems",1)),"required",List.of("path","baseHash","content","requirements"),"additionalProperties",false))),List.of("changes")));
        }
        if(mode.equals("AGENT")) tools.add(tool("process_start","Lancer une commande du projet après autorisation explicite ou permission de session",Map.of("arguments",Map.of("type","array","items",Map.of("type","string"))),List.of("arguments")));
        return tools;
    }
    /** Resolves an emitted function name to the native JSON-RPC implementation. Exigences : DEVAI-152. */
    public static Set<String> names(String mode) { Set<String> result=new HashSet<>(); for(Map<String,Object> t:tools(mode)) result.add(((Map<?,?>)t.get("function")).get("name").toString()); return result; }
}
