package com.oceanbase.powermem.sdk.transport;

/**
 * JDK11 {@code java.net.http.HttpClient}-based transport implementation.
 *
 * <p>Optional infrastructure: used when the Java SDK needs to call external HTTP APIs (LLM/embedding),
 * or when providing a remote-client mode.</p>
 *
 * <p>No direct Python equivalent; conceptually similar to Python's {@code httpx} usage.</p>
 */
public class JavaHttpTransport implements HttpTransport {
    private final java.net.http.HttpClient client;

    public JavaHttpTransport() {
        this(java.time.Duration.ofSeconds(60));
    }

    public JavaHttpTransport(java.time.Duration timeout) {
        java.time.Duration t = timeout == null ? java.time.Duration.ofSeconds(60) : timeout;
        this.client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(t)
                .build();
    }

    public String postJson(String url,
                           java.util.Map<String, String> headers,
                           String jsonBody,
                           java.time.Duration timeout) {
        try {
            java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody))
                    .header("Content-Type", "application/json");
            if (headers != null) {
                for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
                    if (e.getKey() != null && e.getValue() != null) {
                        b.header(e.getKey(), e.getValue());
                    }
                }
            }
            if (timeout != null) {
                b.timeout(timeout);
            }
            java.net.http.HttpResponse<String> resp =
                    client.send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code < 200 || code >= 300) {
                throw new com.oceanbase.powermem.sdk.exception.HttpException("HTTP " + code + " from " + url + ": " + resp.body());
            }
            return resp.body();
        } catch (com.oceanbase.powermem.sdk.exception.PowermemException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new com.oceanbase.powermem.sdk.exception.HttpException("HTTP request failed: " + ex.getMessage(), ex);
        }
    }
}

