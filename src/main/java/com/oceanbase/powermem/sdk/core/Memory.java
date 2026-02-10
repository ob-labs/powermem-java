package com.oceanbase.powermem.sdk.core;

/**
 * Synchronous PowerMem memory manager (pure Java core migration target).
 *
 * <p>This class is intended to become the Java counterpart of the Python synchronous {@code Memory}
 * implementation, including orchestration of configuration, embedding, storage adapters, intelligent
 * memory, auditing, and telemetry.</p>
 *
 * <p>Python reference: {@code src/powermem/core/memory.py}</p>
 */
public class Memory implements MemoryBase {
    private final com.oceanbase.powermem.sdk.config.MemoryConfig config;
    private final com.oceanbase.powermem.sdk.storage.base.VectorStore vectorStore;
    private final com.oceanbase.powermem.sdk.storage.base.GraphStore graphStore;
    private final com.oceanbase.powermem.sdk.integrations.embeddings.Embedder embedder;
    private final com.oceanbase.powermem.sdk.intelligence.IntelligenceManager intelligence;
    private final com.oceanbase.powermem.sdk.integrations.llm.LLM llm;
    private final com.oceanbase.powermem.sdk.storage.adapter.StorageAdapter storage;
    private final com.oceanbase.powermem.sdk.intelligence.plugin.IntelligentMemoryPlugin plugin;
    private final com.oceanbase.powermem.sdk.integrations.rerank.Reranker reranker;

    public Memory() {
        this(com.oceanbase.powermem.sdk.config.ConfigLoader.fromEnvAndDotEnv());
    }

    public Memory(com.oceanbase.powermem.sdk.config.MemoryConfig config) {
        this.config = config == null ? new com.oceanbase.powermem.sdk.config.MemoryConfig() : config;
        this.vectorStore = com.oceanbase.powermem.sdk.storage.factory.VectorStoreFactory.fromConfig(this.config.getVectorStore());
        this.embedder = com.oceanbase.powermem.sdk.integrations.embeddings.EmbedderFactory.fromConfig(this.config.getEmbedder());
        this.llm = com.oceanbase.powermem.sdk.integrations.llm.LLMFactory.fromConfig(this.config.getLlm());
        this.storage = buildStorageAdapter(this.config, this.vectorStore, this.embedder);
        this.intelligence = new com.oceanbase.powermem.sdk.intelligence.IntelligenceManager(this.config.getIntelligentMemory());
        this.plugin = new com.oceanbase.powermem.sdk.intelligence.plugin.EbbinghausIntelligencePlugin(this.config.getIntelligentMemory());
        this.reranker = com.oceanbase.powermem.sdk.integrations.rerank.RerankFactory.fromConfig(this.config.getReranker());
        this.graphStore = com.oceanbase.powermem.sdk.storage.factory.GraphStoreFactory.fromConfig(
                this.config.getGraphStore(), this.embedder, this.llm);
    }

    private static com.oceanbase.powermem.sdk.storage.adapter.StorageAdapter buildStorageAdapter(
            com.oceanbase.powermem.sdk.config.MemoryConfig cfg,
            com.oceanbase.powermem.sdk.storage.base.VectorStore mainStore,
            com.oceanbase.powermem.sdk.integrations.embeddings.Embedder mainEmbedder) {
        java.util.List<com.oceanbase.powermem.sdk.config.SubStoreConfig> subs =
                (cfg == null) ? null : cfg.getSubStores();
        if (subs == null || subs.isEmpty()) {
            return new com.oceanbase.powermem.sdk.storage.adapter.StorageAdapter(mainStore, mainEmbedder);
        }

        com.oceanbase.powermem.sdk.storage.adapter.SubStorageAdapter adapter =
                new com.oceanbase.powermem.sdk.storage.adapter.SubStorageAdapter(mainStore, mainEmbedder);

        com.oceanbase.powermem.sdk.config.VectorStoreConfig mainVsCfg = cfg.getVectorStore();
        com.oceanbase.powermem.sdk.config.EmbedderConfig mainEmbCfg = cfg.getEmbedder();
        String mainCollection = mainVsCfg == null ? "memories" : mainVsCfg.getCollectionName();
        int mainDims = mainVsCfg == null ? 0 : mainVsCfg.getEmbeddingModelDims();

        int idx = 0;
        for (com.oceanbase.powermem.sdk.config.SubStoreConfig sc : subs) {
            if (sc == null) {
                idx++;
                continue;
            }
            java.util.Map<String, Object> rf = sc.getRoutingFilter();
            if (rf == null || rf.isEmpty()) {
                idx++;
                continue;
            }
            String storeName = sc.getName();
            if (storeName == null || storeName.isBlank()) {
                storeName = mainCollection + "_sub_" + idx;
            }

            // Vector store config: start from main, apply sub overrides, then enforce name/dims.
            com.oceanbase.powermem.sdk.config.VectorStoreConfig vcfg =
                    mainVsCfg == null ? new com.oceanbase.powermem.sdk.config.VectorStoreConfig() : mainVsCfg.copy();
            com.oceanbase.powermem.sdk.config.VectorStoreConfig ov = sc.getVectorStore();
            if (ov != null) {
                applyVectorOverrides(vcfg, ov);
            }
            vcfg.setCollectionName(storeName);
            Integer dims = sc.getEmbeddingModelDims();
            if (dims != null && dims > 0) {
                vcfg.setEmbeddingModelDims(dims);
            } else if (vcfg.getEmbeddingModelDims() <= 0 && mainDims > 0) {
                vcfg.setEmbeddingModelDims(mainDims);
            }

            com.oceanbase.powermem.sdk.storage.base.VectorStore subStore =
                    com.oceanbase.powermem.sdk.storage.factory.VectorStoreFactory.fromConfig(vcfg);

            // Embedder config: start from main, apply overrides (inherit apiKey/baseUrl when missing), enforce dims when set.
            com.oceanbase.powermem.sdk.config.EmbedderConfig ecfg =
                    mainEmbCfg == null ? new com.oceanbase.powermem.sdk.config.EmbedderConfig() : mainEmbCfg.copy();
            com.oceanbase.powermem.sdk.config.EmbedderConfig eov = sc.getEmbedder();
            if (eov != null) {
                if (eov.getProvider() != null && !eov.getProvider().isBlank()) {
                    ecfg.setProvider(eov.getProvider());
                }
                if (eov.getApiKey() != null && !eov.getApiKey().isBlank()) {
                    ecfg.setApiKey(eov.getApiKey());
                }
                if (eov.getModel() != null && !eov.getModel().isBlank()) {
                    ecfg.setModel(eov.getModel());
                }
                if (eov.getBaseUrl() != null && !eov.getBaseUrl().isBlank()) {
                    ecfg.setBaseUrl(eov.getBaseUrl());
                }
                if (eov.getEmbeddingDims() > 0) {
                    ecfg.setEmbeddingDims(eov.getEmbeddingDims());
                }
            }
            if (dims != null && dims > 0) {
                ecfg.setEmbeddingDims(dims);
            }
            com.oceanbase.powermem.sdk.integrations.embeddings.Embedder subEmbedder =
                    com.oceanbase.powermem.sdk.integrations.embeddings.EmbedderFactory.fromConfig(ecfg);

            adapter.registerSubStore(storeName, rf, subStore, subEmbedder);
            if (sc.getReady() != null) {
                adapter.setSubStoreReady(storeName, sc.getReady());
            }
            idx++;
        }
        return adapter;
    }

    private static void applyVectorOverrides(com.oceanbase.powermem.sdk.config.VectorStoreConfig base,
                                            com.oceanbase.powermem.sdk.config.VectorStoreConfig ov) {
        if (base == null || ov == null) return;
        if (ov.getProvider() != null && !ov.getProvider().isBlank()) base.setProvider(ov.getProvider());
        if (ov.getDatabasePath() != null && !ov.getDatabasePath().isBlank()) base.setDatabasePath(ov.getDatabasePath());
        if (ov.getHost() != null && !ov.getHost().isBlank()) base.setHost(ov.getHost());
        if (ov.getPort() > 0) base.setPort(ov.getPort());
        if (ov.getUser() != null && !ov.getUser().isBlank()) base.setUser(ov.getUser());
        if (ov.getPassword() != null) base.setPassword(ov.getPassword());
        if (ov.getDatabase() != null && !ov.getDatabase().isBlank()) base.setDatabase(ov.getDatabase());
        if (ov.getCollectionName() != null && !ov.getCollectionName().isBlank()) base.setCollectionName(ov.getCollectionName());
        if (ov.getEmbeddingModelDims() > 0) base.setEmbeddingModelDims(ov.getEmbeddingModelDims());
        if (ov.getIndexType() != null && !ov.getIndexType().isBlank()) base.setIndexType(ov.getIndexType());
        if (ov.getMetricType() != null && !ov.getMetricType().isBlank()) base.setMetricType(ov.getMetricType());
        if (ov.getVectorIndexName() != null && !ov.getVectorIndexName().isBlank()) base.setVectorIndexName(ov.getVectorIndexName());
        // Hybrid/FTS fields: keep best-effort (only override when non-blank/non-zero)
        if (ov.getFulltextParser() != null && !ov.getFulltextParser().isBlank()) base.setFulltextParser(ov.getFulltextParser());
        if (ov.getVectorWeight() > 0) base.setVectorWeight(ov.getVectorWeight());
        if (ov.getFtsWeight() > 0) base.setFtsWeight(ov.getFtsWeight());
        if (ov.getFusionMethod() != null && !ov.getFusionMethod().isBlank()) base.setFusionMethod(ov.getFusionMethod());
        if (ov.getRrfK() > 0) base.setRrfK(ov.getRrfK());
    }

    @Override
    public com.oceanbase.powermem.sdk.model.AddMemoryResponse add(com.oceanbase.powermem.sdk.model.AddMemoryRequest request) {
        com.oceanbase.powermem.sdk.util.Preconditions.requireNonNull(request, "AddMemoryRequest is required");

        // Graph store (optional): add raw conversation text and return relations summary.
        java.util.Map<String, Object> graphResult = maybeAddToGraph(request);

        java.util.List<com.oceanbase.powermem.sdk.model.Message> msgs = request.getMessages();
        String normalized = com.oceanbase.powermem.sdk.util.PowermemUtils.normalizeInput(request.getText(), msgs);
        if (normalized.isBlank()) {
            return new com.oceanbase.powermem.sdk.model.AddMemoryResponse(java.util.Collections.emptyList());
        }
        boolean useInfer = request.isInfer() && msgs != null && !msgs.isEmpty();
        if (!useInfer) {
            java.util.Map<String, Object> extra = plugin != null && plugin.isEnabled()
                    ? plugin.onAdd(normalized, request.getMetadata())
                    : java.util.Collections.emptyMap();
            com.oceanbase.powermem.sdk.model.MemoryRecord r = storage.addMemory(
                    normalized,
                    request.getUserId(),
                    request.getAgentId(),
                    request.getRunId(),
                    request.getMetadata(),
                    extra,
                    request.getScope(),
                    request.getMemoryType());
            com.oceanbase.powermem.sdk.model.AddMemoryResponse resp =
                    new com.oceanbase.powermem.sdk.model.AddMemoryResponse(java.util.Collections.singletonList(r));
            resp.setResults(java.util.Collections.singletonList(toAddResultDto(r, "ADD", null, request.getMetadata())));
            java.util.Map<String, Integer> counts = new java.util.HashMap<>();
            counts.put("ADD", 1);
            counts.put("UPDATE", 0);
            counts.put("DELETE", 0);
            counts.put("NONE", 0);
            // Python benchmark/server: add() response does not include action_counts; keep null to omit serialization.
            resp.setActionCounts(null);
            // Python parity: when graph is enabled, return relations even if empty.
            resp.setRelations(graphResult == null ? java.util.Collections.emptyMap() : graphResult);
            return resp;
        }
        com.oceanbase.powermem.sdk.model.AddMemoryResponse resp = intelligentAdd(request, normalized);
        if (resp != null) {
            resp.setRelations(graphResult == null ? java.util.Collections.emptyMap() : graphResult);
        }
        return resp;
    }

    private com.oceanbase.powermem.sdk.model.AddMemoryResponse intelligentAdd(com.oceanbase.powermem.sdk.model.AddMemoryRequest request, String normalized) {
        // 1) extract facts
        String conversation = com.oceanbase.powermem.sdk.prompts.IntelligentMemoryPrompts.parseMessagesForFacts(request.getMessages());
        java.util.List<com.oceanbase.powermem.sdk.model.Message> factMsgs = new java.util.ArrayList<>();
        String factPrompt = (config != null && config.getCustomFactExtractionPrompt() != null && !config.getCustomFactExtractionPrompt().isBlank())
                ? config.getCustomFactExtractionPrompt()
                : com.oceanbase.powermem.sdk.prompts.IntelligentMemoryPrompts.factRetrievalPrompt();
        factMsgs.add(new com.oceanbase.powermem.sdk.model.Message("system", factPrompt));
        factMsgs.add(new com.oceanbase.powermem.sdk.model.Message("user", "Input:\n" + conversation));
        java.util.Map<String, Object> jsonFormat = new java.util.HashMap<>();
        jsonFormat.put("type", "json_object");
        String factResp = llm.generateResponse(factMsgs, jsonFormat);
        java.util.Map<String, Object> factObj = com.oceanbase.powermem.sdk.util.LlmJsonUtils.parseJsonObjectLoose(factResp);
        java.util.List<String> facts = new java.util.ArrayList<>();
        Object factsObj = factObj.get("facts");
        if (factsObj instanceof java.util.List) {
            for (Object f : (java.util.List<?>) factsObj) {
                if (f != null && !String.valueOf(f).isBlank()) {
                    facts.add(String.valueOf(f));
                }
            }
        }
        if (facts.isEmpty()) {
            return new com.oceanbase.powermem.sdk.model.AddMemoryResponse(java.util.Collections.emptyList());
        }

        // 2) search similar memories for each fact (dedup by id)
        java.util.Map<String, com.oceanbase.powermem.sdk.model.MemoryRecord> unique = new java.util.LinkedHashMap<>();
        java.util.Map<String, float[]> factEmbeddings = new java.util.HashMap<>();
        for (String fact : facts) {
            // Sub-store parity: choose embedder based on request metadata.
            float[] vec = storage.embed(fact, "search", request.getMetadata());
            factEmbeddings.put(fact, vec);
            int topK = 5;
            int candidateLimit = topK;
            if (reranker != null && fact != null && !fact.isBlank()) {
                candidateLimit = Math.max(topK, topK * 3);
            }
            java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> hits =
                    storage.searchMemories(fact, vec, candidateLimit, request.getUserId(), request.getAgentId(), request.getRunId(), request.getFilters());
            if (reranker != null && hits != null && !hits.isEmpty() && fact != null && !fact.isBlank()) {
                hits = applyRerank(fact, hits, topK);
            }
            for (com.oceanbase.powermem.sdk.storage.base.OutputData d : hits) {
                if (d == null || d.getRecord() == null || d.getRecord().getId() == null) {
                    continue;
                }
                unique.putIfAbsent(d.getRecord().getId(), d.getRecord());
                if (unique.size() >= 10) {
                    break;
                }
            }
        }

        java.util.List<com.oceanbase.powermem.sdk.model.MemoryRecord> existing = new java.util.ArrayList<>(unique.values());
        // 3) map existing ids to 0..n-1 to reduce hallucinations (Python-style)
        java.util.Map<String, String> tempToReal = new java.util.HashMap<>();
        java.util.List<java.util.Map<String, Object>> oldMemoryForPrompt = new java.util.ArrayList<>();
        for (int i = 0; i < existing.size(); i++) {
            com.oceanbase.powermem.sdk.model.MemoryRecord r = existing.get(i);
            String tmp = String.valueOf(i);
            tempToReal.put(tmp, r.getId());
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", tmp);
            m.put("text", r.getContent());
            oldMemoryForPrompt.add(m);
        }

        String customUpdatePrompt = request.getPrompt();
        if (customUpdatePrompt == null || customUpdatePrompt.isBlank()) {
            customUpdatePrompt = (config != null ? config.getCustomUpdateMemoryPrompt() : null);
        }
        String updatePrompt = com.oceanbase.powermem.sdk.prompts.IntelligentMemoryPrompts.getMemoryUpdatePrompt(
                oldMemoryForPrompt, facts, customUpdatePrompt);
        java.util.List<com.oceanbase.powermem.sdk.model.Message> updMsgs = new java.util.ArrayList<>();
        updMsgs.add(new com.oceanbase.powermem.sdk.model.Message("user", updatePrompt));
        String updResp = llm.generateResponse(updMsgs, jsonFormat);
        java.util.Map<String, Object> updObj = com.oceanbase.powermem.sdk.util.LlmJsonUtils.parseJsonObjectLoose(updResp);

        Object memArr = updObj.get("memory");
        if (!(memArr instanceof java.util.List)) {
            return new com.oceanbase.powermem.sdk.model.AddMemoryResponse(java.util.Collections.emptyList());
        }

        java.util.List<com.oceanbase.powermem.sdk.model.MemoryRecord> results = new java.util.ArrayList<>();
        java.util.List<com.oceanbase.powermem.sdk.model.AddMemoryResponse.Result> resultDtos = new java.util.ArrayList<>();
        java.util.Map<String, Integer> actionCounts = new java.util.HashMap<>();
        actionCounts.put("ADD", 0);
        actionCounts.put("UPDATE", 0);
        actionCounts.put("DELETE", 0);
        actionCounts.put("NONE", 0);

        for (Object a : (java.util.List<?>) memArr) {
            if (!(a instanceof java.util.Map)) {
                continue;
            }
            java.util.Map<?, ?> act = (java.util.Map<?, ?>) a;
            String event = act.get("event") == null ? "NONE" : String.valueOf(act.get("event")).trim().toUpperCase();
            String text = act.get("text") == null ? "" : String.valueOf(act.get("text"));
            String id = act.get("id") == null ? "" : String.valueOf(act.get("id"));
            String oldMemory = act.get("old_memory") == null ? null : String.valueOf(act.get("old_memory"));
            if (event.isBlank() || "NONE".equalsIgnoreCase(event)) {
                actionCounts.put("NONE", actionCounts.get("NONE") + 1);
                continue;
            }
            if ((text == null || text.isBlank()) && !"DELETE".equalsIgnoreCase(event)) {
                // empty text is only tolerable for NONE/DELETE (python side sometimes sends)
                continue;
            }
            if ("ADD".equalsIgnoreCase(event)) {
                java.util.Map<String, Object> extra = plugin != null && plugin.isEnabled()
                        ? plugin.onAdd(text, request.getMetadata())
                        : java.util.Collections.emptyMap();
                com.oceanbase.powermem.sdk.model.MemoryRecord r = storage.addMemory(
                        text,
                        request.getUserId(),
                        request.getAgentId(),
                        request.getRunId(),
                        request.getMetadata(),
                        extra,
                        request.getScope(),
                        request.getMemoryType());
                results.add(r);
                resultDtos.add(toAddResultDto(r, "ADD", null, request.getMetadata()));
                actionCounts.put("ADD", actionCounts.get("ADD") + 1);
            } else if ("UPDATE".equalsIgnoreCase(event)) {
                String realId = tempToReal.getOrDefault(id, id);
                // id hallucination guard: must exist
                if (storage.getMemory(realId, request.getUserId(), request.getAgentId()) == null) {
                    continue;
                }
                // Python parity: update event can also go through on_add to refresh intelligence fields
                if (plugin != null && plugin.isEnabled()) {
                    java.util.Map<String, Object> extra = plugin.onAdd(text, request.getMetadata());
                    storage.updatePayloadFields(realId, request.getUserId(), request.getAgentId(), extra);
                }
                com.oceanbase.powermem.sdk.model.MemoryRecord r = storage.updateMemory(
                        realId, text, request.getUserId(), request.getAgentId(), request.getMetadata());
                if (r != null) {
                    results.add(r);
                    resultDtos.add(toAddResultDto(r, "UPDATE", oldMemory, request.getMetadata()));
                    actionCounts.put("UPDATE", actionCounts.get("UPDATE") + 1);
                }
            } else if ("DELETE".equalsIgnoreCase(event)) {
                String realId = tempToReal.getOrDefault(id, id);
                com.oceanbase.powermem.sdk.model.MemoryRecord before = storage.getMemory(realId, request.getUserId(), request.getAgentId());
                if (before == null) {
                    continue;
                }
                storage.deleteMemory(realId, request.getUserId(), request.getAgentId());
                // Python returns delete operations in results (so benchmark can verify).
                com.oceanbase.powermem.sdk.model.AddMemoryResponse.Result del = new com.oceanbase.powermem.sdk.model.AddMemoryResponse.Result();
                del.setId(realId);
                del.setMemory(text);
                del.setEvent("DELETE");
                del.setUserId(request.getUserId());
                del.setAgentId(request.getAgentId());
                del.setRunId(request.getRunId());
                del.setMetadata(request.getMetadata());
                del.setCreatedAt(before.getCreatedAt() == null ? null : before.getCreatedAt().toString());
                resultDtos.add(del);
                actionCounts.put("DELETE", actionCounts.get("DELETE") + 1);
            }
        }
        com.oceanbase.powermem.sdk.model.AddMemoryResponse resp = new com.oceanbase.powermem.sdk.model.AddMemoryResponse(results);
        resp.setResults(resultDtos);
        // Python benchmark/server: add() response does not include action_counts; keep null to omit serialization.
        resp.setActionCounts(null);
        // relations reserved for graph-store parity
        resp.setRelations(null);
        return resp;
    }

    private static com.oceanbase.powermem.sdk.model.AddMemoryResponse.Result toAddResultDto(
            com.oceanbase.powermem.sdk.model.MemoryRecord r,
            String event,
            String previousMemory,
            java.util.Map<String, Object> requestMetadata) {
        com.oceanbase.powermem.sdk.model.AddMemoryResponse.Result dto = new com.oceanbase.powermem.sdk.model.AddMemoryResponse.Result();
        if (r == null) {
            return dto;
        }
        dto.setId(r.getId());
        dto.setMemory(r.getContent());
        dto.setEvent(event);
        dto.setUserId(r.getUserId());
        dto.setAgentId(r.getAgentId());
        dto.setRunId(r.getRunId());
        // Python parity: return the caller-provided metadata (including category) when available.
        dto.setMetadata(requestMetadata != null ? requestMetadata : r.getMetadata());
        dto.setCreatedAt(r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        dto.setPreviousMemory(previousMemory);
        return dto;
    }

    @Override
    public com.oceanbase.powermem.sdk.model.SearchMemoriesResponse search(com.oceanbase.powermem.sdk.model.SearchMemoriesRequest request) {
        com.oceanbase.powermem.sdk.util.Preconditions.requireNonNull(request, "SearchMemoriesRequest is required");
        com.oceanbase.powermem.sdk.util.Preconditions.requireNonBlank(request.getQuery(), "query is required");

        int limit = request.getLimit() != null && request.getLimit() > 0 ? request.getLimit() : request.getTopK();
        int candidateLimit = limit;
        boolean rerankEnabled = reranker != null && request.getQuery() != null && !request.getQuery().isBlank();
        if (rerankEnabled) {
            candidateLimit = Math.max(limit, limit * 3);
        }
        // Sub-store parity: choose embedder based on filters.
        float[] queryVec = storage.embed(request.getQuery(), "search", request.getFilters());
        java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> raw =
                storage.searchMemories(request.getQuery(), queryVec, candidateLimit, request.getUserId(), request.getAgentId(), request.getRunId(), request.getFilters());

        // Hybrid fine ranking: rerank candidates by query (Python parity).
        if (rerankEnabled && raw != null && !raw.isEmpty()) {
            raw = applyRerank(request.getQuery(), raw, limit);
        }

        // plugin lifecycle (python: on_search(processed_results) then update/delete)
        if (plugin != null && plugin.isEnabled() && raw != null && !raw.isEmpty()) {
            java.util.List<java.util.Map<String, Object>> payloads = new java.util.ArrayList<>();
            for (com.oceanbase.powermem.sdk.storage.base.OutputData d : raw) {
                if (d == null || d.getRecord() == null) {
                    continue;
                }
                payloads.add(toPayloadMap(d.getRecord()));
            }
            com.oceanbase.powermem.sdk.intelligence.plugin.IntelligentMemoryPlugin.OnSearchResult hook = plugin.onSearch(payloads);
            if (hook != null) {
                if (hook.getUpdates() != null) {
                    for (java.util.Map.Entry<String, java.util.Map<String, Object>> e : hook.getUpdates()) {
                        if (e == null) continue;
                        storage.updatePayloadFields(e.getKey(), request.getUserId(), request.getAgentId(), e.getValue());
                    }
                }
                if (hook.getDeletes() != null) {
                    for (String id : hook.getDeletes()) {
                        storage.deleteMemory(id, request.getUserId(), request.getAgentId());
                    }
                }
            }
        }

        java.util.List<com.oceanbase.powermem.sdk.model.SearchMemoriesResponse.SearchResult> results = intelligence.postProcess(raw);
        // Python parity: threshold filtering
        if (request.getThreshold() != null) {
            double th = request.getThreshold();
            java.util.List<com.oceanbase.powermem.sdk.model.SearchMemoriesResponse.SearchResult> filtered = new java.util.ArrayList<>();
            for (com.oceanbase.powermem.sdk.model.SearchMemoriesResponse.SearchResult r : results) {
                if (r != null && r.getScore() >= th) {
                    filtered.add(r);
                }
            }
            results = filtered;
        }
        com.oceanbase.powermem.sdk.model.SearchMemoriesResponse resp = new com.oceanbase.powermem.sdk.model.SearchMemoriesResponse(results);
        if (graphStore != null && config != null && config.getGraphStore() != null && config.getGraphStore().isEnabled()) {
            java.util.Map<String, Object> gf = buildGraphFilters(request.getUserId(), request.getAgentId(), request.getRunId(), request.getFilters());
            java.util.List<java.util.Map<String, Object>> gr = graphStore.search(request.getQuery(), gf, limit);
            // Python parity: when graph enabled, relations should always be present (empty list allowed).
            resp.setRelations(gr == null ? java.util.Collections.emptyList() : gr);
        } else {
            resp.setRelations(null);
        }
        return resp;
    }

    private java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> applyRerank(
            String query,
            java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> candidates,
            int finalLimit) {
        if (query == null || query.isBlank() || candidates == null || candidates.isEmpty() || reranker == null) {
            return candidates;
        }
        java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> safe = new java.util.ArrayList<>();
        java.util.List<String> docs = new java.util.ArrayList<>();
        for (com.oceanbase.powermem.sdk.storage.base.OutputData d : candidates) {
            if (d == null || d.getRecord() == null) continue;
            String text = d.getRecord().getContent();
            if (text == null) text = "";
            safe.add(d);
            docs.add(text);
        }
        if (safe.isEmpty()) {
            return candidates;
        }

        int topN = finalLimit > 0 ? finalLimit : safe.size();
        com.oceanbase.powermem.sdk.config.RerankConfig rc = config.getReranker();
        if (rc != null && rc.getTopK() > 0) {
            topN = Math.min(topN, rc.getTopK());
        }
        java.util.List<com.oceanbase.powermem.sdk.integrations.rerank.RerankResult> rr =
                reranker.rerank(query, docs, topN);
        if (rr == null || rr.isEmpty()) {
            return candidates;
        }

        java.util.List<com.oceanbase.powermem.sdk.storage.base.OutputData> out = new java.util.ArrayList<>();
        for (com.oceanbase.powermem.sdk.integrations.rerank.RerankResult r : rr) {
            if (r == null) continue;
            int idx = r.getIndex();
            if (idx < 0 || idx >= safe.size()) continue;
            com.oceanbase.powermem.sdk.storage.base.OutputData d = safe.get(idx);
            if (d == null || d.getRecord() == null) continue;

            // Attach debug fields (Python parity):
            // - _fusion_score: coarse score (vector/fts fusion)
            // - _rerank_score: fine score
            java.util.Map<String, Object> attrs = d.getRecord().getAttributes();
            if (attrs == null) {
                attrs = new java.util.HashMap<>();
                d.getRecord().setAttributes(attrs);
            }
            attrs.put("_fusion_score", d.getScore());
            attrs.put("_rerank_score", r.getScore());

            out.add(new com.oceanbase.powermem.sdk.storage.base.OutputData(d.getRecord(), r.getScore()));
        }
        return out;
    }

    @Override
    public com.oceanbase.powermem.sdk.model.UpdateMemoryResponse update(com.oceanbase.powermem.sdk.model.UpdateMemoryRequest request) {
        com.oceanbase.powermem.sdk.util.Preconditions.requireNonNull(request, "UpdateMemoryRequest is required");
        com.oceanbase.powermem.sdk.util.Preconditions.requireNonBlank(request.getMemoryId(), "memoryId is required");

        com.oceanbase.powermem.sdk.model.MemoryRecord updated = storage.updateMemory(
                request.getMemoryId(), request.getNewContent(), request.getUserId(), request.getAgentId(), request.getMetadata());
        return new com.oceanbase.powermem.sdk.model.UpdateMemoryResponse(updated);
    }

    @Override
    public com.oceanbase.powermem.sdk.model.GetMemoryResponse get(com.oceanbase.powermem.sdk.model.GetMemoryRequest request) {
        com.oceanbase.powermem.sdk.util.Preconditions.requireNonNull(request, "GetMemoryRequest is required");
        com.oceanbase.powermem.sdk.util.Preconditions.requireNonBlank(request.getMemoryId(), "memoryId is required");
        // Sub-store parity: lookup must scan main store and sub stores.
        com.oceanbase.powermem.sdk.model.MemoryRecord r = storage.getMemory(request.getMemoryId(), request.getUserId(), request.getAgentId());
        if (r != null && plugin != null && plugin.isEnabled()) {
            com.oceanbase.powermem.sdk.intelligence.plugin.IntelligentMemoryPlugin.OnGetResult hook = plugin.onGet(toPayloadMap(r));
            if (hook != null) {
                if (hook.isDelete()) {
                    storage.deleteMemory(r.getId(), request.getUserId(), request.getAgentId());
                    r = null;
                } else if (hook.getUpdates() != null && !hook.getUpdates().isEmpty()) {
                    storage.updatePayloadFields(r.getId(), request.getUserId(), request.getAgentId(), hook.getUpdates());
                }
            }
        }
        return new com.oceanbase.powermem.sdk.model.GetMemoryResponse(r);
    }

    private static java.util.Map<String, Object> toPayloadMap(com.oceanbase.powermem.sdk.model.MemoryRecord r) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        if (r == null) {
            return m;
        }
        m.put("id", r.getId());
        m.put("content", r.getContent());
        m.put("user_id", r.getUserId());
        m.put("agent_id", r.getAgentId());
        m.put("run_id", r.getRunId());
        m.put("hash", r.getHash());
        m.put("category", r.getCategory());
        m.put("scope", r.getScope());
        m.put("created_at", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        m.put("updated_at", r.getUpdatedAt() == null ? null : r.getUpdatedAt().toString());
        m.put("last_accessed_at", r.getLastAccessedAt() == null ? null : r.getLastAccessedAt().toString());
        m.put("metadata", r.getMetadata() == null ? new java.util.HashMap<>() : r.getMetadata());
        if (r.getAttributes() != null) {
            m.putAll(r.getAttributes());
        }
        return m;
    }

    @Override
    public com.oceanbase.powermem.sdk.model.DeleteMemoryResponse delete(String memoryId, String userId, String agentId) {
        com.oceanbase.powermem.sdk.util.Preconditions.requireNonBlank(memoryId, "memoryId is required");
        boolean deleted = storage.deleteMemory(memoryId, userId, agentId);
        return new com.oceanbase.powermem.sdk.model.DeleteMemoryResponse(deleted);
    }

    @Override
    public com.oceanbase.powermem.sdk.model.GetAllMemoriesResponse getAll(com.oceanbase.powermem.sdk.model.GetAllMemoriesRequest request) {
        com.oceanbase.powermem.sdk.util.Preconditions.requireNonNull(request, "GetAllMemoriesRequest is required");
        java.util.List<com.oceanbase.powermem.sdk.model.MemoryRecord> list = storage.getAllMemories(
                request.getUserId(), request.getAgentId(), request.getRunId(), request.getLimit(), request.getOffset());
        com.oceanbase.powermem.sdk.model.GetAllMemoriesResponse resp = new com.oceanbase.powermem.sdk.model.GetAllMemoriesResponse(list);
        java.util.List<java.util.Map<String, Object>> results = new java.util.ArrayList<>();
        for (com.oceanbase.powermem.sdk.model.MemoryRecord r : list) {
            if (r == null) {
                continue;
            }
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", r.getId());
            m.put("memory", r.getContent());
            m.put("user_id", r.getUserId());
            m.put("agent_id", r.getAgentId());
            m.put("run_id", r.getRunId());
            java.util.Map<String, Object> meta = r.getMetadata() == null ? new java.util.HashMap<>() : new java.util.HashMap<>(r.getMetadata());
            if (r.getCategory() != null && !r.getCategory().isBlank()) {
                meta.put("category", r.getCategory());
            }
            if (r.getScope() != null && !r.getScope().isBlank()) {
                meta.put("scope", r.getScope());
            }
            m.put("metadata", meta);
            m.put("created_at", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
            m.put("updated_at", r.getUpdatedAt() == null ? null : r.getUpdatedAt().toString());
            results.add(m);
        }
        resp.setResults(results);
        if (graphStore != null && config != null && config.getGraphStore() != null && config.getGraphStore().isEnabled()) {
            java.util.Map<String, Object> gf = buildGraphFilters(request.getUserId(), request.getAgentId(), request.getRunId(), null);
            int lim = request.getLimit() > 0 ? request.getLimit() : 100;
            java.util.List<java.util.Map<String, Object>> gr = graphStore.getAll(gf, lim + Math.max(0, request.getOffset()));
            resp.setRelations(gr == null ? java.util.Collections.emptyList() : gr);
        } else {
            resp.setRelations(null);
        }
        return resp;
    }

    @Override
    public com.oceanbase.powermem.sdk.model.DeleteAllMemoriesResponse deleteAll(
            com.oceanbase.powermem.sdk.model.DeleteAllMemoriesRequest request) {
        com.oceanbase.powermem.sdk.util.Preconditions.requireNonNull(request, "DeleteAllMemoriesRequest is required");
        int deleted = storage.clearMemories(request.getUserId(), request.getAgentId(), request.getRunId());
        if (graphStore != null && config != null && config.getGraphStore() != null && config.getGraphStore().isEnabled()) {
            java.util.Map<String, Object> gf = buildGraphFilters(request.getUserId(), request.getAgentId(), request.getRunId(), null);
            try {
                graphStore.deleteAll(gf);
            } catch (Exception ignored) {
                // best-effort, graph should not break deleteAll
            }
        }
        return new com.oceanbase.powermem.sdk.model.DeleteAllMemoriesResponse(deleted);
    }

    private java.util.Map<String, Object> maybeAddToGraph(com.oceanbase.powermem.sdk.model.AddMemoryRequest request) {
        if (graphStore == null || config == null || config.getGraphStore() == null || !config.getGraphStore().isEnabled()) {
            return null;
        }
        String data;
        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (com.oceanbase.powermem.sdk.model.Message m : request.getMessages()) {
                if (m == null) continue;
                if ("system".equalsIgnoreCase(m.getRole())) continue;
                if (m.getContent() == null || m.getContent().isBlank()) continue;
                if (sb.length() > 0) sb.append("\n");
                sb.append(m.getContent());
            }
            data = sb.toString();
        } else {
            data = request.getText();
        }
        if (data == null || data.isBlank()) {
            return null;
        }
        java.util.Map<String, Object> gf = buildGraphFilters(request.getUserId(), request.getAgentId(), request.getRunId(), request.getFilters());
        return graphStore.add(data, gf);
    }

    private static java.util.Map<String, Object> buildGraphFilters(
            String userId,
            String agentId,
            String runId,
            java.util.Map<String, Object> extraFilters) {
        java.util.Map<String, Object> gf = extraFilters == null ? new java.util.HashMap<>() : new java.util.HashMap<>(extraFilters);
        gf.put("user_id", userId == null ? "user" : userId);
        gf.put("agent_id", agentId);
        gf.put("run_id", runId);
        if (gf.get("user_id") == null) {
            gf.put("user_id", "user");
        }
        return gf;
    }
}

