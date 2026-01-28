# powermem-java-sdk

Java SDK for PowerMem (JDK 11 / Maven).

> This repository maintains **two README files**: `README_EN.md` and `README_CN.md`. Please keep them in sync.

## Quickstart

### 1) Prepare `.env`

An `.env.example` is provided at the repo root (covers all supported configs). Copy it to `.env` and edit as needed:

```bash
cp .env.example .env
```

Config loading rules:
- **Default search**: `${user.dir}/.env` (also `config/.env`, `conf/.env`)
- **Precedence**: OS environment variables **override** `.env` keys

### 2) Build & run

```bash
mvn -q -DskipTests package
```

This is a **library SDK** project. It does not ship a runnable `Main` entry by default.
Use **unit tests / integration tests** to validate behavior.

### 3) Run tests

```bash
# Unit tests (offline)
mvn test
```

If you use `-q` (quiet), Maven prints nothing on success. Use the **exit code** to tell (0 means success).

#### Where to find test reports

- **Surefire reports**: `target/surefire-reports/`
  - `*.txt`: plain text output
  - `*.xml`: JUnit XML (CI-friendly)

## `.env` Examples

### SQLite + Mock (offline)

```dotenv
DATABASE_PROVIDER=sqlite
SQLITE_PATH=./data/powermem_dev.db
EMBEDDING_PROVIDER=mock
LLM_PROVIDER=mock
```

### SQLite + Qwen (real embedding/LLM)

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

### OceanBase + Mock (offline connectivity smoke test)

```dotenv
DATABASE_PROVIDER=oceanbase
OCEANBASE_HOST=127.0.0.1
OCEANBASE_PORT=2881
OCEANBASE_USER=root@sys
OCEANBASE_PASSWORD=your_password
OCEANBASE_DATABASE=ai_work
OCEANBASE_COLLECTION=memories_java_debug

# MockEmbedder uses fixed 10 dims
OCEANBASE_EMBEDDING_MODEL_DIMS=10
EMBEDDING_PROVIDER=mock
LLM_PROVIDER=mock
```

Notes:
- **Strongly recommended** to use a dedicated `OCEANBASE_COLLECTION` (e.g. `memories_java_debug`) to avoid clashing with existing tables.
- If the table already exists, the SDK will best-effort `ALTER TABLE` to add required columns (e.g. `payload`/`vector`) to avoid errors like `Unknown column 'payload'`.

### OceanBase + Qwen (real vector search)

```dotenv
DATABASE_PROVIDER=oceanbase
OCEANBASE_HOST=127.0.0.1
OCEANBASE_PORT=2881
OCEANBASE_USER=root@sys
OCEANBASE_PASSWORD=your_password
OCEANBASE_DATABASE=ai_work
OCEANBASE_COLLECTION=memories_java_debug

# Must match your embedding dims
OCEANBASE_EMBEDDING_MODEL_DIMS=1536

EMBEDDING_PROVIDER=qwen
EMBEDDING_API_KEY=sk-xxx
EMBEDDING_MODEL=text-embedding-v4
EMBEDDING_DIMS=1536
QWEN_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1

LLM_PROVIDER=mock
```

### OceanBase Hybrid + Reranker (Python parity: coarse fusion + fine rerank)

> The reranker reorders fused candidates (default: `topK*3`) and writes `_fusion_score` / `_rerank_score` into the returned `metadata`.
>
> **FULLTEXT parser support & downgrade strategy (best-effort)**:
> - **Supported values**: `ik` / `ngram` / `ngram2` / `beng` / `space` (same as the Python implementation)
> - If an unsupported value is configured, the SDK logs a warning and ignores it
> - If FULLTEXT index creation fails, the SDK retries in this order: no parser → configured parser → other supported parsers
> - If all attempts fail, FTS falls back to `LIKE` (with warnings explaining why)

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

EMBEDDING_PROVIDER=qwen
EMBEDDING_API_KEY=sk-xxx
EMBEDDING_MODEL=text-embedding-v4
EMBEDDING_DIMS=1536
QWEN_EMBEDDING_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1

RERANKER_PROVIDER=qwen
RERANKER_API_KEY=sk-xxx
RERANKER_MODEL=gte-rerank-v2
RERANKER_TOP_K=10
RERANKER_BASE_URL=https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank
```

## OceanBase validation (integration tests)

Integration tests are gated by env vars to avoid accidental connections:
- `OceanBaseVectorStoreIT`
- `OceanBaseMemoryE2eIT`

## Tests

Unit tests (offline):

```bash
mvn test
```

OceanBase integration test (requires OceanBase env vars):

```bash
export OCEANBASE_HOST=xxx
export OCEANBASE_USER=xxx
export OCEANBASE_PASSWORD=xxx
export OCEANBASE_DATABASE=xxx
export OCEANBASE_PORT=2881

mvn -Dtest=OceanBaseVectorStoreIT test
mvn -Dtest=OceanBaseMemoryE2eIT test
```

## Packaging & using as a real Java SDK

### 1) Install locally (use from other local projects)

```bash
mvn -DskipTests install
```

Then add dependency in your app (Maven):

```xml
<dependency>
  <groupId>com.oceanbase.powermem.sdk</groupId>
  <artifactId>powermem-java-sdk</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

(Gradle)

```gradle
implementation "com.oceanbase.powermem.sdk:powermem-java-sdk:1.0-SNAPSHOT"
```

### 2) Publish to a repository (team/production)

- **Private Maven repo (recommended)**: configure `distributionManagement` in `pom.xml`, then run `mvn deploy`
- **Maven Central**: requires full POM metadata (license/scm/developers), sources+javadocs, plus signing and release workflow

### 3) Minimal usage

```java
import com.oceanbase.powermem.sdk.config.ConfigLoader;
import com.oceanbase.powermem.sdk.core.Memory;
import com.oceanbase.powermem.sdk.model.AddMemoryRequest;
import com.oceanbase.powermem.sdk.model.SearchMemoriesRequest;

var cfg = ConfigLoader.fromEnvAndDotEnv();
var mem = new Memory(cfg);

var add = AddMemoryRequest.ofText("User prefers concise answers", "user123");
add.setInfer(false);
mem.add(add);

var search = SearchMemoriesRequest.ofQuery("What are the user's preferences?", "user123");
var resp = mem.search(search);
System.out.println(resp.getResults());
```

