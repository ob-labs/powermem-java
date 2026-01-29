package com.oceanbase.powermem.sdk.storage.sqlite;

import com.oceanbase.powermem.sdk.json.JacksonJsonCodec;
import com.oceanbase.powermem.sdk.json.JsonCodec;
import com.oceanbase.powermem.sdk.model.MemoryRecord;
import com.oceanbase.powermem.sdk.storage.base.OutputData;
import com.oceanbase.powermem.sdk.storage.base.VectorStore;
import com.oceanbase.powermem.sdk.util.PowermemUtils;
import com.oceanbase.powermem.sdk.util.SnowflakeIdGenerator;
import com.oceanbase.powermem.sdk.util.VectorMath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQLite vector store implementation (Python-compatible schema).
 *
 * <p>Python reference: {@code src/powermem/storage/sqlite/sqlite_vector_store.py}</p>
 *
 * <p>Schema (aligned with Python):</p>
 * <pre>
 * CREATE TABLE {collection} (
 *   id INTEGER PRIMARY KEY,
 *   vector TEXT,    -- JSON array
 *   payload TEXT,   -- JSON object
 *   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 * )
 * </pre>
 *
 * <p>History table follows the plan and Python {@code storage/sqlite/sqlite.py}.</p>
 */
public class SQLiteVectorStore implements VectorStore {
    private static final String TABLE_HISTORY = "history";

    private final String databasePath;
    private final String tableName;
    private final boolean enableWal;
    private final int busyTimeoutSeconds;

    private final JsonCodec json = new JacksonJsonCodec();
    private final SnowflakeIdGenerator historyIdGenerator = SnowflakeIdGenerator.defaultGenerator();
    private final SnowflakeIdGenerator idGenerator = SnowflakeIdGenerator.defaultGenerator();

    public SQLiteVectorStore() {
        this("./data/powermem_dev.db", "memories", true, 30);
    }

    public SQLiteVectorStore(String databasePath, String tableName, boolean enableWal, int busyTimeoutSeconds) {
        this.databasePath = (databasePath == null || databasePath.isBlank()) ? "./data/powermem_dev.db" : databasePath;
        this.tableName = (tableName == null || tableName.isBlank()) ? "memories" : tableName;
        this.enableWal = enableWal;
        this.busyTimeoutSeconds = busyTimeoutSeconds <= 0 ? 30 : busyTimeoutSeconds;
        ensureInitialized();
    }

    private void ensureInitialized() {
        try {
            Path p = Paths.get(databasePath);
            Path parent = p.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception ignored) {
            // best-effort
        }

        try (Connection c = openConnection(); Statement st = c.createStatement()) {
            if (enableWal) {
                st.execute("PRAGMA journal_mode=WAL;");
            }
            st.execute("PRAGMA foreign_keys=ON;");
            st.execute("PRAGMA busy_timeout=" + (busyTimeoutSeconds * 1000) + ";");

            ensureMemoriesTableCompatible(c);
            ensureHistoryTable(c);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to initialize SQLite schema: " + ex.getMessage(), ex);
        }
    }

    private Connection openConnection() throws Exception {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // If dependency isn't on classpath, DriverManager.getConnection will fail.
        }
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath);
    }

    private void ensureHistoryTable(Connection c) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + " ("
                    + "id TEXT PRIMARY KEY,"
                    + "memory_id TEXT,"
                    + "old_memory TEXT,"
                    + "new_memory TEXT,"
                    + "event TEXT,"
                    + "created_at INTEGER,"
                    + "updated_at INTEGER,"
                    + "is_deleted INTEGER,"
                    + "actor_id TEXT,"
                    + "role TEXT"
                    + ");");
            st.execute("CREATE INDEX IF NOT EXISTS idx_history_memory_id ON " + TABLE_HISTORY + " (memory_id);");
        }
    }

    private void ensureMemoriesTableCompatible(Connection c) throws Exception {
        if (!tableExists(c, tableName)) {
            createPythonSchemaTable(c, tableName);
            return;
        }

        // if already python schema => done
        List<String> cols = getColumns(c, tableName);
        boolean hasVector = cols.contains("vector");
        boolean hasPayload = cols.contains("payload");
        if (hasVector && hasPayload) {
            return;
        }

        // legacy Java schema detection: content/user_id/embedding etc
        boolean hasLegacyContent = cols.contains("content") && cols.contains("embedding");
        if (!hasLegacyContent) {
            // unknown: recreate as python schema (best-effort)
            migrateToPythonSchema(c, cols);
            return;
        }

        migrateToPythonSchema(c, cols);
    }

    private boolean tableExists(Connection c, String name) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<String> getColumns(Connection c, String name) throws Exception {
        List<String> cols = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + name + ")")) {
            while (rs.next()) {
                String col = rs.getString(2);
                if (col != null) {
                    cols.add(col.toLowerCase());
                }
            }
        }
        return cols;
    }

    private void createPythonSchemaTable(Connection c, String name) throws Exception {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS " + name + " ("
                    + "id INTEGER PRIMARY KEY,"
                    + "vector TEXT,"
                    + "payload TEXT,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                    + ");");
        }
    }

    private void migrateToPythonSchema(Connection c, List<String> existingCols) throws Exception {
        String newTable = tableName + "_v2";
        try (Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + newTable);
        }
        createPythonSchemaTable(c, newTable);

        // Try best-effort migrate from old schema if it looks like the legacy Java schema
        if (existingCols.contains("content") && existingCols.contains("embedding")) {
            String sql = "SELECT id, user_id, agent_id, run_id, content, embedding, metadata, created_at, updated_at, last_accessed_at FROM " + tableName;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    long id = parseLongOrGenerate(rs.getString("id"));
                    String content = rs.getString("content");
                    String userId = rs.getString("user_id");
                    String agentId = rs.getString("agent_id");
                    String runId = rs.getString("run_id");
                    String embeddingCsv = rs.getString("embedding");
                    String metaLegacy = rs.getString("metadata");
                    long createdAt = rs.getLong("created_at");
                    long updatedAt = rs.getLong("updated_at");
                    long lastAccessedAt = rs.getLong("last_accessed_at");

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("data", content == null ? "" : content);
                    payload.put("fulltext_content", content == null ? "" : content);
                    payload.put("user_id", userId == null ? "" : userId);
                    payload.put("agent_id", agentId == null ? "" : agentId);
                    payload.put("run_id", runId == null ? "" : runId);
                    payload.put("hash", PowermemUtils.md5Hex(content == null ? "" : content));
                    payload.put("created_at", toIso(createdAt));
                    payload.put("updated_at", toIso(updatedAt));
                    payload.put("last_accessed_at", toIso(lastAccessedAt));
                    payload.put("metadata", decodeLegacyMetadata(metaLegacy));

                    float[] vec = decodeLegacyEmbeddingCsv(embeddingCsv);
                    insertRaw(c, newTable, id, json.toJson(vec == null ? new float[0] : vec), json.toJson(payload));
                }
            } catch (Exception ignored) {
                // Best-effort; if anything fails, users can rebuild data.
            }
        }

        try (Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + tableName);
            st.execute("ALTER TABLE " + newTable + " RENAME TO " + tableName);
        }
    }

    private void insertRaw(Connection c, String tbl, long id, String vectorJson, String payloadJson) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + tbl + " (id, vector, payload) VALUES (?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, vectorJson);
            ps.setString(3, payloadJson);
            ps.executeUpdate();
        }
    }

    private long parseLongOrGenerate(String s) {
        if (s == null || s.isBlank()) {
            return Long.parseLong(idGenerator.nextId());
        }
        try {
            return Long.parseLong(s.trim());
        } catch (Exception ex) {
            return Long.parseLong(idGenerator.nextId());
        }
    }

    private static String toIso(long epochMilli) {
        if (epochMilli <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(epochMilli).toString();
    }

    private static float[] decodeLegacyEmbeddingCsv(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String[] parts = s.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Float.parseFloat(parts[i].trim());
            } catch (Exception ex) {
                out[i] = 0.0f;
            }
        }
        return out;
    }

    private static Map<String, Object> decodeLegacyMetadata(String s) {
        Map<String, Object> m = new HashMap<>();
        if (s == null || s.isBlank()) {
            return m;
        }
        // legacy format: key=value;key2=value2 (best-effort)
        String[] pairs = s.split(";");
        for (String p : pairs) {
            if (p == null || p.isBlank()) {
                continue;
            }
            int idx = p.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String k = p.substring(0, idx);
            String v = p.substring(idx + 1);
            m.put(k, v);
        }
        return m;
    }

    @Override
    public void upsert(MemoryRecord record, float[] embedding) {
        if (record == null || record.getId() == null || record.getId().isBlank()) {
            return;
        }
        long id = parseLongOrGenerate(record.getId());

        Map<String, Object> oldPayload = null;
        String oldMemory = null;
        try {
            oldPayload = readPayloadById(id);
            if (oldPayload != null) {
                Object data = oldPayload.get("data");
                oldMemory = data == null ? null : String.valueOf(data);
            }
        } catch (Exception ignored) {
        }

        Instant now = Instant.now();
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(oldPayload != null ? parseInstant(oldPayload.get("created_at")) : now);
        }
        record.setUpdatedAt(now);
        if (record.getLastAccessedAt() == null) {
            record.setLastAccessedAt(now);
        }
        if (record.getHash() == null || record.getHash().isBlank()) {
            record.setHash(PowermemUtils.md5Hex(record.getContent() == null ? "" : record.getContent()));
        }

        Map<String, Object> payload = toPayload(record);
        String vectorJson = json.toJson(embedding == null ? new float[0] : embedding);
        String payloadJson = json.toJson(payload);

        String sql = "INSERT INTO " + tableName + " (id, vector, payload) VALUES (?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET vector=excluded.vector, payload=excluded.payload";
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, vectorJson);
            ps.setString(3, payloadJson);
            ps.executeUpdate();
        } catch (Exception ex) {
            throw new RuntimeException("SQLite upsert failed: " + ex.getMessage(), ex);
        }

        writeHistory(Long.toString(id), oldMemory, record.getContent(), oldPayload == null ? "ADD" : "UPDATE",
                record.getUserId(), record.getAgentId(), false);
    }

    @Override
    public MemoryRecord get(String memoryId, String userId, String agentId) {
        if (memoryId == null || memoryId.isBlank()) {
            return null;
        }
        long id;
        try {
            id = Long.parseLong(memoryId.trim());
        } catch (Exception ex) {
            return null;
        }
        try {
            Map<String, Object> payload = readPayloadById(id);
            if (payload == null) {
                return null;
            }
            if (userId != null && !userId.isBlank() && !userId.equals(asString(payload.get("user_id")))) {
                return null;
            }
            if (agentId != null && !agentId.isBlank() && !agentId.equals(asString(payload.get("agent_id")))) {
                return null;
            }
            return fromPayload(memoryId, payload);
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public boolean delete(String memoryId, String userId, String agentId) {
        MemoryRecord existing = get(memoryId, userId, agentId);
        if (existing == null) {
            return false;
        }
        long id = Long.parseLong(existing.getId());
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + tableName + " WHERE id=?")) {
            ps.setLong(1, id);
            int changed = ps.executeUpdate();
            boolean deleted = changed > 0;
            if (deleted) {
                writeHistory(existing.getId(), existing.getContent(), null, "DELETE", userId, agentId, true);
            }
            return deleted;
        } catch (Exception ex) {
            throw new RuntimeException("SQLite delete failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public int deleteAll(String userId, String agentId, String runId) {
        List<MemoryRecord> before = list(userId, agentId, runId, 0, Integer.MAX_VALUE);

        StringBuilder sql = new StringBuilder("DELETE FROM " + tableName + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        sql.append(buildJsonWhere(args, userId, agentId, runId, null));

        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            int deleted = ps.executeUpdate();
            for (MemoryRecord r : before) {
                if (r != null) {
                    writeHistory(r.getId(), r.getContent(), null, "DELETE", userId, agentId, true);
                }
            }
            return deleted;
        } catch (Exception ex) {
            throw new RuntimeException("SQLite deleteAll failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<MemoryRecord> list(String userId, String agentId, String runId, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? 100 : limit;

        StringBuilder sql = new StringBuilder("SELECT id, payload FROM " + tableName + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        sql.append(buildJsonWhere(args, userId, agentId, runId, null));
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(safeLimit);
        args.add(safeOffset);

        List<MemoryRecord> out = new ArrayList<>();
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String idStr = Long.toString(rs.getLong("id"));
                    Map<String, Object> payload = json.fromJsonToMap(rs.getString("payload"));
                    out.add(fromPayload(idStr, payload));
                }
            }
            return out;
        } catch (Exception ex) {
            throw new RuntimeException("SQLite list failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<OutputData> search(float[] queryEmbedding,
                                  int topK,
                                  String userId,
                                  String agentId,
                                  String runId,
                                  Map<String, Object> filters) {
        int k = topK <= 0 ? 5 : topK;
        Instant now = Instant.now();

        StringBuilder sql = new StringBuilder("SELECT id, vector, payload FROM " + tableName + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        sql.append(buildJsonWhere(args, userId, agentId, runId, filters));

        List<OutputData> scored = new ArrayList<>();
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String idStr = Long.toString(rs.getLong("id"));
                    float[] vec = json.fromJson(rs.getString("vector"), float[].class);
                    Map<String, Object> payload = json.fromJsonToMap(rs.getString("payload"));
                    MemoryRecord record = fromPayload(idStr, payload);
                    double score = VectorMath.cosineSimilarity(queryEmbedding, vec);
                    record.setLastAccessedAt(now);
                    scored.add(new OutputData(record, score));
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException("SQLite search failed: " + ex.getMessage(), ex);
        }

        scored.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (scored.size() > k) {
            scored = new ArrayList<>(scored.subList(0, k));
        }

        // best-effort: persist last_accessed_at into payload JSON
        for (OutputData d : scored) {
            if (d == null || d.getRecord() == null) {
                continue;
            }
            updateLastAccessedAt(d.getRecord().getId(), now);
        }
        return scored;
    }

    private String buildJsonWhere(List<Object> args,
                                  String userId,
                                  String agentId,
                                  String runId,
                                  Map<String, Object> filters) {
        Map<String, Object> eff = new HashMap<>();
        if (filters != null) {
            eff.putAll(filters);
        }
        if (userId != null) {
            eff.put("user_id", userId);
        }
        if (agentId != null) {
            eff.put("agent_id", agentId);
        }
        if (runId != null) {
            eff.put("run_id", runId);
        }

        StringBuilder where = new StringBuilder();
        for (Map.Entry<String, Object> e : eff.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                continue;
            }
            where.append(" AND json_extract(payload, '$.").append(e.getKey()).append("') = ?");
            args.add(e.getValue());
        }
        return where.toString();
    }

    private void updateLastAccessedAt(String memoryId, Instant at) {
        if (memoryId == null || memoryId.isBlank()) {
            return;
        }
        long id;
        try {
            id = Long.parseLong(memoryId.trim());
        } catch (Exception ex) {
            return;
        }
        try {
            Map<String, Object> payload = readPayloadById(id);
            if (payload == null) {
                return;
            }
            payload.put("last_accessed_at", at == null ? null : at.toString());
            try (Connection c = openConnection();
                 PreparedStatement ps = c.prepareStatement("UPDATE " + tableName + " SET payload=? WHERE id=?")) {
                ps.setString(1, json.toJson(payload));
                ps.setLong(2, id);
                ps.executeUpdate();
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private Map<String, Object> readPayloadById(long id) throws Exception {
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement("SELECT payload FROM " + tableName + " WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return json.fromJsonToMap(rs.getString("payload"));
            }
        }
    }

    private Map<String, Object> toPayload(MemoryRecord r) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", r.getContent() == null ? "" : r.getContent());
        payload.put("fulltext_content", r.getContent() == null ? "" : r.getContent());
        payload.put("user_id", r.getUserId() == null ? "" : r.getUserId());
        payload.put("agent_id", r.getAgentId() == null ? "" : r.getAgentId());
        payload.put("run_id", r.getRunId() == null ? "" : r.getRunId());
        payload.put("hash", r.getHash() == null ? "" : r.getHash());
        payload.put("category", r.getCategory() == null ? "" : r.getCategory());
        if (r.getScope() != null) {
            payload.put("scope", r.getScope());
        }
        payload.put("created_at", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        payload.put("updated_at", r.getUpdatedAt() == null ? null : r.getUpdatedAt().toString());
        payload.put("last_accessed_at", r.getLastAccessedAt() == null ? null : r.getLastAccessedAt().toString());
        payload.put("metadata", r.getMetadata() == null ? Collections.emptyMap() : r.getMetadata());
        // Python parity: allow extra top-level payload fields.
        if (r.getAttributes() != null) {
            for (Map.Entry<String, Object> e : r.getAttributes().entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) {
                    continue;
                }
                String k = e.getKey();
                // avoid overriding core reserved keys
                if (payload.containsKey(k) || "metadata".equals(k)) {
                    continue;
                }
                payload.put(k, e.getValue());
            }
        }
        return payload;
    }

    private MemoryRecord fromPayload(String id, Map<String, Object> payload) {
        MemoryRecord r = new MemoryRecord();
        r.setId(id);
        if (payload == null) {
            return r;
        }
        r.setContent(asString(payload.get("data")));
        r.setUserId(asString(payload.get("user_id")));
        r.setAgentId(asString(payload.get("agent_id")));
        r.setRunId(asString(payload.get("run_id")));
        r.setHash(asString(payload.get("hash")));
        r.setCategory(asString(payload.get("category")));
        r.setScope(asString(payload.get("scope")));
        Object meta = payload.get("metadata");
        if (meta instanceof Map) {
            Map<String, Object> safe = new HashMap<>();
            Map<?, ?> raw = (Map<?, ?>) meta;
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                safe.put(String.valueOf(e.getKey()), e.getValue());
            }
            r.setMetadata(safe);
        } else {
            r.setMetadata(new HashMap<>());
        }
        r.setCreatedAt(parseInstant(payload.get("created_at")));
        r.setUpdatedAt(parseInstant(payload.get("updated_at")));
        r.setLastAccessedAt(parseInstant(payload.get("last_accessed_at")));
        // Capture extra fields (top-level keys not in reserved set) into MemoryRecord.attributes.
        java.util.Set<String> reserved = new java.util.HashSet<>();
        reserved.add("data");
        reserved.add("fulltext_content");
        reserved.add("user_id");
        reserved.add("agent_id");
        reserved.add("run_id");
        reserved.add("hash");
        reserved.add("category");
        reserved.add("scope");
        reserved.add("created_at");
        reserved.add("updated_at");
        reserved.add("last_accessed_at");
        reserved.add("metadata");
        java.util.Map<String, Object> attrs = new java.util.HashMap<>();
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            if (e.getKey() == null || reserved.contains(e.getKey())) {
                continue;
            }
            attrs.put(e.getKey(), e.getValue());
        }
        r.setAttributes(attrs.isEmpty() ? null : attrs);
        return r;
    }

    /**
     * Best-effort partial update: merges provided fields into payload JSON without recomputing embedding.
     * Used by intelligent memory lifecycle hooks (access_count/search_count/...).
     */
    public void updatePayloadFields(String memoryId, Map<String, Object> fieldUpdates) {
        if (memoryId == null || memoryId.isBlank() || fieldUpdates == null || fieldUpdates.isEmpty()) {
            return;
        }
        long id;
        try {
            id = Long.parseLong(memoryId.trim());
        } catch (Exception ex) {
            return;
        }
        try {
            Map<String, Object> payload = readPayloadById(id);
            if (payload == null) {
                return;
            }
            for (Map.Entry<String, Object> e : fieldUpdates.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) {
                    continue;
                }
                if ("metadata".equals(e.getKey()) && e.getValue() instanceof Map) {
                    payload.put("metadata", e.getValue());
                } else {
                    payload.put(e.getKey(), e.getValue());
                }
            }
            // keep updated_at consistent if caller didn't set it
            if (!fieldUpdates.containsKey("updated_at")) {
                payload.put("updated_at", java.time.Instant.now().toString());
            }
            try (Connection c = openConnection();
                 PreparedStatement ps = c.prepareStatement("UPDATE " + tableName + " SET payload=? WHERE id=?")) {
                ps.setString(1, json.toJson(payload));
                ps.setLong(2, id);
                ps.executeUpdate();
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static String asString(Object v) {
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private static Instant parseInstant(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof String) {
            String s = (String) v;
            if (s.isBlank()) {
                return null;
            }
            try {
                return Instant.parse(s);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private void writeHistory(String memoryId,
                              String oldMemory,
                              String newMemory,
                              String event,
                              String userId,
                              String agentId,
                              boolean isDeleted) {
        if (memoryId == null || event == null) {
            return;
        }
        String sql = "INSERT INTO " + TABLE_HISTORY
                + " (id, memory_id, old_memory, new_memory, event, created_at, updated_at, is_deleted, actor_id, role)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Instant now = Instant.now();
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, historyIdGenerator.nextId());
            ps.setString(2, memoryId);
            ps.setString(3, oldMemory);
            ps.setString(4, newMemory);
            ps.setString(5, event);
            ps.setLong(6, now.toEpochMilli());
            ps.setLong(7, now.toEpochMilli());
            ps.setInt(8, isDeleted ? 1 : 0);
            ps.setString(9, agentId != null && !agentId.isBlank() ? agentId : userId);
            ps.setString(10, "sdk");
            ps.executeUpdate();
        } catch (Exception ignored) {
            // best-effort
        }
    }
}

