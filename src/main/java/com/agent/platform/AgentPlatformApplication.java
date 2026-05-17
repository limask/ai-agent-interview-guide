package com.agent.platform;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI Agent 平台启动类
 */
@EnableAsync
@SpringBootApplication
public class AgentPlatformApplication {

    public static void main(String[] args) {
        // 加载 .env 文件
        Dotenv dotenv = Dotenv.configure().load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
        SpringApplication.run(AgentPlatformApplication.class, args);
    }
}
