package com.example.tradingsimulator.config;

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
public class AsyncConfig {

    private ExecutorService taskExecutorService;

    @Bean(name = "taskExecutor")
    public ExecutorService taskExecutor() {
        taskExecutorService = Executors.newVirtualThreadPerTaskExecutor();
        return taskExecutorService;
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (taskExecutorService != null) {
            taskExecutorService.shutdown();
            taskExecutorService.awaitTermination(30, TimeUnit.SECONDS);
        }
    }
}