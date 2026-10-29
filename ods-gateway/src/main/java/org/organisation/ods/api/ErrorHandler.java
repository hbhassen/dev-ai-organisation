package org.organisation.ods.api;
import org.organisation.ods.contracts.OdsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;

/** Converts internal exceptions into redacted, stable REST errors. Exigences : DEVAI-011, DEVAI-118. */
@RestControllerAdvice
public final class ErrorHandler {
    /** Returns a safe domain error and correlation identifier without stack traces. Exigences : DEVAI-118. */
    @ExceptionHandler(OdsException.class) public ResponseEntity<?> domain(OdsException error) {
        int status=switch(error.code()) { case "AUTHENTICATION_REQUIRED"->401; case "ACTION_DENIED","MODEL_NOT_ALLOWED","PROJECT_ACCESS_DENIED","POLICY_INVALID","POLICY_EXPIRED","POLICY_REPLAYED","POLICY_CONFLICT"->403; case "NOT_FOUND"->404; case "IDEMPOTENCY_CONFLICT","CONFLICT","STALE_BASE"->409; case "QUOTA_EXCEEDED","LIMIT_REACHED","RATE_LIMITED"->429; case "DEPENDENCY_UNAVAILABLE","CONFIGURATION_INVALID"->503; default->400; };
        return ResponseEntity.status(status).body(Map.of("code",error.code(),"message",error.getMessage(),"correlationId",UUID.randomUUID().toString(),"retryable",status==503));
    }
    /** Prevents unexpected library/database details from escaping into a client response. Exigences : DEVAI-118. */
    @ExceptionHandler(Exception.class) public ResponseEntity<?> unexpected(Exception error) { return ResponseEntity.internalServerError().body(Map.of("code","INTERNAL_ERROR","message","Erreur interne ; consulter l'exploitant","correlationId",UUID.randomUUID().toString(),"retryable",false)); }
}
