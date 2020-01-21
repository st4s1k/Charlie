package com.st4s1k.charlie.service;

import com.jcraft.jsch.JSchException;
import com.st4s1k.charlie.data.model.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.io.*;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.jcraft.jsch.ChannelSftp.APPEND;

@Service
@RequiredArgsConstructor
public class CharlieService {

  public static final String USER_NAME_REGEX = "([A-Za-z0-9\\-.]+)";
  private static final String IP_ADDRESS_REGEX =
      "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
          "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
  private static final String HOST_NAME_REGEX =
      "([a-z_][a-z0-9_\\-]*[$]?)";
  public static final String HOST_REGEX = "("
      + IP_ADDRESS_REGEX + "|"
      + HOST_NAME_REGEX + ")";
  public static final String PORT_REGEX =
      "([0-9]{1,4}" +
          "|[1-5][0-9]{4}" +
          "|6[0-4][0-9]{3}" +
          "|65[0-4][0-9]{2}" +
          "|655[0-2][0-9]" +
          "|6553[0-5])";
  public static final String HOST_INFO_REGEX = "^"
      + USER_NAME_REGEX + "@"
      + HOST_REGEX + ":"
      + PORT_REGEX + "$";


  public final Map<Predicate<String>, Consumer<ChatSession>> operations =
      Map.of(
          msg -> msg.matches("^/ui\\s+.+$"), this::parseConnectionInfo,
          msg -> msg.matches("^/keygen$"), this::keyGen,
          msg -> msg.matches("^/keyauth$"), this::switchToKeyAuthMode,
          msg -> msg.matches("^/cd\\s+.+$"), this::cd,
          msg -> msg.matches("^/password\\s+.+$"), this::setPassword,
          msg -> msg.matches("^/pwd$"), this::pwd,
          msg -> msg.matches("^/download\\s+.+$"), this::downloadAndSendDocumentToChat,
          msg -> msg.matches("^/reset$"), this::reset
      );

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

  public void downloadAndSendDocumentToChat(final ChatSession chatSession) {
    chatSession.runSftp(sftp -> {
      final var remoteFilePath = getReceivedText(chatSession)
          .replaceFirst("^/download\\s+", "");
      sftp.cd(chatSession.getCurrentDir() + "/");
      final var fileName = remoteFilePath.substring(remoteFilePath.lastIndexOf('/') + 1);
      final var inputStream = sftp.get(remoteFilePath);
      sendDocument(fileName, inputStream, fileName, chatSession);
    });
  }

  public void executeCommand(
      final String command,
      final ChatSession chatSession) {
    chatSession.sendCommand("cd " + chatSession.getCurrentDir()
        + " && " + command);
  }

  public void parse(final ChatSession chatSession) {
    final var receivedText = getReceivedText(chatSession);
    if (receivedText.startsWith("/")) {
      parseCommand(chatSession);
    } else {
      executeCommand(receivedText, chatSession);
    }
  }

  public void parseCommand(final ChatSession chatSession) {
    operations.entrySet().stream()
        .filter(e -> e.getKey().test(getReceivedText(chatSession)))
        .findFirst()
        .ifPresentOrElse(e -> e.getValue().accept(chatSession),
            () -> chatSession.addResponse("Unknown command ..."));
  }

  private String getReceivedText(final ChatSession chatSession) {
    return Optional.of(chatSession)
        .map(ChatSession::getReceivedMessage)
        .filter(Message::hasText)
        .map(Message::getText).orElse("");
  }

  public void parseConnectionInfo(final ChatSession chatSession) {
    final var hostInfo = getReceivedText(chatSession)
        .replaceFirst("^/ui\\s+", "");
    if (hostInfo.matches(HOST_INFO_REGEX)) {
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
      final String caption,
      final ChatSession chatSession) {
    final var sendDocumentRequest = new SendDocument()
        .setChatId(chatSession.getChatId())
        .setDocument(documentName, inputStream)
        .setCaption(caption);
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

  public void cd(final ChatSession chatSession) {
    final var dir = getReceivedText(chatSession)
        .replaceFirst("^/cd\\s+", "");
    executeCommand("cd " + dir + " && pwd", chatSession);
    chatSession.setCurrentDir(chatSession.getResponse().trim());
    executeCommand("ls", chatSession);
  }

  private void setPassword(final ChatSession chatSession) {
    final var password = getReceivedText(chatSession)
        .replaceFirst("^/password\\s+", "");
    chatSession.setPassword(password);
    chatSession.addResponse("[Password set]");
  }

  private void switchToKeyAuthMode(final ChatSession chatSession) {
    chatSession.runSftp(sftp -> {
      chatSession.genKeyPair();
      final var publicKeyPath = chatSession.getPublicKeyPath();
      final var fileInputStream = new FileInputStream(publicKeyPath);
      final var home = sftp.getHome();
      final var authorizedKeysPath = home + "/.ssh/authorized_keys";
      sftp.put(fileInputStream, authorizedKeysPath, APPEND);
      chatSession.setPassword(null);
    });
  }

  private void keyGen(final ChatSession chatSession) {
    try {
      chatSession.genKeyPair();
      final var publicKeyPath = chatSession.getPublicKeyPath();
      final var fileInputStream = new FileInputStream(publicKeyPath);
      final var bufferedInputStream = new BufferedInputStream(fileInputStream);
      final var documentName = "id_rsa_charlie.pub";
      final var caption = "Execute this command on the remote ssh server:\n" +
          "cat /path/to/" + documentName + " >> /path/to/.ssh/authorized_keys\n" +
          "example: cat ./" + documentName + " >> ~/.ssh/authorized_keys";
      sendDocument(documentName, bufferedInputStream, caption, chatSession);
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
