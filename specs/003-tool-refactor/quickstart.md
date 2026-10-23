# Quickstart Validation: Tool 模块重构

**Date**: 2026-10-23

## 前置条件

- JDK 21 + Maven 3.9+
- 前序天（Day 1-4）代码已提交
- 分支 `005-day5-tool-refactor`

## 验证场景

### 场景 1：Sandbox 下沉验证

```bash
# 运行 Sandbox 相关测试
mvn test -pl oryxos-tool "-Dtest=FileToolsSandboxTest,ShellToolsSandboxTest,HttpToolsSandboxTest"

# 期望：12 个测试全绿
# 验证点：Sandbox 在 IO 之前执行；Sandbox 拒绝时 IO 不执行；Sandbox 为 null 时工具正常工作
```

### 场景 2：条件注册验证

```bash
# 运行条件注册测试
mvn test -pl oryxos-core "-Dtest=ConditionalToolTest,ToolAutoRegistrarTest"

# 期望：5 个测试全绿
# 验证点：available 工具注册；unavailable 工具不注册；非 ConditionalTool 正常注册
```

### 场景 3：ToolExecutor 重构验证

```bash
# 运行 ToolExecutor 测试
mvn test -pl oryxos-core "-Dtest=ToolExecutorTest"

# 期望：10 个测试全绿
# 验证点：查找/执行/兜底/审计/retryable 信号/失败隔离
```

### 场景 4：NotifyTool 独立验证

```bash
# 运行 NotifyTool 测试
mvn test -pl oryxos-tool "-Dtest=NotifyToolTest"

# 期望：6 个测试全绿
# 验证点：渠道查找/不存在/默认渠道/条件注册/参数校验
```

### 场景 5：@Tool 注解验证

```bash
# 运行 @Tool 注解测试
mvn test -pl oryxos-tool "-Dtest=ToolMethodScannerTest,AnnotatedToolAdapterTest"

# 期望：8 个测试全绿
# 验证点：扫描/注册/不误注册/名称/描述/Schema/执行/异常
```

### 场景 6：ReActLoop 联动验证

```bash
# 运行 ReActLoop 测试
mvn test -pl oryxos-core "-Dtest=ReActLoopRetryableTest,ReActLoopIsolationTest"

# 期望：7 个测试全绿
# 验证点：retryable 信号传递/循环不断/同批次隔离/跨轮次隔离/历史完整/优雅终止
```

### 场景 7：InOrder 顺序验证

```bash
# 运行 InOrder 测试
mvn test -pl oryxos-tool "-Dtest=BuiltInToolsSandboxOrderTest"

# 期望：3 个测试全绿
# 验证点：Sandbox 校验在 IO 之前执行（钉死顺序）
```

### 场景 8：全量回归

```bash
# 全量验证
mvn clean verify

# 期望：BUILD SUCCESS，0 failures，0 errors
# 验证点：所有 51 个新测试 + 前序天所有测试回归通过
```
