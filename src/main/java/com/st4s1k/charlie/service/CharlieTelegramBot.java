package com.st4s1k.charlie.service;

import com.st4s1k.charlie.data.model.ChatSession;
import com.st4s1k.charlie.data.model.ConnectionInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

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

  @Async
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
        final var response = parse(session, receivedMessage);

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

  private String parse(
      final ChatSession chatSession,
      final String receivedMessage) {
    final var response = new StringBuilder();
    final var connectionInfo = chatSession.getConnectionInfo();

    if (connectionInfo.isSetupInProgress()) {
      completeSetup(receivedMessage, response, connectionInfo);
    } else {
      final var sshManager = Optional.ofNullable(connectionInfo.getSshManager());
      switch (receivedMessage) {
        case "connect":
          connectionInfo.setSetupInProgress(true);
          completeSetup(receivedMessage, response, connectionInfo);
          break;
        case "disconnect":
          sshManager.ifPresent(SSHManager::close);
          break;
        case "reconnect":
          sshManager.ifPresent(SSHManager::connect);
          break;
        case "reset":
          connectionInfo.setHostname(null);
          connectionInfo.setUsername(null);
          connectionInfo.setPort(null);
          connectionInfo.setPassword(null);
          connectionInfo.setSshManager(null);
          connectionInfo.setSetupInProgress(false);
          break;
        default:
          response.append(sshManager
              .map(ssh -> ssh.sendCommand(receivedMessage))
              .orElse("No connection ..."));
      }
    }

    return response.toString();
  }

  private void completeSetup(
      final String receivedMessage,
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    setHostname(receivedMessage, response, connectionInfo);
    setUsername(receivedMessage, response, connectionInfo);
    setPort(receivedMessage, response, connectionInfo);
    setPassword(receivedMessage, response, connectionInfo);
    finalizeSetup(response, connectionInfo);
  }

  private void setHostname(
      final String receivedMessage,
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    if (isNull(connectionInfo.getHostname())) {
      if (!connectionInfo.isAwaitHostname()) {
        response.append("[Please provide hostname]");
        connectionInfo.setAwaitHostname(true);
      } else {
        connectionInfo.setHostname(receivedMessage);
        connectionInfo.setAwaitHostname(false);
      }
    }
  }

  private void setUsername(
      final String receivedMessage,
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    if (isNull(connectionInfo.getUsername()) &&
        nonNull(connectionInfo.getHostname())) {
      if (!connectionInfo.isAwaitUsername()) {
        response.append("[Please provide username]");
        connectionInfo.setAwaitUsername(true);
      } else {
        connectionInfo.setUsername(receivedMessage);
        connectionInfo.setAwaitUsername(false);
      }
    }
  }

  private void setPort(
      final String receivedMessage,
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    if (isNull(connectionInfo.getPort()) &&
        nonNull(connectionInfo.getUsername())) {
      if (!connectionInfo.isAwaitPort()) {
        response.append("[Please provide port]");
        connectionInfo.setAwaitPort(true);
      } else {
        connectionInfo.setPort(Integer.valueOf(receivedMessage));
        connectionInfo.setAwaitPort(false);
      }
    }
  }

  private void setPassword(
      final String receivedMessage,
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    if (isNull(connectionInfo.getPassword()) &&
        nonNull(connectionInfo.getPort())) {
      if (!connectionInfo.isAwaitPassword()) {
        response.append("[Please provide password]");
        connectionInfo.setAwaitPassword(true);
      } else {
        connectionInfo.setPassword(receivedMessage);
        connectionInfo.setAwaitPassword(false);
      }
    }
  }

  private void finalizeSetup(
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    if (connectionInfo.allSet()) {
      connectionInfo.setUpSshManager();
      final var sshManager = connectionInfo.getSshManager();
      final var connectErrorMessage = sshManager.connect();
      if (isNull(connectErrorMessage)) {
        connectionInfo.setSshManager(sshManager);
        connectionInfo.setSetupInProgress(false);
        response.append("[Setup complete]");
      } else {
        response.append(connectErrorMessage);
      }
    }
  }
}