package wot.thermostat;

import java.util.LinkedHashMap;
import java.util.Map;

/** Web Thing Model of the thermostat (GET /model). */
public final class ThermostatDescription {

    public static final String ID = "thermostat";

    private ThermostatDescription() {
    }

    public static Map<String, Object> model() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ID);
        m.put("name", "Thermostat");
        m.put("description", "Virtual thermostat of the Smart Lab");
        m.put("properties", Map.of(
                "temperature", Map.of(
                        "type", "number",
                        "readOnly", true,
                        "unit", "degree celsius",
                        "description", "current temperature (simulated, read-only)",
                        "links", Map.of("property", "/properties/temperature")
                ),
                "target", Map.of(
                        "type", "number",
                        "unit", "degree celsius",
                        "description", "target temperature (setpoint)",
                        "links", Map.of("property", "/properties/target")
                ),
                "mode", Map.of(
                        "type", "string",
                        "enum", new String[]{"off", "heat", "eco"},
                        "description", "operating mode: off | heat | eco",
                        "links", Map.of("property", "/properties/mode")
                )
        ));
        m.put("actions", Map.of(
                "setTarget", Map.of(
                        "description", "sets the target temperature",
                        "input", Map.of("type", "number"),
                        "links", Map.of("action", "/actions/setTarget")
                )
        ));
        m.put("events", Map.of(
                "targetReached", Map.of(
                        "description", "fired once when temperature reaches target from below (heat mode only)"
                ),
                "propertyChanged", Map.of(
                        "description", "fired when a property changes: {\"property\": ..., \"value\": ...}"
                )
        ));
        m.put("links", Map.of(
                "self", "/model",
                "properties", "/properties",
                "setTarget", "/actions/setTarget"
        ));
        return m;
    }
}
