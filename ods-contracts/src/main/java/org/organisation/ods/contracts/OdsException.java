package org.organisation.ods.contracts;

/** Carries a safe, stable error code across IPC and REST without leaking causes. Exigences : DEVAI-011, DEVAI-118. */
public final class OdsException extends RuntimeException {
    private final String code;
    /** Defines a client-safe failure; internal diagnostics must be recorded separately. Exigences : DEVAI-118. */
    public OdsException(String code, String message) { super(message); this.code = code; }
    /** Returns the stable code used to select the translated UI message. Exigences : DEVAI-011. */
    public String code() { return code; }
}
