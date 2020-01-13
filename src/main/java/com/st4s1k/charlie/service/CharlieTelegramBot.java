package com.st4s1k.charlie.service;

import com.st4s1k.charlie.data.model.ChatSession;
import com.st4s1k.charlie.data.model.ChatSessionId;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class CharlieTelegramBot extends TelegramLongPollingBot {

  private final CharlieService charlieService;

  @Value("${charlie.token}")
  private String token;

  @Value("${charlie.username}")
  private String username;

  private Map<ChatSessionId, ChatSession> sessions = new HashMap<>();

  @Override
  public String getBotUsername() {
    return username;
  }

  @Override
  public String getBotToken() {
    return token;
  }

  @Override
  public void onUpdateReceived(final Update update) {
    try {
      if (update.hasMessage() && update.getMessage().hasText()) {

        final var message = update.getMessage();
        final var chat = message.getChat();
        final var user = message.getFrom();
        final var chatSessionId = new ChatSessionId(chat, user);

        sessions.computeIfAbsent(chatSessionId, id ->
            new ChatSession(id, this));

        final var chatSession = sessions.get(chatSessionId);

        chatSession.setReceivedMessage(message.getText());

        CompletableFuture
            .runAsync(() -> charlieService.parse(chatSession))
            .thenRun(() -> charlieService.sendResponse(chatSession));
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  @PreDestroy
  public void cleanUp() {
    sessions.values()
        .forEach(ChatSession::reset);
  }
}
