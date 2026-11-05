package org.organisation.ods.ai;
import org.organisation.ods.Settings;
import org.organisation.ods.integration.RemoteHttp;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

/** Routes signed provider selections to the preserved Mistral API or isolated Vibe generic adapter. Exigences : DEVAI-035, DEVAI-064, DEVAI-170, DEVAI-190, DEVAI-191. */
@Component
public final class MistralProvider implements ModelProvider {
    private final Settings settings; private final RemoteHttp http;
    /** Injects only server-side model settings and the constrained HTTP transport. Exigences : DEVAI-064. */
    public MistralProvider(Settings settings,RemoteHttp http) { this.settings=settings; this.http=http; }
    /** Calls one approved deployment model; client model names/endpoints are never forwarded. Exigences : DEVAI-064, DEVAI-075. */
    @Override public JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput) {
        if(settings.get("llm-transport").equals("VIBE_CLI")) return new VibeProvider(settings).complete(messages,tools,maxOutput);
        Map<String,Object> payload=new LinkedHashMap<>(); payload.put("model",settings.require("mistral-model")); payload.put("messages",messages); payload.put("max_tokens",Math.min(maxOutput,4000)); payload.put("stream",false);
        if(!tools.isEmpty()) { payload.put("tools",tools); payload.put("tool_choice","auto"); payload.put("parallel_tool_calls",false); }
        URI uri=URI.create(settings.origin("mistral-url").toString().replaceAll("/$","")+"/v1/chat/completions");
        return http.json("POST",uri,Map.of("Authorization","Bearer "+settings.require("mistral-key")),payload);
    }
    /** Calls the provider selected by signed policy; generic providers can only use the packaged Vibe bridge. Exigences : DEVAI-064, DEVAI-170, DEVAI-190, DEVAI-191. */
    @Override public JsonNode complete(ModelSelection selection,List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput) {
        if(selection==null||selection.providerId().equals("MISTRAL")) {
            if(settings.get("llm-transport").equals("VIBE_CLI")) return new VibeProvider(settings).complete(selection==null?legacySelection():selection,messages,tools,maxOutput);
            return complete(messages,tools,maxOutput);
        }
        if(!selection.backend().equals("generic")) throw new org.organisation.ods.contracts.OdsException("MODEL_NOT_ALLOWED","Backend fournisseur non qualifié");
        return new VibeProvider(settings).complete(selection,messages,tools,maxOutput);
    }
    /** Requests real Mistral SSE while returning fully reconstructed tools and measured usage. Exigences : DEVAI-078, DEVAI-153. */
    @Override public JsonNode stream(List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput,java.util.function.Consumer<String> progress) {
        if(settings.get("llm-transport").equals("VIBE_CLI")) return new VibeProvider(settings).complete(messages,tools,maxOutput);
        Map<String,Object> payload=new LinkedHashMap<>(); payload.put("model",settings.require("mistral-model")); payload.put("messages",messages); payload.put("max_tokens",Math.min(maxOutput,4000)); payload.put("stream",true);
        if(!tools.isEmpty()) { payload.put("tools",tools); payload.put("tool_choice","auto"); payload.put("parallel_tool_calls",false); }
        MistralStream collector=new MistralStream(progress);
        http.events(URI.create(settings.origin("mistral-url").toString().replaceAll("/$","")+"/v1/chat/completions"),Map.of("Authorization","Bearer "+settings.require("mistral-key")),payload,collector::accept);
        return collector.finish();
    }
    /** Streams the existing Mistral transport or returns a normalized Vibe response for a managed generic provider. Exigences : DEVAI-078, DEVAI-170, DEVAI-191, DEVAI-193. */
    @Override public JsonNode stream(ModelSelection selection,List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput,java.util.function.Consumer<String> progress) {
        if(selection==null) return stream(messages,tools,maxOutput,progress);
        if(selection.providerId().equals("MISTRAL")) {
            if(settings.get("llm-transport").equals("VIBE_CLI")) return new VibeProvider(settings).complete(selection,messages,tools,maxOutput);
            return stream(messages,tools,maxOutput,progress);
        }
        if(!selection.backend().equals("generic")) throw new org.organisation.ods.contracts.OdsException("MODEL_NOT_ALLOWED","Backend fournisseur non qualifié");
        return new VibeProvider(settings).complete(selection,messages,tools,maxOutput);
    }
    /** Reconstructs the legacy Mistral choice for existing calls without signed model metadata. Exigences : DEVAI-064, DEVAI-196. */
    private ModelSelection legacySelection() { return new ModelSelection("MISTRAL",settings.require("mistral-model"),"mistral","openai",settings.origin("vibe-url").toString(),settings.get("vibe-auth").equals("CLI_TOKEN")?"ODS_VIBE_TOKEN":"ODS_MISTRAL_API_KEY",true,true,true,false,true); }
}
