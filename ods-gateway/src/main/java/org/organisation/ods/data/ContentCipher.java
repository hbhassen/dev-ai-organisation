package org.organisation.ods.data;
import org.organisation.ods.Settings;
import org.organisation.ods.contracts.OdsException;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Encrypts persisted assistant content and checkpoints with authenticated, context-bound AES-GCM. Exigences : DEVAI-067, DEVAI-121. */
@Component
public final class ContentCipher {
    private final SecretKeySpec key; private final SecureRandom random=new SecureRandom();
    /** Requires an externally managed 256-bit key in production; local sessions use an ephemeral key when unset. Exigences : DEVAI-035, DEVAI-127. */
    public ContentCipher(Settings settings,Environment environment) {
        String configured=settings.get("data-key"); byte[] bytes;
        if(configured.isBlank()) {
            if(Arrays.asList(environment.getActiveProfiles()).contains("production")) throw new IllegalStateException("ODS_DATA_KEY_BASE64 required in production");
            bytes=new byte[32]; random.nextBytes(bytes);
        } else { try { bytes=Base64.getDecoder().decode(configured); } catch(IllegalArgumentException e) { throw new IllegalStateException("Invalid data encryption key"); } }
        if(bytes.length!=32) throw new IllegalStateException("Data encryption key must contain 32 bytes"); key=new SecretKeySpec(bytes,"AES");
    }
    /** Encrypts a payload with a fresh nonce and binds it to its job or conversation identity. Exigences : DEVAI-067. */
    public String seal(String content,String identity) {
        try {
            byte[] nonce=new byte[12]; random.nextBytes(nonce); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce)); cipher.updateAAD(identity.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(content.getBytes(StandardCharsets.UTF_8)), combined=new byte[nonce.length+encrypted.length]; System.arraycopy(nonce,0,combined,0,nonce.length); System.arraycopy(encrypted,0,combined,nonce.length,encrypted.length);
            return "gcm1:"+Base64.getEncoder().encodeToString(combined);
        } catch(Exception e) { throw new IllegalStateException("Content encryption unavailable"); }
    }
    /** Rejects tampering, cross-identity copies and a lost local key without exposing ciphertext details. Exigences : DEVAI-067, DEVAI-118. */
    public String open(String stored,String identity) {
        try {
            if(!stored.startsWith("gcm1:")) throw new IllegalArgumentException(); byte[] combined=Base64.getDecoder().decode(stored.substring(5)); if(combined.length<28) throw new IllegalArgumentException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(combined,0,12))); cipher.updateAAD(identity.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Arrays.copyOfRange(combined,12,combined.length)),StandardCharsets.UTF_8);
        } catch(Exception e) { throw new OdsException("SESSION_UNAVAILABLE","Contenu expiré, altéré ou clé de session indisponible"); }
    }
}
