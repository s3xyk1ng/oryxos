# Research: Notify 通知推送模块

## 技术决策记录

### Decision 1: NotifyTarget 使用 Java record

**Decision**: `public record NotifyTarget(String type, Map<String, String> config)`

**Rationale**: record 是不可变数据载体，天然线程安全，自动生成 equals/hashCode/toString，语法简洁。NotifyTarget 创建后不需要修改，符合 record 的使用场景。

**Alternatives considered**:
- 普通 class + final fields + getter → 代码量大，需手写 equals/hashCode
- `Map<String, Object>` 作为 config → 类型不安全，适配器需要强制转换

### Decision 2: 适配器自己判断 type 匹配 vs 接口加 getType()

**Decision**: 适配器在 send() 内部判断 `target.getType()` 是否匹配，不匹配返回 "unsupported type: xxx"。接口不加 getType() 方法。

**Rationale**: 保持接口中立性——接口不关心适配器"是什么类型"，只关心"能不能处理这个 target"。如果加了 getType()，接口就有了路由语义，违反了"接口中立"的设计原则。

**Alternatives considered**:
- 接口加 getType() + 路由器 → 违反接口中立性，增加约束
- NotifyTools 硬编码路由 → 每加一个适配器就要改 NotifyTools

### Decision 3: JSON 构建用 Jackson ObjectMapper

**Decision**: 使用 `com.fasterxml.jackson.databind.ObjectMapper` 构建 webhook payload JSON。

**Rationale**: Spring Boot 已包含 Jackson 依赖（已确认 2.18.1 在 dependency tree 中），自动处理字符串转义（手动 String.formatted() 容易遗漏反斜杠/引号转义），代码更健壮。

**Alternatives considered**:
- 手动拼接 JSON 字符串 → 转义不安全，维护成本高
- javax.json (Jakarta JSON Processing) → 额外依赖，Spring 生态不常用

### Decision 4: 测试用 JDK 内置 HttpServer

**Decision**: 使用 `com.sun.net.httpserver.HttpServer` 作为 mock webhook 服务端，随机端口。

**Rationale**: 零额外依赖（JDK 内置），可以验证真实的 HTTP 请求/响应链路（body 内容、URL 路径、状态码），比纯 mock HttpClient 更可靠。

**Alternatives considered**:
- WireMock → 功能更强但需额外 Maven 依赖
- Mock HttpClient → 无法验证真实 HTTP 协议行为

### Decision 5: 5xx 响应抛异常 vs 返回错误字符串

**Decision**: 非 2xx 响应抛 `RuntimeException`，由 NotifyTools 上层 catch 后包装为 `ToolResult.fail()`。

**Rationale**: 适配器层不应"消化"错误——如果返回错误字符串，调用方可能误判为成功（返回非 null 即成功）。抛异常确保错误一定被上层感知，符合"不能静默失败"原则。

**Alternatives considered**:
- 返回错误字符串 → 调用方可能忽略，静默失败
- 返回 Optional → 过度设计，错误信息丢失

### Decision 6: 工具注册控制方案

**Decision**: NotifyTools 内部判断 `channelRepository.count() == 0` 时，NotifyTool 的 `execute()` 仍可用但 ToolRegistry 层面不暴露给 LLM。具体实现：在 NotifyTools 初始化时检查渠道数量，若为 0 则不创建 NotifyTool Bean（或通过 @Conditional 控制）。

**Rationale**: 避免 LLM 看到工具 schema 后发起注定失败的调用。

**Alternatives considered**:
- 始终注册但 execute 时返回错误 → LLM 会反复尝试，浪费 token
- 使用 Spring @ConditionalOnProperty → 条件不是配置项，是数据库状态

**补充**: 实现上可以通过在 NotifyTools 中注入 Repository，在 @PostConstruct 中检查 count，若为 0 则从 ToolRegistry 中移除自身。或者更简洁地，让 NotifyTool 不直接是 @Component，而是由 NotifyTools 在确认有渠道时才创建并注册。具体实现方式在 tasks 阶段确定。
