package org.organisation.ods.contracts;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises hostile JSON at the transport and signature boundaries. Exigences : DEVAI-014, DEVAI-030. */
class JsonTest {
    /** Rejects ambiguous duplicate properties before signature interpretation. Exigences : DEVAI-030. */
    @Test void duplicateKeys() { assertThrows(OdsException.class, () -> Json.parse("{\"allow\":true,\"allow\":false}")); }
    /** Rejects fields that could silently bypass a versioned contract. Exigences : DEVAI-030. */
    @Test void unknownFields() { assertThrows(OdsException.class, () -> Json.fields(Json.parse("{\"unknown\":1}"), Set.of("known"))); }
    /** Keeps mandatory strings distinct from null, numbers and empty values. Exigences : DEVAI-117. */
    @Test void requiredText() { for (String value : new String[]{"null","42","\" \""}) assertThrows(OdsException.class, () -> Json.text(Json.parse("{\"key\":"+value+"}"), "key")); }
    /** Stops deeply nested inputs before they exhaust the parser stack. Exigences : DEVAI-014. */
    @Test void depthLimit() { assertThrows(OdsException.class, () -> Json.parse("[".repeat(70)+"0"+"]".repeat(70))); }
}
