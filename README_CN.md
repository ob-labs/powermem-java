# powermem-java-sdk

PowerMem 的 Java SDK（JDK 11 / Maven）。

> 本仓库提供 `README_CN.md` / `README_EN.md` 两份文档，请保持同步更新。

[README](README.md) | [English](README_EN.md) | [PowerMem 主仓库](https://github.com/oceanbase/powermem)

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

