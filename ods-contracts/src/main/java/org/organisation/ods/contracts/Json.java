package org.organisation.ods.contracts;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.Set;

/** Provides bounded duplicate-rejecting JSON for signed documents and IPC. Exigences : DEVAI-014, DEVAI-030. */
public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(2_097_152).build()).build()).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    /** Prevents construction of the shared codec. Exigences : DEVAI-014. */
    private Json() { }
    /** Parses bounded UTF-8 JSON; malformed inputs receive a safe error. Exigences : DEVAI-030. */
    public static JsonNode parse(String text) {
        if (text == null || text.length() > 8_388_608) throw new OdsException("INVALID_JSON", "Document trop volumineux ou absent");
        try { JsonNode node=MAPPER.readTree(text); if(node==null) throw new IllegalArgumentException(); return node; } catch (Exception e) { throw new OdsException("INVALID_JSON", "Document JSON invalide"); }
    }
    /** Serializes primitive DTOs without exposing implementation exceptions. Exigences : DEVAI-117. */
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); } catch (Exception e) { throw new OdsException("SERIALIZATION_FAILED", "Réponse non sérialisable"); }
    }
    /** Converts internal JSON into transport-neutral maps for Spring's codec. Exigences : DEVAI-004. */
    public static Map<String, Object> map(JsonNode node) { return MAPPER.convertValue(node, new TypeReference<Map<String,Object>>() { }); }
    /** Rejects unknown fields in security-critical objects. Exigences : DEVAI-030. */
    public static void fields(JsonNode node, Set<String> allowed) {
        if (!node.isObject()) throw new OdsException("INVALID_SCHEMA", "Un objet JSON est requis");
        node.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw new OdsException("INVALID_SCHEMA", "Champ inconnu dans le document"); });
    }
    /** Reads a mandatory nonblank string with a strict length limit. Exigences : DEVAI-117. */
    public static String text(JsonNode node, String key) {
        if (!node.path(key).isTextual() || node.path(key).asText().isBlank() || node.path(key).asText().length() > 4096)
            throw new OdsException("INVALID_REQUEST", "Paramètre requis invalide : " + key);
        return node.path(key).asText();
    }
}
