package com.st4s1k.charlie.service;

import com.jcraft.jsch.JSch;
import com.st4s1k.charlie.data.model.ChatSession;
import com.st4s1k.charlie.data.model.ChatSessionId;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class CharlieTelegramBot extends TelegramLongPollingBot {

  private final JSch jsch;
  private final CharlieService charlieService;
  private final String token;
  private final String username;
  private final String dotSsh;
  private final Map<ChatSessionId, ChatSession> sessions;

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
    if (update.hasMessage() && update.getMessage().hasText()) {
      final var message = update.getMessage();
      final var chat = message.getChat();
      final var user = message.getFrom();
      final var chatSessionId = new ChatSessionId(chat, user);

      sessions.computeIfAbsent(chatSessionId,
          id -> new ChatSession(id, dotSsh, this, jsch));

      final var chatSession = sessions.get(chatSessionId);

      chatSession.setUpdate(update);

      CompletableFuture
          .runAsync(() -> charlieService.parse(chatSession));
    }
  }

  @PreDestroy
  public void cleanUp() {
    sessions.values().forEach(ChatSession::reset);
  }
}
