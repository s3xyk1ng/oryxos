# Memory 模块设计文档

> 本文是 Memory 模块讨论后的设计快照。核心目标是：用统一的 `MemoryService` 收口会话历史与长期记忆，用统一的 `LongTermMemoryStore` 契约约束 Markdown、SQLite、Mem0 三档后端。
>
> 本文以本次讨论确认的四条后端契约为准：不缓存、CORE 永不截断且不检索、Agent 显式选择 scope、初版只做关键词包含匹配。

---

## 第一段：模块是什么，干嘛用的

### 1.1 模块定位

**Memory 是 Agent 的上下文与长期状态层：向上为 ReAct 循环提供“核心记忆 + 会话历史”，为 Agent 提供记忆保存和归档召回工具；向下通过统一后端接口切换 Markdown、SQLite、Mem0。**

它解决四个问题：

1. Agent 重启或跨会话后，仍能保留长期有效的重要信息。
2. 核心记忆始终可见，普通归档信息按需召回，避免所有历史都进入 Prompt。
3. ReAct 循环不需要分别理解 Session、文件、数据库和外部记忆服务。
4. 三档后端可以按部署需求替换，上层代码不变。

### 1.2 在系统中的位置

```text
                         User / Channel
                              │
                              ▼
                       AgentService.process()
                              │
                              ▼
              ┌────────── ReActLoop ──────────┐
              │                               │
              │ buildPromptContext(session)   │ save_memory / recall_memory
              ▼                               ▼
       ┌────────────────── MemoryService ──────────────────┐
       │                                                    │
       │ session history                                   │ explicit scope / keyword
       ▼                                                    ▼
 SessionManager                                  LongTermMemoryStore
                                                        │
                           ┌────────────────────────────┼────────────────────────────┐
                           ▼                            ▼                            ▼
                MarkdownMemoryStore            SqliteMemoryStore             Mem0MemoryStore
                .oryxos/memory/MEMORY.md       memory_entries table          self-hosted Mem0
```

### 1.3 核心职责

| 职责 | 说明 |
|---|---|
| 统一上下文入口 | 为 Prompt 组装提供核心记忆和当前会话历史 |
| 长期记忆写入 | 按 Agent 显式选择的 CORE / ARCHIVAL 分区追加记忆 |
| 归档记忆召回 | 仅在 ARCHIVAL 中执行普通关键词包含匹配 |
| 后端可插拔 | 通过配置选择 Markdown、SQLite 或 Mem0 |
| 保证后端一致性 | 三档后端执行同一套契约测试，任何实现违约都必须变红 |

### 1.4 上下游边界

| 方向 | 对象 | 交互方式 |
|---|---|---|
| 上游 | `PromptBuilder` / `ReActLoop` | 获取核心记忆与会话历史组成的上下文 |
| 上游 | `MemoryTools` | 调用 `saveMemory`、`recallMemory` |
| 下游 | `SessionManager` | 获取当前会话历史，不把会话历史写入长期记忆 |
| 下游 | `LongTermMemoryStore` | 追加、加载、检索长期记忆 |
| 下游 | SQLite / 文件系统 / Mem0 | 由所选后端负责真实持久化 |

---

## 第二段：动手之前先想清楚几件事

### 2.1 MemoryScope 只有两个值

```java
public enum MemoryScope {
    CORE,
    ARCHIVAL
}
```

| Scope | 语义 | 加载行为 | 检索行为 | 典型内容 |
|---|---|---|---|---|
| `CORE` | 每轮都可能需要的稳定、关键事实 | 每次完整加载，禁止截断 | 不参与 recall，因为已经完整可见 | 用户长期偏好、长期项目约束 |
| `ARCHIVAL` | 不需要每轮出现，但以后可能召回的信息 | 按最新 N 条的读取窗口加载 | 仅在此分区做关键词检索 | 历史讨论、阶段性结论、普通参考资料 |

Scope 只回答一个问题：**这条记忆应该始终可见，还是按需召回？**

本期不增加 `SESSION`、`TEMP`、`SHORT_TERM`、`AUTO`：

- 会话历史归 `SessionManager`，不是长期记忆分区。
- 没有过期和清理语义前，不增加临时分区。
- 不允许 `AUTO`，因为分区必须由 Agent 显式决定。
- 主题分类属于未来的标签或元数据，不属于 Scope。

### 2.2 三档后端不是强制升级路线

| 后端 | 定位 | 适用场景 | 主要代价 |
|---|---|---|---|
| Markdown | 零依赖、人可读的默认档 | 单机、记忆量较小、希望直接查看或 Git 管理 | 文件解析与检索能力有限 |
| SQLite | 结构化、较大记忆量进阶档 | 上千条记忆、需要结构化查询、不希望新增外部服务 | 需要维护 schema 和数据库访问 |
| Mem0 | 外部智能记忆集成档 | 确实需要独立记忆服务，未来需要语义能力 | 增加部署、网络、凭证和故障处理 |

三档共享同一份契约。选择 Mem0 不意味着可以绕过 CORE / ARCHIVAL 语义，也不意味着本期就启用语义召回。

### 2.3 四条不可破坏的后端契约

| # | 契约 | 必须做到 | 禁止行为 |
|---|---|---|---|
| 1 | 不缓存 | 每次加载重新读取文件、查库或调用 API；写入后下一次立即可见 | 返回历史快照、依赖缓存失效、重启后才可见 |
| 2 | CORE 永不截断 | CORE 全量加载；截断和检索只作用于 ARCHIVAL | CORE 加 LIMIT、CORE 字符串裁剪、recall 返回 CORE |
| 3 | Agent 显式决定 Scope | 每次保存必须明确传 CORE 或 ARCHIVAL，并原样写入 | 默认 ARCHIVAL、自动升级 CORE、门面或后端猜分区 |
| 4 | 初版简单召回 | 只做普通关键词包含匹配，特殊字符按普通文本处理 | 正则、分词、语义扩展、把 `%` 或 `.*` 当模式语法 |

补充语义：

- 归档截断只是 `load()` 的读取窗口，不物理删除旧归档。
- 检索命中多行记忆的一行时，必须返回完整记忆，不能只返回命中行。
- 无匹配返回空结果；后端故障不能伪装成空记忆。
- 后续即使升级检索能力，也不能改变“只检索 ARCHIVAL”的约束。

### 2.4 模块边界

| Memory 负责 | Memory 不负责 |
|---|---|
| 长期记忆分区、追加、加载、召回 | 自动从对话提炼重要事实 |
| 核心记忆和会话历史的 Prompt 上下文组装 | Provider 调用和模型协议转换 |
| 三档后端的统一行为 | Tool 的调度和执行，仍归 `ToolExecutor` |
| 后端配置选择 | 三档之间的数据自动迁移 |
| 完整记忆条目的边界 | 初版正则、分词、向量化和语义检索 |

### 2.5 避坑指南

| 陷阱 | 后果 | 应对 |
|---|---|---|
| `MemoryService` 和 `PromptBuilder` 都拼会话历史 | 历史重复、浪费 Token | 明确唯一拼接入口，并用集成测试钉死 |
| `MemoryPortImpl` 把 ARCHIVAL 全部注入 | 归档失去按需召回意义 | Prompt 自动注入只取 CORE 和会话历史 |
| `save_memory` 缺省 ARCHIVAL | Agent 没有显式决策 | `scope` 设为必填参数 |
| Markdown 检索整个文件 | CORE 可能通过 recall 泄漏 | 解析后只检索 ARCHIVAL 分区 |
| SQLite 直接拼 `%keyword%` | `%`、`_` 变成 LIKE 模式 | 使用普通子串语义，例如 SQLite `instr()` |
| Mem0 只加载第一页 | CORE 被隐性截断 | CORE 必须完整处理分页 |
| 后端异常时返回空 | 服务故障被误认为没有记忆 | 空结果与后端异常必须区分 |
| 多后端 Bean 同时存在 | 注入歧义或写入多个后端 | 配置条件装配，任意时刻只有一个实现生效 |

---

## 第三段：代码怎么写

### 3.1 核心类与接口

| 类 / 接口 | 模块 | 职责 | 改动方向 |
|---|---|---|---|
| `MemoryScope` | `oryxos-memory` | 定义 CORE / ARCHIVAL | 独立枚举，新增 |
| `MemoryService` | `oryxos-memory` | 对上统一门面 | 调整为三个上层能力 |
| `LongTermMemoryStore` | `oryxos-memory` | 长期记忆可插拔接口 | 只保留 append / load / recallByKeyword |
| `MarkdownMemoryStore` | `oryxos-memory` | 默认文件后端 | 修复分区检索、完整条目和无缓存语义 |
| `SqliteMemoryStore` | `oryxos-memory` | SQLite 后端 | 新增 |
| `Mem0MemoryStore` | `oryxos-memory` | 自托管 Mem0 后端 | 新增 |
| `MemoryTools` | `oryxos-memory` | `save_memory` / `recall_memory` | scope 改为必填 |
| `MemoryPort` | `oryxos-core` | core 对 Memory 的依赖抽象 | 配合唯一 Prompt 拼接入口调整 |
| `MemoryPortImpl` | `oryxos-memory` | 将 core 调用委托给 MemoryService | 不再直接注入完整长期记忆 |
| `MemoryEntryEntity` | `oryxos-storage` | `memory_entries` JPA 映射 | 新增 |
| `MemoryEntryRepository` | `oryxos-storage` | SQLite 查询 | 新增 |

`MemoryTools` 当前实现采用内部类 + `@Component("saveMemoryTool")` / `@Component("recallMemoryTool")` 注册为独立 Bean，供 ToolRegistry 发现。是否后续改为 Day 5 的 `@Tool` 注解方式，取决于工具分支的集成顺序：

> 已确认：本期行为验收不依赖内部类或 `@Tool` 的具体实现方式，内部类 Bean 方式可保持现有 `OryxTool` 契约不变。

### 3.2 上层接口：MemoryService

```java
public class MemoryService {

    public String buildPromptContext(Session session) {
        // 每次重新加载长期记忆
        // 只拼接完整 CORE 与当前会话历史
        // 不自动拼接 ARCHIVAL
    }

    public void saveMemory(String content, MemoryScope scope) {
        // content 与 scope 必填
        // scope 原样委托给 LongTermMemoryStore
    }

    public List<String> recallMemory(String keyword) {
        // 普通关键词原样委托
        // 返回完整归档记忆条目
    }
}
```

上层只暴露三个能力，不让 `ReActLoop` 直接访问文件、Repository 或 Mem0 API。

### 3.3 下层接口：LongTermMemoryStore

```java
public interface LongTermMemoryStore {

    void append(String content, MemoryScope scope);

    MemoryContent load();

    List<String> recallByKeyword(String keyword);
}
```

接口只保留三个方法，不将 `capabilities()`、`archivalEntries()` 等扩展能力纳入本期契约。

`MemoryContent` 必须能区分核心与归档内容；返回空存储时不得返回 `null`。`recallByKeyword` 返回的每个字符串代表一条完整记忆，而不是文件中的一行。

### 3.4 Prompt 数据流

```text
ReActLoop / PromptBuilder
    │
    │ buildPromptContext(session)
    ▼
MemoryService
    │
    ├─ LongTermMemoryStore.load()
    │      ├─ CORE：完整保留并注入
    │      └─ ARCHIVAL：允许按窗口返回，但此处不自动注入
    │
    └─ SessionManager / Session
           └─ 当前会话历史，按既有 maxHistoryTurns 规则处理
    │
    ▼
Core Memory + Conversation History
    │
    ▼
PromptBuilder 继续拼接 Tool 列表、当前时间等既有内容
```

必须确保核心记忆和会话历史都只出现一次。

### 3.5 保存与召回数据流

```text
Agent tool_call: save_memory
    │ content + explicit scope
    ▼
MemoryTools
    ▼
MemoryService.saveMemory(content, scope)
    ▼
LongTermMemoryStore.append(content, scope)
    ▼
当前后端持久化

Agent tool_call: recall_memory
    │ keyword
    ▼
MemoryTools
    ▼
MemoryService.recallMemory(keyword)
    ▼
LongTermMemoryStore.recallByKeyword(keyword)
    ▼
只检索 ARCHIVAL，返回完整匹配条目
```

### 3.6 SQLite 设计

Entity 与 Repository 放在 `oryxos-storage`，`SqliteMemoryStore` 放在 `oryxos-memory`。Entity 中的 scope 可用字符串保存，避免 storage 反向依赖 memory。

新增迁移脚本：

```text
oryxos-storage/src/main/resources/db/migration/sqlite/V4__create_memory_entries.sql
```

```sql
CREATE TABLE IF NOT EXISTS memory_entries (
    id          INTEGER      PRIMARY KEY AUTOINCREMENT,
    scope       VARCHAR(16)  NOT NULL
                CHECK (scope IN ('CORE', 'ARCHIVAL')),
    content     TEXT         NOT NULL
                CHECK (length(trim(content)) > 0),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_memory_entries_scope_id
    ON memory_entries(scope, id);
```

设计约束：

- 一条记忆一行，多行文本整体存入 `content`。
- 表只追加，不需要 `updated_at`。
- 使用 `id` 判断写入先后，不依赖可能相同的 `created_at`。
- 不增加 `session_id`，会话历史仍归 SessionManager。
- 建表走 Flyway 迁移，禁止依赖 `hibernate.ddl-auto=update`。

加载查询：

```sql
SELECT *
FROM memory_entries
WHERE scope = 'CORE'
ORDER BY id ASC;
```

```sql
SELECT *
FROM memory_entries
WHERE scope = 'ARCHIVAL'
ORDER BY id DESC
LIMIT :archivalLimit;
```

归档查询可在返回前恢复为写入正序。CORE 查询永远不能有 LIMIT。

初版召回使用普通子串语义：

```sql
SELECT *
FROM memory_entries
WHERE scope = 'ARCHIVAL'
  AND instr(lower(content), lower(:keyword)) > 0
ORDER BY id DESC;
```

`instr()` 避免把 `%`、`_` 当作 LIKE 通配符，也不引入正则或分词。

### 3.7 Markdown 设计

默认路径为 `.oryxos/memory/MEMORY.md`，保留两个固定分区：

```markdown
# Memory

## 核心记忆

## 归档记忆
```

实现要求：

1. 首次写入时自动创建父目录和文件。
2. 每次 `load()` 重新读取文件，不保留文件内容快照。
3. CORE 全量返回，不能按行数、字符数或条数裁剪。
4. ARCHIVAL 只返回最新 N 条，旧内容仍保留在文件中并可召回。
5. 检索只解析 ARCHIVAL 分区，不能先搜整个文件再事后过滤。
6. 一条多行记忆必须作为整体追加、截断和召回。
7. 文件损坏或不可读时抛出后端异常，不能返回空记忆。

> 已确认：Markdown 条目格式为 `### yyyy-MM-dd HH:mm:ss\n<content>\n`，条目之间无额外空行。`load()` / `recallByKeyword()` 返回的内容去掉日期 header，仅保留原始 content，保证多行记忆作为整体返回。

### 3.8 Mem0 设计

已确定的约束：

- 只接自托管 Mem0，地址和凭证通过环境变量或本地配置注入。
- `append` 时必须把 Agent 选择的 scope 写入 Mem0 metadata。
- 每次加载都调用远端，不在本地保存记忆快照。
- CORE 加载必须完整处理分页，不能只取第一页。
- 加载和召回都必须带分区约束，最终返回结果不得包含非目标分区。
- Mem0 故障必须显式暴露，不能返回空记忆。
- 真实 Mem0 验收必须覆盖重启后读取和 ARCHIVAL-only recall。

> 已确认：Mem0 初版采用 `POST /v1/memories/search/` 进行关键词召回，请求体带 `query`、`user_id` 和 `filters.scope=ARCHIVAL`；远端返回候选列表后本地不再二次过滤（模拟语义搜索场景下由 Mem0 完成包含匹配）。加载时使用 `GET /v1/memories/?user_id=...&metadata__scope=CORE|ARCHIVAL&limit=N` 拉取对应分区并本地按 limit 截断 ARCHIVAL。实际自托管 Mem0 的 API 路径和 metadata filter 语法以部署版本为准，当前契约测试使用本地 HttpServer 模拟上述约定。

### 3.9 配置设计

```yaml
oryxos:
  memory:
    backend: markdown
    archival-limit: 200
    markdown:
      path: .oryxos/memory/MEMORY.md
```

| 配置键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `oryxos.memory.backend` | string | `markdown` | 可选 `markdown` / `sqlite` / `mem0` |
| `oryxos.memory.archival-limit` | int | `200` | ARCHIVAL 加载窗口，单位是完整记忆条数 |
| `oryxos.memory.markdown.path` | string | `.oryxos/memory/MEMORY.md` | Markdown 存储路径 |
| `oryxos.memory.mem0.base-url` | string | `${MEM0_BASE_URL}` | 自托管 Mem0 地址，建议环境变量注入 |
| `oryxos.memory.mem0.api-key` | string | `${MEM0_API_KEY}` | Mem0 凭证，必须环境变量注入，不落明文 |

使用条件装配保证同一时间只有一个 `LongTermMemoryStore` Bean。切换后端只做选择，不做数据迁移。

### 3.10 异常处理

| 场景 | 处理策略 |
|---|---|
| content 空白 | 参数失败，不写入 |
| scope 缺失或非法 | 参数失败，不使用默认值 |
| 后端没有记忆 | 返回空内容或空列表 |
| recall 无匹配 | 返回空列表，由 Tool 转成明确提示 |
| 文件不可读 / SQLite 不可用 / Mem0 请求失败 | 抛出 `MemoryStoreException` 或既有等价后端异常 |
| 写入成功后的下一次 load | 必须立即可见，不允许缓存延迟 |

---

## 第四段：验收 harness：把验收标准变成可执行的测试

### 4.1 测试分层

| 测试层 | 测试类 | 环境 | 执行方式 |
|---|---|---|---|
| 抽象契约 | `LongTermMemoryStoreContractTest` | 定义全部后端不可破坏行为 | 不单独执行 |
| Markdown 契约 | `MarkdownMemoryStoreContractTest` | `@TempDir` 真实文件 | 默认执行 |
| SQLite 契约 | `SqliteMemoryStoreContractTest` | 独立 SQLite 测试库与真实迁移 | 默认执行 |
| Mem0 契约 | `Mem0MemoryStoreContractTest` | 可控 Fake Mem0 HTTP 服务 | 默认执行 |
| 上层门面 | `MemoryServiceTest` | Mock 后端与会话管理 | 默认执行 |
| Agent 工具 | `MemoryToolsTest` | Mock MemoryService | 默认执行 |
| Prompt 集成 | `PromptBuilderMemoryIntegrationTest` | 真实 PromptBuilder | 默认执行 |
| 配置装配 | `MemoryBackendSelectionTest` | `ApplicationContextRunner` | 默认执行 |
| 真实 Mem0 | `Mem0MemoryStoreIntegrationTest` | 自托管 Mem0 | `@Tag("integration")`，CI 跳过 |
| 真实模型 | Memory E2E 场景 | 真实 Provider + ReAct + 当前后端 | 人工触发，blocking |

### 4.2 三档共同契约测试

以下用例必须在三个实现上全部执行，禁止子类删除或放宽断言。

| 用例名 | 输入 / 操作 | 期望输出 | 验证点 |
|---|---|---|---|
| `load_afterExternalAppendReturnsLatestState` | Store A load；Store B 向同一后端 append；Store A 再 load | 第二次立即可见新记忆 | 禁止缓存，不能只测同一对象 |
| `load_coreIsNeverTruncated` | CORE 超过 N 条，包含超长、多行内容 | 所有 CORE 完整返回 | CORE 永不截断 |
| `load_archivalReturnsLatestNOnly` | 归档上限 N，写入 N+X 条 | 只返回最新 N 条 | 截断只在 ARCHIVAL |
| `load_truncationDoesNotDeleteArchivalEntries` | 旧归档不在加载窗口后，按旧内容检索 | 仍能召回旧归档 | 截断不物理删除 |
| `append_preservesAgentSelectedScope` | 分别写入 CORE 和 ARCHIVAL | 分区与显式 scope 一致 | 后端不改分区 |
| `recall_searchesArchivalOnly` | CORE 与 ARCHIVAL 含相同关键词、不同内容 | 只返回 ARCHIVAL | CORE 禁止检索 |
| `recall_returnsCompleteMultilineEntry` | 多行记忆第二行命中关键词 | 返回完整多行记忆 | 记忆条目不可拆分 |
| `recall_treatsKeywordAsLiteralText` | 分别查询 `%`、`_`、`.*`、`[a-z]` | 仅按普通文本包含匹配 | 无 LIKE/正则/分词语义 |
| `recall_withoutMatchReturnsEmpty` | 查询不存在的关键词 | 返回空结果 | 不伪造、不返回 CORE |

### 4.3 MemoryService 与 MemoryTools

| 测试类 | 用例名 | 期望输出 | 验证点 |
|---|---|---|---|
| `MemoryServiceTest` | `buildPromptContext_includesCoreAndSessionHistory` | 包含完整 CORE 和当前会话历史 | 上层组装正确 |
| `MemoryServiceTest` | `buildPromptContext_excludesArchivalMemory` | ARCHIVAL 不自动进入 Prompt | 归档按需召回 |
| `MemoryServiceTest` | `buildPromptContext_reloadsMemoryEveryTime` | 连续构建重新加载，新内容立即可见 | 门面也不缓存 |
| `MemoryServiceTest` | `saveMemory_delegatesScopeUnchanged` | scope 原样传给后端 | 门面不猜分区 |
| `MemoryServiceTest` | `recallMemory_delegatesKeyword` | 关键词原样传递 | 不增加复杂处理 |
| `MemoryToolsTest` | `saveMemory_requiresContent` | 空白内容返回参数失败 | 禁止空记忆 |
| `MemoryToolsTest` | `saveMemory_requiresExplicitScope` | 缺 scope 返回参数失败 | 禁止默认 ARCHIVAL |
| `MemoryToolsTest` | `saveMemory_acceptsCore` | CORE 原样保存 | CORE 工具路径 |
| `MemoryToolsTest` | `saveMemory_acceptsArchival` | ARCHIVAL 原样保存 | ARCHIVAL 工具路径 |
| `MemoryToolsTest` | `recallMemory_returnsMatchingEntries` | 返回完整匹配结果 | Agent 可召回归档 |
| `MemoryToolsTest` | `recallMemory_withoutMatchReturnsEmptyMessage` | 返回明确无结果提示 | 不报错、不伪造 |

### 4.4 Prompt 与后端装配

| 测试类 | 用例名 | 期望输出 | 验证点 |
|---|---|---|---|
| `PromptBuilderMemoryIntegrationTest` | `memoryContext_isInjectedOnce` | CORE 只出现一次 | 防止重复注入 |
| `PromptBuilderMemoryIntegrationTest` | `conversationHistory_isNotDuplicated` | 历史只出现一次 | 唯一拼接入口 |
| `PromptBuilderMemoryIntegrationTest` | `emptyMemory_keepsPromptValid` | 无记忆时 Prompt 仍完整 | 空记忆不破坏 ReAct |
| `MemoryBackendSelectionTest` | `backend_defaultsToMarkdown` | 默认 Markdown | 零依赖默认档 |
| `MemoryBackendSelectionTest` | `backend_sqliteSelectsSqliteStore` | 选择 SQLite | 配置切换 |
| `MemoryBackendSelectionTest` | `backend_mem0SelectsMem0Store` | 选择 Mem0 | 配置切换 |
| `MemoryBackendSelectionTest` | `context_containsOnlyOneLongTermMemoryStore` | 只有一个后端 Bean | 防止注入歧义 |

### 4.5 后端专项测试

| 后端 | 用例名 | 验证点 |
|---|---|---|
| Markdown | `append_createsMemoryFileAndParentDirectories` | 首次写入自动建目录和文件 |
| Markdown | `load_preservesMultilineEntryBoundaries` | 多行记忆不被拆散 |
| Markdown | `recall_doesNotReadCoreSection` | 文件解析层排除 CORE |
| SQLite | `migration_createsMemoryEntriesSchema` | 表、CHECK、索引由迁移创建 |
| SQLite | `database_rejectsInvalidScopeAndBlankContent` | DB 约束与接口一致 |
| SQLite | `load_usesStableIdOrdering` | 同时间戳仍有稳定新旧顺序 |
| Mem0 | `append_writesScopeMetadata` | 写入携带显式 scope |
| Mem0 | `load_coreRetrievesAllPages` | CORE 分页也完整加载 |
| Mem0 | `recall_appliesArchivalFilterBeforeReturning` | 返回只含 ARCHIVAL |
| Mem0 | `remoteFailure_doesNotReturnEmptyMemory` | 故障不伪装成空记忆 |

### 4.6 测试数据准备

| 数据 | 准备方式 |
|---|---|
| 唯一关键词 | 每个用例使用独立 UUID，避免历史数据干扰 |
| CORE 超长数据 | 构造超过归档上限的条目数，并包含超长、多行文本 |
| ARCHIVAL 窗口数据 | 按可识别顺序写入 N+X 条，断言最新 N 条 |
| 多行条目 | 至少三行，关键词放第二行，验证完整返回 |
| 特殊关键词 | `%`、`_`、`.*`、`[a-z]` 分别保存和查询 |
| Markdown 环境 | 每个测试独立 `@TempDir` |
| SQLite 环境 | 每个测试使用独立测试库并执行真实迁移 |
| Mem0 环境 | Fake 服务记录请求并可控返回分页、分区、故障 |
| 真实系统数据 | 使用 `ORYXOS-E2E-<timestamp>` 等唯一标识，验收后清理 |

### 4.7 Harness 防放水验证

| 故意制造的实现错误 | 必须变红的测试 |
|---|---|
| 增加本地缓存 | `load_afterExternalAppendReturnsLatestState` |
| CORE 查询增加 LIMIT | `load_coreIsNeverTruncated` |
| recall 去掉 ARCHIVAL 过滤 | `recall_searchesArchivalOnly` |
| save_memory 恢复默认 ARCHIVAL | `saveMemory_requiresExplicitScope` |
| SQLite 改成未转义 LIKE | `recall_treatsKeywordAsLiteralText` |
| Mem0 只取第一页 | `load_coreRetrievesAllPages` |
| 后端异常返回空 | `remoteFailure_doesNotReturnEmptyMemory` |

首版约 37 个用例定义；契约在三档展开后约 55 次测试执行。

### 4.8 运行命令

```bash
# 安装依赖模块，保证多模块测试使用当前源码
mvn install -DskipTests

# Memory 单元与契约测试
mvn test -pl oryxos-memory

# 全量自动化验收
mvn clean verify

# 真实 Mem0 集成测试：仅在环境变量和自托管服务就绪后执行
mvn test -pl oryxos-memory -Dtest=Mem0MemoryStoreIntegrationTest -Dgroups=integration
```

期望：默认构建无外部服务依赖，全部测试 0 failures、0 errors。

---

## 第五段：做完怎样验

### 5.1 真实系统验收一：三档切换

| # | 操作 | 预期结果 | 类型 |
|---|---|---|---|
| 1 | `backend=markdown` 启动，写入 CORE 和 ARCHIVAL | 使用 Markdown，MEMORY.md 两个分区出现对应内容 | blocking |
| 2 | 重启切换 `sqlite`，写入不同记忆 | 只写 `memory_entries`，不写 MEMORY.md | blocking |
| 3 | 重启切换 `mem0`，写入不同记忆 | Mem0 服务端记录带正确 scope metadata | blocking |
| 4 | 三档分别 load / recall | CORE 完整，ARCHIVAL 可召回，只读当前后端 | blocking |
| 5 | 再切回 Markdown | 原 Markdown 数据仍在，不出现其他后端数据 | blocking |
| 6 | 检查每次启动的 Bean | 任意时刻只有一个 LongTermMemoryStore 生效 | blocking |

该场景同时验证：配置切换有效、数据不串、上层不变、不做隐式迁移。

### 5.2 真实系统验收二：Mem0 真实连接

| # | 操作 | 预期结果 | 类型 |
|---|---|---|---|
| 1 | 用环境变量配置自托管 Mem0 并启动 | 连接成功，日志不含明文凭证 | blocking |
| 2 | 写入唯一 CORE 标识 | 服务端记录存在，metadata 为 CORE | blocking |
| 3 | 写入唯一 ARCHIVAL 标识 | 服务端记录存在，metadata 为 ARCHIVAL | blocking |
| 4 | 重启应用后重新加载 | 仍能从远端读取，证明不是本地缓存 | blocking |
| 5 | 用 ARCHIVAL 关键词 recall | 返回完整归档记忆 | blocking |
| 6 | 用只存在于 CORE 的关键词 recall | 不返回 CORE | blocking |
| 7 | 停止 Mem0 后加载 | 明确报告不可用，不返回空记忆 | blocking |
| 8 | 恢复 Mem0 | 无需改数据即可恢复加载与召回 | blocking |

### 5.3 真实系统验收三：真实模型完整 ReAct

| 轮次 | 用户输入 | 预期行为 | 类型 |
|---|---|---|---|
| 1 | 要求把唯一项目代号作为长期重要信息保存到 CORE | 模型调用 `save_memory`，显式 `scope=CORE` | blocking |
| 2 | 要求把一条验收备注保存到 ARCHIVAL | 模型调用 `save_memory`，显式 `scope=ARCHIVAL` | blocking |
| 3 | 新会话或重启后询问项目代号 | CORE 重新加载并注入，模型回答正确 | blocking |
| 4 | 要求用 `recall_memory` 查询验收备注 | 模型调用工具，取得 ARCHIVAL 后回答 | blocking |
| 5 | 要求 recall 只存在于 CORE 的项目代号 | 工具不返回 CORE，模型不伪造工具结果 | blocking |

必须观察到完整链路：

```text
真实用户输入
→ ReActLoop
→ 真实 LLM 返回 tool_call
→ ToolExecutor
→ save_memory / recall_memory
→ 当前 LongTermMemoryStore
→ ToolResult 回填
→ 真实 LLM 生成最终回答
```

### 5.4 人工确认清单

| # | 检查项 | 通过标准 | 类型 |
|---|---|---|---|
| 1 | 上层接口 | MemoryService 只有 Prompt 上下文、保存、召回三类能力 | blocking |
| 2 | 下层接口 | LongTermMemoryStore 只保留三个契约方法 | blocking |
| 3 | Scope 必填 | save_memory schema 和实现均不要求默认 scope，缺失即失败 | blocking |
| 4 | CORE 加载 | 三档均无 CORE 截断逻辑 | blocking |
| 5 | ARCHIVAL 检索 | 三档 recall 均只查 ARCHIVAL | blocking |
| 6 | 无缓存 | 每次 load 都真实访问后端 | blocking |
| 7 | Prompt 唯一拼接 | CORE 和会话历史均只出现一次 | blocking |
| 8 | 审计 | 真实工具调用写 `tool_invocations`，模型调用写 `llm_calls` | blocking |
| 9 | 凭证 | 配置、日志、截图均无明文 API Key / Mem0 凭证 | blocking |
| 10 | 前序回归 | Provider、ReAct、Tool 等既有测试不受影响 | blocking |

### 5.5 自动化通过标准

```bash
mvn clean verify
```

通过标准：

- 15 个 Maven 模块 BUILD SUCCESS。
- 默认测试 0 failures、0 errors。
- 三档共同契约测试全部执行，不能只跑 Markdown。
- 静态检查、格式检查通过。
- 真实 Mem0 和真实模型验收不能用“未配置外部服务”伪装成通过；未执行时只能标记为待验收。

### 5.6 与 AGENTS.md 原则对齐

| 原则 | 对齐方式 | 类型 |
|---|---|---|
| 自实现 ReAct Loop | Memory 只提供上下文和工具，不接管循环 | blocking |
| Spring AI 边界 | 不引入 ChatClient 自动 Tool 执行或 Advisor | blocking |
| 同步执行 | 文件、JPA、Mem0 REST 初版均同步调用，不用 Reactor/WebFlux | blocking |
| OryxTool 抽象 | save_memory / recall_memory 仍经统一工具体系执行 | blocking |
| 单二进制部署 | Markdown / SQLite 无新增外部进程；Mem0 为可选自托管档 | blocking |
| 审计 Day One | 真实工具与模型调用继续落审计表 | blocking |
| 配置与凭证 | 敏感配置走环境变量占位，不明文硬编码 | blocking |
| 代码规范 | 代码全英文，主要代码块使用详细中文注释 | blocking |

### 5.7 性能、安全、可观测性

| 维度 | 验收点 | 通过标准 | 类型 |
|---|---|---|---|
| 性能 | ARCHIVAL 加载窗口 | 只加载最新 N 条，不全量注入 Prompt | blocking |
| 性能 | CORE 完整性优先 | 不为降低 Token 而截断 CORE；CORE 应由 Agent 保持精简 | blocking |
| 安全 | CORE 检索隔离 | recall 不能通过任何后端返回 CORE | blocking |
| 安全 | Mem0 数据边界 | 只接自托管服务，凭证不明文落盘 | blocking |
| 安全 | SQL 与模式注入 | 关键词按普通文本处理，不拼接可执行模式 | blocking |
| 可观测性 | 后端选择日志 | 启动日志能看到当前 backend，不打印凭证 | blocking |
| 可观测性 | 写入与召回日志 | 记录 scope、结果数量、耗时，不记录敏感全文 | non-blocking |
| 可观测性 | 后端故障 | 错误日志能区分文件、SQLite、Mem0 故障 | blocking |

### 5.8 实施前仍需确认的事项

| 事项 | 状态 | 说明 |
|---|---|---|
| Markdown 多行记忆的日期 header 与条边界格式 | 已确认 | `### yyyy-MM-dd HH:mm:ss\n<content>\n`，返回时去掉日期 header |
| Mem0 初版普通关键词匹配的远端 / 本地实现路径 | 已确认 | 由 Mem0 `/v1/memories/search/` 完成召回，带 ARCHIVAL filter |
| Mem0 实际版本的 API、分页与 metadata filter 语法 | 已确认（初版） | `GET /v1/memories/` 与 `POST /v1/memories/search/`，metadata__scope 过滤，以实际自托管版本为准 |
| MemoryTools 最终采用内部类还是 `@Tool` 注解 | 已确认 | 当前使用内部类 `@Component` Bean，后续可迁移至 `@Tool` 注解 |

以上事项已在实现中固定，不影响四条后端契约。

---

## 变更记录

| 日期 | 变更内容 | 变更人 |
|---|---|---|
| 2026-10-27 | 初始版本：Memory 统一门面、三档后端契约、SQLite 设计、验收 Harness 与真实系统验收方案 | AI |
