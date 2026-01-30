package com.oceanbase.powermem.sdk.storage.memory;

import com.oceanbase.powermem.sdk.storage.base.GraphStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory graph store for offline usage and tests.
 *
 * <p>This implementation is NOT intended for production. It exists to:
 * - provide Python-like return shapes for {@code relations}
 * - enable unit/integration tests without a real graph database</p>
 */
public class InMemoryGraphStore implements GraphStore {

    private static final class Triple {
        final String source;
        final String relationship;
        final String destination;

        Triple(String source, String relationship, String destination) {
            this.source = source;
            this.relationship = relationship;
            this.destination = destination;
        }
    }

    // scopeKey -> triples
    private final Map<String, List<Triple>> store = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> add(String data, Map<String, Object> filters) {
        if (data == null || data.isBlank()) {
            return Collections.emptyMap();
        }
        String scopeKey = scopeKey(filters);

        List<Triple> newTriples = extractTriples(data);
        if (newTriples.isEmpty()) {
            Map<String, Object> out = new HashMap<>();
            out.put("deleted_entities", Collections.emptyList());
            out.put("added_entities", Collections.emptyList());
            return out;
        }

        store.compute(scopeKey, (k, existing) -> {
            List<Triple> next = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            next.addAll(newTriples);
            return next;
        });

        // Python parity: {"deleted_entities":[...], "added_entities":[{"source":..,"relationship":..,"target":..}]}
        List<Map<String, Object>> added = new ArrayList<>();
        for (Triple t : newTriples) {
            Map<String, Object> m = new HashMap<>();
            m.put("source", t.source);
            m.put("relationship", t.relationship);
            m.put("target", t.destination);
            added.add(m);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("deleted_entities", Collections.emptyList());
        out.put("added_entities", added);
        return out;
    }

    @Override
    public List<Map<String, Object>> search(String query, Map<String, Object> filters, int limit) {
        if (query == null) {
            query = "";
        }
        int top = limit > 0 ? limit : 100;
        String q = query.trim().toLowerCase(Locale.ROOT);
        String scopeKey = scopeKey(filters);

        List<Triple> triples = store.getOrDefault(scopeKey, Collections.emptyList());
        if (triples.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Triple t : triples) {
            if (!q.isEmpty()) {
                String combined = (t.source + " " + t.relationship + " " + t.destination).toLowerCase(Locale.ROOT);
                if (!combined.contains(q)) {
                    continue;
                }
            }
            Map<String, Object> m = new HashMap<>();
            m.put("source", t.source);
            m.put("relationship", t.relationship);
            m.put("destination", t.destination);
            out.add(m);
            if (out.size() >= top) {
                break;
            }
        }
        return out;
    }

    @Override
    public void deleteAll(Map<String, Object> filters) {
        store.remove(scopeKey(filters));
    }

    @Override
    public List<Map<String, Object>> getAll(Map<String, Object> filters, int limit) {
        return search("", filters, limit);
    }

    @Override
    public void reset() {
        store.clear();
    }

    private static String scopeKey(Map<String, Object> filters) {
        String userId = valueAsString(filters == null ? null : filters.get("user_id"));
        String agentId = valueAsString(filters == null ? null : filters.get("agent_id"));
        String runId = valueAsString(filters == null ? null : filters.get("run_id"));

        // Python parity: graph requires user_id; when absent, Memory uses fallback "user".
        if (userId == null || userId.isBlank()) {
            userId = "user";
        }
        if (agentId == null) agentId = "";
        if (runId == null) runId = "";
        return userId + "|" + agentId + "|" + runId;
    }

    private static String valueAsString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s;
    }

    /**
     * Extremely small heuristic triple extractor.
     *
     * <p>Goal: enable tests and provide a reasonable default behavior offline.
     * For production, use a real graph store (OceanBaseGraphStore) with LLM-based extraction.</p>
     */
    private static List<Triple> extractTriples(String data) {
        List<Triple> out = new ArrayList<>();
        String[] lines = data.split("[\\n\\r]+");
        for (String raw : lines) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isEmpty()) continue;

            // Simple Chinese patterns: "用户喜欢X" / "我喜欢X" / "用户偏好X"
            Triple t = parseChinesePreference(s);
            if (t != null) {
                out.add(t);
                continue;
            }

            // English fallback: "User likes X"
            Triple e = parseEnglishPreference(s);
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    private static Triple parseChinesePreference(String s) {
        String subject = null;
        String rest = null;

        // normalize punctuation
        String x = s.replace("：", ":").replace("，", ",").replace("。", ".").trim();

        if (x.startsWith("用户")) {
            subject = "用户";
            rest = x.substring(2);
        } else if (x.startsWith("我")) {
            subject = "我";
            rest = x.substring(1);
        } else {
            // unknown subject, skip
            return null;
        }

        if (rest == null) return null;
        rest = rest.trim();

        String rel;
        int idx;
        if ((idx = rest.indexOf("喜欢")) >= 0) {
            rel = "喜欢";
        } else if ((idx = rest.indexOf("偏好")) >= 0) {
            rel = "偏好";
        } else if ((idx = rest.indexOf("讨厌")) >= 0) {
            rel = "讨厌";
        } else {
            return null;
        }

        String obj = rest.substring(idx + rel.length()).trim();
        if (obj.isEmpty()) {
            return null;
        }
        return new Triple(subject, rel, obj);
    }

    private static Triple parseEnglishPreference(String s) {
        String x = s.trim();
        String lower = x.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("user ")) {
            return null;
        }
        // e.g. "User likes coffee"
        int idx = lower.indexOf(" likes ");
        String rel = "likes";
        if (idx < 0) {
            idx = lower.indexOf(" prefers ");
            rel = "prefers";
        }
        if (idx < 0) {
            return null;
        }
        String obj = x.substring(idx + (" " + rel + " ").length()).trim();
        if (obj.isEmpty()) return null;
        return new Triple("User", rel, obj);
    }
}

