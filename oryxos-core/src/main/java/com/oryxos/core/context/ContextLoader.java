package com.oryxos.core.context;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 上下文加载器：加载 Bootstrap 文件（AGENTS.md/SOUL.md/USER.md）、 AGENT.md 正文、Skill 元数据。每次现读不缓存。 */
@Component
public class ContextLoader {

    private static final Logger log = LoggerFactory.getLogger(ContextLoader.class);

    private final Path workspaceRoot;

    public ContextLoader() {
        this.workspaceRoot = Path.of(".");
    }

    public ContextLoader(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /** 加载 Bootstrap 文件内容（AGENTS.md + SOUL.md + USER.md），拼接为字符串。 */
    public String loadBootstrap() {
        StringBuilder sb = new StringBuilder();
        Path oryxosDir = workspaceRoot.resolve(".oryxos");

        for (String file : List.of("AGENTS.md", "SOUL.md", "USER.md")) {
            Path path = oryxosDir.resolve(file);
            if (Files.exists(path)) {
                try {
                    sb.append(Files.readString(path)).append("\n\n");
                } catch (IOException e) {
                    log.warn("Failed to read {}: {}", path, e.getMessage());
                }
            }
        }
        return sb.toString().trim();
    }

    /** 加载指定 Agent 的 AGENT.md 正文（去掉 frontmatter）。 */
    public String loadAgentBody(String agentName) {
        Path agentMd =
                workspaceRoot
                        .resolve(".oryxos")
                        .resolve("agents")
                        .resolve(agentName)
                        .resolve("AGENT.md");
        if (!Files.exists(agentMd)) {
            return "";
        }
        try {
            String content = Files.readString(agentMd);
            // Strip frontmatter
            if (content.startsWith("---")) {
                int endIndex = content.indexOf("---", 3);
                if (endIndex >= 0) {
                    return content.substring(endIndex + 3).trim();
                }
            }
            return content.trim();
        } catch (IOException e) {
            log.warn("Failed to read AGENT.md for {}: {}", agentName, e.getMessage());
            return "";
        }
    }

    /** 扫描 Agent 的 skills/ 软连接，注入 Skill 元数据（name/description/path）。 返回格式化的 Skill 描述列表。 */
    public List<SkillMeta> loadSkills(String agentName) {
        List<SkillMeta> skills = new ArrayList<>();
        Path skillsDir =
                workspaceRoot
                        .resolve(".oryxos")
                        .resolve("agents")
                        .resolve(agentName)
                        .resolve("skills");
        if (!Files.isDirectory(skillsDir)) {
            return skills;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(skillsDir)) {
            for (Path skillLink : stream) {
                if (Files.isDirectory(skillLink) || Files.isSymbolicLink(skillLink)) {
                    Path fileName = skillLink.getFileName();
                    if (fileName == null) {
                        continue;
                    }
                    String skillName = fileName.toString();
                    Path skillMd = skillLink.resolve("SKILL.md");
                    String description = "";
                    if (Files.exists(skillMd)) {
                        String content = Files.readString(skillMd);
                        // Extract first line as description
                        String firstLine = content.lines().findFirst().orElse("");
                        if (firstLine.startsWith("# ")) {
                            description = firstLine.substring(2).trim();
                        }
                    }
                    skills.add(new SkillMeta(skillName, description, skillLink.toString()));
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan skills for {}: {}", agentName, e.getMessage());
        }
        return skills;
    }

    /** Skill 元数据。 */
    public record SkillMeta(String name, String description, String path) {}
}
