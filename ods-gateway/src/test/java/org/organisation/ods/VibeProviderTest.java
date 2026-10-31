package org.organisation.ods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.organisation.ods.ai.VibeProvider;
import org.organisation.ods.contracts.OdsException;
import org.springframework.mock.env.MockEnvironment;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests the real Java-to-Python private transport without running any external model or using host credentials. Exigences : DEVAI-170, DEVAI-176, DEVAI-177. */
class VibeProviderTest {
    @TempDir Path temporary;
    /** Builds an isolated provider configuration with only synthetic credentials for either supported Vibe mode. Exigences : DEVAI-170, DEVAI-177. */
    private Settings settings(String mode) {
        return new Settings(new MockEnvironment().withProperty("ods.runtime-directory",temporary.toString()).withProperty("ods.python-command",System.getProperty("os.name").startsWith("Windows")?"py":"python3.12").withProperty("ods.vibe-url","https://mistral.invalid/v1").withProperty("ods.mistral-model","synthetic-model").withProperty("ods.vibe-auth",mode).withProperty("ods.mistral-key","synthetic-api").withProperty("ods.vibe-token","synthetic-cli"));
    }
    /** Confirms credential selection and stdin framing while asserting that no secret appears in process arguments or inherited service environment. Exigences : DEVAI-170, DEVAI-176. */
    @Test void privateBridgeSelectsApiKeyAndCliCredential() throws Exception {
        for(String mode:List.of("API_KEY","CLI_TOKEN")) {
            String expected=mode.equals("API_KEY")?"synthetic-api":"synthetic-cli";
            Files.writeString(temporary.resolve("vibe_bridge.py"),"""
                import json, os, sys
                request = json.load(sys.stdin)
                assert request['credential'] == '%s'
                assert request['model'] == 'synthetic-model'
                assert len(sys.argv) == 1
                assert not any(key.startswith(('ODS_', 'MISTRAL_', 'VIBE_')) for key in os.environ)
                print(json.dumps({'choices': [{'message': {'role': 'assistant', 'content': 'Synthetic bridge answer'}, 'finish_reason': 'stop'}], 'usage': {'prompt_tokens': 3, 'completion_tokens': 2, 'total_tokens': 5}}))
                """.formatted(expected));
            var result=new VibeProvider(settings(mode)).complete(List.of(Map.of("role","user","content","Test")),List.of(),100);
            assertEquals("Synthetic bridge answer",result.path("choices").path(0).path("message").path("content").asText());
            assertEquals(5,result.path("usage").path("total_tokens").asInt());
        }
    }
    /** Keeps private child failures out of public gateway errors, including text written to stderr. Exigences : DEVAI-176. */
    @Test void childFailureDoesNotDiscloseCredentials() throws Exception {
        Files.writeString(temporary.resolve("vibe_bridge.py"),"import sys\nsys.stderr.write('synthetic-api private diagnostic')\nsys.exit(1)\n");
        var error=assertThrows(OdsException.class,()->new VibeProvider(settings("API_KEY")).complete(List.of(),List.of(),10));
        assertEquals("DEPENDENCY_UNAVAILABLE",error.code()); assertFalse(error.getMessage().contains("synthetic-api"));
    }
    /** Maps only reviewed bridge failure codes while keeping provider bodies and credentials private. Exigences : DEVAI-170, DEVAI-176, DEVAI-177. */
    @Test void structuredFailuresAreAllowlisted() throws Exception {
        for(String code:List.of("MODEL_AUTHENTICATION_FAILED","MODEL_RATE_LIMITED","MODEL_UNAVAILABLE","VIBE_RUNTIME_UNAVAILABLE","UNTRUSTED_CODE")) {
            Files.writeString(temporary.resolve("vibe_bridge.py"),"import json,sys\nprint(json.dumps({'error':{'code':'"+code+"','message':'synthetic-api private body'}}))\nsys.exit(1)\n");
            var error=assertThrows(OdsException.class,()->new VibeProvider(settings("API_KEY")).complete(List.of(),List.of(),10));
            assertEquals(code.startsWith("MODEL_")?code:"DEPENDENCY_UNAVAILABLE",error.code());
            assertFalse(error.getMessage().contains("synthetic-api")); assertFalse(error.getMessage().contains("private body"));
            if(code.startsWith("MODEL_")) assertFalse(error.getMessage().contains("Python"));
        }
    }
}
