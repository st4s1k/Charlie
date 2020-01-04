package com.st4s1k.charlie;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

class Charlie extends TelegramLongPollingBot {

  private String token;
  private String botUserName;
  private Map<Long, ChatSession> sessions = new HashMap<>();

  Charlie(
      final String token,
      final String botUserName) {
    this.token = token;
    this.botUserName = botUserName;
  }

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

    if (!connectionInfo.isSetupComplete()) {
      completeSetup(receivedMessage, response, connectionInfo);
    } else {
      switch (receivedMessage) {
        case "connect":
          connectionInfo.setSetupComplete(false);
          completeSetup(receivedMessage, response, connectionInfo);
          break;
        case "disconnect":
          connectionInfo.setHostname(null);
          connectionInfo.setUsername(null);
          connectionInfo.setPort(null);
          connectionInfo.setPassword(null);
          connectionInfo.getSshManager().close();
          connectionInfo.setSetupComplete(true);
          break;
        default:
          response.append(connectionInfo.getSshManager()
              .sendCommand(receivedMessage));
      }
    }

    return response.toString();
  }

  @SuppressWarnings("MethodComplexity")
  private void completeSetup(
      final String receivedMessage,
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    if (isNull(connectionInfo.getHostname())) {
      setHostname(receivedMessage, response, connectionInfo);
    }
    if (isNull(connectionInfo.getUsername()) &&
        nonNull(connectionInfo.getHostname())) {
      setUsername(receivedMessage, response, connectionInfo);
    }
    if (isNull(connectionInfo.getPort()) &&
        nonNull(connectionInfo.getUsername())) {
      setPort(receivedMessage, response, connectionInfo);
    }
    if (isNull(connectionInfo.getPassword()) &&
        nonNull(connectionInfo.getPort())) {
      setPassword(receivedMessage, response, connectionInfo);
    }
    if (nonNull(connectionInfo.getPassword())) {
      finalizeSetup(response, connectionInfo);
    }
  }

  private void finalizeSetup(
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    final var sshManager = new SSHManager(
        connectionInfo.getUsername(),
        connectionInfo.getPassword(),
        connectionInfo.getHostname(), "",
        connectionInfo.getPort()
    );

    final var connectErrorMessage = sshManager.connect();

    if (isNull(connectErrorMessage)) {
      connectionInfo.setSshManager(sshManager);
      connectionInfo.setSetupComplete(true);
      response.append("[Setup complete]");
    } else {
      response.append(connectErrorMessage);
    }
  }

  private void setPassword(
      final String receivedMessage,
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    if (!connectionInfo.isAwaitPassword()) {
      response.append("[Please provide password]");
      connectionInfo.setAwaitPassword(true);
    } else {
      connectionInfo.setPassword(receivedMessage);
      connectionInfo.setAwaitPassword(false);
    }
  }

  private void setPort(
      final String receivedMessage,
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    if (!connectionInfo.isAwaitPort()) {
      response.append("[Please provide port]");
      connectionInfo.setAwaitPort(true);
    } else {
      connectionInfo.setPort(Integer.valueOf(receivedMessage));
      connectionInfo.setAwaitPort(false);
    }
  }

  private void setUsername(
      final String receivedMessage,
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    if (!connectionInfo.isAwaitUsername()) {
      response.append("[Please provide username]");
      connectionInfo.setAwaitUsername(true);
    } else {
      connectionInfo.setUsername(receivedMessage);
      connectionInfo.setAwaitUsername(false);
    }
  }

  private void setHostname(
      final String receivedMessage,
      final StringBuilder response,
      final ConnectionInfo connectionInfo) {
    if (!connectionInfo.isAwaitHostname()) {
      response.append("[Please provide hostname]");
      connectionInfo.setAwaitHostname(true);
    } else {
      connectionInfo.setHostname(receivedMessage);
      connectionInfo.setAwaitHostname(false);
    }
  }
}