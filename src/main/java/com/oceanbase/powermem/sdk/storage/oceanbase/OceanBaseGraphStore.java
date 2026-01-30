package com.oceanbase.powermem.sdk.storage.oceanbase;

import com.oceanbase.powermem.sdk.exception.ApiException;
import com.oceanbase.powermem.sdk.integrations.embeddings.Embedder;
import com.oceanbase.powermem.sdk.integrations.llm.LLM;
import com.oceanbase.powermem.sdk.integrations.llm.LlmResponse;
import com.oceanbase.powermem.sdk.json.JacksonJsonCodec;
import com.oceanbase.powermem.sdk.json.JsonCodec;
import com.oceanbase.powermem.sdk.model.Message;
import com.oceanbase.powermem.sdk.prompts.graph.GraphPrompts;
import com.oceanbase.powermem.sdk.prompts.graph.GraphToolsPrompts;
import com.oceanbase.powermem.sdk.storage.base.GraphStore;
import com.oceanbase.powermem.sdk.util.Bm25;
import com.oceanbase.powermem.sdk.util.LlmJsonUtils;
import com.oceanbase.powermem.sdk.util.SnowflakeIdGenerator;
import com.oceanbase.powermem.sdk.util.TextTokenizer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OceanBase graph store implementation (Python parity).
 *
 * <p>Python reference: {@code src/powermem/storage/oceanbase/oceanbase_graph.py}</p>
 *
 * <p>Implements:
 * - entities + relationships tables (graph_entities / graph_relationships)
 * - LLM tools based extraction (extract_entities + establish_relationships + delete_graph_memory)
 * - ANN-style entity resolution via VECTOR distance (best-effort) with embedding_json brute-force fallback
 * - BM25 rerank of candidate relationships
 * - multi-hop expansion</p>
 */
public class OceanBaseGraphStore implements GraphStore {
    private static final Logger LOG = Logger.getLogger(OceanBaseGraphStore.class.getName());

    private final com.oceanbase.powermem.sdk.config.GraphStoreConfig config;
    private final Embedder embedder;
    private final LLM llm;
    private final JsonCodec json = new JacksonJsonCodec();
    private final SnowflakeIdGenerator idGenerator = SnowflakeIdGenerator.defaultGenerator();

    private volatile boolean initialized;
    private volatile boolean hasVectorColumn;

    public OceanBaseGraphStore(com.oceanbase.powermem.sdk.config.GraphStoreConfig config) {
        this(config, null, null);
    }

    public OceanBaseGraphStore(com.oceanbase.powermem.sdk.config.GraphStoreConfig config, Embedder embedder, LLM llm) {
        this.config = config;
        this.embedder = embedder;
        this.llm = llm;
        ensureInitialized();
    }

    @Override
    public Map<String, Object> add(String data, Map<String, Object> filters) {
        if (data == null || data.isBlank()) {
            return Collections.emptyMap();
        }
        ensureInitialized();
        Map<String, Object> scope = normalizeScope(filters);

        Map<String, String> entityTypeMap = extractEntitiesWithTypes(data, scope);
        List<RelationTriple> toAdd = extractRelations(data, entityTypeMap, scope);
        if (toAdd.isEmpty()) {
            return Map.of("deleted_entities", Collections.emptyList(), "added_entities", Collections.emptyList());
        }

        // Neighborhood search then delete decision (Python parity).
        List<Map<String, Object>> neighborhood = searchGraphNeighborhood(entityTypeMap.keySet(), scope, safeSearchLimit());
        String existingMemories = formatMemoriesForPrompt(neighborhood);
        List<RelationTriple> toDelete = decideDeletions(existingMemories, data, scope);

        List<Map<String, Integer>> deleted = deleteRelations(toDelete, scope);
        List<Map<String, Object>> added = addEntitiesAndRelationships(toAdd, entityTypeMap, scope);

        Map<String, Object> out = new HashMap<>();
        out.put("deleted_entities", deleted);
        out.put("added_entities", added);
        return out;
    }

    @Override
    public List<Map<String, Object>> search(String query, Map<String, Object> filters, int limit) {
        ensureInitialized();
        Map<String, Object> scope = normalizeScope(filters);
        int top = limit > 0 ? limit : safeBm25TopN();

        Map<String, String> seed = extractEntitiesWithTypes(query, scope);
        List<Map<String, Object>> candidates = multiHopNeighborhood(seed.keySet(), scope, safeMaxHops(), safeSearchLimit());
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> reranked = bm25Rerank(query, candidates, top);
        // Ensure Python search shape: destination key.
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : reranked) {
            if (r == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("source", r.get("source"));
            m.put("relationship", r.get("relationship"));
            Object dst = r.get("destination");
            if (dst == null) dst = r.get("target");
            m.put("destination", dst);
            out.add(m);
        }
        return out;
    }

    @Override
    public void deleteAll(Map<String, Object> filters) {
        ensureInitialized();
        Map<String, Object> scope = normalizeScope(filters);
        try (Connection c = openConnection()) {
            Set<Long> entityIds = new HashSet<>();

            StringBuilder where = new StringBuilder(" WHERE user_id = ?");
            List<Object> args = new ArrayList<>();
            args.add(String.valueOf(scope.get("user_id")));
            if (asNullableString(scope.get("agent_id")) != null) {
                where.append(" AND agent_id = ?");
                args.add(asNullableString(scope.get("agent_id")));
            }
            if (asNullableString(scope.get("run_id")) != null) {
                where.append(" AND run_id = ?");
                args.add(asNullableString(scope.get("run_id")));
            }

            String q = "SELECT source_entity_id, destination_entity_id FROM " + relationshipsTable() + where;
            try (PreparedStatement ps = c.prepareStatement(q)) {
                for (int i = 0; i < args.size(); i++) ps.setString(i + 1, String.valueOf(args.get(i)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entityIds.add(rs.getLong(1));
                        entityIds.add(rs.getLong(2));
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + relationshipsTable() + where)) {
                for (int i = 0; i < args.size(); i++) ps.setString(i + 1, String.valueOf(args.get(i)));
                ps.executeUpdate();
            }

            if (!entityIds.isEmpty()) {
                String in = placeholders(entityIds.size());
                String delEnt = "DELETE FROM " + entitiesTable() + " WHERE user_id = ?"
                        + (asNullableString(scope.get("agent_id")) != null ? " AND agent_id = ?" : "")
                        + (asNullableString(scope.get("run_id")) != null ? " AND run_id = ?" : "")
                        + " AND id IN (" + in + ")";
                try (PreparedStatement ps = c.prepareStatement(delEnt)) {
                    int p = 1;
                    ps.setString(p++, String.valueOf(scope.get("user_id")));
                    if (asNullableString(scope.get("agent_id")) != null) ps.setString(p++, asNullableString(scope.get("agent_id")));
                    if (asNullableString(scope.get("run_id")) != null) ps.setString(p++, asNullableString(scope.get("run_id")));
                    for (Long id : entityIds) ps.setLong(p++, id);
                    ps.executeUpdate();
                }
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "OceanBaseGraphStore.deleteAll failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<Map<String, Object>> getAll(Map<String, Object> filters, int limit) {
        ensureInitialized();
        Map<String, Object> scope = normalizeScope(filters);
        int top = limit > 0 ? limit : safeSearchLimit();

        String sql = "SELECT se.name AS source, r.relationship_type AS relationship, de.name AS target "
                + "FROM " + relationshipsTable() + " r "
                + "JOIN " + entitiesTable() + " se ON se.id = r.source_entity_id "
                + "JOIN " + entitiesTable() + " de ON de.id = r.destination_entity_id "
                + "WHERE r.user_id = ?"
                + (asNullableString(scope.get("agent_id")) != null ? " AND r.agent_id = ?" : "")
                + (asNullableString(scope.get("run_id")) != null ? " AND r.run_id = ?" : "")
                + " ORDER BY r.updated_at DESC LIMIT " + top;

        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            ps.setString(p++, String.valueOf(scope.get("user_id")));
            if (asNullableString(scope.get("agent_id")) != null) ps.setString(p++, asNullableString(scope.get("agent_id")));
            if (asNullableString(scope.get("run_id")) != null) ps.setString(p++, asNullableString(scope.get("run_id")));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("source", rs.getString("source"));
                    m.put("relationship", rs.getString("relationship"));
                    m.put("target", rs.getString("target"));
                    out.add(m);
                }
            }
        } catch (Exception ex) {
            throw new ApiException("OceanBaseGraphStore.getAll failed: " + ex.getMessage(), ex);
        }
        return out;
    }

    @Override
    public void reset() {
        ensureInitialized();
        try (Connection c = openConnection(); Statement st = c.createStatement()) {
            st.execute("TRUNCATE TABLE " + relationshipsTable());
            st.execute("TRUNCATE TABLE " + entitiesTable());
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "OceanBaseGraphStore.reset failed: " + ex.getMessage(), ex);
        }
    }

    // ---------------- init/schema ----------------

    private synchronized void ensureInitialized() {
        if (initialized) return;
        try (Connection c = openConnection(); Statement st = c.createStatement()) {
            String ent = entitiesTable();
            String rel = relationshipsTable();

            st.execute("CREATE TABLE IF NOT EXISTS " + ent + " (\n" +
                    "  id BIGINT PRIMARY KEY,\n" +
                    "  user_id VARCHAR(128) NOT NULL,\n" +
                    "  agent_id VARCHAR(128) NULL,\n" +
                    "  run_id VARCHAR(128) NULL,\n" +
                    "  name VARCHAR(512) NOT NULL,\n" +
                    "  entity_type VARCHAR(128) NULL,\n" +
                    "  embedding_json LONGTEXT NULL,\n" +
                    "  created_at TIMESTAMP NULL,\n" +
                    "  updated_at TIMESTAMP NULL\n" +
                    ")");

            st.execute("CREATE TABLE IF NOT EXISTS " + rel + " (\n" +
                    "  id BIGINT PRIMARY KEY,\n" +
                    "  user_id VARCHAR(128) NOT NULL,\n" +
                    "  agent_id VARCHAR(128) NULL,\n" +
                    "  run_id VARCHAR(128) NULL,\n" +
                    "  source_entity_id BIGINT NOT NULL,\n" +
                    "  relationship_type VARCHAR(255) NOT NULL,\n" +
                    "  destination_entity_id BIGINT NOT NULL,\n" +
                    "  created_at TIMESTAMP NULL,\n" +
                    "  updated_at TIMESTAMP NULL\n" +
                    ")");

            try {
                st.execute("CREATE INDEX IF NOT EXISTS idx_" + ent + "_scope ON " + ent + " (user_id, agent_id, run_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_" + rel + "_scope ON " + rel + " (user_id, agent_id, run_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_" + rel + "_src ON " + rel + " (source_entity_id)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_" + rel + "_dst ON " + rel + " (destination_entity_id)");
            } catch (Exception ignored) {}

            int dims = config == null ? 0 : config.getEmbeddingModelDims();
            if (dims > 0) {
                try {
                    try (PreparedStatement ps = c.prepareStatement("ALTER TABLE " + ent + " ADD COLUMN embedding VECTOR(" + dims + ")")) {
                        ps.execute();
                    }
                } catch (Exception ignored) {}
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=? AND COLUMN_NAME='embedding'")) {
                    ps.setString(1, ent);
                    try (ResultSet rs = ps.executeQuery()) {
                        hasVectorColumn = rs.next();
                    }
                } catch (Exception ignored) {
                    hasVectorColumn = false;
                }

                if (hasVectorColumn) {
                    Integer existingDims = tryGetVectorDims(c, ent, "embedding");
                    if (existingDims != null && existingDims > 0 && existingDims != dims) {
                        throw new RuntimeException("Vector dimension mismatch: existing VECTOR(" + existingDims + "), requested dims=" + dims);
                    }
                    String indexType = config.getIndexType() == null ? OceanBaseConstants.DEFAULT_INDEX_TYPE : config.getIndexType().trim().toUpperCase(Locale.ROOT);
                    String metricType = config.getMetricType() == null ? OceanBaseConstants.DEFAULT_OCEANBASE_VECTOR_METRIC_TYPE : config.getMetricType().trim().toLowerCase(Locale.ROOT);
                    String vidx = config.getVectorIndexName() == null || config.getVectorIndexName().isBlank()
                            ? OceanBaseConstants.DEFAULT_VIDX_NAME
                            : config.getVectorIndexName().trim();
                    String ddl = "CREATE VECTOR INDEX " + vidx + " ON " + ent + " (embedding) USING " + indexType
                            + " WITH (metric_type='" + metricType + "')";
                    try (PreparedStatement ps = c.prepareStatement(ddl)) {
                        ps.execute();
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ex) {
            throw new ApiException("Failed to initialize OceanBaseGraphStore: " + ex.getMessage(), ex);
        }
        initialized = true;
    }

    private static Integer tryGetVectorDims(Connection c, String table, String col) {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=?")) {
            ps.setString(1, table);
            ps.setString(2, col);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String t = rs.getString(1);
                if (t == null) return null;
                String s = t.trim().toLowerCase(Locale.ROOT);
                int l = s.indexOf('(');
                int r = s.indexOf(')', l + 1);
                if (l < 0 || r <= l) return null;
                return Integer.parseInt(s.substring(l + 1, r).trim());
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    // ---------------- extraction (LLM tools + fallbacks) ----------------

    private Map<String, String> extractEntitiesWithTypes(String text, Map<String, Object> scope) {
        if (llm != null) {
            try {
                String system = "You are a smart assistant who understands entities and their types in a given text. "
                        + "If user message contains self reference such as 'I', 'me', 'my' etc. "
                        + "then use " + String.valueOf(scope.get("user_id")) + " as the source entity. "
                        + "Extract all the entities from the text. "
                        + "***DO NOT*** answer the question itself if the given text is a question.";
                List<Message> msgs = List.of(
                        new Message("system", system),
                        new Message("user", text == null ? "" : text)
                );
                LlmResponse resp = llm.generateResponseWithTools(
                        msgs,
                        null,
                        List.of(GraphToolsPrompts.extractEntitiesTool(false)),
                        GraphToolsPrompts.toolChoiceFunction("extract_entities")
                );
                Map<String, String> parsed = parseExtractEntities(resp);
                if (!parsed.isEmpty()) return normalizeEntityTypeMap(parsed);
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Graph extract_entities failed; fallback. cause=" + ex.getMessage(), ex);
            }
        }
        // heuristic fallback
        Map<String, String> out = new HashMap<>();
        for (RelationTriple t : heuristicTriples(text)) {
            out.put(t.source, "entity");
            out.put(t.destination, "entity");
        }
        // If still empty (e.g., a pure query), fall back to token-based entities so search can work offline.
        if (out.isEmpty() && text != null && !text.isBlank()) {
            for (String tok : TextTokenizer.tokenize(text)) {
                if (tok == null || tok.isBlank()) continue;
                // skip ultra-short noise tokens
                if (tok.length() <= 1) continue;
                out.put(normalizeName(tok), "entity");
            }
        }
        return normalizeEntityTypeMap(out);
    }

    private List<RelationTriple> extractRelations(String text, Map<String, String> entityTypeMap, Map<String, Object> scope) {
        if (llm != null && text != null && !text.isBlank() && entityTypeMap != null && !entityTypeMap.isEmpty()) {
            try {
                String userIdentity = "user_id: " + scope.get("user_id")
                        + (asNullableString(scope.get("agent_id")) != null ? ", agent_id: " + scope.get("agent_id") : "")
                        + (asNullableString(scope.get("run_id")) != null ? ", run_id: " + scope.get("run_id") : "");
                String custom = config == null ? null : (config.getCustomExtractRelationsPrompt() != null ? config.getCustomExtractRelationsPrompt() : config.getCustomPrompt());
                String system = GraphPrompts.extractRelationsSystemPrompt(userIdentity, custom);
                String userMsg = "List of entities: " + entityTypeMap.keySet() + ". \n\nText: " + text;
                List<Message> msgs = List.of(
                        new Message("system", system),
                        new Message("user", userMsg)
                );
                LlmResponse resp = llm.generateResponseWithTools(
                        msgs,
                        null,
                        List.of(GraphToolsPrompts.establishRelationsTool(false)),
                        null
                );
                List<RelationTriple> parsed = parseEstablishRelations(resp);
                if (!parsed.isEmpty()) return normalizeRelationTriples(parsed);
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Graph establish_relationships failed; fallback. cause=" + ex.getMessage(), ex);
            }
        }
        return normalizeRelationTriples(heuristicTriples(text));
    }

    private List<RelationTriple> decideDeletions(String existingMemories, String newText, Map<String, Object> scope) {
        if (llm == null) return Collections.emptyList();
        try {
            String custom = config == null ? null : config.getCustomDeleteRelationsPrompt();
            String system = GraphPrompts.deleteRelationsSystemPrompt(String.valueOf(scope.get("user_id")), custom);
            String user = GraphPrompts.deleteRelationsUserPrompt(existingMemories, newText);
            List<Message> msgs = List.of(new Message("system", system), new Message("user", user));
            LlmResponse resp = llm.generateResponseWithTools(
                    msgs,
                    null,
                    List.of(GraphToolsPrompts.deleteGraphMemoryTool(false)),
                    null
            );
            return normalizeRelationTriples(parseDeleteGraphMemory(resp));
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Graph delete decision failed; ignore deletes. cause=" + ex.getMessage(), ex);
            return Collections.emptyList();
        }
    }

    // ---------------- entity/relationship operations ----------------

    private List<Map<String, Integer>> deleteRelations(List<RelationTriple> toDelete, Map<String, Object> scope) {
        if (toDelete == null || toDelete.isEmpty()) return Collections.emptyList();
        List<Map<String, Integer>> out = new ArrayList<>();
        for (RelationTriple t : toDelete) {
            int n = deleteRelationByNames(t, scope);
            out.add(Map.of("deleted_count", n));
        }
        return out;
    }

    private int deleteRelationByNames(RelationTriple t, Map<String, Object> scope) {
        if (t == null) return 0;
        try (Connection c = openConnection()) {
            List<Long> srcIds = findEntityIdsByName(c, t.source, scope);
            List<Long> dstIds = findEntityIdsByName(c, t.destination, scope);
            if (srcIds.isEmpty() || dstIds.isEmpty()) return 0;

            String relTable = relationshipsTable();
            StringBuilder sql = new StringBuilder("DELETE FROM ").append(relTable)
                    .append(" WHERE relationship_type = ? AND user_id = ?");
            List<Object> args = new ArrayList<>();
            args.add(t.relationship);
            args.add(String.valueOf(scope.get("user_id")));
            if (asNullableString(scope.get("agent_id")) != null) {
                sql.append(" AND agent_id = ?");
                args.add(asNullableString(scope.get("agent_id")));
            }
            if (asNullableString(scope.get("run_id")) != null) {
                sql.append(" AND run_id = ?");
                args.add(asNullableString(scope.get("run_id")));
            }

            sql.append(" AND source_entity_id IN (").append(placeholders(srcIds.size())).append(")");
            sql.append(" AND destination_entity_id IN (").append(placeholders(dstIds.size())).append(")");

            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                int p = 1;
                ps.setString(p++, String.valueOf(args.get(0)));
                ps.setString(p++, String.valueOf(args.get(1)));
                for (int i = 2; i < args.size(); i++) ps.setString(p++, String.valueOf(args.get(i)));
                for (Long id : srcIds) ps.setLong(p++, id);
                for (Long id : dstIds) ps.setLong(p++, id);
                return ps.executeUpdate();
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "deleteRelation failed: " + ex.getMessage(), ex);
            return 0;
        }
    }

    private List<Map<String, Object>> addEntitiesAndRelationships(List<RelationTriple> triples, Map<String, String> entityTypeMap, Map<String, Object> scope) {
        if (triples == null || triples.isEmpty()) return Collections.emptyList();
        if (embedder == null) throw new ApiException("OceanBaseGraphStore requires an embedder");

        List<Map<String, Object>> out = new ArrayList<>();
        for (RelationTriple t : triples) {
            long srcId = getOrCreateEntity(t.source, entityTypeMap.getOrDefault(t.source, "entity"), scope);
            long dstId = getOrCreateEntity(t.destination, entityTypeMap.getOrDefault(t.destination, "entity"), scope);
            Map<String, Object> rel = createRelationshipIfNotExists(srcId, dstId, t.relationship, scope);
            if (rel != null && !rel.isEmpty()) out.add(rel);
        }
        return out;
    }

    private long getOrCreateEntity(String name, String type, Map<String, Object> scope) {
        String n = normalizeName(name);
        float[] vec = embedder.embed(n, "search");
        EntityRow hit = searchSimilarEntity(vec, scope, similarityThreshold(), 1);
        if (hit != null) return hit.id;
        return createEntity(n, type, vec, scope);
    }

    private long createEntity(String name, String type, float[] embedding, Map<String, Object> scope) {
        long id = parseLongOrZero(idGenerator.nextId());
        Instant now = Instant.now();
        String embeddingJson = json.toJson(embedding == null ? new float[0] : embedding);

        List<String> cols = new ArrayList<>();
        cols.add("id");
        cols.add("user_id");
        cols.add("agent_id");
        cols.add("run_id");
        cols.add("name");
        cols.add("entity_type");
        cols.add("embedding_json");
        cols.add("created_at");
        cols.add("updated_at");
        if (hasVectorColumn) cols.add("embedding");

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(entitiesTable()).append(" (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(cols.get(i));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(")");

        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int p = 1;
            ps.setLong(p++, id);
            ps.setString(p++, String.valueOf(scope.get("user_id")));
            ps.setString(p++, asNullableString(scope.get("agent_id")));
            ps.setString(p++, asNullableString(scope.get("run_id")));
            ps.setString(p++, name);
            ps.setString(p++, type);
            ps.setString(p++, embeddingJson);
            ps.setTimestamp(p++, java.sql.Timestamp.from(now));
            ps.setTimestamp(p++, java.sql.Timestamp.from(now));
            if (hasVectorColumn) ps.setString(p, embeddingJson);
            ps.executeUpdate();
            return id;
        } catch (Exception ex) {
            throw new ApiException("createEntity failed: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> createRelationshipIfNotExists(long srcId, long dstId, String relType, Map<String, Object> scope) {
        long id = parseLongOrZero(idGenerator.nextId());
        Instant now = Instant.now();

        try (Connection c = openConnection()) {
            String exists = "SELECT 1 FROM " + relationshipsTable() + " WHERE user_id=?"
                    + (asNullableString(scope.get("agent_id")) != null ? " AND agent_id=?" : "")
                    + (asNullableString(scope.get("run_id")) != null ? " AND run_id=?" : "")
                    + " AND source_entity_id=? AND destination_entity_id=? AND relationship_type=? LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(exists)) {
                int p = 1;
                ps.setString(p++, String.valueOf(scope.get("user_id")));
                if (asNullableString(scope.get("agent_id")) != null) ps.setString(p++, asNullableString(scope.get("agent_id")));
                if (asNullableString(scope.get("run_id")) != null) ps.setString(p++, asNullableString(scope.get("run_id")));
                ps.setLong(p++, srcId);
                ps.setLong(p++, dstId);
                ps.setString(p, relType);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Collections.emptyMap();
                }
            }

            String insert = "INSERT INTO " + relationshipsTable()
                    + " (id, user_id, agent_id, run_id, source_entity_id, relationship_type, destination_entity_id, created_at, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                int p = 1;
                ps.setLong(p++, id);
                ps.setString(p++, String.valueOf(scope.get("user_id")));
                ps.setString(p++, asNullableString(scope.get("agent_id")));
                ps.setString(p++, asNullableString(scope.get("run_id")));
                ps.setLong(p++, srcId);
                ps.setString(p++, relType);
                ps.setLong(p++, dstId);
                ps.setTimestamp(p++, java.sql.Timestamp.from(now));
                ps.setTimestamp(p, java.sql.Timestamp.from(now));
                ps.executeUpdate();
            }

            Map<String, Object> out = new HashMap<>();
            out.put("source", getEntityNameById(c, srcId));
            out.put("relationship", relType);
            out.put("target", getEntityNameById(c, dstId));
            return out;
        } catch (Exception ex) {
            throw new ApiException("createRelationship failed: " + ex.getMessage(), ex);
        }
    }

    private String getEntityNameById(Connection c, long id) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT name FROM " + entitiesTable() + " WHERE id=? LIMIT 1")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    private List<Long> findEntityIdsByName(Connection c, String name, Map<String, Object> scope) throws Exception {
        String sql = "SELECT id FROM " + entitiesTable() + " WHERE user_id=?"
                + (asNullableString(scope.get("agent_id")) != null ? " AND agent_id=?" : "")
                + (asNullableString(scope.get("run_id")) != null ? " AND run_id=?" : "")
                + " AND name=? LIMIT " + safeSearchLimit();
        List<Long> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            ps.setString(p++, String.valueOf(scope.get("user_id")));
            if (asNullableString(scope.get("agent_id")) != null) ps.setString(p++, asNullableString(scope.get("agent_id")));
            if (asNullableString(scope.get("run_id")) != null) ps.setString(p++, asNullableString(scope.get("run_id")));
            ps.setString(p, name);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getLong(1));
            }
        }
        return out;
    }

    // ---------------- ANN lookup ----------------

    private EntityRow searchSimilarEntity(float[] queryEmbedding, Map<String, Object> scope, double threshold, int limit) {
        if (queryEmbedding == null || queryEmbedding.length == 0) return null;
        int k = Math.max(1, limit);

        if (hasVectorColumn) {
            String metricType = config.getMetricType() == null ? OceanBaseConstants.DEFAULT_OCEANBASE_VECTOR_METRIC_TYPE : config.getMetricType().trim().toLowerCase(Locale.ROOT);
            String distFunc = "l2_distance";
            if ("cosine".equals(metricType)) distFunc = "cosine_distance";
            if ("inner_product".equals(metricType) || "ip".equals(metricType)) distFunc = "inner_product";

            String sql = "SELECT id, name, entity_type, " + distFunc + "(embedding, ?) AS d FROM " + entitiesTable()
                    + " WHERE user_id=?"
                    + (asNullableString(scope.get("agent_id")) != null ? " AND agent_id=?" : "")
                    + (asNullableString(scope.get("run_id")) != null ? " AND run_id=?" : "")
                    + " ORDER BY d ASC LIMIT " + k;
            try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                int p = 1;
                ps.setString(p++, json.toJson(queryEmbedding));
                ps.setString(p++, String.valueOf(scope.get("user_id")));
                if (asNullableString(scope.get("agent_id")) != null) ps.setString(p++, asNullableString(scope.get("agent_id")));
                if (asNullableString(scope.get("run_id")) != null) ps.setString(p++, asNullableString(scope.get("run_id")));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        double d = rs.getDouble("d");
                        if (threshold > 0 && d >= threshold) return null;
                        return new EntityRow(rs.getLong("id"), rs.getString("name"), rs.getString("entity_type"), d);
                    }
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Graph SQL distance failed; fallback to brute-force. cause=" + ex.getMessage(), ex);
            }
        }

        // Brute-force fallback via embedding_json
        try (Connection c = openConnection()) {
            String sql = "SELECT id, name, entity_type, embedding_json FROM " + entitiesTable()
                    + " WHERE user_id=?"
                    + (asNullableString(scope.get("agent_id")) != null ? " AND agent_id=?" : "")
                    + (asNullableString(scope.get("run_id")) != null ? " AND run_id=?" : "")
                    + " LIMIT " + safeSearchLimit();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                int p = 1;
                ps.setString(p++, String.valueOf(scope.get("user_id")));
                if (asNullableString(scope.get("agent_id")) != null) ps.setString(p++, asNullableString(scope.get("agent_id")));
                if (asNullableString(scope.get("run_id")) != null) ps.setString(p++, asNullableString(scope.get("run_id")));
                EntityRow best = null;
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        float[] vec = json.fromJson(rs.getString("embedding_json"), float[].class);
                        if (vec == null || vec.length == 0) continue;
                        double d = l2Distance(queryEmbedding, vec);
                        if (threshold > 0 && d >= threshold) continue;
                        if (best == null || d < best.distance) {
                            best = new EntityRow(rs.getLong("id"), rs.getString("name"), rs.getString("entity_type"), d);
                        }
                    }
                }
                return best;
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Graph brute-force entity search failed: " + ex.getMessage(), ex);
            return null;
        }
    }

    private static double l2Distance(float[] a, float[] b) {
        if (a == null || b == null) return Double.POSITIVE_INFINITY;
        int n = Math.min(a.length, b.length);
        double s = 0.0;
        for (int i = 0; i < n; i++) {
            double d = (double) a[i] - (double) b[i];
            s += d * d;
        }
        return Math.sqrt(s);
    }

    // ---------------- neighborhood + BM25 + multi-hop ----------------

    private List<Map<String, Object>> searchGraphNeighborhood(Set<String> seedNames, Map<String, Object> scope, int limit) {
        if (seedNames == null || seedNames.isEmpty() || embedder == null) return Collections.emptyList();
        Set<Long> ids = new HashSet<>();
        for (String n : seedNames) {
            if (n == null || n.isBlank()) continue;
            float[] vec = embedder.embed(n, "search");
            EntityRow hit = searchSimilarEntity(vec, scope, similarityThreshold(), 1);
            if (hit != null) ids.add(hit.id);
        }
        if (ids.isEmpty()) return Collections.emptyList();
        return fetchRelationsByEntityIds(ids, scope, limit);
    }

    private List<Map<String, Object>> multiHopNeighborhood(Set<String> seedNames, Map<String, Object> scope, int maxHops, int limit) {
        if (seedNames == null || seedNames.isEmpty() || embedder == null) return Collections.emptyList();
        int hops = Math.max(1, maxHops);
        int cap = Math.max(1, limit);

        Set<Long> frontier = new HashSet<>();
        Set<Long> visitedNodes = new HashSet<>();
        for (String s : seedNames) {
            if (s == null || s.isBlank()) continue;
            float[] vec = embedder.embed(s, "search");
            EntityRow hit = searchSimilarEntity(vec, scope, similarityThreshold(), 1);
            if (hit != null) {
                frontier.add(hit.id);
                visitedNodes.add(hit.id);
            }
        }
        if (frontier.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> dedup = new HashSet<>();

        for (int hop = 0; hop < hops && !frontier.isEmpty() && edges.size() < cap; hop++) {
            List<Map<String, Object>> batch = fetchRelationsByEntityIds(frontier, scope, cap - edges.size());
            Set<Long> next = new HashSet<>();
            for (Map<String, Object> e : batch) {
                if (e == null) continue;
                String key = String.valueOf(e.get("source")) + "|" + String.valueOf(e.get("relationship")) + "|" + String.valueOf(e.get("destination"));
                if (dedup.add(key)) edges.add(e);
                // Prefer id-based traversal for parity (avoid name->id roundtrip).
                Object sid = e.get("_src_id");
                Object did = e.get("_dst_id");
                if (sid instanceof Number) next.add(((Number) sid).longValue());
                if (did instanceof Number) next.add(((Number) did).longValue());
            }
            next.removeAll(visitedNodes);
            visitedNodes.addAll(next);
            frontier = next;
        }
        return edges;
    }

    private List<Map<String, Object>> fetchRelationsByEntityIds(Set<Long> ids, Map<String, Object> scope, int limit) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        int top = Math.max(1, limit);
        String in = placeholders(ids.size());
        String sql = "SELECT r.source_entity_id AS src_id, r.destination_entity_id AS dst_id, "
                + "se.name AS source, r.relationship_type AS relationship, de.name AS destination "
                + "FROM " + relationshipsTable() + " r "
                + "JOIN " + entitiesTable() + " se ON se.id = r.source_entity_id "
                + "JOIN " + entitiesTable() + " de ON de.id = r.destination_entity_id "
                + "WHERE r.user_id=?"
                + (asNullableString(scope.get("agent_id")) != null ? " AND r.agent_id=?" : "")
                + (asNullableString(scope.get("run_id")) != null ? " AND r.run_id=?" : "")
                + " AND (r.source_entity_id IN (" + in + ") OR r.destination_entity_id IN (" + in + "))"
                + " ORDER BY r.updated_at DESC LIMIT " + top;

        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection c = openConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int p = 1;
            ps.setString(p++, String.valueOf(scope.get("user_id")));
            if (asNullableString(scope.get("agent_id")) != null) ps.setString(p++, asNullableString(scope.get("agent_id")));
            if (asNullableString(scope.get("run_id")) != null) ps.setString(p++, asNullableString(scope.get("run_id")));
            for (Long id : ids) ps.setLong(p++, id);
            for (Long id : ids) ps.setLong(p++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("_src_id", rs.getLong("src_id"));
                    m.put("_dst_id", rs.getLong("dst_id"));
                    m.put("source", rs.getString("source"));
                    m.put("relationship", rs.getString("relationship"));
                    m.put("destination", rs.getString("destination"));
                    out.add(m);
                }
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "fetchRelationsByEntityIds failed: " + ex.getMessage(), ex);
        }
        return out;
    }

    private List<Map<String, Object>> bm25Rerank(String query, List<Map<String, Object>> candidates, int topN) {
        if (candidates == null || candidates.isEmpty()) return Collections.emptyList();
        int top = Math.max(1, topN);

        List<List<String>> corpus = new ArrayList<>();
        for (Map<String, Object> r : candidates) {
            String combined = String.valueOf(r.get("source")) + " " + String.valueOf(r.get("relationship")) + " " + String.valueOf(r.get("destination"));
            corpus.add(TextTokenizer.tokenize(combined));
        }
        Bm25 bm25 = new Bm25(corpus);
        double[] scores = bm25.getScores(TextTokenizer.tokenize(query == null ? "" : query));

        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) idx.add(i);
        idx.sort((a, b) -> Double.compare(scores[b], scores[a]));

        List<Map<String, Object>> out = new ArrayList<>();
        for (int i : idx) {
            out.add(candidates.get(i));
            if (out.size() >= top) break;
        }
        return out;
    }

    private static String formatMemoriesForPrompt(List<Map<String, Object>> rels) {
        if (rels == null || rels.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : rels) {
            if (r == null) continue;
            if (sb.length() > 0) sb.append("\n");
            sb.append(String.valueOf(r.get("source"))).append(" -- ")
                    .append(String.valueOf(r.get("relationship"))).append(" -- ")
                    .append(String.valueOf(r.get("destination")));
        }
        return sb.toString();
    }

    // ---------------- tool parsing helpers ----------------

    private Map<String, String> parseExtractEntities(LlmResponse resp) {
        Map<String, String> out = new HashMap<>();
        for (Map<String, Object> tc : extractToolCalls(resp)) {
            String name = toolCallName(tc);
            if (!"extract_entities".equals(name)) continue;
            Map<String, Object> args = toolCallArguments(tc);
            Object ents = args.get("entities");
            if (ents instanceof List) {
                for (Object e : (List<?>) ents) {
                    if (!(e instanceof Map)) continue;
                    Map<?, ?> em = (Map<?, ?>) e;
                    Object en = em.get("entity");
                    Object et = em.get("entity_type");
                    if (en == null) continue;
                    out.put(normalizeName(String.valueOf(en)), et == null ? "entity" : normalizeName(String.valueOf(et)));
                }
            }
        }
        return out;
    }

    private List<RelationTriple> parseEstablishRelations(LlmResponse resp) {
        List<RelationTriple> out = new ArrayList<>();
        for (Map<String, Object> tc : extractToolCalls(resp)) {
            String name = toolCallName(tc);
            if (!"establish_relationships".equals(name) && !"establish_relations".equals(name)) continue;
            Map<String, Object> args = toolCallArguments(tc);
            Object ents = args.get("entities");
            if (ents instanceof List) {
                for (Object e : (List<?>) ents) {
                    if (!(e instanceof Map)) continue;
                    Map<?, ?> em = (Map<?, ?>) e;
                    String src = normalizeName(String.valueOf(em.get("source")));
                    String rel = normalizeName(String.valueOf(em.get("relationship")));
                    String dst = normalizeName(String.valueOf(em.get("destination")));
                    if (!src.isBlank() && !rel.isBlank() && !dst.isBlank()) out.add(new RelationTriple(src, rel, dst));
                }
            }
        }
        return out;
    }

    private List<RelationTriple> parseDeleteGraphMemory(LlmResponse resp) {
        List<RelationTriple> out = new ArrayList<>();
        for (Map<String, Object> tc : extractToolCalls(resp)) {
            String name = toolCallName(tc);
            if (!"delete_graph_memory".equals(name)) continue;
            Map<String, Object> args = toolCallArguments(tc);
            String src = normalizeName(String.valueOf(args.get("source")));
            String rel = normalizeName(String.valueOf(args.get("relationship")));
            String dst = normalizeName(String.valueOf(args.get("destination")));
            if (!src.isBlank() && !rel.isBlank() && !dst.isBlank()) out.add(new RelationTriple(src, rel, dst));
        }
        return out;
    }

    private List<Map<String, Object>> extractToolCalls(LlmResponse resp) {
        if (resp == null) return Collections.emptyList();
        if (resp.getToolCalls() != null && !resp.getToolCalls().isEmpty()) return resp.getToolCalls();
        Map<String, Object> m = LlmJsonUtils.parseJsonObjectLoose(resp.getContent());
        Object tc = m.get("tool_calls");
        if (tc instanceof List) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : (List<?>) tc) {
                if (o instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mm = (Map<String, Object>) o;
                    out.add(mm);
                }
            }
            return out;
        }
        return Collections.emptyList();
    }

    private static String toolCallName(Map<String, Object> tc) {
        if (tc == null) return null;
        Object fn = tc.get("function");
        if (fn instanceof Map) {
            Object n = ((Map<?, ?>) fn).get("name");
            return n == null ? null : String.valueOf(n);
        }
        Object n = tc.get("name");
        return n == null ? null : String.valueOf(n);
    }

    private static Map<String, Object> toolCallArguments(Map<String, Object> tc) {
        if (tc == null) return Collections.emptyMap();
        Object fn = tc.get("function");
        Object args = null;
        if (fn instanceof Map) args = ((Map<?, ?>) fn).get("arguments");
        if (args == null) args = tc.get("arguments");
        if (args instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) args;
            return m;
        }
        if (args instanceof String) return LlmJsonUtils.parseJsonObjectLoose((String) args);
        return Collections.emptyMap();
    }

    // ---------------- fallbacks / normalization ----------------

    private static Map<String, String> normalizeEntityTypeMap(Map<String, String> m) {
        Map<String, String> out = new HashMap<>();
        if (m == null) return out;
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (e.getKey() == null) continue;
            String k = normalizeName(e.getKey());
            if (k.isBlank()) continue;
            String v = e.getValue() == null ? "entity" : normalizeName(e.getValue());
            if (v.isBlank()) v = "entity";
            out.put(k, v);
        }
        return out;
    }

    private static List<RelationTriple> normalizeRelationTriples(List<RelationTriple> in) {
        if (in == null || in.isEmpty()) return Collections.emptyList();
        List<RelationTriple> out = new ArrayList<>();
        for (RelationTriple t : in) {
            if (t == null) continue;
            String s = normalizeName(t.source);
            String r = normalizeName(t.relationship);
            String d = normalizeName(t.destination);
            if (s.isBlank() || r.isBlank() || d.isBlank()) continue;
            out.add(new RelationTriple(s, r, d));
        }
        return out;
    }

    private static String normalizeName(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replace(" ", "_").trim();
    }

    private static List<RelationTriple> heuristicTriples(String data) {
        if (data == null || data.isBlank()) return Collections.emptyList();
        List<RelationTriple> out = new ArrayList<>();
        String[] lines = data.split("[\\n\\r]+");
        for (String raw : lines) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isEmpty()) continue;
            // Allow explicit triple format: source -- relationship -- destination
            RelationTriple explicit = parseExplicitTriple(s);
            if (explicit != null) {
                out.add(explicit);
                continue;
            }
            RelationTriple t = parseChinesePreference(s);
            if (t != null) {
                out.add(t);
                continue;
            }
            RelationTriple e = parseEnglishPreference(s);
            if (e != null) out.add(e);
        }
        return out;
    }

    private static RelationTriple parseExplicitTriple(String s) {
        if (s == null) return null;
        String x = s.trim();
        if (!x.contains("--")) return null;
        String[] parts = x.split("\\s*--\\s*");
        if (parts.length < 3) return null;
        String src = normalizeName(parts[0]);
        String rel = normalizeName(parts[1]);
        String dst = normalizeName(parts[2]);
        if (src.isBlank() || rel.isBlank() || dst.isBlank()) return null;
        return new RelationTriple(src, rel, dst);
    }

    private static RelationTriple parseChinesePreference(String s) {
        String x = s.replace("：", ":").replace("，", ",").replace("。", ".").trim();
        String subject;
        String rest;
        if (x.startsWith("用户")) {
            subject = "用户";
            rest = x.substring(2);
        } else if (x.startsWith("我")) {
            subject = "我";
            rest = x.substring(1);
        } else {
            return null;
        }
        rest = rest.trim();
        String rel;
        int idx;
        if ((idx = rest.indexOf("喜欢")) >= 0) rel = "喜欢";
        else if ((idx = rest.indexOf("偏好")) >= 0) rel = "偏好";
        else if ((idx = rest.indexOf("讨厌")) >= 0) rel = "讨厌";
        else return null;
        String obj = rest.substring(idx + rel.length()).trim();
        if (obj.isEmpty()) return null;
        return new RelationTriple(normalizeName(subject), normalizeName(rel), normalizeName(obj));
    }

    private static RelationTriple parseEnglishPreference(String s) {
        String x = s.trim();
        String lower = x.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("user ")) return null;
        int idx = lower.indexOf(" likes ");
        String rel = "likes";
        if (idx < 0) {
            idx = lower.indexOf(" prefers ");
            rel = "prefers";
        }
        if (idx < 0) return null;
        String obj = x.substring(idx + (" " + rel + " ").length()).trim();
        if (obj.isEmpty()) return null;
        return new RelationTriple("user", normalizeName(rel), normalizeName(obj));
    }

    // ---------------- config helpers ----------------

    private int safeSearchLimit() {
        int v = config == null ? 0 : config.getSearchLimit();
        return v > 0 ? v : OceanBaseConstants.DEFAULT_SEARCH_LIMIT;
    }

    private int safeBm25TopN() {
        int v = config == null ? 0 : config.getBm25TopN();
        return v > 0 ? v : OceanBaseConstants.DEFAULT_BM25_TOP_N;
    }

    private int safeMaxHops() {
        int v = config == null ? 0 : config.getMaxHops();
        return v > 0 ? v : OceanBaseConstants.DEFAULT_MAX_HOPS;
    }

    private double similarityThreshold() {
        double v = config == null ? 0.0 : config.getSimilarityThreshold();
        return v > 0.0 ? v : OceanBaseConstants.DEFAULT_SIMILARITY_THRESHOLD;
    }

    // ---------------- connection + SQL helpers ----------------

    private String entitiesTable() {
        String tn = config == null ? null : config.getEntitiesTable();
        if (tn == null || tn.isBlank()) tn = OceanBaseConstants.TABLE_GRAPH_ENTITIES;
        String safe = tn.trim();
        if (!safe.matches("[A-Za-z0-9_]+")) safe = OceanBaseConstants.TABLE_GRAPH_ENTITIES;
        return safe;
    }

    private String relationshipsTable() {
        String tn = config == null ? null : config.getRelationshipsTable();
        if (tn == null || tn.isBlank()) tn = OceanBaseConstants.TABLE_GRAPH_RELATIONSHIPS;
        String safe = tn.trim();
        if (!safe.matches("[A-Za-z0-9_]+")) safe = OceanBaseConstants.TABLE_GRAPH_RELATIONSHIPS;
        return safe;
    }

    private String jdbcUrl() {
        String host = config.getHost() == null || config.getHost().isBlank() ? "127.0.0.1" : config.getHost();
        int port = config.getPort() > 0 ? config.getPort() : 2881;
        String db = config.getDatabase() == null || config.getDatabase().isBlank() ? "ai_work" : config.getDatabase();
        int timeout = Math.max(1, config.getTimeoutSeconds());
        return "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?useUnicode=true&characterEncoding=UTF-8"
                + "&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                + "&connectTimeout=" + (timeout * 1000)
                + "&socketTimeout=" + (timeout * 1000);
    }

    private Connection openConnection() throws Exception {
        String user = config.getUser();
        String pass = config.getPassword();
        if (user == null || user.isBlank()) {
            throw new ApiException("OceanBase user is required (graph_store.user)");
        }
        if (pass == null) pass = "";
        return DriverManager.getConnection(jdbcUrl(), user, pass);
    }

    private static Map<String, Object> normalizeScope(Map<String, Object> filters) {
        Map<String, Object> f = filters == null ? new HashMap<>() : new HashMap<>(filters);
        Object uid = f.get("user_id");
        if (uid == null || String.valueOf(uid).isBlank()) {
            f.put("user_id", "user"); // Python parity default
        }
        return f;
    }

    private static String asNullableString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }

    private static long parseLongOrZero(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static String placeholders(int n) {
        if (n <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        return sb.toString();
    }

    // ---------------- data types ----------------

    private static final class RelationTriple {
        final String source;
        final String relationship;
        final String destination;
        RelationTriple(String source, String relationship, String destination) {
            this.source = source;
            this.relationship = relationship;
            this.destination = destination;
        }
    }

    private static final class EntityRow {
        final long id;
        final String name;
        final String entityType;
        final double distance;
        EntityRow(long id, String name, String entityType, double distance) {
            this.id = id;
            this.name = name;
            this.entityType = entityType;
            this.distance = distance;
        }
    }
}

