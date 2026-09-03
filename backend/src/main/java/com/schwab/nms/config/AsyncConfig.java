package com.schwab.nms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Prototype-scale async/scheduling setup. The submit API publishes an event and
 * returns immediately (section 4.1 "Asynchronous processing"); this executor backs
 * the listener and the delivery worker's dispatch calls. A production deployment
 * would replace this in-process pool with a durable broker (Kafka/SQS) so queued
 * work survives an instance restart — see docs/architecture-overview.md trade-offs.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("nms-async-");
        executor.initialize();
        return executor;
    }
}
