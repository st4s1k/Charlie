package com.st4s1k.charlie.service;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.st4s1k.charlie.data.model.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.io.*;

@Service
@RequiredArgsConstructor
public class CharlieService {

  public static final String HOME = "~";

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static void createFile(final String filePath) throws IOException {
    final var file = new File(filePath);
    createFileDirs(filePath);
    if (!file.exists()) {
      file.createNewFile();
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public static void createFileDirs(final String filePath) {
    final var fileDir = new File(filePath.substring(0, filePath.lastIndexOf('/')));
    if (!fileDir.exists()) {
      fileDir.mkdirs();
    }
  }

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
      chatSession.addResponse(chatSession
          .sendCommand("cd " + chatSession.getCurrentDir() + " && " + command));
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
    } else if (receivedText.equals("/keygen")) {
      keyGen(chatSession);
    } else if (receivedText.matches("^/cd\\s+.+")) {
      final var dir = receivedText.replaceFirst("^/cd\\s+", "");
      cd(dir, chatSession);
    } else if (receivedText.equals("/pwd")) {
      pwd(chatSession);
    } else if (receivedText.matches("^/download\\s+.+")) {
      final var remoteFilePath = receivedText.replaceFirst("^/download\\s+", "");
      sendDocumentToChat(remoteFilePath, chatSession);
    } else if (receivedText.equals("/reset")) {
      reset(chatSession);
    } else {
      chatSession.addResponse("Unknown command ...");
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
      final var userName = hostInfo.substring(0, hostInfo.indexOf('@'));
      final var hostName = hostInfo.substring(hostInfo.indexOf('@') + 1, hostInfo.indexOf(':'));
      final var port = hostInfo.substring(hostInfo.indexOf(':') + 1);
      chatSession.setUserName(userName);
      chatSession.setHostName(hostName);
      chatSession.setPort(Integer.parseInt(port));
      chatSession.addResponse("[User info is set]\n");
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

  private void keyGen(final ChatSession chatSession) {
    try {
      chatSession.genKeyPair();
      final var publicKeyPath = chatSession.getPublicKeyPath();
      final var fileInputStream = new FileInputStream(publicKeyPath);
      final var bufferedInputStream = new BufferedInputStream(fileInputStream);
      final var documentName = publicKeyPath.substring(publicKeyPath.lastIndexOf('/') + 1);
      sendDocument(documentName, bufferedInputStream, chatSession);
    } catch (JSchException | IOException e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
  }

  public void reset(final ChatSession chatSession) {
    chatSession.reset();
    chatSession.addResponse("[User info cleared]");
  }
}
