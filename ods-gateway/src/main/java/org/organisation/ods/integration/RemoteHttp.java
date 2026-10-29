package org.organisation.ods.integration;
import org.organisation.ods.contracts.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** HTTP adapter with TLS verification, no redirects and no automatic replay of side effects. Exigences : DEVAI-036, DEVAI-080, DEVAI-125. */
@Component
public final class RemoteHttp {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    /** Sends a bounded response request without including provider bodies in public error messages. Exigences : DEVAI-118, DEVAI-125. */
    public JsonNode json(String method,URI uri,Map<String,String> headers,Object body) {
        return exchange(method,uri,headers,body).body();
    }
    /** Retains accepted/pending status so asynchronous providers are not mistaken for completed scans. Exigences : DEVAI-173. */
    public record Response(int status,JsonNode body) {}
    /** Sends an authenticated request with a bounded body while preserving HTTP 202 for polling. Exigences : DEVAI-173, DEVAI-176. */
    public Response exchange(String method,URI uri,Map<String,String> headers,Object body) {
        if(!"https".equals(uri.getScheme())) throw new OdsException("CONFIGURATION_INVALID","HTTPS obligatoire");
        try {
            HttpRequest.Builder request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(120)).header("Accept","application/json"); headers.forEach(request::header);
            request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(Json.write(body)));
            if(body!=null) request.header("Content-Type","application/json");
            HttpResponse<java.io.InputStream> response=client.send(request.build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()) {
                byte[] data=stream.readNBytes(8_388_609);
                if(response.statusCode()<200||response.statusCode()>=300) throw new OdsException(response.statusCode()==403||response.statusCode()==401?"PROJECT_ACCESS_DENIED":"DEPENDENCY_UNAVAILABLE","Le service distant a refusé l'opération ("+response.statusCode()+")");
                if(data.length>8_388_608) throw new OdsException("RESPONSE_TOO_LARGE","Réponse distante trop volumineuse");
                return new Response(response.statusCode(),data.length==0?Json.MAPPER.createObjectNode():Json.parse(new String(data,java.nio.charset.StandardCharsets.UTF_8)));
            }
        } catch(OdsException e) { throw e; } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new OdsException("CANCELLED","Opération interrompue"); } catch(Exception e) { throw new OdsException("DEPENDENCY_UNAVAILABLE","Service distant indisponible"); }
    }
    /** Encodes one path or query component; it never accepts a new destination host. Exigences : DEVAI-036. */
    public static String encode(String value) { return URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20"); }
    /** Consumes bounded SSE data lines with a deadline and no redirect or automatic request replay. Exigences : DEVAI-078, DEVAI-125. */
    public void events(URI uri,Map<String,String> headers,Object body,java.util.function.Consumer<JsonNode> consumer) {
        if(!"https".equals(uri.getScheme())) throw new OdsException("CONFIGURATION_INVALID","HTTPS obligatoire");
        try {
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(120)).header("Accept","text/event-stream").header("Content-Type","application/json"); headers.forEach(request::header);
            var response=client.send(request.POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var input=response.body(); var timer=java.util.concurrent.Executors.newSingleThreadScheduledExecutor()) {
                if(response.statusCode()!=200) throw new OdsException("DEPENDENCY_UNAVAILABLE","Flux Mistral indisponible ("+response.statusCode()+")");
                var timeout=timer.schedule(()->{try{input.close();}catch(java.io.IOException ignored){/* Closing a timed-out stream is best effort. */}},120,java.util.concurrent.TimeUnit.SECONDS);
                try {
                    java.io.ByteArrayOutputStream line=new java.io.ByteArrayOutputStream(); int total=0,next;
                    while((next=input.read())!=-1) {
                        if(++total>8_388_608||line.size()>262144) throw new OdsException("RESPONSE_TOO_LARGE","Flux trop volumineux");
                        if(next!='\n') { line.write(next); continue; }
                        String text=line.toString(java.nio.charset.StandardCharsets.UTF_8).trim(); line.reset();
                        if(!text.startsWith("data:")) continue; String data=text.substring(5).trim(); if(data.equals("[DONE]")) return; consumer.accept(Json.parse(data));
                    }
                    throw new OdsException("INCOMPLETE_RESPONSE","Flux interrompu avant la fin confirmée");
                } finally { timeout.cancel(false); timer.shutdownNow(); }
            }
        } catch(OdsException e) { throw e; } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new OdsException("CANCELLED","Flux interrompu"); } catch(Exception e) { throw new OdsException("DEPENDENCY_UNAVAILABLE","Flux fournisseur indisponible"); }
    }
}
