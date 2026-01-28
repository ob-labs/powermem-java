package com.oceanbase.powermem.sdk.storage.oceanbase;

import com.oceanbase.powermem.sdk.json.JacksonJsonCodec;
import com.oceanbase.powermem.sdk.json.JsonCodec;
import com.oceanbase.powermem.sdk.model.MemoryRecord;
import com.oceanbase.powermem.sdk.storage.base.OutputData;
import com.oceanbase.powermem.sdk.storage.base.VectorStore;
import com.oceanbase.powermem.sdk.util.SnowflakeIdGenerator;
import com.oceanbase.powermem.sdk.util.VectorMath;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OceanBase vector store implementation (Java migration target).
 *
 * <p>Python reference: {@code src/powermem/storage/oceanbase/oceanbase.py} (OceanBaseVectorStore)</p>
 */
public class OceanBaseVectorStore implements VectorStore {
    private static final String DEFAULT_TABLE = "memories";
    private static final String TABLE_HISTORY = "history";
    private static final Logger LOG = Logger.getLogger(OceanBaseVectorStore.class.getName());
    private static final java.util.Set<String> SUPPORTED_FULLTEXT_PARSERS = java.util.Set.of(
            "ik", "ngram", "ngram2", "beng", "space"
    );

    private final com.oceanbase.powermem.sdk.config.VectorStoreConfig config;
    private final JsonCodec json = new JacksonJsonCodec();
    private final SnowflakeIdGenerator historyIdGenerator = SnowflakeIdGenerator.defaultGenerator();

    private volatile boolean initialized;
    private volatile boolean hasVectorColumn;
    private volatile boolean hasPayloadColumn;
    private volatile boolean hasVectorJsonColumn;
    // Denormalized columns (Python OceanBase schema parity; best-effort)
    private volatile boolean hasUserIdColumn;
    private volatile boolean hasAgentIdColumn;
    private volatile boolean hasRunIdColumn;
    private volatile boolean hasHashColumn;
    private volatile boolean hasCategoryColumn;
    private volatile boolean hasCreatedAtColumn;
    private volatile boolean hasUpdatedAtColumn;
    private volatile boolean hasFulltextColumn;
    private volatile boolean fulltextIndexChecked;
    private volatile boolean fulltextIndexReady;
    private String tableName;

    public OceanBaseVectorStore() {
        this(new com.oceanbase.powermem.sdk.config.VectorStoreConfig());
    }

    public OceanBaseVectorStore(com.oceanbase.powermem.sdk.config.VectorStoreConfig config) {
        this.config = config == null ? new com.oceanbase.powermem.sdk.config.VectorStoreConfig() : config;
        this.tableName = (this.config.getCollectionName() == null || this.config.getCollectionName().isBlank())
                ? DEFAULT_TABLE
                : this.config.getCollectionName();
        ensureInitialized();
    }

    private String jdbcUrl() {
        String host = config.getHost() == null || config.getHost().isBlank() ? "127.0.0.1" : config.getHost();
        int port = config.getPort() > 0 ? config.getPort() : 2881;
        String db = config.getDatabase() == null || config.getDatabase().isBlank() ? "ai_work" : config.getDatabase();
        StringBuilder sb = new StringBuilder();
        sb.append("jdbc:mysql://").append(host).append(":").append(port).append("/").append(db);
        // Safe defaults for OceanBase MySQL mode
        sb.append("?useUnicode=true&characterEncoding=UTF-8");
        sb.append("&useSSL=false");
        sb.append("&allowPublicKeyRetrieval=true");
        sb.append("&serverTimezone=UTC");
        sb.append("&connectTimeout=").append(Math.max(1, config.getTimeoutSeconds()) * 1000);
        sb.append("&socketTimeout=").append(Math.max(1, config.getTimeoutSeconds()) * 1000);
        return sb.toString();
    }

    private Connection openConnection() throws Exception {
        String user = config.getUser();
        String pass = config.getPassword();
        if (user == null || user.isBlank()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("OceanBase user is required (vector_store.user)");
        }
        if (pass == null) {
            pass = "";
        }
        return DriverManager.getConnection(jdbcUrl(), user, pass);
    }

    private synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        try (Connection c = openConnection()) {
            // Base schema aligned with Java SQLiteVectorStore (vector JSON + payload JSON).
            // Keep payload as JSON (or text JSON) for json_extract filtering.
            //
            // IMPORTANT: If the table already exists with a different schema (e.g. created manually),
            // CREATE TABLE IF NOT EXISTS will NOT change it. We must detect missing columns and
            // best-effort migrate via ALTER TABLE to avoid runtime failures like:
            //   "Unknown column 'payload' in 'where clause'"
            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + tableName + " (\n" +
                            "  id BIGINT PRIMARY KEY,\n" +
                            "  vector LONGTEXT NOT NULL,\n" +
                            "  payload JSON NOT NULL\n" +
                            ")")) {
                ps.execute();
            } catch (Exception ignored) {
                // If a pre-existing table has an incompatible definition, ignore here and rely on column checks below.
            }

            // Ensure required columns exist even for pre-existing tables.
            hasPayloadColumn = ensureColumn(c, tableName, "payload", "JSON");
            if (!hasPayloadColumn) {
                // Fallback to LONGTEXT; MySQL/OB JSON functions can typically work with JSON strings.
                hasPayloadColumn = ensureColumn(c, tableName, "payload", "LONGTEXT");
            }
            hasVectorJsonColumn = ensureColumn(c, tableName, "vector", "LONGTEXT");

            if (!hasPayloadColumn) {
                throw new RuntimeException(
                        "OceanBase table '" + tableName + "' is missing required column 'payload' and auto-migration failed. " +
                                "Please set OCEANBASE_COLLECTION to a new table name (e.g. memories_java_debug) or create/alter the table to include a JSON/TEXT 'payload' column.");
            }
            if (!hasVectorJsonColumn) {
                throw new RuntimeException(
                        "OceanBase table '" + tableName + "' is missing required column 'vector' and auto-migration failed. " +
                                "Please set OCEANBASE_COLLECTION to a new table name (e.g. memories_java_debug) or create/alter the table to include a TEXT 'vector' column.");
            }

            // Denormalized columns to match Python OceanBase schema (best-effort).
            // These columns speed up filtering and avoid json_extract quirks.
            hasUserIdColumn = ensureColumn(c, tableName, "user_id", "VARCHAR(128)");
            hasAgentIdColumn = ensureColumn(c, tableName, "agent_id", "VARCHAR(128)");
            hasRunIdColumn = ensureColumn(c, tableName, "run_id", "VARCHAR(128)");
            hasHashColumn = ensureColumn(c, tableName, "hash", "VARCHAR(32)");
            hasCategoryColumn = ensureColumn(c, tableName, "category", "VARCHAR(64)");
            hasCreatedAtColumn = ensureColumn(c, tableName, "created_at", "VARCHAR(128)");
            hasUpdatedAtColumn = ensureColumn(c, tableName, "updated_at", "VARCHAR(128)");
            hasFulltextColumn = ensureColumn(c, tableName, "fulltext_content", "LONGTEXT");

            // Try to enable native VECTOR column for ANN search.
            // OceanBase version requirement: 4.3.5.1+ (vector index + fulltext in same table).
            // We keep it best-effort: if VECTOR type isn't supported in current cluster, fallback to brute-force.
            int dims = config.getEmbeddingModelDims();
            if (dims > 0) {
                try {
                    // Add column if missing (OceanBase MySQL mode supports IF NOT EXISTS in newer versions; be defensive).
                    try (PreparedStatement ps = c.prepareStatement(
                            "ALTER TABLE " + tableName + " ADD COLUMN embedding VECTOR(" + dims + ")")) {
                        ps.execute();
                    }
                } catch (Exception ignored) {
                    // ignore - column may already exist or VECTOR unsupported
                }
                // Detect whether embedding column exists
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=? AND COLUMN_NAME='embedding'")) {
                    ps.setString(1, tableName);
                    try (ResultSet rs = ps.executeQuery()) {
                        hasVectorColumn = rs.next();
                    }
                } catch (Exception ignored) {
                    hasVectorColumn = false;
                }

                // Best-effort: detect vector dimension mismatch (Python throws on mismatch)
                if (hasVectorColumn) {
                    Integer existingDims = tryGetVectorDims(c, tableName, "embedding");
                    if (existingDims != null && existingDims > 0 && existingDims != dims) {
                        throw new RuntimeException(
                                "Vector dimension mismatch for table '" + tableName + "': existing embedding VECTOR(" + existingDims + ") " +
                                        "but requested dims=" + dims + ". Please use a different OCEANBASE_COLLECTION or recreate the table.");
                    }
                }

                // Try to create ANN vector index (best-effort).
                if (hasVectorColumn) {
                    String indexType = config.getIndexType() == null ? "HNSW" : config.getIndexType().trim().toUpperCase();
                    String metricType = config.getMetricType() == null ? "cosine" : config.getMetricType().trim().toLowerCase();
                    String vidx = config.getVectorIndexName() == null || config.getVectorIndexName().isBlank()
                            ? "vidx"
                            : config.getVectorIndexName().trim();
                    // Note: exact syntax may vary by OceanBase minor versions; keep best-effort and don't fail init.
                    String ddl1 = "CREATE VECTOR INDEX " + vidx + " ON " + tableName + " (embedding) USING " + indexType
                            + " WITH (metric_type='" + metricType + "')";
                    try (PreparedStatement ps = c.prepareStatement(ddl1)) {
                        ps.execute();
                    } catch (Exception ignored) {
                        // maybe index exists or syntax differs; ignore
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + " (\n" +
                            "  id VARCHAR(64) PRIMARY KEY,\n" +
                            "  memory_id VARCHAR(64),\n" +
                            "  old_memory LONGTEXT,\n" +
                            "  new_memory LONGTEXT,\n" +
                            "  event VARCHAR(10),\n" +
                            "  created_at BIGINT,\n" +
                            "  updated_at BIGINT,\n" +
                            "  is_deleted INT,\n" +
                            "  actor_id VARCHAR(64),\n" +
                            "  role VARCHAR(32)\n" +
                            ")")) {
                ps.execute();
            }
            initialized = true;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to initialize OceanBase schema: " + ex.getMessage(), ex);
        }
    }

    /**
     * OceanBase hybrid search (vector + full-text), Python parity:
     * - if queryText is present and hybridSearch enabled, combine vector search and fulltext search.
     */
    public java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> searchHybrid(
            String queryText,
            float[] queryEmbedding,
            int topK,
            String userId,
            String agentId,
            String runId,
            java.util.Map<String, Object> filters) {
        ensureInitialized();
        int candidateLimit = topK <= 0 ? 5 : topK;
        String q = queryText == null ? "" : queryText.trim();
        if (q.isEmpty() || !config.isHybridSearch() || !hasFulltextColumn) {
            return search(queryEmbedding, candidateLimit, userId, agentId, runId, filters);
        }

        Instant now = Instant.now();

        // 1) vector candidates (no last_accessed update yet)
        CompletableFuture<java.util.List<OutputData>> vectorFuture =
                CompletableFuture.supplyAsync(() -> vectorSearchInternal(queryEmbedding, candidateLimit, userId, agentId, runId, filters, false))
                        .exceptionally(ex -> {
                            LOG.log(Level.WARNING, "OceanBase hybrid: vector branch failed; continuing with FTS only. cause=" + ex.getMessage(), ex);
                            return java.util.Collections.emptyList();
                        });

        // 2) full-text candidates
        CompletableFuture<java.util.List<OutputData>> ftsFuture =
                CompletableFuture.supplyAsync(() -> fulltextSearchInternal(q, candidateLimit, userId, agentId, runId, filters))
                        .exceptionally(ex -> {
                            LOG.log(Level.WARNING, "OceanBase hybrid: FTS branch failed; continuing with vector only. cause=" + ex.getMessage(), ex);
                            return java.util.Collections.emptyList();
                        });

        java.util.List<OutputData> vectorResults = vectorFuture.join();
        java.util.List<OutputData> ftsResults = ftsFuture.join();

        // 3) fuse
        String method = config.getFusionMethod() == null ? "rrf" : config.getFusionMethod().trim().toLowerCase();
        java.util.List<OutputData> fused;
        if ("weighted".equals(method)) {
            fused = weightedFusion(vectorResults, ftsResults, candidateLimit, config.getVectorWeight(), config.getFtsWeight());
        } else {
            fused = rrfFusion(vectorResults, ftsResults, candidateLimit, config.getRrfK(), config.getVectorWeight(), config.getFtsWeight());
        }

        // 4) update last_accessed for final results
        for (OutputData d : fused) {
            if (d == null || d.getRecord() == null) continue;
            d.getRecord().setLastAccessedAt(now);
            updateLastAccessedAt(d.getRecord().getId(), now);
        }
        return fused;
    }

    private static boolean ensureColumn(Connection c, String table, String col, String ddlType) {
        if (c == null || table == null || table.isBlank() || col == null || col.isBlank() || ddlType == null || ddlType.isBlank()) {
            return false;
        }
        try {
            if (columnExists(c, table, col)) {
                return true;
            }
        } catch (Exception ignored) {}
        try (PreparedStatement ps = c.prepareStatement("ALTER TABLE " + table + " ADD COLUMN " + col + " " + ddlType)) {
            ps.execute();
        } catch (Exception ignored) {
            // ignore
        }
        try {
            return columnExists(c, table, col);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean columnExists(Connection c, String table, String col) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")) {
            ps.setString(1, table);
            ps.setString(2, col);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static Integer tryGetVectorDims(Connection c, String table, String col) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")) {
            ps.setString(1, table);
            ps.setString(2, col);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String t = rs.getString(1);
                if (t == null) {
                    return null;
                }
                String s = t.trim().toLowerCase();
                int l = s.indexOf('(');
                int r = s.indexOf(')', l + 1);
                if (l < 0 || r <= l) {
                    return null;
                }
                String num = s.substring(l + 1, r).trim();
                return Integer.parseInt(num);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void upsert(com.oceanbase.powermem.sdk.model.MemoryRecord record, float[] embedding) {
        if (record == null || record.getId() == null || record.getId().isBlank()) {
            throw new com.oceanbase.powermem.sdk.exception.ApiException("memory.id is required");
        }
        ensureInitialized();
        long id = Long.parseLong(record.getId().trim());
        Map<String, Object> payload = toPayload(record);
        String vectorJson = json.toJson(embedding == null ? new float[0] : embedding);
        String payloadJson = json.toJson(payload);

        // Upsert: MySQL syntax works in OceanBase MySQL mode.
        // Also populate denormalized columns (user_id/agent_id/run_id/...) when present for faster filtering.
        List<String> cols = new ArrayList<>();
        cols.add("id");
        cols.add("vector");
        cols.add("payload");
        if (hasUserIdColumn) cols.add("user_id");
        if (hasAgentIdColumn) cols.add("agent_id");
        if (hasRunIdColumn) cols.add("run_id");
        if (hasHashColumn) cols.add("hash");
        if (hasCategoryColumn) cols.add("category");
        if (hasCreatedAtColumn) cols.add("created_at");
        if (hasUpdatedAtColumn) cols.add("updated_at");
        if (hasFulltextColumn) cols.add("fulltext_content");
        if (hasVectorColumn) cols.add("embedding");

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(cols.get(i));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(") ON DUPLICATE KEY UPDATE ");
        for (int i = 1; i < cols.size(); i++) {
            if (i > 1) sql.append(", ");
            String cn = cols.get(i);
            sql.append(cn).append("=VALUES(").append(cn).append(")");
        }

        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int p = 1;
            ps.setLong(p++, id);
            ps.setString(p++, vectorJson);
            ps.setString(p++, payloadJson);
            if (hasUserIdColumn) ps.setString(p++, asString(payload.get("user_id")));
            if (hasAgentIdColumn) ps.setString(p++, asString(payload.get("agent_id")));
            if (hasRunIdColumn) ps.setString(p++, asString(payload.get("run_id")));
            if (hasHashColumn) ps.setString(p++, asString(payload.get("hash")));
            if (hasCategoryColumn) ps.setString(p++, asString(payload.get("category")));
            if (hasCreatedAtColumn) ps.setString(p++, asString(payload.get("created_at")));
            if (hasUpdatedAtColumn) ps.setString(p++, asString(payload.get("updated_at")));
            if (hasFulltextColumn) ps.setString(p++, asString(payload.get("fulltext_content")));
            if (hasVectorColumn) {
                // Best-effort: rely on OceanBase to cast from JSON string to VECTOR type if supported.
                ps.setString(p, vectorJson);
            }
            ps.executeUpdate();
            // Write history best-effort based on whether record looks new or updated
            String event = record.getUpdatedAt() != null && record.getCreatedAt() != null
                    && record.getUpdatedAt().isAfter(record.getCreatedAt())
                    ? "UPDATE"
                    : "ADD";
            writeHistory(record.getId(), null, record.getContent(), event, record.getUserId(), record.getAgentId(), false);
        } catch (Exception ex) {
            throw new RuntimeException("OceanBase upsert failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public com.oceanbase.powermem.sdk.model.MemoryRecord get(String memoryId, String userId, String agentId) {
        ensureInitialized();
        if (memoryId == null || memoryId.isBlank()) {
            return null;
        }
        long id;
        try {
            id = Long.parseLong(memoryId.trim());
        } catch (Exception ex) {
            return null;
        }
        StringBuilder sql = new StringBuilder("SELECT id, payload FROM " + tableName + " WHERE id=?");
        List<Object> args = new ArrayList<>();
        args.add(id);
        // Access control filters (prefer denormalized columns; fallback to payload json)
        sql.append(buildJsonWhere(args, userId, agentId, null, null));

        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String idStr = Long.toString(rs.getLong("id"));
                Map<String, Object> payload = json.fromJsonToMap(rs.getString("payload"));
                return fromPayload(idStr, payload);
            }
        } catch (Exception ex) {
            throw new RuntimeException("OceanBase get failed: " + ex.getMessage(), ex);
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
            throw new RuntimeException("OceanBase delete failed: " + ex.getMessage(), ex);
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
            throw new RuntimeException("OceanBase deleteAll failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public java.util.List<com.oceanbase.powermem.sdk.model.MemoryRecord> list(String userId, String agentId, String runId, int offset, int limit) {
        ensureInitialized();
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
            throw new RuntimeException("OceanBase list failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> search(
            float[] queryEmbedding,
            int topK,
            String userId,
            String agentId,
            String runId,
            java.util.Map<String, Object> filters) {
        ensureInitialized();
        int k = topK <= 0 ? 5 : topK;
        return vectorSearchInternal(queryEmbedding, k, userId, agentId, runId, filters, true);
    }

    private java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> vectorSearchInternal(
            float[] queryEmbedding,
            int topK,
            String userId,
            String agentId,
            String runId,
            java.util.Map<String, Object> filters,
            boolean updateAccess) {
        int k = topK <= 0 ? 5 : topK;
        Instant now = Instant.now();

        // Prefer SQL distance computation with ANN vector index when available.
        if (hasVectorColumn && queryEmbedding != null) {
            String metricType = config.getMetricType() == null ? "cosine" : config.getMetricType().trim().toLowerCase();
            // Best-effort function names (may vary across versions). If unsupported, fallback to brute-force.
            String distFunc;
            boolean higherIsBetter = false;
            if ("inner_product".equals(metricType) || "ip".equals(metricType)) {
                distFunc = "inner_product";
                higherIsBetter = true;
            } else if ("l2".equals(metricType)) {
                distFunc = "l2_distance";
            } else {
                distFunc = "cosine_distance";
            }

            StringBuilder sql = new StringBuilder("SELECT id, payload, " + distFunc + "(embedding, ?) AS d FROM " + tableName + " WHERE 1=1");
            List<Object> args = new ArrayList<>();
            sql.append(buildJsonWhere(args, userId, agentId, runId, filters));
            sql.append(" ORDER BY d ").append(higherIsBetter ? "DESC" : "ASC").append(" LIMIT ?");

            String queryVecJson = json.toJson(queryEmbedding);
            List<OutputData> out = new ArrayList<>();
            try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
                int idx = 1;
                ps.setString(idx++, queryVecJson);
                for (Object a : args) {
                    ps.setObject(idx++, a);
                }
                ps.setInt(idx, k);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String idStr = Long.toString(rs.getLong("id"));
                        Map<String, Object> payload = json.fromJsonToMap(rs.getString("payload"));
                        MemoryRecord record = fromPayload(idStr, payload);
                        double d = rs.getDouble("d");
                        double score;
                        if (higherIsBetter) {
                            score = d;
                        } else {
                            // Convert distance to a "higher is better" score.
                            score = 1.0 / (1.0 + Math.max(0.0, d));
                        }
                        record.setLastAccessedAt(now);
                        out.add(new OutputData(record, score));
                    }
                }
                if (updateAccess) {
                    for (OutputData d : out) {
                        if (d == null || d.getRecord() == null) continue;
                        updateLastAccessedAt(d.getRecord().getId(), now);
                    }
                }
                return out;
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "OceanBase vector SQL distance failed; fallback to brute-force. cause=" + ex.getMessage(), ex);
            }
        }

        // Fallback brute-force (compatibility path): scan JSON vector and compute cosine in Java.
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
            throw new RuntimeException("OceanBase search failed: " + ex.getMessage(), ex);
        }
        scored.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (scored.size() > k) {
            scored = new ArrayList<>(scored.subList(0, k));
        }
        if (updateAccess) {
            for (OutputData d : scored) {
                if (d == null || d.getRecord() == null) continue;
                updateLastAccessedAt(d.getRecord().getId(), now);
            }
        }
        return scored;
    }

    private java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> fulltextSearchInternal(
            String queryText,
            int topK,
            String userId,
            String agentId,
            String runId,
            java.util.Map<String, Object> filters) {
        int k = topK <= 0 ? 5 : topK;
        String q = queryText == null ? "" : queryText.trim();
        if (q.isEmpty() || !hasFulltextColumn) {
            return new ArrayList<>();
        }
        try {
            ensureFulltextIndex();
        } catch (Exception ignored) {
            LOG.log(Level.WARNING, "OceanBase FTS: ensureFulltextIndex failed; will try MATCH then fallback to LIKE. cause=" + ignored.getMessage(), ignored);
        }

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, payload, MATCH(fulltext_content) AGAINST (? IN NATURAL LANGUAGE MODE) AS s FROM ")
                .append(tableName).append(" WHERE 1=1");
        // Filters first
        sql.append(buildJsonWhere(args, userId, agentId, runId, filters));
        // FTS condition
        sql.append(" AND MATCH(fulltext_content) AGAINST (? IN NATURAL LANGUAGE MODE)");
        sql.append(" ORDER BY s DESC LIMIT ?");

        List<OutputData> out = new ArrayList<>();
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int p = 1;
            ps.setString(p++, q);
            for (Object a : args) {
                ps.setObject(p++, a);
            }
            ps.setString(p++, q);
            ps.setInt(p, k);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String idStr = Long.toString(rs.getLong("id"));
                    Map<String, Object> payload = json.fromJsonToMap(rs.getString("payload"));
                    MemoryRecord record = fromPayload(idStr, payload);
                    double score = rs.getDouble("s");
                    out.add(new OutputData(record, score));
                }
            }
            return out;
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "OceanBase FTS MATCH failed; fallback to LIKE. cause=" + ex.getMessage(), ex);
        }

        // LIKE fallback (best-effort)
        args.clear();
        sql.setLength(0);
        sql.append("SELECT id, payload FROM ").append(tableName).append(" WHERE 1=1");
        sql.append(buildJsonWhere(args, userId, agentId, runId, filters));
        sql.append(" AND fulltext_content LIKE ? LIMIT ?");
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int p = 1;
            for (Object a : args) {
                ps.setObject(p++, a);
            }
            ps.setString(p++, "%" + q + "%");
            ps.setInt(p, k);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String idStr = Long.toString(rs.getLong("id"));
                    Map<String, Object> payload = json.fromJsonToMap(rs.getString("payload"));
                    MemoryRecord record = fromPayload(idStr, payload);
                    out.add(new OutputData(record, 1.0));
                }
            }
        } catch (Exception ignored2) {
            // ignore
        }
        return out;
    }

    private synchronized void ensureFulltextIndex() throws Exception {
        if (fulltextIndexChecked) {
            if (fulltextIndexReady) {
                return;
            }
            // Already checked and not ready; do not retry aggressively.
            return;
        }
        fulltextIndexChecked = true;
        if (!hasFulltextColumn) {
            fulltextIndexReady = false;
            return;
        }
        // Check existing indexes
        try (Connection c = openConnection();
             PreparedStatement ps = c.prepareStatement("SHOW INDEX FROM " + tableName);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String indexType = rs.getString("Index_type");
                String colName = rs.getString("Column_name");
                if (indexType != null && "FULLTEXT".equalsIgnoreCase(indexType)
                        && colName != null && colName.toLowerCase().contains("fulltext_content")) {
                    fulltextIndexReady = true;
                    LOG.info("OceanBase FTS: FULLTEXT index already exists for " + tableName + ".fulltext_content");
                    return;
                }
            }
        } catch (Exception ignored) {
            // ignore
        }

        // Auto-downgrade strategy (Python has strict validation; Java keeps best-effort for compatibility):
        // 1) Try without parser
        // 2) If configured parser is valid, try WITH PARSER <configured>
        // 3) If still failing, try other supported parsers in order
        Exception last = null;
        if (tryCreateFulltextIndex(null)) {
            fulltextIndexReady = true;
            LOG.info("OceanBase FTS: created FULLTEXT index on " + tableName + ".fulltext_content");
            return;
        }
        String configured = config.getFulltextParser();
        String normalized = normalizeFulltextParser(configured);
        if (configured != null && !configured.isBlank() && normalized == null) {
            LOG.warning("OceanBase FTS: unsupported fulltext parser '" + configured.trim()
                    + "'. Supported: " + SUPPORTED_FULLTEXT_PARSERS + ". Will try other strategies and may fallback to LIKE.");
        }
        java.util.List<String> candidates = new java.util.ArrayList<>();
        if (normalized != null) {
            candidates.add(normalized);
        }
        for (String p : SUPPORTED_FULLTEXT_PARSERS) {
            if (normalized == null || !p.equals(normalized)) {
                candidates.add(p);
            }
        }

        for (String p : candidates) {
            try {
                if (tryCreateFulltextIndex(p)) {
                    fulltextIndexReady = true;
                    LOG.info("OceanBase FTS: created FULLTEXT index with parser=" + p);
                    return;
                }
            } catch (Exception ex) {
                last = ex;
                LOG.log(Level.WARNING, "OceanBase FTS: failed to create FULLTEXT index with parser=" + p + " (will try downgrade)", ex);
            }
        }

        fulltextIndexReady = false;
        if (last != null) {
            LOG.log(Level.WARNING, "OceanBase FTS: FULLTEXT index is not available; will fallback to LIKE. last_error=" + last.getMessage(), last);
        } else {
            LOG.warning("OceanBase FTS: FULLTEXT index is not available; will fallback to LIKE.");
        }
    }

    private boolean tryCreateFulltextIndex(String parser) throws Exception {
        String sql;
        if (parser == null || parser.isBlank()) {
            sql = "ALTER TABLE " + tableName + " ADD FULLTEXT INDEX fulltext_index_for_col_text (fulltext_content)";
        } else {
            // parser is validated by normalizeFulltextParser -> safe to embed
            sql = "ALTER TABLE " + tableName + " ADD FULLTEXT INDEX fulltext_index_for_col_text (fulltext_content) WITH PARSER " + parser;
        }
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
            return true;
        } catch (Exception ex) {
            // ignore; caller decides downgrade/logging
            return false;
        }
    }

    private static String normalizeFulltextParser(String parser) {
        if (parser == null) return null;
        String p = parser.trim().toLowerCase();
        if (p.isEmpty()) return null;
        // safety: only allow known tokens to avoid SQL injection
        if (!SUPPORTED_FULLTEXT_PARSERS.contains(p)) {
            return null;
        }
        return p;
    }

    private java.util.List<OutputData> rrfFusion(
            java.util.List<OutputData> vectorResults,
            java.util.List<OutputData> ftsResults,
            int limit,
            int k,
            double vectorWeight,
            double ftsWeight) {
        int safeK = k <= 0 ? 60 : k;
        double vw = vectorWeight <= 0 ? 0.5 : vectorWeight;
        double tw = ftsWeight <= 0 ? 0.5 : ftsWeight;
        java.util.Map<String, ScoreEntry> map = new java.util.HashMap<>();

        java.util.List<OutputData> vr = vectorResults == null ? java.util.Collections.<OutputData>emptyList() : vectorResults;
        java.util.List<OutputData> tr = ftsResults == null ? java.util.Collections.<OutputData>emptyList() : ftsResults;

        int rank = 1;
        for (OutputData r : vr) {
            if (r == null || r.getRecord() == null || r.getRecord().getId() == null) continue;
            String id = r.getRecord().getId();
            ScoreEntry e = map.computeIfAbsent(id, x -> new ScoreEntry(r));
            e.rrf += vw * (1.0 / (safeK + rank));
            if (e.vectorRank == null) {
                e.vectorRank = rank;
                e.vectorScore = r.getScore();
            }
            rank++;
        }
        rank = 1;
        for (OutputData r : tr) {
            if (r == null || r.getRecord() == null || r.getRecord().getId() == null) continue;
            String id = r.getRecord().getId();
            ScoreEntry e = map.computeIfAbsent(id, x -> new ScoreEntry(r));
            e.rrf += tw * (1.0 / (safeK + rank));
            if (e.ftsRank == null) {
                e.ftsRank = rank;
                e.ftsScore = r.getScore();
            }
            rank++;
        }

        java.util.List<OutputData> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, ScoreEntry> e : map.entrySet()) {
            OutputData d = e.getValue().data;
            attachFusionInfo(d.getRecord(), buildFusionInfo("rrf", vw, tw, safeK, e.getValue().vectorRank, e.getValue().ftsRank,
                    e.getValue().vectorScore, e.getValue().ftsScore, e.getValue().rrf, null, null));
            out.add(new OutputData(d.getRecord(), e.getValue().rrf));
        }
        out.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (out.size() > limit) {
            out = new java.util.ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    private java.util.List<OutputData> weightedFusion(
            java.util.List<OutputData> vectorResults,
            java.util.List<OutputData> ftsResults,
            int limit,
            double vectorWeight,
            double ftsWeight) {
        double vw = vectorWeight <= 0 ? 0.5 : vectorWeight;
        double tw = ftsWeight <= 0 ? 0.5 : ftsWeight;
        java.util.Map<String, WeightedEntry> map = new java.util.HashMap<>();

        java.util.List<OutputData> vr = vectorResults == null ? java.util.Collections.<OutputData>emptyList() : vectorResults;
        java.util.List<OutputData> tr = ftsResults == null ? java.util.Collections.<OutputData>emptyList() : ftsResults;

        for (OutputData r : vr) {
            if (r == null || r.getRecord() == null || r.getRecord().getId() == null) continue;
            String id = r.getRecord().getId();
            WeightedEntry e = map.computeIfAbsent(id, x -> new WeightedEntry(r));
            e.vectorScore = r.getScore();
        }
        for (OutputData r : tr) {
            if (r == null || r.getRecord() == null || r.getRecord().getId() == null) continue;
            String id = r.getRecord().getId();
            WeightedEntry e = map.computeIfAbsent(id, x -> new WeightedEntry(r));
            e.ftsScore = r.getScore();
        }

        // Normalize to 0..1 for stability (Python weighted fusion assumes normalized scores).
        double vMin = Double.POSITIVE_INFINITY, vMax = Double.NEGATIVE_INFINITY;
        double tMin = Double.POSITIVE_INFINITY, tMax = Double.NEGATIVE_INFINITY;
        for (WeightedEntry e : map.values()) {
            vMin = Math.min(vMin, e.vectorScore);
            vMax = Math.max(vMax, e.vectorScore);
            tMin = Math.min(tMin, e.ftsScore);
            tMax = Math.max(tMax, e.ftsScore);
        }
        double vRange = (vMax > vMin) ? (vMax - vMin) : 0.0;
        double tRange = (tMax > tMin) ? (tMax - tMin) : 0.0;

        java.util.List<OutputData> out = new java.util.ArrayList<>();
        for (WeightedEntry e : map.values()) {
            double vNorm = vRange > 0 ? (e.vectorScore - vMin) / vRange : (map.size() > 0 ? 1.0 : 0.0);
            double tNorm = tRange > 0 ? (e.ftsScore - tMin) / tRange : (map.size() > 0 ? 1.0 : 0.0);
            double score = vw * vNorm + tw * tNorm;
            attachFusionInfo(e.data.getRecord(), buildFusionInfo("weighted", vw, tw, null, null, null,
                    e.vectorScore, e.ftsScore, score, vNorm, tNorm));
            out.add(new OutputData(e.data.getRecord(), score));
        }
        out.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (out.size() > limit) {
            out = new java.util.ArrayList<>(out.subList(0, limit));
        }
        return out;
    }

    private static final class ScoreEntry {
        final OutputData data;
        double rrf;
        Integer vectorRank;
        Integer ftsRank;
        Double vectorScore;
        Double ftsScore;
        ScoreEntry(OutputData data) {
            this.data = data;
        }
    }

    private static final class WeightedEntry {
        final OutputData data;
        double vectorScore;
        double ftsScore;
        WeightedEntry(OutputData data) {
            this.data = data;
        }
    }

    private static void attachFusionInfo(MemoryRecord r, java.util.Map<String, Object> info) {
        if (r == null || info == null || info.isEmpty()) {
            return;
        }
        java.util.Map<String, Object> attrs = r.getAttributes();
        if (attrs == null) {
            attrs = new java.util.HashMap<>();
            r.setAttributes(attrs);
        }
        attrs.put("_fusion_info", info);
    }

    private static java.util.Map<String, Object> buildFusionInfo(
            String method,
            double vectorWeight,
            double ftsWeight,
            Integer rrfK,
            Integer vectorRank,
            Integer ftsRank,
            Double vectorScore,
            Double ftsScore,
            Double fusionScore,
            Double vectorScoreNorm,
            Double ftsScoreNorm) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("fusion_method", method);
        m.put("vector_weight", vectorWeight);
        m.put("fts_weight", ftsWeight);
        if (rrfK != null) m.put("rrf_k", rrfK);
        if (vectorRank != null) m.put("vector_rank", vectorRank);
        if (ftsRank != null) m.put("fts_rank", ftsRank);
        if (vectorScore != null) m.put("vector_score", vectorScore);
        if (ftsScore != null) m.put("fts_score", ftsScore);
        if (vectorScoreNorm != null) m.put("vector_score_norm", vectorScoreNorm);
        if (ftsScoreNorm != null) m.put("fts_score_norm", ftsScoreNorm);
        if (fusionScore != null) m.put("fusion_score", fusionScore);
        return m;
    }

    private String buildJsonWhere(List<Object> args,
                                  String userId,
                                  String agentId,
                                  String runId,
                                  Map<String, Object> filters) {
        // Python parity: merge user_id/agent_id/run_id into filters and allow complex filter syntax.
        Map<String, Object> eff = new HashMap<>();
        if (filters != null) {
            eff.putAll(filters);
        }
        if (userId != null && !userId.isBlank()) {
            eff.put("user_id", userId);
        }
        if (agentId != null && !agentId.isBlank()) {
            eff.put("agent_id", agentId);
        }
        if (runId != null && !runId.isBlank()) {
            eff.put("run_id", runId);
        }

        String clause = buildCondition(eff, args);
        if (clause == null || clause.isBlank()) {
            return "";
        }
        return " AND " + clause;
    }

    /**
     * Build SQL condition from filter object, supporting Python-style filters:
     * - {"field": "value"}
     * - {"field": ["a","b"]} -> IN
     * - {"field": {"gte": 1, "lte": 10}}
     * - {"AND": [ {...}, {...} ]} / {"OR": [ ... ]}
     *
     * <p>For payload fields:</p>
     * - use "payload.xxx" to force top-level payload JSON path
     * - use "metadata.xxx" to target nested user metadata payload.metadata.xxx
     * - otherwise, unknown keys default to payload.metadata.xxx (Python behavior: unknown keys treated as metadata)
     */
    private String buildCondition(Object filterObj, List<Object> args) {
        if (filterObj == null) {
            return null;
        }
        if (filterObj instanceof List) {
            List<?> list = (List<?>) filterObj;
            List<String> parts = new ArrayList<>();
            for (Object o : list) {
                String p = buildCondition(o, args);
                if (p != null && !p.isBlank()) {
                    parts.add("(" + p + ")");
                }
            }
            return parts.isEmpty() ? null : String.join(" AND ", parts);
        }
        if (!(filterObj instanceof Map)) {
            return null;
        }
        Map<?, ?> m = (Map<?, ?>) filterObj;

        if (m.containsKey("AND")) {
            return joinLogical("AND", m.get("AND"), args);
        }
        if (m.containsKey("OR")) {
            return joinLogical("OR", m.get("OR"), args);
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() == null) continue;
            String key = String.valueOf(e.getKey());
            if (key == null || key.isBlank()) continue;
            String cond = buildFieldCondition(key.trim(), e.getValue(), args);
            if (cond != null && !cond.isBlank()) {
                parts.add(cond);
            }
        }
        return parts.isEmpty() ? null : String.join(" AND ", parts);
    }

    private String joinLogical(String op, Object v, List<Object> args) {
        if (v instanceof List) {
            List<?> list = (List<?>) v;
            List<String> parts = new ArrayList<>();
            for (Object o : list) {
                String p = buildCondition(o, args);
                if (p != null && !p.isBlank()) {
                    parts.add("(" + p + ")");
                }
            }
            return parts.isEmpty() ? null : String.join(" " + op + " ", parts);
        }
        String p = buildCondition(v, args);
        return (p == null || p.isBlank()) ? null : "(" + p + ")";
    }

    private String buildFieldCondition(String key, Object value, List<Object> args) {
        String colExpr = columnExprForKey(key);
        if (colExpr == null) {
            return null;
        }
        if (value == null) {
            return colExpr + " IS NULL";
        }
        if (value instanceof List) {
            return buildInList(colExpr, (List<?>) value, args, false);
        }
        if (value instanceof Map) {
            Map<?, ?> ops = (Map<?, ?>) value;
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> e : ops.entrySet()) {
                if (e.getKey() == null) continue;
                String op = String.valueOf(e.getKey());
                if (op == null) continue;
                op = op.trim();
                if (op.startsWith("$")) op = op.substring(1);
                String part = buildOpCondition(colExpr, op, e.getValue(), args);
                if (part != null && !part.isBlank()) {
                    parts.add(part);
                }
            }
            return parts.isEmpty() ? null : String.join(" AND ", parts);
        }
        args.add(value);
        return colExpr + " = ?";
    }

    private String buildOpCondition(String colExpr, String op, Object v, List<Object> args) {
        if (op == null || op.isBlank()) return null;
        switch (op) {
            case "eq":
                args.add(v);
                return colExpr + " = ?";
            case "ne":
                args.add(v);
                return colExpr + " <> ?";
            case "gt":
                args.add(v);
                return colExpr + " > ?";
            case "gte":
                args.add(v);
                return colExpr + " >= ?";
            case "lt":
                args.add(v);
                return colExpr + " < ?";
            case "lte":
                args.add(v);
                return colExpr + " <= ?";
            case "in":
                if (!(v instanceof List)) return null;
                return buildInList(colExpr, (List<?>) v, args, false);
            case "nin":
                if (!(v instanceof List)) return null;
                return buildInList(colExpr, (List<?>) v, args, true);
            case "like":
                args.add(v == null ? null : String.valueOf(v));
                return colExpr + " LIKE ?";
            case "ilike":
                args.add(v == null ? null : String.valueOf(v).toLowerCase());
                return "LOWER(" + colExpr + ") LIKE ?";
            default:
                return null;
        }
    }

    private String buildInList(String colExpr, List<?> list, List<Object> args, boolean negate) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        StringBuilder in = new StringBuilder();
        if (negate) {
            in.append("NOT (");
        }
        in.append(colExpr).append(" IN (");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) in.append(", ");
            in.append("?");
            args.add(list.get(i));
        }
        in.append(")");
        if (negate) {
            in.append(")");
        }
        return in.toString();
    }

    private String columnExprForKey(String key) {
        if (key == null || key.isBlank()) return null;
        String k = key.trim();

        // Explicit prefixes
        if (k.startsWith("payload.")) {
            String pk = k.substring("payload.".length()).trim();
            return payloadJsonExtractExpr(pk);
        }
        if (k.startsWith("metadata.")) {
            String mk = k.substring("metadata.".length()).trim();
            return metadataJsonExtractExpr(mk);
        }

        // Denormalized columns first (Python parity)
        if ("user_id".equals(k) && hasUserIdColumn) return "user_id";
        if ("agent_id".equals(k) && hasAgentIdColumn) return "agent_id";
        if ("run_id".equals(k) && hasRunIdColumn) return "run_id";
        if ("hash".equals(k) && hasHashColumn) return "hash";
        if ("category".equals(k) && hasCategoryColumn) return "category";
        if ("created_at".equals(k) && hasCreatedAtColumn) return "created_at";
        if ("updated_at".equals(k) && hasUpdatedAtColumn) return "updated_at";

        // Common top-level payload keys
        if ("user_id".equals(k) || "agent_id".equals(k) || "run_id".equals(k)
                || "hash".equals(k) || "category".equals(k) || "created_at".equals(k) || "updated_at".equals(k)) {
            return payloadJsonExtractExpr(k);
        }

        // Default: treat as user metadata key (Python behavior)
        return metadataJsonExtractExpr(k);
    }

    private String payloadJsonExtractExpr(String key) {
        if (!isSafeJsonKey(key)) {
            return null;
        }
        return "json_extract(payload, '$." + key + "')";
    }

    private String metadataJsonExtractExpr(String metaKey) {
        if (!isSafeJsonKey(metaKey)) {
            return null;
        }
        return "json_extract(payload, '$.metadata." + metaKey + "')";
    }

    private static boolean isSafeJsonKey(String k) {
        if (k == null || k.isBlank()) return false;
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    /**
     * Best-effort partial update: merges provided fields into payload JSON without recomputing embedding.
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
            if (!fieldUpdates.containsKey("updated_at")) {
                payload.put("updated_at", Instant.now().toString());
            }
            List<String> sets = new ArrayList<>();
            sets.add("payload=?");
            if (hasUserIdColumn) sets.add("user_id=?");
            if (hasAgentIdColumn) sets.add("agent_id=?");
            if (hasRunIdColumn) sets.add("run_id=?");
            if (hasHashColumn) sets.add("hash=?");
            if (hasCategoryColumn) sets.add("category=?");
            if (hasCreatedAtColumn) sets.add("created_at=?");
            if (hasUpdatedAtColumn) sets.add("updated_at=?");
            if (hasFulltextColumn) sets.add("fulltext_content=?");

            StringBuilder sql = new StringBuilder();
            sql.append("UPDATE ").append(tableName).append(" SET ");
            for (int i = 0; i < sets.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(sets.get(i));
            }
            sql.append(" WHERE id=?");

            try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
                int p = 1;
                ps.setString(p++, json.toJson(payload));
                if (hasUserIdColumn) ps.setString(p++, asString(payload.get("user_id")));
                if (hasAgentIdColumn) ps.setString(p++, asString(payload.get("agent_id")));
                if (hasRunIdColumn) ps.setString(p++, asString(payload.get("run_id")));
                if (hasHashColumn) ps.setString(p++, asString(payload.get("hash")));
                if (hasCategoryColumn) ps.setString(p++, asString(payload.get("category")));
                if (hasCreatedAtColumn) ps.setString(p++, asString(payload.get("created_at")));
                if (hasUpdatedAtColumn) ps.setString(p++, asString(payload.get("updated_at")));
                if (hasFulltextColumn) ps.setString(p++, asString(payload.get("fulltext_content")));
                ps.setLong(p, id);
                ps.executeUpdate();
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private void updateLastAccessedAt(String memoryId, Instant at) {
        if (memoryId == null || memoryId.isBlank()) {
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put("last_accessed_at", at == null ? null : at.toString());
        updatePayloadFields(memoryId, update);
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
        if (r.getAttributes() != null) {
            for (Map.Entry<String, Object> e : r.getAttributes().entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) continue;
                String k = e.getKey();
                if (payload.containsKey(k) || "metadata".equals(k)) continue;
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
                if (e.getKey() == null) continue;
                safe.put(String.valueOf(e.getKey()), e.getValue());
            }
            r.setMetadata(safe);
        } else {
            r.setMetadata(new HashMap<>());
        }
        r.setCreatedAt(parseInstant(payload.get("created_at")));
        r.setUpdatedAt(parseInstant(payload.get("updated_at")));
        r.setLastAccessedAt(parseInstant(payload.get("last_accessed_at")));

        java.util.Set<String> reserved = new java.util.HashSet<>();
        Collections.addAll(reserved,
                "data", "fulltext_content", "user_id", "agent_id", "run_id", "hash", "category", "scope",
                "created_at", "updated_at", "last_accessed_at", "metadata");
        Map<String, Object> attrs = new HashMap<>();
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            if (e.getKey() == null || reserved.contains(e.getKey())) continue;
            attrs.put(e.getKey(), e.getValue());
        }
        r.setAttributes(attrs.isEmpty() ? null : attrs);
        return r;
    }

    private static String asString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private static Instant parseInstant(Object v) {
        if (!(v instanceof String)) return null;
        String s = (String) v;
        if (s.isBlank()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception ignored) {
            return null;
        }
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

