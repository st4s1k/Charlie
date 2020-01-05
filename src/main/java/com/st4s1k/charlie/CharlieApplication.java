package com.st4s1k.charlie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.telegram.telegrambots.ApiContextInitializer;

@SpringBootApplication
public class CharlieApplication {
  public static void main(final String[] args) {
    ApiContextInitializer.init();
    SpringApplication.run(CharlieApplication.class, args);
  }
}