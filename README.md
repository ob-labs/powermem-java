# powermem-java-sdk

Java SDK for PowerMem (JDK 11 / Maven).

[中文](README_CN.md) | [PowerMem Main Repository](https://github.com/oceanbase/powermem)

## Overview

PowerMem is an intelligent memory system for AI applications. This module provides a **Java SDK** so you can integrate memory capabilities into Java services and connect to different storages and model providers (SQLite, OceanBase, Qwen, etc.).

## Key Features

- **Developer-friendly**: JDK 11 + Maven; loads config from `.env` automatically (env vars override).
- **Pluggable storage backends**: **SQLite** (local dev) and **OceanBase (MySQL mode)**.
- **Hybrid Search**: OceanBase supports **FULLTEXT + Vector** with fusion strategies (RRF / Weighted Fusion).
- **Optional reranker**: second-stage reranking for hybrid/search candidates; writes `_fusion_score` / `_rerank_score` into result `metadata`.
- **Graph Store (Python parity)**: `graph_store` support (entity/relation extraction, persistence, multi-hop retrieval, BM25 rerank) and returns `relations` from `add/search/get_all`.
- **Best-effort schema evolution**: auto `ALTER TABLE` for missing required columns when using existing OceanBase tables.
- **Testable**: offline unit tests by default; OceanBase integration tests are env-gated.

## Minimal Example (Java)

```java
import com.oceanbase.powermem.sdk.config.ConfigLoader;
import com.oceanbase.powermem.sdk.core.Memory;
import com.oceanbase.powermem.sdk.model.AddMemoryRequest;
import com.oceanbase.powermem.sdk.model.SearchMemoriesRequest;

var cfg = ConfigLoader.fromEnvAndDotEnv();
var mem = new Memory(cfg);

mem.add(AddMemoryRequest.ofText("User prefers concise answers", "user123"));

var resp = mem.search(SearchMemoriesRequest.ofQuery("What are the user's preferences?", "user123"));
System.out.println(resp.getResults());
```

## Quick Links

- **English guide (this file)**: `README.md`
- **中文完整指南**: `README_CN.md`
- **Config template**: `.env.example`
- **Test reports**: `target/surefire-reports/`

