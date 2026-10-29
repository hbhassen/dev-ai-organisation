package org.organisation.ods;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Hosts governed integrations either as an owned local gateway or an explicitly deployed server. Exigences : DEVAI-002, DEVAI-168. */
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class GatewayApplication {
    /** Starts the owned runtime and exits when its extension parent closes stdin. Exigences : DEVAI-168, DEVAI-176. */
    public static void main(String[] args) {
        SpringApplication app=new SpringApplication(GatewayApplication.class);
        boolean embedded="true".equals(System.getenv("ODS_EMBEDDED"));
        if(embedded) app.addListeners((org.springframework.context.ApplicationListener<org.springframework.boot.web.server.context.WebServerInitializedEvent>) event->System.out.println("ODS_READY:"+event.getWebServer().getPort()));
        var context=app.run(args);
        if(embedded) {
            Runtime.getRuntime().addShutdownHook(new Thread(()->ProcessHandle.current().descendants().forEach(ProcessHandle::destroyForcibly),"ods-owned-processes"));
            Thread.ofPlatform().daemon().name("ods-parent-lifetime").start(()->{
                try { while(System.in.read()!=-1) { /* The open pipe proves parent lifetime; no service secret is read here. */ } }
                catch(java.io.IOException ignored) { /* A broken parent channel has the same shutdown semantics as EOF. */ }
                finally { context.close(); System.exit(0); }
            });
        }
    }
}
