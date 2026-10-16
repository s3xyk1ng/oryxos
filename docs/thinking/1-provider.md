# Provider开发

## 1. 是什么？

1. 概念：用于Agent和底层LLM之间的一个前台，让Agent用户无感知调用底层LLM

2. 挑对模型、发起一次调用、把结果拿回来

## 2.  职责划分

1. 职责边界：
   
   1. 必须做的：
      
      1. 挑对哪家模型
      
      2. 组装并发起一次LLM调用
      
      3. 把工具翻译成各家能懂的格式
      
      4. 记住这次调用的用量/审计
   
   2. 绝对不做的：
      
      1. 循环怎么转
      
      2. 工具怎么执行
      
      3. 上下文怎么拼
      
      4. 记忆怎么存

2. 由于各家LLM供应商下一不同，项目中采用Spring AI Alibaba进行转换，使用时，封装一层自己的类 ProviderService

3. 可能会产生的坑
   
   1. 多个provider，如何区分？维护一张表：provider的名字 -> 对应的ChatModel
   
   2. Spring AI可能会执行工具。必须把Spring AI的自动执行关闭，只要他执行协议哦转换和schema生成
   
   3. 对于接入的LLM是否需要验证？必须验证，Spring AI生态里每家模型背后都是一个独立的starter依赖，这些依赖必须锁定在Spring AI BOM版本里，版本号也需要对应。在接入前必须确认依赖能否下载、能否解析，确认后必须跑一遍mvn dependency:tree进行确认

## 3. 代码结构

- 核心就一个ProviderService类，加2个配角：一个把工具转成Spring AI格式的适配器，一个负责写审计日志。对外只暴露一个方法：chat(sessionId, Profile, Prompt)

- 调用流程：
  
  1. Profile的解析加载：作为第一个消费Profile的模块，要从里面读取provider名称，model，温度。所以Profile从YAML变Java对象这件事就归这里负责，应该包含三样：
     
     1. Profile：承载全部字段的记录类，包含name 、 description 、 identity 、 provider(name/model/temperature)、 tools 、 skills 、 mcp_servers 、 channels 、notify_channels 、 schedules 、 bootstrap 、 settings
     
     2. ProfileLoader：启动时扫描.oryxos/profiles/下所有YAML，用SnakeYAML解析成Profile，逐个做合法校验（这里先校验provider名能在全局层找到这一条，后面再补充更多校验规则），坏的Profile做错误日志记录，不阻断启动
     
     3. ProfileRegistry：解析好的Profile放进内存索引（Map<String, Profile>），按name查找
  
  2. 两层配置
     
     1. 全局层（application.yaml）：声明这个实例上接了哪些provider、每家的凭证从哪个环境变量中读取
     
     2. Profile层（每个Agent自己的YAML）：声明这个Agent具体用哪个provider、哪个model、什么温度
     
     3. ```yaml
        # application.yaml —— 全局层：声明有哪些 provider、凭证从哪来
        oryxos:
         providers:
         - name: deepseek
         api-key: ${DEEPSEEK_API_KEY}
         - name: kimi
         api-key: ${KIMI_API_KEY}
        ```
        
        ```yaml
        # .oryxos/profiles/ops-agent.yaml —— Profile 层：这个 Agent 具体怎么⽤
        provider:
         name: deepseek # 必须能在全局层的 providers 列表⾥找到同名项
         model: deepseek-chat # ⽤哪个模型，Profile ⾃⼰定
         temperature: 0.7
        ```
  
  3. 建映射表：启动时，按application.yaml里providers列表逐条创建对应的ChatModel，把name和ChatModel存进一个Map<String, ChatModel>，显示建表，不能靠类型扫描
  
  4. 写chat方法，下面是代码骨架
     
     1. ```java
        public Response chat(String sessionId, Profile profile, Prompt prompt) {
         ChatModel model = providerMap.get(profile.getProvider()); // 按名字取模型
         if (model == null) {
             throw new ProviderNotFoundException(profile.getProvider());
         }
         var tools = adapter.toSpringAiTools(prompt.getAvailableTools()); // 只翻译，不执⾏
         long startedAt = System.currentTimeMillis();
         try {
             Response resp = model.call(request(prompt, tools, /*
        autoExecuteTools */ false)); // 关掉⾃动执⾏
             audit.record(sessionId, profile.getProvider(), profile.getModel(),resp.usage(),
                     true, null, System.currentTimeMillis() - startedAt);
             return resp;
         } catch (RuntimeException e) {
             audit.record(sessionId, profile.getProvider(), profile.getModel(),
        null,
             false, e.getMessage(), System.currentTimeMillis() -
        startedAt);
         throw e; // 调⽤失败也留痕，再把错误抛给上层处理
         }
        }
        ```
  
  5. 工具适配和审计：负责把我们自己的OryxTool的参数说明（getInputSchema()）转成Spring AI的工具描述，只翻译，不执行。调⽤成功，把 provider、model、token ⽤量写进 llm_calls ；调⽤失败（超时、限流、模型报错），也要写⼀条， success 记 false 、 error_message 记原因。所以llm_calls 表本身得有 success / error_message 这两列——不然模型调⽤失败时，这次事故在数据库⾥完全没有痕迹，跟⼯具那边的 tool_invocations 表⽐就不对称，"可审计"这个卖点就打了折扣。这张表的 JPA 实体、Repository 和建表脚本也归这节交付；注意 SQLite 的ALTERTABLE 很弱，建表⽤⼿⼯维护的脚本，别指望 hibernate.ddl-auto=update做迁移
  
  6. 有⼏样先别做。 fallback（⼀家挂了换另⼀家）、hedge racing（同时发⼏家抢最快的）、熔断，这些都放到扩展阶段；现在故障就直接把错误抛给上层。成本看板也放后⾯，眼下只落llm_calls 这张表，够审计⽤就够了。核⼼阶段的⽬标是"能稳定调通⼀次"，别⼀上来就求全

. 交付物：
  
  . 代码： Profile 、 ProfileLoader 、 ProfileRegistry 、 ProviderService （含chat(sessionId, Profile, Prompt) ）、⼯具格式适配器、 LlmCall 实体+LlmCallRepository
  
  . 测试： ProfileLoaderTest 、 ProviderServiceTest 、 ToolSchemaAdapterTest 、LlmCallRepositoryTest 、 ProviderSmokeIT （⻅第四部分的验收 harness）
  
  . 配置： application.yaml 的 oryxos.providers 全局层；Profile YAML 的 provider段表： llm_calls （含 success / error_message 列，⼿⼯建表脚本）

## 4. 验收harness：把验收标准变成可执行的测试

- 这节的验收就是一套测试，要求是产出代码全绿

- 先分层：
  
  - 单测：默认全跑，路由、校验、审计、翻译，全部把 ChatModel mock 掉，不花⼀分钱、不依赖⽹络，秒级跑完，这是 harness 的主体。
  
  - 集成冒烟：打 @Tag("integration") ，本地⼿动跑，真调⼀次模型，验证"key 对、依赖对、真的通"。CI ⾥默认跳过——不能让外部 API 的可⽤性变成⾃⼰流⽔线的可⽤性。

- 4个单测类，逐条对应验收标准

- | 测试类                     | 覆盖的验收点                                                           |
  | ----------------------- | ---------------------------------------------------------------- |
  | `ProfileLoaderTest`     | 合法 YAML 全字段解析；引用不存在的 provider 报错清晰；坏文件不阻断其余加载；`${ENV}` 占位从环境变量解析 |
  | `ProviderServiceTest`   | 双 provider 按名路由不串台；未知名抛异常；成功/失败都落审计；自动执行关闭                       |
  | `ToolSchemaAdapterTest` | `OryxTool` 的 schema 翻译成 Spring AI 格式后字段一一对齐；只翻译、产物里不含任何执行逻辑      |
  | `LlmCallRepositoryTest` | 手工建表脚本建出的 `llm_calls` 能存能读，`success` / `error_message` 两列真实存在    |

- `ProviderServiceTest` 里，mock 两个 `ChatModel` 就能测：

```java
@Test
void 按名路由_两个provider不串台() {
    var deepseek = mock(ChatModel.class);
    var kimi = mock(ChatModel.class);
    var service = new ProviderService(Map.of("deepseek", deepseek, "kimi", kimi), adapter, audit);

    service.chat("s-1", profileUsing("kimi"), prompt);

    verify(kimi, times(1)).call(any());       // 调的是 kimi
    verify(deepseek, never()).call(any());    // deepseek 一次都没被碰——"不串台"的直接证据
}

@Test
void 调用失败_审计必须留下success为false的记录() {
    when(chatModel.call(any())).thenThrow(new RuntimeException("connect timeout"));

    assertThrows(RuntimeException.class,
        () -> service.chat("s-1", profileUsing("deepseek"), prompt));   // 异常继续上抛

    verify(audit).record(eq("s-1"), eq("deepseek"), any(), isNull(),
        eq(false), contains("timeout"), anyLong());   // 但审计先落了：success=false + 原因
}

@Test
void 带工具schema调用_请求里关闭了自动执行() {
    service.chat("s-1", profileUsing("deepseek"), promptWithTools(httpGetTool));

    var captor = ArgumentCaptor.forClass(Request.class);
    verify(chatModel).call(captor.capture());

    assertFalse(captor.getValue().autoExecuteTools());   // 坑二的回归测试：一旦有人改回自动执行，这里立刻红
    assertNotNull(captor.getValue().tools());            // 翻译过的 schema 确实带上了
}
```

- 第一个测的是坑一（显式映射），第三个测的是坑二（关自动执行）——每个"想清楚"阶段点过名的坑，都应该有一个对应的回归测试，这样坑就被永久钉死了，后来的人改不回去。
- 第二个测的是最容易漏的失败审计路径：注意断言的不是"没抛异常"，而是"抛了异常并且审计先落了账"。

`LlmCallRepositoryTest` 的一个讲究：建表要走那份手工脚本（测试里执行 `schema.sql`），不要让 Hibernate 自动建——不然测试绿了、生产上跑真脚本时列名对不上，白测。

### 集成冒烟 ProviderSmokeIT

一个方法，读环境变量里的真 key、真调一次、断言拿到非空响应且 `llm_calls` 多了一条 `success=true`。跑法：

```bash
mvn test                                    # 日常：只跑单测，全绿才算实现完成
DEEPSEEK_API_KEY=xxx mvn test -Dgroups=integration   # 手动：冒烟验真连通
```
