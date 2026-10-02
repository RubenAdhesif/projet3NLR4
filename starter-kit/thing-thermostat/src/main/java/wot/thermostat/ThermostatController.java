package wot.thermostat;

import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /model, GET /properties, GET|PUT /properties/{name}, POST /actions/setTarget.
 *
 * <p>Simulation loop (@Scheduled at 1 Hz):
 * <ul>
 *   <li>heat / eco: temperature climbs toward target at +0.2°C/s.
 *       When it crosses target from below, {@code targetReached} is emitted <em>once</em>,
 *       then the temperature is pinned to target.
 *       If the temperature is already above target (e.g. target was lowered manually),
 *       it drifts back down at -0.1°C/s without emitting an event.</li>
 *   <li>off: temperature drifts toward the outdoor setpoint (15°C) at -0.05°C/s.</li>
 * </ul>
 */
@RestController
public class ThermostatController {

    private static final Set<String> VALID_MODES = Set.of("off", "heat", "eco");
    private static final double OUTDOOR_TEMP    = 15.0;
    private static final double RISE_RATE       = 0.2;   // °C/s in heat or eco
    private static final double FALL_RATE       = 0.05;  // °C/s drift toward outdoor in off mode
    private static final double COOL_RATE       = 0.1;   // °C/s when above target

    // Initial temperature set below 19 so Rule R2 fires immediately during the demo
    private volatile double temperature = 17.5;
    private volatile double target      = 19.0;
    private volatile String mode        = "off";

    /**
     * Guard that prevents emitting targetReached more than once per heating cycle.
     * Reset to false whenever the mode switches to heat or target changes.
     */
    private volatile boolean targetReachedEmitted = false;

    private final GatewayClient gateway;

    public ThermostatController(GatewayClient gateway) {
        this.gateway = gateway;
    }

    // ── Web Thing routes ──────────────────────────────────────────────────────

    @GetMapping({"/", "/model"})
    public Map<String, Object> model() {
        return ThermostatDescription.model();
    }

    @GetMapping("/properties")
    public Map<String, Object> properties() {
        return Map.of(
                "temperature", Math.round(temperature * 100.0) / 100.0,
                "target", target,
                "mode", mode
        );
    }

    @GetMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> read(@PathVariable String name) {
        return switch (name) {
            case "temperature" -> ok(name, Math.round(temperature * 100.0) / 100.0);
            case "target"      -> ok(name, target);
            case "mode"        -> ok(name, mode);
            default            -> error(HttpStatus.NOT_FOUND, "unknown property " + name);
        };
    }

    @PutMapping("/properties/{name}")
    public ResponseEntity<Map<String, Object>> write(@PathVariable String name,
                                                     @RequestBody Map<String, Object> body) {
        Object value = body.get("value");
        return switch (name) {
            case "temperature" ->
                // read-only: always 400, regardless of the value
                error(HttpStatus.BAD_REQUEST, "property temperature is read-only");

            case "target" -> {
                if (!(value instanceof Number num)) {
                    yield error(HttpStatus.BAD_REQUEST,
                            "property target expects a number {\"value\": <number>}");
                }
                setTarget(num.doubleValue());
                yield ok(name, target);
            }

            case "mode" -> {
                if (!(value instanceof String s) || !VALID_MODES.contains(s)) {
                    yield error(HttpStatus.BAD_REQUEST,
                            "property mode must be one of: off, heat, eco");
                }
                setMode(s);
                yield ok(name, mode);
            }

            default -> error(HttpStatus.NOT_FOUND, "unknown property " + name);
        };
    }

    @PostMapping("/actions/setTarget")
    public ResponseEntity<Map<String, Object>> actionSetTarget(@RequestBody Map<String, Object> body) {
        Object value = body.get("value");
        if (!(value instanceof Number num)) {
            return error(HttpStatus.BAD_REQUEST,
                    "action setTarget expects {\"value\": <number>}");
        }
        setTarget(num.doubleValue());
        return ResponseEntity.ok(Map.of("action", "setTarget", "status", "completed", "value", target));
    }

    @PostMapping("/actions/{name}")
    public ResponseEntity<Map<String, Object>> unknownAction(@PathVariable String name) {
        return error(HttpStatus.NOT_FOUND, "unknown action " + name);
    }

    // ── Simulation loop ───────────────────────────────────────────────────────

    /**
     * Runs every second. Advances the temperature simulation and emits
     * {@code targetReached} exactly once when the temperature reaches the setpoint
     * from below while in heat mode.
     */
    @Scheduled(fixedRate = 1000)
    public void simulate() {
        switch (mode) {
            case "heat", "eco" -> simulateHeating();
            case "off"         -> simulateCooling();
        }
    }

    private void simulateHeating() {
        if (temperature < target) {
            temperature = Math.min(temperature + RISE_RATE, target);
            // Crossed or just reached the target from below
            if (temperature >= target && !targetReachedEmitted) {
                temperature = target; // pin exactly
                targetReachedEmitted = true;
                // Only emit targetReached in heat mode (not eco, per spec)
                if ("heat".equals(mode)) {
                    gateway.emit("targetReached",
                            Map.of("temperature", temperature, "target", target));
                }
            }
        } else if (temperature > target) {
            // Target was lowered: drift back without emitting an event
            temperature = Math.max(temperature - COOL_RATE, target);
        }
        // temperature == target: steady state, nothing to do
    }

    private void simulateCooling() {
        if (temperature > OUTDOOR_TEMP) {
            temperature = Math.max(temperature - FALL_RATE, OUTDOOR_TEMP);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void setTarget(double newTarget) {
        this.target = newTarget;
        // Changing the target resets the guard so targetReached can fire again
        this.targetReachedEmitted = false;
        gateway.emit("propertyChanged", Map.of("property", "target", "value", newTarget));
    }

    private void setMode(String newMode) {
        this.mode = newMode;
        if ("heat".equals(newMode)) {
            // Switching to heat resets the guard so targetReached can fire again
            this.targetReachedEmitted = false;
        }
        gateway.emit("propertyChanged", Map.of("property", "mode", "value", newMode));
    }

    private static ResponseEntity<Map<String, Object>> ok(String name, Object value) {
        return ResponseEntity.ok(Map.of("name", name, "value", value));
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", message));
    }
}
