package org.organisation.ods.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.organisation.ods.contracts.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Converts CycloneDX to the Graph Scan contract used by scan-sbom-xray, preserving disconnected dependencies. Exigences : DEVAI-173. */
public final class XrayGraph {
    /** Prevents utility construction because conversion has no mutable shared state. Exigences : DEVAI-173. */
    private XrayGraph() {}
    /** Builds a bounded cycle-safe graph and refuses unsupported or unresolved identities instead of silently omitting them. Exigences : DEVAI-173. */
    public static Map<String,Object> convert(JsonNode bom,String stack) {
        if(!bom.path("bomFormat").asText().equals("CycloneDX")||!bom.path("components").isArray()) throw new OdsException("INVALID_SBOM","Document CycloneDX requis");
        Map<String,String> ids=new LinkedHashMap<>(); Map<String,List<String>> edges=new HashMap<>();
        for(JsonNode component:bom.path("components")) {
            String ref=component.path("bom-ref").asText();
            if(ref.isBlank()||ids.putIfAbsent(ref,identity(component,stack))!=null) throw new OdsException("INVALID_SBOM","Identité de composant absente ou dupliquée");
        }
        if(ids.isEmpty()||ids.size()>10000) throw new OdsException("INVALID_SBOM","Nombre de composants non pris en charge");
        for(JsonNode dependency:bom.path("dependencies")) { List<String> children=new ArrayList<>(); dependency.path("dependsOn").forEach(child->children.add(child.asText())); edges.put(dependency.path("ref").asText(),children); }
        Set<String> visited=new HashSet<>(); List<Object> children=new ArrayList<>(); int[] count={0};
        String metadata=bom.path("metadata").path("component").path("bom-ref").asText();
        for(String ref:edges.getOrDefault(metadata,List.of())) if(ids.containsKey(ref)) children.add(node(ref,ids,edges,new HashSet<>(),visited,count));
        for(String ref:ids.keySet()) if(!visited.contains(ref)) children.add(node(ref,ids,edges,new HashSet<>(),visited,count));
        return Map.of("component_id","root-node","nodes",children);
    }
    /** Expands shared dependencies without infinite cycles or exponential unbounded payloads. Exigences : DEVAI-173. */
    private static Map<String,Object> node(String ref,Map<String,String> ids,Map<String,List<String>> edges,Set<String> ancestors,Set<String> visited,int[] count) {
        if(++count[0]>50000||ancestors.size()>100) throw new OdsException("INVALID_SBOM","Graphe de dépendances trop volumineux");
        visited.add(ref); List<Object> children=new ArrayList<>();
        if(ancestors.add(ref)) { for(String child:edges.getOrDefault(ref,List.of())) if(ids.containsKey(child)) children.add(node(child,ids,edges,new HashSet<>(ancestors),visited,count)); }
        return Map.of("component_id",ids.get(ref),"nodes",children);
    }
    /** Maps resolved PURLs or explicit stack fields to JFrog component identities, including scoped npm packages. Exigences : DEVAI-173. */
    private static String identity(JsonNode component,String stack) {
        String name=component.path("name").asText(),group=component.path("group").asText(),version=component.path("version").asText();
        String purl=component.path("purl").asText();
        if(purl.startsWith("pkg:")) {
            String base=purl.substring(4).split("[?#]",2)[0]; int slash=base.indexOf('/'),at=base.lastIndexOf('@');
            if(slash<1||at<=slash) throw new OdsException("INVALID_SBOM","PURL sans version résolue");
            String type=base.substring(0,slash),path=decode(base.substring(slash+1,at)); version=decode(base.substring(at+1));
            int separator=path.lastIndexOf('/'); group=separator<0?"":path.substring(0,separator); name=separator<0?path:path.substring(separator+1);
            stack=switch(type) { case "maven"->"JAVA"; case "npm"->"ANGULAR"; case "pypi"->"PYTHON"; case "nuget"->"DOTNET"; default->throw new OdsException("INVALID_SBOM","Écosystème Xray non pris en charge"); };
        }
        if(name.isBlank()||version.isBlank()||version.matches(".*[<>*\\s].*")) throw new OdsException("INVALID_SBOM","Version résolue requise pour chaque dépendance");
        return switch(stack) { case "JAVA"->{ if(group.isBlank()) throw new OdsException("INVALID_SBOM","Groupe Maven absent"); yield "gav://"+group+":"+name+":"+version; } case "ANGULAR"->"npm://"+(group.isBlank()?"":group+"/")+name+":"+version; case "PYTHON"->"pypi://"+name+":"+version; case "DOTNET"->"nuget://"+name+":"+version; default->throw new OdsException("INVALID_SBOM","Stack inconnue"); };
    }
    /** Decodes PURL percent escapes while preserving literal plus signs in package versions. Exigences : DEVAI-173. */
    private static String decode(String value) { return URLDecoder.decode(value.replace("+","%2B"),StandardCharsets.UTF_8); }
}
