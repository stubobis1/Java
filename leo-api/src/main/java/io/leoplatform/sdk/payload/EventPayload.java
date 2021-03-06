package io.leoplatform.sdk.payload;

import javax.json.JsonObject;
import java.time.Instant;
import java.util.UUID;

public interface EventPayload {
    default String id() {
        return UUID.randomUUID().toString();
    }

    default Instant eventTime() {
        return Instant.now();
    }

    default StreamCorrelation streamCorrelation() {
        return null;
    }

    JsonObject payload();
}
