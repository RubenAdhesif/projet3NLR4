package wot.motion;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /model, GET /properties, GET|PUT /properties/{name}, POST /actions/simulateMotion.
 * <p>
 * The property lastMotion is read-only: any PUT on it returns 400 Bad Request.
 */
@RestController
public class MotionController {

    // null means no motion has been detected yet since startup
    private volatile String lastMotion = null;

    private final GatewayClient gateway;

    public MotionController(GatewayClient gateway) {
        this.gateway = gateway;
    }

    @GetMapping({"/", "/model"})
    public Map<String, Object> model() {
        return MotionDescription.model();
    }

    @GetMapping("/properties")
    public Map<String, Object> properties() {
        return Map.of("lastMotion", lastMotion == null ? "null" : lastMotion);
    }

    @GetMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> read(@PathVariable String name) {
        if (!"lastMotion".equals(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        return ResponseEntity.ok(Map.of("name", "lastMotion", "value",
                lastMotion == null ? "null" : lastMotion));
    }

    /**
     * lastMotion is read-only: always return 400.
     * The gateway proxy will forward PUT attempts here, and we must reject them.
     */
    @PutMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> write(@PathVariable String name,
                                                     @RequestBody(required = false) Object body) {
        if (!"lastMotion".equals(name)) {
            return error(HttpStatus.NOT_FOUND, "unknown property " + name);
        }
        return error(HttpStatus.BAD_REQUEST, "property lastMotion is read-only");
    }

    /**
     * Simulates a motion detection:
     *  1. Records the current timestamp in lastMotion.
     *  2. Emits a "motion" event to the gateway asynchronously.
     */
    @PostMapping("/actions/simulateMotion")
    public Map<String, Object> simulateMotion() {
        lastMotion = Instant.now().toString();
        // emit in a separate thread so the gateway can call us back without deadlock
        gateway.emit("motion", Map.of("timestamp", lastMotion));
        return Map.of("action", "simulateMotion", "status", "completed", "timestamp", lastMotion);
    }

    @PostMapping("/actions/{name}")
    public ResponseEntity<Map<String, Object>> unknownAction(@PathVariable String name) {
        return error(HttpStatus.NOT_FOUND, "unknown action " + name);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
