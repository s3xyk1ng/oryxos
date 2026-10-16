package com.oryxos.core.context;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 配置加载器：从环境变量加载敏感配置，解析 ${ENV_VAR} 占位符。 */
@Component
public class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);
    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 解析字符串中的 ${ENV_VAR} 占位符，替换为对应的环境变量值。
     *
     * @param input 含占位符的原始字符串
     * @return 解析后的字符串
     * @throws IllegalStateException 如果必需的环境变量未设置
     */
    public String resolve(String input) {
        if (input == null) return null;
        Matcher matcher = ENV_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String envVar = matcher.group(1);
            String value = System.getenv(envVar);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Required environment variable not set: " + envVar);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 安全解析：如果环境变量不存在，返回原始占位符字符串（不抛异常）。 */
    public String resolveLenient(String input) {
        if (input == null) return null;
        Matcher matcher = ENV_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String envVar = matcher.group(1);
            String value = System.getenv(envVar);
            if (value != null && !value.isBlank()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
