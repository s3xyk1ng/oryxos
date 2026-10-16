package com.oryxos.cli.command;

import com.oryxos.boot.OryxOsApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine.Command;

/** oryxos serve：启动 Spring Boot Web 服务（默认端口 8080）。 定时任务随 serve 一起常驻调度。 */
@Command(name = "serve", description = "Start the OryxOS web server")
public class ServeCommand implements Runnable {

    @Override
    public void run() {
        SpringApplication app = new SpringApplication(OryxOsApplication.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        try (ConfigurableApplicationContext ctx = app.run()) {
            System.out.println("OryxOS server started on http://localhost:8080");
            System.out.println("Press Ctrl+C to stop");
            // Block until shutdown
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
