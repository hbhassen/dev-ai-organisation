package org.organisation.ods;
import org.organisation.ods.ai.MistralStream;
import org.organisation.ods.contracts.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies streamed argument reconstruction and credential rejection before tool execution. Exigences : DEVAI-037, DEVAI-078. */
class MistralStreamTest {
    /** Reassembles a JSON argument split across actual SSE-shaped deltas and retains measured usage. Exigences : DEVAI-075, DEVAI-153. */
    @Test void fragmentedToolArguments() {
        MistralStream stream=new MistralStream(text->{});
        stream.accept(Json.parse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\",\"function\":{\"name\":\"files_read\",\"arguments\":\"{\\\"path\\\":\"}}]}}]}"));
        stream.accept(Json.parse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"pom.xml\\\"}\"}}]}}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":7}}"));
        var response=stream.finish(); assertEquals(7,response.path("usage").path("completion_tokens").asInt());
        String args=response.path("choices").path(0).path("message").path("tool_calls").path(0).path("function").path("arguments").asText(); assertEquals("pom.xml",Json.parse(args).path("path").asText());
    }
    /** Holds a suffix and blocks a synthetic secret before any credential bytes enter presentation events. Exigences : DEVAI-037, DEVAI-078. */
    @Test void streamingSecretBoundary() {
        List<String> published=new ArrayList<>(); MistralStream stream=new MistralStream(published::add);
        assertThrows(OdsException.class,()->stream.accept(Json.MAPPER.valueToTree(Map.of("choices",List.of(Map.of("delta",Map.of("content","glpat-"+"x".repeat(30)))))))); assertTrue(published.isEmpty());
    }
}
