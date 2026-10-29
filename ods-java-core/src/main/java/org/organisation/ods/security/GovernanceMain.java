package org.organisation.ods.security;
import org.organisation.ods.contracts.*;
import org.organisation.ods.config.PromptCatalog;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

/** Provides offline governance signing without distributing a private signing key in the application. Exigences : DEVAI-044, DEVAI-164. */
public final class GovernanceMain {
    /** Generates keys, signs exact manifest bytes or verifies a prompt bundle before administrative publication. Exigences : DEVAI-029, DEVAI-044. */
    public static void main(String[] args) throws Exception {
        if(args.length==2&&args[0].equals("keygen")) {
            Path directory=Path.of(args[1]); Files.createDirectories(directory);
            KeyPair keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            Files.writeString(directory.resolve("signing.key"),Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded()),StandardOpenOption.CREATE_NEW);
            Files.writeString(directory.resolve("public-key.txt"),Base64.getEncoder().encodeToString(keys.getPublic().getEncoded()),StandardOpenOption.CREATE_NEW);
            System.out.println("Ed25519 key pair created. Keep signing.key outside source control and restrict its permissions."); return;
        }
        if(args.length==3&&args[0].equals("sign")) {
            Path manifest=Path.of(args[1]); byte[] bytes=Files.readAllBytes(manifest); Json.parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
            PrivateKey key=KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(Files.readString(Path.of(args[2])).trim())));
            Signature signer=Signature.getInstance("Ed25519"); signer.initSign(key); signer.update(bytes);
            String filename=manifest.getFileName().toString().replaceFirst("\\.json$","")+".sig";
            Files.writeString(manifest.resolveSibling(filename),Base64.getEncoder().encodeToString(signer.sign())); System.out.println("Detached signature written for exact manifest bytes."); return;
        }
        if(args.length==3&&args[0].equals("verify-prompts")) {
            PromptCatalog catalog=new PromptCatalog(Path.of(args[1]),Files.readString(Path.of(args[2])).trim());
            for(var task:catalog.tasks()) catalog.compose(List.of("JAVA","ANGULAR","PYTHON","DOTNET"),task.get("id"));
            System.out.println("Signature, digests, profiles and structured mandatory constraints verified."); return;
        }
        System.err.println("Usage: GovernanceMain keygen <private-directory> | sign <manifest.json> <signing.key> | verify-prompts <bundle-directory> <public-key.txt>"); System.exit(2);
    }
}
