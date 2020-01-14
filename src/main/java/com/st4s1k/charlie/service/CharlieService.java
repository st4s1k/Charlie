package com.st4s1k.charlie.service;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.st4s1k.charlie.data.model.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

@Service
@RequiredArgsConstructor
public class CharlieService {

  public static final String HOME = "~";

  @Value("${jsch.dotSsh}")
  private String dotSsh;

  @Value("${jsch.knownHosts.file}")
  private String knownHostsFile;

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

    if (receivedText.matches("/rsa\\s+[\\s\\S]+")) {
      final var idRsa = receivedText.replaceFirst("/rsa\\s+", "");
      setIdentity(idRsa, chatSession);
    } else if (receivedText.matches("/rsa$")) {
      setIdentity(chatSession);
    } else if (receivedText.matches("/ui\\s+.+")) {
      final var hostInfo = receivedText.replaceFirst("/ui\\s+", "");
      parseConnectionInfo(hostInfo, chatSession);
    } else if (receivedText.matches("/cd\\s+.+")) {
      final var dir = receivedText.replaceFirst("/cd\\s+", "");
      cd(dir, chatSession);
    } else if (receivedText.equals("/pwd")) {
      pwd(chatSession);
    } else if (receivedText.matches("/download\\s+.+")) {
      final var remoteFilePath = receivedText.replaceFirst("/download\\s+", "");
      sendDocumentToChat(remoteFilePath, chatSession);
    } else if (receivedText.equals("/disconnect")) {
      close(chatSession);
    } else {
      chatSession.addResponse("Unknown command ...");
    }
  }

  private void setIdentity(
      String idRsa,
      final ChatSession chatSession) {
    try {
      final var userName = chatSession.getUserName();
      final var hostName = chatSession.getSessionFactory().getHostname();
      final var identityFile = dotSsh + "/id_rsa_" + userName + "_" + hostName;

      if (!idRsa.matches("^[\\s\\S]+\\n$")) {
        idRsa += "\n";
      }

      createFile(identityFile, idRsa);

      addToKnownHosts(hostName);

      chatSession.getSessionFactory()
          .setIdentityFromPrivateKey(identityFile);

      chatSession.getSessionFactory()
          .setKnownHosts(knownHostsFile);

      chatSession.addResponse("[Identity is set]");
    } catch (IOException | JSchException e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
  }

  private void setIdentity(final ChatSession chatSession) {
    try {
      final var identityFile = downloadFile(chatSession);

      final var identityFilePath = identityFile.getPath();

      chatSession.getSessionFactory()
          .setIdentityFromPrivateKey(identityFilePath);

      chatSession.getSessionFactory()
          .setKnownHosts(knownHostsFile);

      final var hostName = chatSession.getSessionFactory().getHostname();

      addToKnownHosts(hostName);

      chatSession.addResponse("[Identity is set]");
    } catch (IOException
        | JSchException
        | TelegramApiException e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
  }

  private void addToKnownHosts(final String hostName) throws IOException {

    final var file = new File(knownHostsFile);

    createFile(file);

    final var content = Files.readString(Path.of(knownHostsFile));

    if (Arrays.stream(content.split(",")).noneMatch(hostName::equals)) {
      final var newContent = ((content.isBlank() ? "" : ",") + hostName).trim();
      Files.write(Path.of(knownHostsFile), newContent.getBytes(), CREATE, APPEND);
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void createFile(final File file) throws IOException {
    final var filePath = file.getPath();
    createFileDirs(filePath);
    if (!file.exists()) {
      file.createNewFile();
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void createFileDirs(final String filePath) {
    final var fileDir = new File(filePath.substring(0, filePath.lastIndexOf('/')));
    if (!fileDir.exists()) {
      fileDir.mkdirs();
    }
  }

  private void createFile(final String filePath, final String content) throws IOException {
    createFileDirs(filePath);
    Files.write(Path.of(filePath), content.getBytes());
  }

  public void parseConnectionInfo(
      final String hostInfo,
      final ChatSession chatSession) {
    final var userNameRegex = "([A-Za-z0-9\\-.]+)";
    final var hostNameRegex = "((\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})" +
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
      final var sessionFactory = chatSession.getSessionFactory();
      sessionFactory.setUsername(username);
      sessionFactory.setHostname(hostname);
      sessionFactory.setPort(Integer.parseInt(port));
      sessionFactory.setConfig("StrictHostKeyChecking", "no");
      sessionFactory.setConfig("PreferredAuthentications", "publickey,password");
      chatSession.addResponse("[User info is set]");
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

  public File downloadFile(final ChatSession chatSession) throws TelegramApiException {
    final var charlie = chatSession.getCharlie();
    final var message = chatSession.getReceivedMessage();
    return charlie.downloadFile(charlie.execute(new GetFile().setFileId(
        message.getDocument().getFileId())));
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
