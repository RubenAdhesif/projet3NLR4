package wot.gateway;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** POST /events (from the things) and GET /events/stream (to the browsers). */
@RestController
@RequestMapping("/events")
public class EventsController {

    public record ThingEvent(String thingId, String type, Map<String, Object> data, String timestamp) {
    }

    private final EventHub hub;
    private final ThingsController things;
    private final RulesEngine rules;

    public EventsController(EventHub hub, ThingsController things, RulesEngine rules) {
        this.hub    = hub;
        this.things = things;
        this.rules  = rules;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return hub.subscribe();
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody ThingEvent event) {
        if (event.thingId() == null || event.type() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fields thingId and type are required");
        }
        // 404 if the thing is not registered (guards against spoofed events)
        things.find(event.thingId());

        // Broadcast to all SSE clients before evaluating rules so the dashboard
        // sees the raw event immediately, regardless of any rule execution time.
        hub.broadcast("thing", event);

        // Evaluate R1 and R2 (synchronous dispatch, the actual HTTP calls are
        // made in the scheduler thread via RestartableTimer or directly).
        rules.onEvent(event.thingId(), event.type(),
                event.data() != null ? event.data() : Map.of());

        return ResponseEntity.accepted().build();
    }
}
