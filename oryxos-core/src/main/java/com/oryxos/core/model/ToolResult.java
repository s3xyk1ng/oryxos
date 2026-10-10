package com.oryxos.core.model;

/**
 * 工具执行结果。
 */
public class ToolResult {

    private boolean success;
    private String content;
    private String error;
    private boolean retryable;

    private ToolResult() {}

    public static ToolResult ok(String content) {
        ToolResult r = new ToolResult();
        r.success = true;
        r.content = content;
        r.retryable = false;
        return r;
    }

    public static ToolResult fail(String error, boolean retryable) {
        ToolResult r = new ToolResult();
        r.success = false;
        r.error = error;
        r.retryable = retryable;
        return r;
    }

    public boolean isSuccess() { return success; }
    public String getContent() { return content; }
    public String getError() { return error; }
    public boolean isRetryable() { return retryable; }
}
