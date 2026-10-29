package org.organisation.ods.ai;
import org.organisation.ods.contracts.*;
import org.organisation.ods.security.Secrets;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.function.Consumer;

/** Reassembles real Mistral deltas and fragmented tool arguments before granting any tool execution. Exigences : DEVAI-078, DEVAI-153. */
public final class MistralStream {
    private final StringBuilder content=new StringBuilder(); private final SortedMap<Integer,Map<String,Object>> calls=new TreeMap<>();
    private JsonNode usage=Json.parse("{}"); private final Consumer<String> progress; private int published;
    /** Injects a presentation-only callback that cannot execute an unvalidated partial tool call. Exigences : DEVAI-078. */
    public MistralStream(Consumer<String> progress) { this.progress=progress; }
    /** Accumulates text and tools while withholding a trailing buffer and rejecting obvious credentials. Exigences : DEVAI-037, DEVAI-078. */
    public void accept(JsonNode event) {
        if(event.path("usage").isObject()) usage=event.path("usage"); JsonNode delta=event.path("choices").path(0).path("delta");
        if(delta.path("content").isTextual()) content.append(delta.path("content").asText());
        if(content.length()>120000) throw new OdsException("RESPONSE_TOO_LARGE","Réponse trop volumineuse"); Secrets.check(content.toString());
        if(content.length()-published>768) { published=content.length()-512; progress.accept(content.substring(0,published)); }
        for(JsonNode call:delta.path("tool_calls")) {
            int index=call.path("index").asInt(-1); if(index<0||index>7) throw new OdsException("INVALID_RESPONSE","Index d'outil invalide");
            Map<String,Object> accumulated=calls.computeIfAbsent(index,k->new LinkedHashMap<>());
            if(call.hasNonNull("id")) accumulated.put("id",call.path("id").asText());
            accumulated.put("type","function");
            @SuppressWarnings("unchecked") Map<String,Object> function=(Map<String,Object>)accumulated.computeIfAbsent("function",k->new LinkedHashMap<>());
            if(call.path("function").hasNonNull("name")) function.put("name",call.path("function").path("name").asText());
            function.put("arguments",function.getOrDefault("arguments","").toString()+call.path("function").path("arguments").asText(""));
        }
    }
    /** Produces a complete provider DTO only after the transport receives the terminal SSE marker. Exigences : DEVAI-075, DEVAI-153. */
    public JsonNode finish() {
        Secrets.check(content.toString()); if(!content.isEmpty()) progress.accept(content.toString());
        Map<String,Object> message=new LinkedHashMap<>(); message.put("role","assistant"); message.put("content",content.toString()); if(!calls.isEmpty()) message.put("tool_calls",List.copyOf(calls.values()));
        return Json.MAPPER.valueToTree(Map.of("choices",List.of(Map.of("message",message)),"usage",usage));
    }
}
