# powermem-java-sdk

PowerMem 的 Java SDK（JDK 11 / Maven）。

[中文](README_CN.md) | [English](README_EN.md) | [PowerMem 主仓库](https://github.com/oceanbase/powermem)

## 项目简介

PowerMem 是一套面向 AI 应用的智能记忆系统，解决“长期记忆、检索与更新”的工程化落地问题。本目录提供其 **Java SDK**，方便在 Java 应用中直接集成记忆能力，并对接不同存储与模型服务（如 SQLite、OceanBase、Qwen 等）。

> 详细的使用与配置请查看：
> - `README_CN.md`（中文使用指南，包含 `.env` 示例 / 测试 / 打包说明）
> - `README_EN.md`（English guide）

## 核心特点

- **开发者友好**：JDK 11 + Maven；配置自动从 `.env` 加载（环境变量优先）。
- **存储后端可插拔**：支持 **SQLite**（本地开发）与 **OceanBase（MySQL mode）**。
- **混合检索（Hybrid Search）**：OceanBase 支持 **FULLTEXT + Vector** 并提供融合策略（RRF / Weighted Fusion）。
- **可选 Reranker**：支持在 hybrid/search 候选集上进行二次精排，并把 `_fusion_score` / `_rerank_score` 写入返回 `metadata` 便于观测。
- **兼容与演进**：OceanBase 表存在时会 best-effort 执行 `ALTER TABLE` 补齐必要列，减少线上环境升级成本。
- **可测试**：离线单测默认可跑；OceanBase 集成测试通过环境变量开关避免误连线上库。

## 快速开始（入口）

- **中文完整指南**：`README_CN.md`
- **English guide**：`README_EN.md`
- **配置示例**：`.env.example`

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

