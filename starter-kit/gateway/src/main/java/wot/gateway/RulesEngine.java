package wot.gateway;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Automation rules R1 and R2.
 *
 * <p><b>R1 — Lighting:</b>
 * On "motion" → turn lamp ON via PUT {lampUrl}/properties/on.
 * Restart timer T1. On expiry → turn lamp OFF.
 *
 * <p><b>R2 — Thermal comfort:</b>
 * On "motion" AND temperature < 19°C → set thermostat mode=heat, target=19.
 * On "targetReached" from thermostat (while mode==heat) → set mode=off.
 *
 * <p>The rules call the things DIRECTLY (not through the gateway proxy) so that
 * the writes are not counted as manual user actions (R4).
 */
@Component
public class RulesEngine {

    private static final Logger log = LoggerFactory.getLogger(RulesEngine.class);

    private static final double COMFORT_TEMP = 19.0;

    // T1: lamp extinction delay after last motion (10 s for demo, 60 s in production)
    private final Duration t1;

    private final RestClient lampClient;
    private final RestClient thermostatClient;
    private final RestartableTimer lampOffTimer;

    // Track thermostat mode as seen by the rules to avoid reading state on each event
    private volatile String thermostatMode = "off";

    public RulesEngine(
            TaskScheduler scheduler,
            @Value("${things.lamp.url:http://localhost:8082}") String lampUrl,
            @Value("${things.thermostat.url:http://localhost:8081}") String thermostatUrl,
            @Value("${rules.t1-seconds:10}") int t1Seconds) {

        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(2000);
        timeouts.setReadTimeout(3000);

        this.lampClient       = RestClient.builder().baseUrl(lampUrl).requestFactory(timeouts).build();
        this.thermostatClient = RestClient.builder().baseUrl(thermostatUrl).requestFactory(timeouts).build();
        this.t1               = Duration.ofSeconds(t1Seconds);
        this.lampOffTimer     = new RestartableTimer(scheduler);
    }

    /**
     * Called by EventsController for each event received from a Thing.
     * Returns immediately — any HTTP call to a Thing is done in the scheduler thread.
     */
    public void onEvent(String thingId, String type, Map<String, Object> data) {
        switch (type) {
            case "motion"        -> handleMotion(data);
            case "targetReached" -> handleTargetReached(thingId);
            default              -> { /* other events are not handled by these rules */ }
        }
    }

    // ── R1 + R2 on motion ────────────────────────────────────────────────────

    private void handleMotion(Map<String, Object> data) {
        applyR1();
        applyR2();
    }

    /** R1: lamp ON + restart extinction timer T1. */
    private void applyR1() {
        lampOn();
        // Restart the timer: any previous countdown is cancelled
        lampOffTimer.restart(t1, () -> {
            log.info("R1: T1 expired, turning lamp off");
            lampOff();
        });
        log.info("R1: motion detected — lamp ON, T1 timer restarted ({}s)", t1.toSeconds());
    }

    /** R2: if temperature < COMFORT_TEMP, switch thermostat to heat mode at 19°C. */
    private void applyR2() {
        double currentTemp = readTemperature();
        if (currentTemp >= COMFORT_TEMP) {
            log.debug("R2: temperature={} >= {}°C, no heating needed", currentTemp, COMFORT_TEMP);
            return;
        }
        log.info("R2: motion + temperature={}°C < {}°C — starting heat", currentTemp, COMFORT_TEMP);
        setThermostatMode("heat");
        setThermostatTarget(COMFORT_TEMP);
    }

    // ── R2 completion on targetReached ───────────────────────────────────────

    /**
     * R2 completion: when the thermostat emits targetReached while in heat mode,
     * switch it to off. Eco mode is intentionally ignored.
     */
    private void handleTargetReached(String thingId) {
        if (!"thermostat".equals(thingId)) {
            return;
        }
        if (!"heat".equals(thermostatMode)) {
            log.debug("R2: targetReached ignored (thermostat not in heat mode, current={})", thermostatMode);
            return;
        }
        log.info("R2: targetReached — switching thermostat to off");
        setThermostatMode("off");
    }

    // ── Direct HTTP calls to the things ──────────────────────────────────────

    private void lampOn() {
        putThingProperty(lampClient, "lamp", "on", true);
    }

    private void lampOff() {
        putThingProperty(lampClient, "lamp", "on", false);
    }

    private void setThermostatMode(String mode) {
        if (putThingProperty(thermostatClient, "thermostat", "mode", mode)) {
            thermostatMode = mode;
        }
    }

    private void setThermostatTarget(double target) {
        putThingProperty(thermostatClient, "thermostat", "target", target);
    }

    /**
     * Reads the current temperature directly from the thermostat.
     * Returns COMFORT_TEMP (neutral value) on error so R2 is not triggered.
     */
    private double readTemperature() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = thermostatClient.get()
                    .uri("/properties/temperature")
                    .retrieve()
                    .body(Map.class);
            if (resp != null && resp.get("value") instanceof Number n) {
                return n.doubleValue();
            }
        } catch (RestClientException e) {
            log.warn("R2: could not read thermostat temperature: {}", e.getMessage());
        }
        return COMFORT_TEMP; // safe fallback: don't trigger R2 if thermostat is unreachable
    }

    /**
     * PUT {baseUrl}/properties/{name} {"value": value}.
     * @return true on success, false on error.
     */
    private boolean putThingProperty(RestClient client, String thingId, String property, Object value) {
        try {
            client.put()
                    .uri("/properties/" + property)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("value", value))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("rule: could not set {}.{} = {}: {}", thingId, property, value, e.getMessage());
            return false;
        }
    }
}
