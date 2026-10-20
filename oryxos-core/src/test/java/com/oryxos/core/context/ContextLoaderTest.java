package com.oryxos.core.context;

import static org.junit.jupiter.api.Assertions.*;

import com.oryxos.core.agent.AgentConfigException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ContextLoader 行为验证：Bootstrap / AGENT.md / Skills 加载 + 错误约束 + 无缓存回归。 */
class ContextLoaderTest {

    @TempDir Path tempDir;

    private ContextLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ContextLoader(tempDir);
    }

    // --- Bootstrap ---

    @Test
    @DisplayName("Bootstrap 三个文件都存在：返回拼接内容")
    void bootstrapAllExist_returnsConcatenated() throws IOException {
        Path oryxosDir = tempDir.resolve(".oryxos");
        Files.createDirectories(oryxosDir);
        Files.writeString(oryxosDir.resolve("AGENTS.md"), "agents content");
        Files.writeString(oryxosDir.resolve("SOUL.md"), "soul content");
        Files.writeString(oryxosDir.resolve("USER.md"), "user content");

        String result = loader.loadBootstrap();

        assertTrue(result.contains("agents content"));
        assertTrue(result.contains("soul content"));
        assertTrue(result.contains("user content"));
    }

    @Test
    @DisplayName("Bootstrap 部分缺失：缺失文件 log WARN，其余正常返回")
    void bootstrapPartialMissing_logsWarnAndReturnsRest() throws IOException {
        Path oryxosDir = tempDir.resolve(".oryxos");
        Files.createDirectories(oryxosDir);
        Files.writeString(oryxosDir.resolve("AGENTS.md"), "agents only");
        // SOUL.md 和 USER.md 不存在

        String result = loader.loadBootstrap();

        assertTrue(result.contains("agents only"));
        assertFalse(result.contains("soul"));
    }

    @Test
    @DisplayName("Bootstrap 全部缺失：全部 WARN，返回空字符串")
    void bootstrapAllMissing_returnsEmpty() {
        // 不创建 .oryxos 目录
        String result = loader.loadBootstrap();
        assertTrue(result.isEmpty());
    }

    // --- AGENT.md ---

    @Test
    @DisplayName("AGENT.md 正常加载：返回去掉 frontmatter 后的正文")
    void agentBodyNormal_stripsFrontmatter() throws IOException {
        Path agentDir = tempDir.resolve(".oryxos/agents/test-agent");
        Files.createDirectories(agentDir);
        Files.writeString(
                agentDir.resolve("AGENT.md"), "---\nname: test\n---\nThis is the agent body.");

        String result = loader.loadAgentBody("test-agent");

        assertEquals("This is the agent body.", result);
    }

    @Test
    @DisplayName("AGENT.md 不存在：抛 AgentConfigException")
    void agentBodyMissing_throwsException() {
        assertThrows(AgentConfigException.class, () -> loader.loadAgentBody("nonexistent"));
    }

    @Test
    @DisplayName("AGENT.md 无 frontmatter：返回完整内容")
    void agentBodyNoFrontmatter_returnsFull() throws IOException {
        Path agentDir = tempDir.resolve(".oryxos/agents/plain-agent");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("AGENT.md"), "Just plain content.");

        String result = loader.loadAgentBody("plain-agent");

        assertEquals("Just plain content.", result);
    }

    // --- Skills ---

    @Test
    @DisplayName("Skills 扫描：读取 skills/ 目录下 SKILL.md 第一行作为 description")
    void skillsScan_readsFirstLine() throws IOException {
        Path skillsDir = tempDir.resolve(".oryxos/agents/skill-agent/skills");
        Path skillDir = skillsDir.resolve("my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "# My Skill Description\nSome details.");

        List<ContextLoader.SkillMeta> skills = loader.loadSkills("skill-agent");

        assertEquals(1, skills.size());
        assertEquals("my-skill", skills.get(0).name());
        assertEquals("My Skill Description", skills.get(0).description());
    }

    @Test
    @DisplayName("Skills 目录不存在：返回空列表")
    void skillsDirMissing_returnsEmpty() {
        List<ContextLoader.SkillMeta> skills = loader.loadSkills("no-skill-agent");
        assertTrue(skills.isEmpty());
    }

    // --- 无缓存回归 ---

    @Test
    @DisplayName("无缓存回归：修改文件后下一次读取立即拿到新内容")
    void noCachingRegression_readsFreshContent() throws IOException {
        Path agentDir = tempDir.resolve(".oryxos/agents/cache-agent");
        Files.createDirectories(agentDir);
        Path agentMd = agentDir.resolve("AGENT.md");

        // 第一次读取
        Files.writeString(agentMd, "---\nname: v1\n---\nVersion 1 content");
        String first = loader.loadAgentBody("cache-agent");
        assertEquals("Version 1 content", first);

        // 修改文件内容
        Files.writeString(agentMd, "---\nname: v2\n---\nVersion 2 content");
        String second = loader.loadAgentBody("cache-agent");

        // 第二次必须读到新内容
        assertEquals("Version 2 content", second);
    }
}
