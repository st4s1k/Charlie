package com.st4s1k.charlie.service;

import com.st4s1k.charlie.data.model.ChatSession;
import com.st4s1k.charlie.data.model.ConnectionInfo;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.isNull;
import static java.util.concurrent.TimeUnit.SECONDS;

@RequiredArgsConstructor
public class CharlieTelegramBot extends TelegramLongPollingBot {

  private final String token;
  private final String botUserName;

  private Map<Long, ChatSession> sessions = new HashMap<>();

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
        final var chatId = chat.getId();

        sessions.putIfAbsent(chatId, new ChatSession(chat, user));

        final var session = sessions.get(chatId);
        final var receivedMessage = message.getText();
        final var responseBuilder = new StringBuilder();

        CompletableFuture.runAsync(() ->
            parse(responseBuilder, session, receivedMessage))
            .orTimeout(5, SECONDS);

        final String response = responseBuilder.toString();

        if (!response.isEmpty()) {
          SendMessage sendMessage = new SendMessage()
              .setChatId(chatId)
              .setText(response);
          execute(sendMessage);
        }
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private void parse(
      final StringBuilder response,
      final ChatSession chatSession,
      final String receivedMessage) {
    final var connectionInfo = chatSession.getConnectionInfo();

    if (receivedMessage.startsWith("/connect ")) {
      final String[] splitMsg = receivedMessage.split(" ");
      if (splitMsg.length == 3) {
        final String hostInfo = splitMsg[1];
        final String username = hostInfo.substring(9,
            hostInfo.indexOf('@'));
        final String hostname = hostInfo.substring(
            hostInfo.indexOf('@') + 1,
            hostInfo.indexOf(':'));
        final Integer port = Integer.valueOf(
            hostInfo.substring(receivedMessage.indexOf(':') + 1));
        final String password = splitMsg[2];
        connectionInfo.setUsername(username);
        connectionInfo.setHostname(hostname);
        connectionInfo.setPort(port);
        connectionInfo.setPassword(password);
        setupConnection(response, connectionInfo);
      }
    } else {
      final var sshManager = Optional.ofNullable(connectionInfo.getSshManager());
      switch (receivedMessage) {
        case "/disconnect":
          sshManager.ifPresent(SSHManager::close);
          break;
        case "/reconnect":
          sshManager.ifPresent(SSHManager::connect);
          break;
        case "/reset":
          connectionInfo.setHostname(null);
          connectionInfo.setUsername(null);
          connectionInfo.setPort(null);
          connectionInfo.setPassword(null);
          connectionInfo.setSshManager(null);
          break;
        default:
          response.append(sshManager
              .map(ssh -> ssh.sendCommand(receivedMessage))
              .orElse("No connection ..."));
      }
    }
  }

  private void setupConnection(
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    if (connectionInfo.allSet()) {
      connectionInfo.setUpSshManager();
      final var sshManager = connectionInfo.getSshManager();
      final var connectErrorMessage = sshManager.connect();
      if (isNull(connectErrorMessage)) {
        connectionInfo.setSshManager(sshManager);
        response.append("[Setup complete]");
      } else {
        response.append(connectErrorMessage);
      }
    }
  }
}