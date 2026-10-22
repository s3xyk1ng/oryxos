# Quickstart: Notify 通知推送模块验证指南

## 前置条件

- JDK 21 已安装
- Maven 已安装
- SQLite 数据库已初始化（V2 迁移脚本已执行）
- notify_channels 表至少有一条记录

## 验证 1：单元测试全绿

```bash
# Notify 适配器测试
mvn test -pl oryxos-tool -Dtest="WebhookNotifyAdapterTest"

# NotifyTools 编排测试
mvn test -pl oryxos-tool -Dtest="NotifyToolsTest"

# AgentLoader frontmatter 解析测试
mvn test -pl oryxos-core -Dtest="AgentLoaderNotifyTest"

# 全量回归
mvn clean verify
```

**期望**：BUILD SUCCESS，0 failures, 0 errors

## 验证 2：Mock Webhook 端到端

启动本地 webhook 接收端（Python 一行命令）：

```bash
python -c "from http.server import HTTPServer, BaseHTTPRequestHandler; exec('''
class H(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(length).decode()
        print(f'\\n=== WEBHOOK RECEIVED ===\\n{body}\\n========================\\n')
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'{\"ok\":true}')
    def log_message(self, format, *args): pass
HTTPServer(('', 9999), H).serve_forever()
''')"
```

在 SQLite 中注册渠道：

```sql
INSERT INTO notify_channels (name, type, url, config_json, description, created_at)
VALUES ('local-test', 'webhook', 'http://localhost:9999/webhook', NULL, 'Local test webhook', datetime('now'));
```

启动 OryxOS CLI，输入"把测试消息推送一下"，观察 Python 控制台是否收到 POST 请求。

## 验证 3：错误场景

| 场景 | 操作 | 期望结果 |
|------|------|---------|
| 渠道不存在 | notify(channel="nonexistent", content="test") | ToolResult.fail("Channel not found: nonexistent") |
| content 为空 | notify(channel="local-test", content="") | ToolResult.fail("content parameter is required") |
| 无默认渠道 | notify(content="test")，Profile 无 defaultNotifyChannel | ToolResult.fail("No notification channel specified") |
| 渠道表为空 | 清空 notify_channels 表，重启 | notify 工具不注册，LLM 看不到 |

## 验证 4：接口中立性

新增一个 mock 适配器（测试代码中），验证：
- 只实现 `NotifyChannelAdapter` 接口
- NotifyTools 不修改
- 新适配器能正确处理匹配的 type，忽略不匹配的 type
