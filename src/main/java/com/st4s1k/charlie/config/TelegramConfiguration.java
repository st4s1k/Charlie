package com.st4s1k.charlie.config;

import com.st4s1k.charlie.service.CharlieTelegramBot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

@Configuration
class TelegramConfiguration {

  @Bean
  TelegramBotsApi getTelegramBotsApi() {
    return new TelegramBotsApi();
  }

  @Bean
  TelegramLongPollingBot getCharlie(
      final TelegramBotsApi telegramBotsApi,
      final @Value("${charlie.token}") String token,
      final @Value("${charlie.username}") String botUserName)
      throws TelegramApiRequestException {
    final var charlie = new CharlieTelegramBot(token, botUserName);
    telegramBotsApi.registerBot(charlie);
    return charlie;
  }
}
