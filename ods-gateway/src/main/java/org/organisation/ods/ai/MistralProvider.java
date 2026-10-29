package org.organisation.ods.ai;
import org.organisation.ods.Settings;
import org.organisation.ods.integration.RemoteHttp;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

/** Implements the official Mistral chat API without exposing provider credentials to clients. Exigences : DEVAI-035, DEVAI-064, DEVAI-153. */
@Component
public final class MistralProvider implements ModelProvider {
    private final Settings settings; private final RemoteHttp http;
    /** Injects only server-side model settings and the constrained HTTP transport. Exigences : DEVAI-064. */
    public MistralProvider(Settings settings,RemoteHttp http) { this.settings=settings; this.http=http; }
    /** Calls one approved deployment model; client model names/endpoints are never forwarded. Exigences : DEVAI-064, DEVAI-075. */
    @Override public JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput) {
        Map<String,Object> payload=new LinkedHashMap<>(); payload.put("model",settings.require("mistral-model")); payload.put("messages",messages); payload.put("max_tokens",Math.min(maxOutput,4000)); payload.put("stream",false);
        if(!tools.isEmpty()) { payload.put("tools",tools); payload.put("tool_choice","auto"); payload.put("parallel_tool_calls",false); }
        URI uri=URI.create(settings.origin("mistral-url").toString().replaceAll("/$","")+"/v1/chat/completions");
        return http.json("POST",uri,Map.of("Authorization","Bearer "+settings.require("mistral-key")),payload);
    }
    /** Requests real Mistral SSE while returning fully reconstructed tools and measured usage. Exigences : DEVAI-078, DEVAI-153. */
    @Override public JsonNode stream(List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput,java.util.function.Consumer<String> progress) {
        Map<String,Object> payload=new LinkedHashMap<>(); payload.put("model",settings.require("mistral-model")); payload.put("messages",messages); payload.put("max_tokens",Math.min(maxOutput,4000)); payload.put("stream",true);
        if(!tools.isEmpty()) { payload.put("tools",tools); payload.put("tool_choice","auto"); payload.put("parallel_tool_calls",false); }
        MistralStream collector=new MistralStream(progress);
        http.events(URI.create(settings.origin("mistral-url").toString().replaceAll("/$","")+"/v1/chat/completions"),Map.of("Authorization","Bearer "+settings.require("mistral-key")),payload,collector::accept);
        return collector.finish();
    }
}
