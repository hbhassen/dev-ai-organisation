package org.organisation.ods.ai;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Stable server-side port for deployment-approved providers resolved from signed project policy. Exigences : DEVAI-064, DEVAI-189, DEVAI-190. */
public interface ModelProvider {
    /** Generates one response with bounded output and structured tools using a configured model. Exigences : DEVAI-064, DEVAI-153. */
    JsonNode complete(List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput);
    /** Generates only with a server-resolved selection; legacy providers reject unimplemented provider IDs by default. Exigences : DEVAI-064, DEVAI-190, DEVAI-191. */
    default JsonNode complete(ModelSelection selection,List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput) {
        if(selection!=null&&!selection.providerId().equals("MISTRAL")) throw new org.organisation.ods.contracts.OdsException("MODEL_NOT_ALLOWED","Fournisseur IA non pris en charge");
        return complete(messages,tools,maxOutput);
    }
    /** Streams optional presentation text while retaining an authoritative complete response for tools and accounting. Exigences : DEVAI-078. */
    default JsonNode stream(List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput,java.util.function.Consumer<String> progress) { return complete(messages,tools,maxOutput); }
    /** Preserves the resolved provider choice through optional streaming without accepting a client-supplied endpoint. Exigences : DEVAI-064, DEVAI-191, DEVAI-193. */
    default JsonNode stream(ModelSelection selection,List<Map<String,Object>> messages,List<Map<String,Object>> tools,int maxOutput,java.util.function.Consumer<String> progress) {
        if(selection!=null&&!selection.providerId().equals("MISTRAL")) throw new org.organisation.ods.contracts.OdsException("MODEL_NOT_ALLOWED","Fournisseur IA non pris en charge");
        return stream(messages,tools,maxOutput,progress);
    }
}
