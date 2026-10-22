package com.oryxos.cli.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;

/** oryxos gateway：守护进程，挂载多通道。 核心阶段只搭骨架，不实现通道挂载逻辑。 */
@Command(name = "gateway", description = "Start daemon mounting multiple channels")
public class GatewayCommand implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(GatewayCommand.class);

    @Override
    public void run() {
        // 核心阶段只搭骨架
        System.out.println("OryxOS gateway started");
        log.info("Gateway daemon started (skeleton mode)");

        // TODO: 扩展阶段实现通道挂载逻辑
        // 目前保持运行直到用户中断
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Gateway shutting down");
        }
    }
}
