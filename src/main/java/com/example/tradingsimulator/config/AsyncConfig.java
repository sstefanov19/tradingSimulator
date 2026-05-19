package com.example.tradingsimulator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncOrder {

    ThreadPoolTaskExecutor poolTaskExecutor = new ThreadPoolTaskExecutor();

    @Bean(name = "async-rder")
    public ThreadPoolTaskExecutor createThreadPool() {
        poolTaskExecutor.
    }

}
