package org.organisation.ods;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Hosts the server-side authorization boundary and asynchronous integrations. Exigences : DEVAI-002, DEVAI-129. */
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class GatewayApplication {
    /** Starts the gateway using external configuration; no provider key is packaged. Exigences : DEVAI-035, DEVAI-127. */
    public static void main(String[] args) { SpringApplication.run(GatewayApplication.class,args); }
}
