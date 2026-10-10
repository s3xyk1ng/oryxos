package com.oryxos.tool.sandbox;

import com.oryxos.core.model.Sandbox;
import com.oryxos.core.model.SandboxAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * 白名单 Sandbox 实现：应用层校验（"劝阻级"防线）。
 * - 文件路径：白名单 + ../ 穿越防护
 * - Shell 命令：精确比对可执行文件白名单
 * - HTTP 域名：host 解析 + 通配符匹配
 */
@Component
public class WhitelistSandbox implements Sandbox {

    private static final Logger log = LoggerFactory.getLogger(WhitelistSandbox.class);

    @Value("${oryxos.sandbox.file.allowed-paths:}")
    private List<String> allowedPaths;

    @Value("${oryxos.sandbox.shell.allowed-commands:}")
    private List<String> allowedCommands;

    @Value("${oryxos.sandbox.http.allowed-domains:}")
    private List<String> allowedDomains;

    @Override
    public void enforce(SandboxAction action) {
        switch (action.getType()) {
            case FILE_READ, FILE_WRITE -> checkFilePath(action.getTarget());
            case SHELL_COMMAND -> checkShellCommand(action.getTarget());
            case HTTP_REQUEST -> checkHttpUrl(action.getTarget());
        }
    }

    private void checkFilePath(String path) {
        if (allowedPaths == null || allowedPaths.isEmpty()) {
            throw new SecurityException("File access denied: no paths configured in whitelist");
        }
        // Normalize path to prevent traversal
        String normalized = Path.of(path).normalize().toString();
        for (String allowed : allowedPaths) {
            if (normalized.startsWith(Path.of(allowed).normalize().toString())) {
                return;
            }
        }
        throw new SecurityException("File access denied: " + path + " not in whitelist");
    }

    private void checkShellCommand(String command) {
        if (allowedCommands == null || allowedCommands.isEmpty()) {
            throw new SecurityException("Shell access denied: no commands configured in whitelist");
        }
        // Extract the executable (first word)
        String executable = command.split("\\s+")[0];
        for (String allowed : allowedCommands) {
            if (executable.equals(allowed) || executable.endsWith(File.separator + allowed)) {
                return;
            }
        }
        throw new SecurityException("Shell command denied: " + command + " not in whitelist");
    }

    private void checkHttpUrl(String url) {
        if (allowedDomains == null || allowedDomains.isEmpty()) {
            throw new SecurityException("HTTP access denied: no domains configured in whitelist");
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) {
                throw new SecurityException("HTTP access denied: invalid URL " + url);
            }
            for (String domain : allowedDomains) {
                if (domain.startsWith("*.")) {
                    // Wildcard match: *.example.com matches sub.example.com
                    String suffix = domain.substring(2);
                    if (host.equals(suffix) || host.endsWith("." + suffix)) {
                        return;
                    }
                } else if (host.equals(domain)) {
                    return;
                }
            }
            throw new SecurityException("HTTP access denied: " + host + " not in whitelist");
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("HTTP access denied: invalid URL " + url);
        }
    }
}
