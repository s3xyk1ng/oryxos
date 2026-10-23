package com.oryxos.tool.annotation;

import com.oryxos.core.agent.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动时扫描所有 Spring Bean 的 @Tool 注解方法，
 * 包装为 AnnotatedToolAdapter 注册到 ToolRegistry。
 */
@Component
public class ToolMethodScanner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolMethodScanner.class);

    private final ApplicationContext applicationContext;
    private final ToolRegistry toolRegistry;

    public ToolMethodScanner(ApplicationContext applicationContext, ToolRegistry toolRegistry) {
        this.applicationContext = applicationContext;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<AnnotatedToolAdapter> scanned = new ArrayList<>();

        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean = applicationContext.getBean(beanName);
            Class<?> beanClass = bean.getClass();

            for (Method method : beanClass.getDeclaredMethods()) {
                Tool annotation = method.getAnnotation(Tool.class);
                if (annotation != null) {
                    AnnotatedToolAdapter adapter = new AnnotatedToolAdapter(bean, method, annotation);
                    scanned.add(adapter);
                    log.info("Discovered @Tool method: {} -> {}", annotation.name(),
                            beanClass.getSimpleName() + "." + method.getName());
                }
            }
        }

        toolRegistry.registerAll(new ArrayList<>(scanned));
        log.info("Scanned and registered {} @Tool methods", scanned.size());
    }
}
