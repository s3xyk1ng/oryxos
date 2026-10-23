package com.oryxos.tool.builtin;

import com.oryxos.core.model.OryxTool;
import com.oryxos.core.model.Sandbox;
import com.oryxos.core.model.SandboxAction;
import com.oryxos.core.model.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 内置 Shell 工具：执行系统命令（不经 Shell 解释器，带超时控制）。
 */
@Component
public class ShellTools {

    private static final Logger log = LoggerFactory.getLogger(ShellTools.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    @Component("shellTool")
    public static class ShellTool implements OryxTool {

        private final Sandbox sandbox;

        public ShellTool(@Autowired(required = false) Sandbox sandbox) {
            this.sandbox = sandbox;
        }

        @Override
        public String getName() { return "shell"; }

        @Override
        public String getDescription() {
            return "Execute a shell command. The command is executed directly without shell interpretation. "
                    + "Returns stdout and stderr.";
        }

        @Override
        public Map<String, Object> getInputSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("command", Map.of("type", "string",
                    "description", "The executable command to run (e.g., 'ls', 'git', 'python')"));
            props.put("args", Map.of("type", "array",
                    "description", "Command arguments as an array of strings",
                    "items", Map.of("type", "string")));
            props.put("timeout", Map.of("type", "integer",
                    "description", "Timeout in seconds (default: 30)"));
            schema.put("properties", props);
            schema.put("required", List.of("command"));
            return schema;
        }

        @Override
        @SuppressWarnings("unchecked")
        public ToolResult execute(Map<String, Object> input) {
            String command = (String) input.get("command");
            if (command == null || command.isBlank()) {
                return ToolResult.fail("command parameter is required", false);
            }

            if (sandbox != null) {
                try {
                    sandbox.enforce(new SandboxAction(SandboxAction.ActionType.SHELL_COMMAND, command));
                } catch (SecurityException e) {
                    return ToolResult.fail("Sandbox rejected: " + e.getMessage(), false);
                }
            }

            List<String> argList = (List<String>) input.getOrDefault("args", List.of());
            int timeout = input.containsKey("timeout")
                    ? ((Number) input.get("timeout")).intValue()
                    : DEFAULT_TIMEOUT_SECONDS;

            // Build command list (no shell interpreter)
            List<String> cmdList = new java.util.ArrayList<>();
            cmdList.add(command);
            cmdList.addAll(argList);

            try {
                ProcessBuilder pb = new ProcessBuilder(cmdList);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                String output;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    output = reader.lines().collect(Collectors.joining("\n"));
                }

                boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return ToolResult.fail("Command timed out after " + timeout + " seconds", true);
                }

                int exitCode = process.exitValue();
                String result = "Exit code: " + exitCode + "\n" + output;
                if (exitCode == 0) {
                    return ToolResult.ok(result);
                } else {
                    return ToolResult.fail(result, false);
                }
            } catch (Exception e) {
                return ToolResult.fail("Shell execution failed: " + e.getMessage(), true);
            }
        }
    }
}
