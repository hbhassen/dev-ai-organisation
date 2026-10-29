package org.organisation.ods.ai;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.organisation.ods.contracts.OdsException;
import java.util.*;
import java.util.concurrent.*;

/** Sends ephemeral progress to already-authorized subscribers; durable results remain in the encrypted job store. Exigences : DEVAI-078, DEVAI-119. */
@Component
public final class JobEvents {
    private final Map<String,Set<SseEmitter>> subscribers=new ConcurrentHashMap<>();
    /** Registers a bounded stream after the controller has checked the authenticated job owner. Exigences : DEVAI-078, DEVAI-117. */
    public SseEmitter subscribe(String job) {
        Set<SseEmitter> group=subscribers.computeIfAbsent(job,k->ConcurrentHashMap.newKeySet()); if(group.size()>=5) throw new OdsException("RATE_LIMITED","Trop de flux actifs");
        SseEmitter emitter=new SseEmitter(150000L); group.add(emitter);
        Runnable cleanup=()->{ group.remove(emitter); if(group.isEmpty()) subscribers.remove(job,group); };
        emitter.onCompletion(cleanup); emitter.onTimeout(cleanup); emitter.onError(error->cleanup.run()); return emitter;
    }
    /** Publishes bounded text snapshots without retaining source content in logs or a second history store. Exigences : DEVAI-078, DEVAI-132. */
    public void text(String job,String text) {
        for(SseEmitter emitter:subscribers.getOrDefault(job,Set.of())) try { emitter.send(SseEmitter.event().name("progress").data(Map.of("text",text))); } catch(Exception e) { emitter.complete(); }
    }
}
