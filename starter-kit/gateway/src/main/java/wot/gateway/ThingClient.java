package wot.gateway;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * HTTP client used by the gateway proxy to forward requests to individual Things.
 * <p>
 * Rules:
 * - Returns the thing's response (status + body) as-is on 4xx/5xx (propagation).
 * - Throws ApiException(502) when the thing is unreachable (connection refused, timeout…).
 */
@Component
public class ThingClient {

    private static final Logger log = LoggerFactory.getLogger(ThingClient.class);

    private final RestClient.Builder builder;

    public ThingClient() {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(3000);
        timeouts.setReadTimeout(5000);
        this.builder = RestClient.builder().requestFactory(timeouts);
    }

    /** GET {baseUrl}{path} */
    public ResponseEntity<Object> get(String baseUrl, String path, String thingId) {
        try {
            var entity = client(baseUrl).get()
                    .uri(path)
                    .retrieve()
                    // Propagate 4xx/5xx from the thing instead of throwing
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    })
                    .toEntity(Object.class);
            return ResponseEntity.status(entity.getStatusCode()).body(entity.getBody());
        } catch (ResourceAccessException e) {
            log.warn("thing {} unreachable on GET {}{}: {}", thingId, baseUrl, path, e.getMessage());
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "thing " + thingId + " is not reachable");
        }
    }

    /** PUT {baseUrl}{path} with body */
    public ResponseEntity<Object> put(String baseUrl, String path, Map<String, Object> body, String thingId) {
        try {
            var entity = client(baseUrl).put()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    })
                    .toEntity(Object.class);
            return ResponseEntity.status(entity.getStatusCode()).body(entity.getBody());
        } catch (ResourceAccessException e) {
            log.warn("thing {} unreachable on PUT {}{}: {}", thingId, baseUrl, path, e.getMessage());
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "thing " + thingId + " is not reachable");
        }
    }

    /** POST {baseUrl}{path} with optional body */
    public ResponseEntity<Object> post(String baseUrl, String path, Map<String, Object> body, String thingId) {
        try {
            var spec = client(baseUrl).post().uri(path);
            if (body != null && !body.isEmpty()) {
                spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            var entity = spec.retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    })
                    .toEntity(Object.class);
            return ResponseEntity.status(entity.getStatusCode()).body(entity.getBody());
        } catch (ResourceAccessException e) {
            log.warn("thing {} unreachable on POST {}{}: {}", thingId, baseUrl, path, e.getMessage());
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "thing " + thingId + " is not reachable");
        }
    }

    private RestClient client(String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }
}
