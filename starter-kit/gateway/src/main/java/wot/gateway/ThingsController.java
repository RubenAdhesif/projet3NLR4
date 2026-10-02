package wot.gateway;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registry of the things (in memory) + reverse proxy toward each thing's baseUrl. */
@RestController
@RequestMapping("/things")
public class ThingsController {

    public record Thing(String id, String name, String baseUrl, Map<String, Object> model) {
    }

    private final Map<String, Thing> things = new ConcurrentHashMap<>();
    private final EventHub hub;
    private final ThingClient thingClient;

    public ThingsController(EventHub hub, ThingClient thingClient) {
        this.hub = hub;
        this.thingClient = thingClient;
    }

    // ── Registry ──────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Thing> register(@RequestBody Thing thing) {
        if (thing.id() == null || thing.id().isBlank() || thing.baseUrl() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fields id and baseUrl are required");
        }
        if (things.putIfAbsent(thing.id(), thing) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "thing " + thing.id() + " is already registered");
        }
        hub.broadcast("registry", Map.of("type", "registered", "thingId", thing.id()));
        return ResponseEntity.created(URI.create("/things/" + thing.id())).body(thing);
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return things.values().stream()
                .map(t -> Map.<String, Object>of("id", t.id(), "name", String.valueOf(t.name()),
                        "links", Map.of("self", "/things/" + t.id())))
                .toList();
    }

    @GetMapping("/{id}")
    public Thing get(@PathVariable String id) {
        return find(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unregister(@PathVariable String id) {
        find(id);
        things.remove(id);
        hub.broadcast("registry", Map.of("type", "unregistered", "thingId", id));
        return ResponseEntity.noContent().build();
    }

    // ── Proxy ─────────────────────────────────────────────────────────────────

    /** GET /things/{id}/properties → forward to GET {baseUrl}/properties */
    @GetMapping("/{id}/properties")
    public ResponseEntity<Object> getProperties(@PathVariable String id) {
        Thing thing = find(id);
        return thingClient.get(thing.baseUrl(), "/properties", id);
    }

    /** GET /things/{id}/properties/{name} → forward to GET {baseUrl}/properties/{name} */
    @GetMapping("/{id}/properties/{name}")
    public ResponseEntity<Object> getProperty(@PathVariable String id, @PathVariable String name) {
        Thing thing = find(id);
        return thingClient.get(thing.baseUrl(), "/properties/" + name, id);
    }

    /** PUT /things/{id}/properties/{name} → forward to PUT {baseUrl}/properties/{name} */
    @PutMapping("/{id}/properties/{name}")
    public ResponseEntity<Object> putProperty(@PathVariable String id, @PathVariable String name,
                                              @RequestBody Map<String, Object> body) {
        Thing thing = find(id);
        return thingClient.put(thing.baseUrl(), "/properties/" + name, body, id);
    }

    /** POST /things/{id}/actions/{name} → forward to POST {baseUrl}/actions/{name} */
    @PostMapping("/{id}/actions/{name}")
    public ResponseEntity<Object> postAction(@PathVariable String id, @PathVariable String name,
                                             @RequestBody(required = false) Map<String, Object> body) {
        Thing thing = find(id);
        return thingClient.post(thing.baseUrl(), "/actions/" + name, body, id);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    public Thing find(String id) {
        Thing thing = things.get(id);
        if (thing == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "thing " + id + " is not registered");
        }
        return thing;
    }
}
