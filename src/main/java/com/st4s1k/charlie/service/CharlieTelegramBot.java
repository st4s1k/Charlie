package com.st4s1k.charlie.service;

import com.st4s1k.charlie.data.model.ChatSession;
import com.st4s1k.charlie.data.model.ChatSession.ChatSessionId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.nio.file.StandardOpenOption.CREATE_NEW;

@Component
public class CharlieTelegramBot extends TelegramLongPollingBot {

  @Value("${charlie.token}")
  private String token;

  @Value("${charlie.username}")
  private String username;

  @Value("${charlie.knownHostsPath}")
  private String knownHostsPath;

  @Value("${charlie.privateKeyPath}")
  private String privateKeyPath;

  @Value("${charlie.privateKey}")
  private String privateKey;

  @Value("${charlie.knownHosts}")
  private String knownHosts;

  private Map<ChatSessionId, ChatSession> sessions = new HashMap<>();

  @PostConstruct
  private void setup() throws IOException {
    final var knownHostsPath = Paths.get(this.knownHostsPath);
    final var privateKeyPath = Paths.get(this.privateKeyPath);
    Files.deleteIfExists(knownHostsPath);
    Files.deleteIfExists(privateKeyPath);
    Files.write(knownHostsPath, knownHosts.getBytes(), CREATE_NEW);
    Files.write(privateKeyPath, privateKey.getBytes(), CREATE_NEW);
  }

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

        if (!sessions.containsKey(chatSessionId)) {
          final var chatSession = new ChatSession(
              chatSessionId,
              this::execute,
              this::execute
          );
          sessions.put(chatSessionId, chatSession);
        }

        final var chatSession = sessions.get(chatSessionId);

        chatSession.setReceivedMessage(message.getText());

        CompletableFuture
            .runAsync(chatSession::parse)
            .thenRun(chatSession::sendMessage);
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }
}