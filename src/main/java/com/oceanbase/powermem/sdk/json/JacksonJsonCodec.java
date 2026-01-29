package com.oceanbase.powermem.sdk.json;

/**
 * Jackson-based {@link JsonCodec} implementation.
 *
 * <p>No direct Python equivalent; in Python this role is split between Pydantic models and {@code json}
 * parsing.</p>
 */
public class JacksonJsonCodec implements JsonCodec {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    public String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            throw new com.oceanbase.powermem.sdk.exception.SerializationException("Failed to serialize JSON: " + ex.getMessage());
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception ex) {
            throw new com.oceanbase.powermem.sdk.exception.SerializationException("Failed to deserialize JSON: " + ex.getMessage());
        }
    }

    @Override
    public java.util.Map<String, Object> fromJsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return new java.util.HashMap<>();
        }
        try {
            return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new com.oceanbase.powermem.sdk.exception.SerializationException("Failed to deserialize JSON to map: " + ex.getMessage());
        }
    }
}

