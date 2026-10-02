package wot.motion;

import java.util.LinkedHashMap;
import java.util.Map;

/** Web Thing Model of the motion sensor (GET /model). */
public final class MotionDescription {

    public static final String ID = "motion";

    private MotionDescription() {
    }

    public static Map<String, Object> model() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ID);
        m.put("name", "Motion Sensor");
        m.put("description", "Virtual presence detector of the Smart Lab");
        m.put("properties", Map.of(
                "lastMotion", Map.of(
                        "type", "string",
                        "readOnly", true,
                        "description", "ISO-8601 timestamp of the last detected motion (null if none)",
                        "links", Map.of("property", "/properties/lastMotion")
                )
        ));
        m.put("actions", Map.of(
                "simulateMotion", Map.of(
                        "description", "simulates a motion detection and notifies the gateway",
                        "links", Map.of("action", "/actions/simulateMotion")
                )
        ));
        m.put("events", Map.of(
                "motion", Map.of("description", "fired when a motion is detected: {\"timestamp\": \"...\"}")
        ));
        m.put("links", Map.of(
                "self", "/model",
                "properties", "/properties",
                "simulateMotion", "/actions/simulateMotion"
        ));
        return m;
    }
}
