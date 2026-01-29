package com.oceanbase.powermem.sdk.json;

/**
 * JSON serialization/deserialization abstraction.
 *
 * <p>No direct Python equivalent; Python uses Pydantic/stdlib {@code json} depending on the layer.</p>
 */
public interface JsonCodec {
    String toJson(Object value);

    <T> T fromJson(String json, Class<T> type);

    java.util.Map<String, Object> fromJsonToMap(String json);
}

