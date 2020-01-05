package com.st4s1k.charlie.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class SpringConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(SpringConfiguration.class);

  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    LOGGER.debug("Creating Async Task Executor");
    final var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("BotThread-");
    executor.initialize();
    return executor;
  }
}
