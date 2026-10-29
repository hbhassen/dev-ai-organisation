package org.organisation.ods.ai;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Stable server-side port for future approved providers; only Mistral is registered initially. Exigences : DEVAI-064. */
public interface ModelProvider {
    /** Generates one response with bounded output and structured tools using a configured model. Exigences : DEVAI-064, DEVAI-153. */
    JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput);
    /** Streams optional presentation text while retaining an authoritative complete response for tools and accounting. Exigences : DEVAI-078. */
    default JsonNode stream(List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput,java.util.function.Consumer<String> progress) { return complete(messages,tools,maxOutput); }
}
