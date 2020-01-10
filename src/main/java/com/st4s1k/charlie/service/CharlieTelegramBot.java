package com.st4s1k.charlie.service;

import com.st4s1k.charlie.data.model.ChatSession;
import com.st4s1k.charlie.data.model.ChatSession.ChatSessionId;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class CharlieTelegramBot extends TelegramLongPollingBot {

  @Value("${charlie.token}")
  private String token;

  @Value("${charlie.username}")
  private String botUserName;

  private Map<ChatSessionId, ChatSession> sessions = new HashMap<>();

  @Override
  public String getBotUsername() {
    return botUserName;
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
          final var chatSession = new ChatSession(chatSessionId);
          sessions.put(chatSessionId, chatSession);
        }

        final var chatSession = sessions.get(chatSessionId);
        final var receivedMessage = message.getText();
        chatSession.setLastReceivedMessage(receivedMessage);

        CompletableFuture
            .runAsync(() -> parse(chatSession))
            .thenRun(() -> sendResponse(chatSession));
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  @SneakyThrows
  private void sendResponse(
      final ChatSession chatSession) {
    final var response = chatSession.getResponse();
    final var chatId = chatSession.getChat().getId();
    if (!response.isEmpty()) {
      final SendMessage sendMessage = new SendMessage()
          .setChatId(chatId)
          .setText(response);
      execute(sendMessage);
      chatSession.clearResponseBuffer();
    }
  }

  private void parse(
      final ChatSession chatSession) {
    final var receivedMessage = chatSession.getLastReceivedMessage();
    if (receivedMessage.startsWith("/")) {
      parseCommand(chatSession);
    } else {
      chatSession.execute(receivedMessage);
    }
  }

  @SneakyThrows
  private void parseCommand(
      final ChatSession chatSession) {
    final var receivedMessage = chatSession.getLastReceivedMessage();
    if (receivedMessage.startsWith("/ui ")) {
      parseConnectionInfo(chatSession);
    } else if (receivedMessage.startsWith("/cd ")) {
      chatSession.cd(receivedMessage.substring("/cd ".length()));
    } else if (receivedMessage.equals("/pwd")) {
      chatSession.pwd();
    } else if (receivedMessage.startsWith("/download ")) {
      chatSession.download(receivedMessage.substring("/download ".length()));
    } else if (receivedMessage.startsWith("/disconnect")) {
      chatSession.close();
    } else {
      chatSession.addResponse("Unknown command ...");
    }
  }

  private void parseConnectionInfo(
      final ChatSession chatSession) {
    final var receivedMessage = chatSession.getLastReceivedMessage();
    final var splitMsg = receivedMessage.split(" ");
    if (splitMsg.length == 3) {
      final var hostInfo = splitMsg[1];
      final var password = splitMsg[2];
      if (hostInfo.matches(".+@.+:.+")) {
        final var username = hostInfo.substring(0, hostInfo.indexOf('@'));
        final var hostname = hostInfo.substring(hostInfo.indexOf('@') + 1, hostInfo.indexOf(':'));
        final var port = Integer.parseInt(hostInfo.substring(hostInfo.indexOf(':') + 1));
        final var sessionFactory = chatSession.getSessionFactory();
        sessionFactory.setUsername(username);
        sessionFactory.setHostname(hostname);
        sessionFactory.setPort(port);
        sessionFactory.setPassword(password);
        sessionFactory.setConfig("StrictHostKeyChecking", "no");

//        try {
//          final var knownHostsPath = "~/.ssh/known_hosts";
//          final var privateKeyPath = "~/.ssh/id_rsa";
//          sessionFactory.setKnownHosts(knownHostsPath);
//          sessionFactory.setIdentityFromPrivateKey(privateKeyPath);
//        } catch (JSchException e) {
//          e.printStackTrace();
//        }

        chatSession.addResponse("[User info is set]");
      }
    }
  }
}