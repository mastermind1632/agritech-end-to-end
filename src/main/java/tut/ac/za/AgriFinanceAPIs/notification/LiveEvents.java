package tut.ac.za.AgriFinanceAPIs.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tut.ac.za.AgriFinanceAPIs.common.Hashing;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real-time push over Server-Sent Events (works through the existing nginx proxy, no WebSocket broker needed).
 * The browser's EventSource cannot send an Authorization header, so it first asks for a short-lived stream ticket
 * with its normal token, then connects with ?ticket=... The ticket only allows receiving this user's events.
 */
@Component
public class LiveEvents {

    private record Ticket(String userId, Instant expires) { }

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public String issueTicket(String userId) {
        String t = Hashing.randomToken(24);
        tickets.put(t, new Ticket(userId, Instant.now().plusSeconds(12 * 3600)));
        return t;
    }

    /** Returns null when the ticket is unknown or expired. */
    public SseEmitter connect(String ticket) {
        Ticket t = ticket == null ? null : tickets.get(ticket);
        if (t == null || t.expires().isBefore(Instant.now())) return null;
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        List<SseEmitter> list = emitters.computeIfAbsent(t.userId(), k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("hello").data(Map.of("connected", true)));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    public void send(String userId, String event, Object data) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) return;
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name(event).data(data));
            } catch (IOException | IllegalStateException ex) {
                list.remove(e);
            }
        }
    }

    public void broadcast(String event, Object data) {
        for (String user : emitters.keySet()) send(user, event, data);
    }

    public int connectedUsers() {
        return (int) emitters.values().stream().filter(l -> !l.isEmpty()).count();
    }

    /** Keeps idle connections open through proxies and drops expired tickets. */
    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(e -> e.getValue().expires().isBefore(now));
        for (Map.Entry<String, List<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter e : entry.getValue()) {
                try {
                    e.send(SseEmitter.event().comment("ping"));
                } catch (IOException | IllegalStateException ex) {
                    entry.getValue().remove(e);
                }
            }
        }
    }
}
