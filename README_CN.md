# powermem-java-sdk

PowerMem 的 Java SDK（JDK 11 / Maven）。

> 本仓库提供 `README_CN.md`（中文）与 `README.md`（English）两份文档，请保持同步更新。

[English](README.md) | [PowerMem 主仓库](https://github.com/oceanbase/powermem)

## 项目简介

PowerMem 是一套面向 AI 应用的智能记忆系统，解决“长期记忆、检索与更新”的工程化落地问题。本目录提供其 **Java SDK**，方便在 Java 应用中直接集成记忆能力，并对接不同存储与模型服务（如 SQLite、OceanBase、Qwen 等）。

## 核心特点

- **开发者友好**：JDK 11 + Maven；配置自动从 `.env` 加载（环境变量优先）。
- **存储后端可插拔**：支持 **SQLite**（本地开发）与 **OceanBase（MySQL mode）**。
- **混合检索（Hybrid Search）**：OceanBase 支持 **FULLTEXT + Vector** 并提供融合策略（RRF / Weighted Fusion）。
- **可选 Reranker**：支持在 hybrid/search 候选集上二次精排，并把 `_fusion_score` / `_rerank_score` 写入返回 `metadata` 便于观测。
- **Graph Store（对齐 Python）**：支持 `graph_store`（实体/关系抽取、图存储、多跳检索、BM25 重排），并在 `add/search/get_all` 返回 `relations`。
- **兼容与演进**：OceanBase 表存在时 best-effort 执行 `ALTER TABLE` 补齐必要列，减少升级成本。
- **可测试**：离线单测默认可跑；OceanBase 集成测试通过环境变量开关避免误连线上库。

## 最小示例（Java）

```java
import com.oceanbase.powermem.sdk.config.ConfigLoader;
import com.oceanbase.powermem.sdk.core.Memory;
import com.oceanbase.powermem.sdk.model.AddMemoryRequest;
import com.oceanbase.powermem.sdk.model.SearchMemoriesRequest;

var cfg = ConfigLoader.fromEnvAndDotEnv();
var mem = new Memory(cfg);

mem.add(AddMemoryRequest.ofText("用户喜欢简洁的中文回答", "user123"));

var resp = mem.search(SearchMemoriesRequest.ofQuery("用户偏好是什么？", "user123"));
System.out.println(resp.getResults());
```

## 快速入口

- **主仓库**：[PowerMem](https://github.com/oceanbase/powermem)
- **配置示例**：`.env.example`
- **测试报告**：`target/surefire-reports/`

## 快速开始

### 1) 准备 `.env`

项目根目录已提供 `.env.example`（包含所有可用配置项）。复制为 `.env` 并按需修改：

```bash
cp .env.example .env
```

配置加载规则：
- **默认会读取**：`${user.dir}/.env`（以及 `config/.env`、`conf/.env`）
- **优先级**：系统环境变量 **覆盖** `.env` 中同名配置

### 2) 编译 & 运行

```bash
mvn -q -DskipTests package
```

这是一个 **library SDK** 工程，默认不提供 `Main` 可执行入口。推荐通过 **单元测试 / 集成测试** 来验证功能。

### 3) 运行测试

```bash
# 运行单元测试（离线）
mvn test
```

如果你使用了 `-q`（quiet），成功时不会输出任何内容。是否成功以 **退出码** 为准（0 表示成功）。

#### 如何查看测试报告

- **Surefire 报告目录**：`target/surefire-reports/`
  - `*.txt`：控制台文本报告
  - `*.xml`：JUnit XML（CI 常用）

## 示例：不同组合的 `.env`

### SQLite + Mock（离线可跑）

```dotenv
DATABASE_PROVIDER=sqlite
SQLITE_PATH=./data/powermem_dev.db
EMBEDDING_PROVIDER=mock
LLM_PROVIDER=mock
```

### SQLite + Qwen（真实 embedding/LLM）

```dotenv
DATABASE_PROVIDER=sqlite
SQLITE_PATH=./data/powermem_dev.db

LLM_PROVIDER=qwen
LLM_API_KEY=sk-xxx
LLM_MODEL=qwen-plus
QWEN_LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1

EMBEDDING_PROVIDER=qwen
EMBEDDING_API_KEY=sk-xxx
EMBEDDING_MODEL=text-embedding-v4
EMBEDDING_DIMS=1536
QWEN_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
```

### OceanBase + Mock（离线可跑，便于连通性验证）

```dotenv
DATABASE_PROVIDER=oceanbase
OCEANBASE_HOST=127.0.0.1
OCEANBASE_PORT=2881
OCEANBASE_USER=root@sys
OCEANBASE_PASSWORD=your_password
OCEANBASE_DATABASE=ai_work
OCEANBASE_COLLECTION=memories_java_debug

# 向量维度（MockEmbedder 固定 10）
OCEANBASE_EMBEDDING_MODEL_DIMS=10
EMBEDDING_PROVIDER=mock
LLM_PROVIDER=mock
```

注意：
- **强烈建议使用独立的 `OCEANBASE_COLLECTION`**（例如 `memories_java_debug`），避免与你环境里已有表冲突
- 如果你指向的是**已存在的表**，SDK 会在启动时 best-effort 执行 `ALTER TABLE` 补齐所需列（例如 `payload`/`vector`），以避免 `Unknown column 'payload'` 这类错误

### OceanBase + Qwen（真实向量检索）

```dotenv
DATABASE_PROVIDER=oceanbase
OCEANBASE_HOST=127.0.0.1
OCEANBASE_PORT=2881
OCEANBASE_USER=root@sys
OCEANBASE_PASSWORD=your_password
OCEANBASE_DATABASE=ai_work
OCEANBASE_COLLECTION=memories_java_debug

# 必须与你 embedding 维度一致
OCEANBASE_EMBEDDING_MODEL_DIMS=1536

EMBEDDING_PROVIDER=qwen
EMBEDDING_API_KEY=sk-xxx
EMBEDDING_MODEL=text-embedding-v4
EMBEDDING_DIMS=1536
QWEN_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1

LLM_PROVIDER=mock
```

### OceanBase Hybrid + Reranker（对齐 Python：粗排 fusion + 精排 rerank）

> reranker 会把融合后的候选集（默认 topK*3）做二次排序，并把 `_fusion_score` / `_rerank_score` 写入返回的 `metadata`。
>
> **FULLTEXT parser 支持与降级策略（best-effort）**：
> - **支持值**：`ik` / `ngram` / `ngram2` / `beng` / `space`（与 Python 侧一致）
> - 若配置了非法值：会记录 warning 并忽略该值，继续尝试其它策略
> - 若 FULLTEXT 索引创建失败：会按“无 parser → 指定 parser → 其它 parser”自动降级尝试
> - 若最终仍失败：FTS 会降级为 `LIKE`（同时日志会提示原因）

```dotenv
DATABASE_PROVIDER=oceanbase
OCEANBASE_HOST=127.0.0.1
OCEANBASE_PORT=2881
OCEANBASE_USER=root@sys
OCEANBASE_PASSWORD=your_password
OCEANBASE_DATABASE=ai_work
OCEANBASE_COLLECTION=memories_java_debug

OCEANBASE_HYBRID_SEARCH=true
OCEANBASE_FUSION_METHOD=rrf
OCEANBASE_VECTOR_WEIGHT=0.5
OCEANBASE_FTS_WEIGHT=0.5
OCEANBASE_FULLTEXT_PARSER=ik

# 真实 embedding
EMBEDDING_PROVIDER=qwen
EMBEDDING_API_KEY=sk-xxx
EMBEDDING_MODEL=text-embedding-v4
EMBEDDING_DIMS=1536
QWEN_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1

# reranker（推荐 China: gte-rerank-v2）
RERANKER_PROVIDER=qwen
RERANKER_API_KEY=sk-xxx
RERANKER_MODEL=gte-rerank-v2
RERANKER_TOP_K=10
RERANKER_BASE_URL=https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
```

## OceanBase 验证（集成测试）

集成测试默认不会跑（避免误连线上库），只有在设置了环境变量时才会启用：
- `OceanBaseVectorStoreIT`
- `OceanBaseMemoryE2eIT`
- `OceanBaseGraphStoreIT`

## Graph Store（关系抽取 / 图检索）

Python 版本支持 `graph_store`，并在 `add/search/get_all` 返回 `relations`。

Java 版本提供了 **GraphStore 接口与返回结构对齐**，并内置一个 **`memory`（InMemoryGraphStore）** 实现用于离线测试：
- 开启方式（代码配置）：`cfg.getGraphStore().setEnabled(true); cfg.getGraphStore().setProvider("memory");`
- 返回结构：
  - `add`：`relations={"deleted_entities":[...], "added_entities":[{"source","relationship","target"}, ...]}`
  - `search/get_all`：`relations=[{"source","relationship","destination"}, ...]`

同时，Java 版本已提供 **`OceanBaseGraphStore`（对齐 Python 的 schema/流程）**：
- **Schema**：`graph_entities` + `graph_relationships` 两表（可通过 `GRAPH_STORE_ENTITIES_TABLE` / `GRAPH_STORE_RELATIONSHIPS_TABLE` 修改表名）
- **抽取**：支持 LLM tools（`extract_entities` / `establish_relationships` / `delete_graph_memory`），并有解析兜底
- **ANN**：best-effort `VECTOR(dims)` + `CREATE VECTOR INDEX`，失败则回退到 `embedding_json` brute-force
- **检索**：多跳扩展 + BM25 重排（tokenizer 为轻量实现，便于离线运行）

### GraphStore 独立 LLM/Embedding 配置（对齐 Python：`graph_store.llm.*`）

你可以为 GraphStore 单独配置 LLM/Embedder（优先级高于全局 `LLM_*` / `EMBEDDING_*`）：

```dotenv
# 开启 graph store
GRAPH_STORE_ENABLED=true
GRAPH_STORE_PROVIDER=oceanbase

# graph_store.llm.*
GRAPH_STORE_LLM_PROVIDER=qwen
GRAPH_STORE_LLM_API_KEY=sk-xxx
GRAPH_STORE_LLM_MODEL=qwen-plus
GRAPH_STORE_LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1

# graph_store.embedder.*
GRAPH_STORE_EMBEDDING_PROVIDER=qwen
GRAPH_STORE_EMBEDDING_API_KEY=sk-xxx
GRAPH_STORE_EMBEDDING_MODEL=text-embedding-v4
GRAPH_STORE_EMBEDDING_DIMS=1536
GRAPH_STORE_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
```

## 测试

单元测试（离线）：

```bash
mvn test
```

OceanBase 集成测试（需要设置 OceanBase 环境变量）：

```bash
export OCEANBASE_HOST=xxx
export OCEANBASE_USER=xxx
export OCEANBASE_PASSWORD=xxx
export OCEANBASE_DATABASE=xxx
export OCEANBASE_PORT=2881

mvn -Dtest=OceanBaseVectorStoreIT test
mvn -Dtest=OceanBaseMemoryE2eIT test
mvn -Dtest=OceanBaseGraphStoreIT test
```

## 作为正式 Java SDK 打包与使用

### 1) 本地安装（给本机其它项目使用）

```bash
mvn -DskipTests install
```

然后在你自己的项目里引入（Maven）：

```xml
<dependency>
  <groupId>com.oceanbase.powermem.sdk</groupId>
  <artifactId>powermem-java-sdk</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

（Gradle）

```gradle
implementation "com.oceanbase.powermem.sdk:powermem-java-sdk:1.0-SNAPSHOT"
```

### 2) 发布到仓库（团队/生产使用）

- **私有仓库（推荐）**：在 `pom.xml` 配置 `distributionManagement`，然后执行 `mvn deploy`
- **Maven Central**：需要完善 `pom.xml` 元数据（license/scm/developer）、生成 `sources/javadoc`、并配置签名与发布流程

### 3) SDK 的基本用法

```java
import com.oceanbase.powermem.sdk.config.ConfigLoader;
import com.oceanbase.powermem.sdk.core.Memory;
import com.oceanbase.powermem.sdk.model.AddMemoryRequest;
import com.oceanbase.powermem.sdk.model.SearchMemoriesRequest;

var cfg = ConfigLoader.fromEnvAndDotEnv();
var mem = new Memory(cfg);

var add = AddMemoryRequest.ofText("用户喜欢简洁的中文回答", "user123");
add.setInfer(false);
mem.add(add);

var search = SearchMemoriesRequest.ofQuery("用户偏好是什么？", "user123");
var resp = mem.search(search);
System.out.println(resp.getResults());
```

