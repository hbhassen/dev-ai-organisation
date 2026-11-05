package org.organisation.ods.ai;

import org.organisation.ods.contracts.Json;
import org.organisation.ods.contracts.OdsException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Applies signed model context and output limits to estimates and reservations. Exigences : DEVAI-065, DEVAI-193, DEVAI-194. */
public final class ModelBudget {
    /** Prevents construction of the stateless budget utility. Exigences : DEVAI-193. */
    private ModelBudget() { }

    /** Estimates input tokens conservatively from UTF-8 bytes and message framing. Exigences : DEVAI-065, DEVAI-193, DEVAI-194. */
    public static long estimateInputTokens(List<Map<String,Object>> messages) {
        long bytes=Json.write(messages).getBytes(StandardCharsets.UTF_8).length;
        return Math.addExact(bytes,Math.multiplyExact(messages.size(),8L));
    }

    /** Restricts a requested output to the selected signed model's output limit. Exigences : DEVAI-193. */
    public static int outputLimit(ModelSelection selection,int requested) {
        if(requested<1) throw new OdsException("INVALID_REQUEST","Limite de sortie invalide");
        return (int)Math.min((long)requested,selection.maxOutputTokens());
    }

    /** Rejects a context estimate that cannot fit alongside its reserved output. Exigences : DEVAI-065, DEVAI-193. */
    public static void requireFits(ModelSelection selection,long estimatedInputTokens,int outputTokens) {
        if(estimatedInputTokens<0||outputTokens<1||outputTokens>selection.maxOutputTokens()
                ||estimatedInputTokens>selection.maxContextTokens()-outputTokens)
            throw new OdsException("CONTEXT_REJECTED","Contexte et sortie dépassent la limite du modèle autorisé");
    }
}
