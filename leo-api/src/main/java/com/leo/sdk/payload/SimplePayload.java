package com.leo.sdk.payload;

import javax.json.JsonObject;
import java.time.Instant;
import java.util.UUID;

public interface SimplePayload {
    default String id() {
        return UUID.randomUUID().toString();
    }

    default Instant eventTime() {
        return Instant.now();
    }

    JsonObject entity();
}