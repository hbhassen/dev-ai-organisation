package org.organisation.ods.ai;

/**
 * Immutable, server-resolved provider and model configuration selected from the
 * signed project policy and managed deployment registry. Exigences : DEVAI-064,
 * DEVAI-189, DEVAI-190, DEVAI-191, DEVAI-193.
 */
public record ModelSelection(String providerId, String model, String backend, String apiStyle,
        String apiBase, String credentialRef, boolean generation, boolean tools,
        boolean streaming, boolean images, boolean usage, long providerTokenQuota,
        long maxContextTokens, int maxOutputTokens) {
    /** Rejects a selection whose configured context cannot reserve any output. Exigences : DEVAI-193. */
    public ModelSelection {
        if (providerTokenQuota < 0 || maxContextTokens < 2 || maxOutputTokens < 1 || maxOutputTokens >= maxContextTokens)
            throw new IllegalArgumentException("Invalid provider token limits");
    }
    /** Preserves the original server adapter constructor for tests and legacy Mistral callers. Exigences : DEVAI-064, DEVAI-196. */
    public ModelSelection(String providerId,String model,String backend,String apiStyle,String apiBase,String credentialRef,
            boolean generation,boolean tools,boolean streaming,boolean images,boolean usage,long providerTokenQuota) {
        this(providerId,model,backend,apiStyle,apiBase,credentialRef,generation,tools,streaming,images,usage,providerTokenQuota,128_000,4_000);
    }
    /** Preserves the original server adapter constructor for tests and legacy Mistral callers. Exigences : DEVAI-064, DEVAI-196. */
    public ModelSelection(String providerId,String model,String backend,String apiStyle,String apiBase,String credentialRef,
            boolean generation,boolean tools,boolean streaming,boolean images,boolean usage) {
        this(providerId,model,backend,apiStyle,apiBase,credentialRef,generation,tools,streaming,images,usage,Long.MAX_VALUE,128_000,4_000);
    }
}
