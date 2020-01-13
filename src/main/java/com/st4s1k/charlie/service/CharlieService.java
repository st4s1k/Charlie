package com.st4s1k.charlie.service;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.st4s1k.charlie.data.model.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class CharlieService {

  public static final String HOME = "~";

  public void sendDocumentToChat(
      final String remoteFilePath,
      final ChatSession chatSession) {
    try {
      chatSession.getSftpRunner().execute(sftp -> {
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
    } catch (JSchException | IOException e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
  }

  public void executeCommand(
      final String command,
      final ChatSession chatSession) {
    try {
      chatSession.addResponse(chatSession.getCommandRunner()
          .execute("cd " + chatSession.getCurrentDir() + " && " + command)
          .getStdout());
    } catch (JSchException | IOException e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
  }

  public void parse(final ChatSession chatSession) {
    final var receivedMessage = chatSession.getReceivedMessage();
    if (receivedMessage.startsWith("/")) {
      parseCommand(chatSession);
    } else {
      executeCommand(receivedMessage, chatSession);
    }
  }

  public void parseCommand(final ChatSession chatSession) {
    final var receivedMessage = chatSession.getReceivedMessage();

    if (receivedMessage.matches("/rsa\\s+[\\s\\S]+")) {
      final var idRsa = receivedMessage.replaceFirst("/rsa\\s+", "");
      setIdentity(idRsa, chatSession);
    } else if (receivedMessage.matches("/ui\\s+.+")) {
      final var hostInfo = receivedMessage.replaceFirst("/ui\\s+", "");
      parseConnectionInfo(hostInfo, chatSession);
    } else if (receivedMessage.matches("/cd\\s+.+")) {
      final var dir = receivedMessage.replaceFirst("/cd\\s+", "");
      cd(dir, chatSession);
    } else if (receivedMessage.equals("/pwd")) {
      pwd(chatSession);
    } else if (receivedMessage.matches("/download\\s+.+")) {
      final var remoteFilePath = receivedMessage.replaceFirst("/download\\s+", "");
      sendDocumentToChat(remoteFilePath, chatSession);
    } else if (receivedMessage.equals("/disconnect")) {
      close(chatSession);
    } else {
      chatSession.addResponse("Unknown command ...");
    }
  }

  private void setIdentity(
      final String idRsa,
      final ChatSession chatSession) {
    try {
      final var userName = chatSession.getUserName();
      final var hostName = chatSession.getSessionFactory().getHostname();
      final var file = "id_rsa_" + userName + hostName;
      final var filePath = Path.of(file);
      Files.write(filePath, idRsa.getBytes());
      chatSession.getSessionFactory().setIdentityFromPrivateKey(file);
    } catch (IOException | JSchException e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
  }

  public void parseConnectionInfo(
      final String hostInfo,
      final ChatSession chatSession) {
    final var userNameRegex = "([A-Za-z0-9\\-.]+)";
    final var hostNameRegex = "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})";
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
      final var sessionFactory = chatSession.getSessionFactory();
      sessionFactory.setUsername(username);
      sessionFactory.setHostname(hostname);
      sessionFactory.setPort(Integer.parseInt(port));
      sessionFactory.setConfig("StrictHostKeyChecking", "no");
      chatSession.addResponse("[User info is set]");
    } else {
      chatSession.addResponse("[Invalid user info format]");
    }
  }

  public void sendResponse(final ChatSession chatSession) {
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
