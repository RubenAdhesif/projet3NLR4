package wot.lamp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** GET /model, GET /properties, GET|PUT /properties/{name}, POST /actions/{name}. */
@RestController
public class LampController {

    private final Map<String, Object> state = new ConcurrentHashMap<>(Map.of("on", false, "brightness", 80));
    private final GatewayClient gateway;

    public LampController(GatewayClient gateway) {
        this.gateway = gateway;
    }

    @GetMapping({"/", "/model"})
    public Map<String, Object> model() {
        return LampDescription.model();
    }

    @GetMapping("/properties")
    public Map<String, Object> properties() {
        return state;
    }

    @GetMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> read(@PathVariable String name) {
        if (!state.containsKey(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        return ResponseEntity.ok(Map.of("name", name, "value", state.get(name)));
    }

    @PutMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> write(@PathVariable String name, @RequestBody Map<String, Object> body) {
        if (!state.containsKey(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        Object value = body.get("value");
        return switch (name) {
            case "on" -> {
                if (!(value instanceof Boolean)) {
                    yield error(HttpStatus.BAD_REQUEST, "property on expects a boolean {\"value\": true|false}");
                }
                set(name, value);
                yield ResponseEntity.ok(Map.of("name", name, "value", value));
            }
            case "brightness" -> {
                if (!(value instanceof Number num) || num.intValue() < 0 || num.intValue() > 100) {
                    yield error(HttpStatus.BAD_REQUEST, "property brightness expects an integer between 0 and 100");
                }
                int brightness = ((Number) value).intValue();
                set(name, brightness);
                yield ResponseEntity.ok(Map.of("name", name, "value", brightness));
            }
            default -> error(HttpStatus.BAD_REQUEST, "property " + name + " is read-only or unknown");
        };
    }

    // the rules must write the property on, not call toggle
    @PostMapping("/actions/toggle")
    public Map<String, Object> toggle() {
        set("on", !(Boolean) state.get("on"));
        return Map.of("action", "toggle", "status", "completed", "properties", state);
    }

    @PostMapping("/actions/setBrightness")
    public ResponseEntity<Map<String, Object>> setBrightness(@RequestBody Map<String, Object> body) {
        Object value = body.get("value");
        if (!(value instanceof Number num) || num.intValue() < 0 || num.intValue() > 100) {
            return error(HttpStatus.BAD_REQUEST, "action setBrightness expects an integer between 0 and 100");
        }
        int brightness = ((Number) value).intValue();
        set("brightness", brightness);
        return ResponseEntity.ok(Map.of("action", "setBrightness", "status", "completed", "value", brightness));
    }

    private void set(String name, Object value) {
        state.put(name, value);
        gateway.emit("propertyChanged", Map.of("property", name, "value", value));
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
