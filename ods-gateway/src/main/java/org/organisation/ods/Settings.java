package org.organisation.ods;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.organisation.ods.contracts.OdsException;
import java.net.URI;

/** Resolves deployment-owned configuration; workspaces cannot override server settings. Exigences : DEVAI-036, DEVAI-127. */
@Component
public final class Settings {
    private final Environment environment;
    /** Injects Spring's deployment environment without copying secrets into DTOs. Exigences : DEVAI-035. */
    public Settings(Environment environment) { this.environment=environment; }
    /** Reads an optional deployment value. Exigences : DEVAI-127. */
    public String get(String key) { return environment.getProperty("ods."+key,""); }
    /** Refuses missing/example configuration rather than contacting an invented service. Exigences : DEVAI-127. */
    public String require(String key) { String value=get(key); if(value.isBlank()||value.contains("<")||value.contains("CHANGE_ME")||value.contains(".example")) throw new OdsException("DEPENDENCY_UNAVAILABLE","Configuration requise : "+key); return value; }
    /** Resolves an HTTPS service origin with no embedded credentials or query. Exigences : DEVAI-036. */
    public URI origin(String key) {
        URI uri=URI.create(require(key));
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null) throw new OdsException("CONFIGURATION_INVALID","Origine HTTPS requise : "+key);
        return uri;
    }
}
