package org.organisation.ods.security;

import org.organisation.ods.contracts.OdsException;
import java.util.regex.Pattern;

/** Filters common credential patterns before context transfer and diagnostic display. Exigences : DEVAI-037, DEVAI-132. */
public final class Secrets {
    private static final Pattern SENSITIVE = Pattern.compile("(?is)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|(?:gh[pousr]_|glpat-|sk-)[A-Za-z0-9_-]{20,}|(?:api[_-]?key|password|secret|access[_-]?token)\\s*[:=]\\s*[\"']?(?!<|\\$\\{|change.me|example|placeholder)[A-Za-z0-9_+/=-]{12,}");
    /** Prevents construction of the stateless filter. Exigences : DEVAI-037. */
    private Secrets() { }
    /** Rejects a context carrying an obvious credential rather than silently leaking it. Exigences : DEVAI-037. */
    public static void check(String text) {
        if (contains(text)) throw new OdsException("CONTEXT_REJECTED", "Contenu sensible détecté");
    }
    /** Reports a sensitive match without returning the credential itself to an upstream tool. Exigences : DEVAI-037, DEVAI-110. */
    public static boolean contains(String text) { return text!=null&&(SENSITIVE.matcher(text).find()||Pattern.compile("https?://[^\\s/@]+:[^\\s/@]+@").matcher(text).find()); }
    /** Masks credential-shaped strings in bounded process output. Exigences : DEVAI-132. */
    public static String redact(String text) {
        if(text==null) return ""; String result=SENSITIVE.matcher(text).replaceAll("<REDACTED>").replaceAll("https?://[^\\s/@]+@","https://<REDACTED>@");
        String home=System.getProperty("user.home",""); if(!home.isBlank()) result=result.replace(home,"<USER_HOME>").replace(home.replace('\\','/'),"<USER_HOME>"); return result;
    }
}
