# Quickstart Validation: Provider 模块

**Feature**: 002-provider-module | **Date**: 2026-10-13

## 前置条件

- JDK 21+
- Maven 3.9+
- 网络可达（下载依赖）

## 验证 1: 单元测试全绿

```bash
mvn -pl oryxos-core,oryxos-provider,oryxos-storage test
```

**期望**：BUILD SUCCESS，所有测试通过（含 SpringAiProviderServiceImplTest / ToolSchemaAdapterTest / ProviderChatModelFactoryTest / InMemoryProviderRegistryTest / ProviderBootstrapTest）

## 验证 2: 集成冒烟测试

```bash
mvn -pl oryxos-provider test -Dtest=ProviderSmokeTest
```

**期望**：ProviderSmokeTest 通过 — YAML 配置 → Registry → ChatModel 构建 → 审计记录写入

## 验证 3: 构建门禁全绿

```bash
mvn clean verify
```

**期望**：Spotless/Checkstyle/SpotBugs/FindSecBugs/PMD 全部通过

## 验证 4: 禁用项检查

```bash
# 确认无 ChatClient 使用
grep -r "ChatClient" oryxos-provider/src/
# 确认无 Advisor 使用
grep -r "Advisor" oryxos-provider/src/
```

**期望**：两个 grep 均无结果

## 验证 5: 凭证安全

```bash
# 确认无硬编码 API key
grep -rn "sk-\|api-key.*=.*[a-zA-Z0-9]" oryxos-provider/src/ --include="*.java"
```

**期望**：无匹配结果（所有 apiKey 来自环境变量）

## 参考

- 数据模型详情：[data-model.md](data-model.md)
- 接口契约：[contracts/provider-api.md](contracts/provider-api.md)
- 研究决策：[research.md](research.md)
