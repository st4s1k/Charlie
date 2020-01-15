package com.st4s1k.charlie.service;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.st4s1k.charlie.data.model.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class CharlieService {

  public static final String HOME = "~";

  public void sendDocumentToChat(
      final String remoteFilePath,
      final ChatSession chatSession) {
    try {
      chatSession.runSftp(sftp -> {
        try {
          sftp.cd(chatSession.getCurrentDir() + "/");
          final var fileName = remoteFilePath.substring(remoteFilePath.lastIndexOf('/') + 1);
          final var inputStream = sftp.get(remoteFilePath);
          sendDocument(fileName, inputStream, chatSession);
        } catch (SftpException e) {
          e.printStackTrace();
          chatSession.addResponse(e.getMessage());
        }
      });
    } catch (JSchException e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
  }

  public void executeCommand(
      final String command,
      final ChatSession chatSession) {
    try {
      chatSession.addResponse(chatSession.sendCommand("cd " +
          chatSession.getCurrentDir() + " && " + command));
    } catch (JSchException | IOException e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
  }

  public void parse(final ChatSession chatSession) {
    final var receivedMessage = chatSession.getReceivedMessage().getText();
    if (receivedMessage.startsWith("/")) {
      parseCommand(chatSession);
    } else {
      executeCommand(receivedMessage, chatSession);
    }
  }

  @SuppressWarnings("MethodComplexity")
  public void parseCommand(final ChatSession chatSession) {
    final var receivedMessage = chatSession.getReceivedMessage();
    final var receivedText = receivedMessage.hasText()
        ? receivedMessage.getText()
        : receivedMessage.hasDocument()
        ? receivedMessage.getCaption()
        : "";

    if (receivedText.matches("^/ui\\s+.+")) {
      final var hostInfo = receivedText.replaceFirst("^/ui\\s+", "");
      parseConnectionInfo(hostInfo, chatSession);
    } else if (receivedText.matches("^/connect(\\s+)?$")) {
      connect(chatSession);
    } else if (receivedText.matches("^/cd\\s+.+")) {
      final var dir = receivedText.replaceFirst("^/cd\\s+", "");
      cd(dir, chatSession);
    } else if (receivedText.equals("/pwd")) {
      pwd(chatSession);
    } else if (receivedText.matches("^/download\\s+.+")) {
      final var remoteFilePath = receivedText.replaceFirst("^/download\\s+", "");
      sendDocumentToChat(remoteFilePath, chatSession);
    } else if (receivedText.equals("/disconnect")) {
      close(chatSession);
    } else {
      chatSession.addResponse("Unknown command ...");
    }
  }

  private void connect(final ChatSession chatSession) {
    try {
      chatSession.connect();
    } catch (JSchException e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
  }

  public void parseConnectionInfo(
      final String hostInfo,
      final ChatSession chatSession) {
    final var userNameRegex = "([A-Za-z0-9\\-.]+)";
    final var hostNameRegex = "" +
        "(((25[0-5]|2[0-4][0-9]" +
        "|[01]?[0-9][0-9]?)\\.){3}" +
        "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)" +
        "|([a-z_][a-z0-9_\\-]*[$]?))";
    final var portRegex = "" +
        "([0-9]{1,4}" +
        "|[1-5][0-9]{4}" +
        "|6[0-4][0-9]{3}" +
        "|65[0-4][0-9]{2}" +
        "|655[0-2][0-9]" +
        "|6553[0-5])";
    final var hostInfoRegex = "^" + userNameRegex + "@" + hostNameRegex + ":" + portRegex + "$";
    if (hostInfo.matches(hostInfoRegex)) {
      final var username = hostInfo.substring(0, hostInfo.indexOf('@'));
      final var hostname = hostInfo.substring(hostInfo.indexOf('@') + 1, hostInfo.indexOf(':'));
      final var port = hostInfo.substring(hostInfo.indexOf(':') + 1);
      try {
        chatSession.setSession(username, hostname, Integer.parseInt(port));
        chatSession.addResponse("[User info is set]\n");
        final var fileInputStream = new FileInputStream(chatSession.getPublicKeyPath());
        final var bufferedInputStream = new BufferedInputStream(fileInputStream);
        sendDocument("id_rsa.pub", bufferedInputStream, chatSession);
      } catch (JSchException | IOException e) {
        e.printStackTrace();
        chatSession.addResponse(e.getMessage());
      }
    } else {
      chatSession.addResponse("[Invalid user info format]");
    }
  }

  public void sendResponse(final ChatSession chatSession) {
    if (chatSession.responseExists()) {
      final var sendMessageRequest = new SendMessage()
          .setChatId(chatSession.getChatId())
          .setText(chatSession.getResponse());
      try {
        chatSession.getCharlie().execute(sendMessageRequest);
      } catch (Exception e) {
        e.printStackTrace();
        chatSession.addResponse(e.getMessage());
      }
    }
  }

  public void sendDocument(
      final String documentName,
      final InputStream inputStream,
      final ChatSession chatSession) {
    final var sendDocumentRequest = new SendDocument()
        .setChatId(chatSession.getChatId())
        .setDocument(documentName, inputStream)
        .setCaption(documentName);
    try {
      chatSession.getCharlie().execute(sendDocumentRequest);
    } catch (Exception e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
  }

  public void pwd(final ChatSession chatSession) {
    chatSession.addResponse(chatSession.getCurrentDir());
  }

  public void cd(
      final String dir,
      final ChatSession chatSession) {
    executeCommand("cd " + dir + " && pwd", chatSession);
    chatSession.setCurrentDir(chatSession.getResponse().trim());
    executeCommand("ls", chatSession);
  }

  public void close(final ChatSession chatSession) {
    chatSession.reset();
    chatSession.addResponse("[User info cleared]");
  }
}
