package com.st4s1k.charlie.data.model;

import com.jcraft.jsch.JSchException;
import com.pastdev.jsch.DefaultSessionFactory;
import com.pastdev.jsch.command.CommandRunner;
import com.pastdev.jsch.scp.ScpFile;
import com.pastdev.jsch.sftp.SftpRunner;
import lombok.*;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static java.util.Objects.isNull;
import static lombok.AccessLevel.NONE;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatSession {

  @Getter
  @RequiredArgsConstructor
  public static class ChatSessionId {


    private final Chat chat;

    private final User user;

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof ChatSessionId)) return false;
      final ChatSessionId that = (ChatSessionId) o;
      return Objects.equals(chat.getId(), that.chat.getId()) &&
          Objects.equals(user.getId(), that.user.getId());
    }

    @Override
    public int hashCode() {
      return Objects.hash(chat.getId(), user.getId());
    }


  }

  @EqualsAndHashCode.Include
  private final ChatSessionId id;
  private final ThrowingConsumer<SendDocument> sendDocument;
  private final ThrowingConsumer<SendMessage> sendMessage;

  private DefaultSessionFactory sessionFactory = new DefaultSessionFactory();
  private CommandRunner commandRunner = new CommandRunner(sessionFactory);
  private SftpRunner sftpRunner = new SftpRunner(sessionFactory);
  private ScpFile scpFile = new ScpFile(sessionFactory);
  @Getter(NONE)
  private StringBuilder responseBuffer = new StringBuilder();
  @Setter
  private String receivedMessage;
  private String currentDir;

  public Long getChatId() {
    return id.getChat().getId();
  }

  public void addResponse(final String response) {
    responseBuffer.append(response);
  }

  public String getResponse() {
    final var response = responseBuffer.toString();
    clearResponseBuffer();
    return response;
  }

  public void clearResponseBuffer() {
    responseBuffer.delete(0, responseBuffer.length());
  }

  public void pwd() {
    addResponse(getCurrentDir());
  }

  public void cd(final String dir) {

    if (currentDir != null) {
      executeCommand("cd " + dir + " && pwd");
      currentDir = getResponse().trim();
    } else {
      currentDir = dir;
    }

    executeCommand("ls");
  }

  @SneakyThrows
  public void downloadAndSendDocument(final String remoteFilePath) {
    sftpRunner.execute(sftp -> {
      final var fileName = remoteFilePath.substring(remoteFilePath.lastIndexOf('/'));
      final var inputStream = sftp.get(remoteFilePath);
      sendDocument(fileName, inputStream);
    });
  }

  public void executeCommand(final String command) {
    try {
      addResponse(commandRunner
          .execute(isNull(currentDir) ? command
              : "cd " + currentDir + " && " + command)
          .getStdout());
    } catch (JSchException | IOException e) {
      e.printStackTrace();
      addResponse(e.getMessage());
    }
  }

  private void parseConnectionInfo() {
    final var splitMsg = receivedMessage.split(" ");
    if (splitMsg.length == 3) {
      final var hostInfo = splitMsg[1];
      final var password = splitMsg[2];
      if (hostInfo.matches(".+@.+:.+")) {
        final var username = hostInfo.substring(0, hostInfo.indexOf('@'));
        final var hostname = hostInfo.substring(hostInfo.indexOf('@') + 1, hostInfo.indexOf(':'));
        final var port = Integer.parseInt(hostInfo.substring(hostInfo.indexOf(':') + 1));
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

        addResponse("[User info is set]");
      }
    }
  }

  @SneakyThrows
  private void parseCommand() {
    if (receivedMessage.startsWith("/ui ")) {
      parseConnectionInfo();
    } else if (receivedMessage.startsWith("/cd ")) {
      cd(receivedMessage.substring("/cd ".length()));
    } else if (receivedMessage.equals("/pwd")) {
      pwd();
    } else if (receivedMessage.startsWith("/download ")) {
      downloadAndSendDocument(receivedMessage.substring("/download ".length()));
    } else if (receivedMessage.startsWith("/disconnect")) {
      close();
    } else {
      addResponse("Unknown command ...");
    }
  }

  public void parse() {
    final var receivedMessage = getReceivedMessage();
    if (receivedMessage.startsWith("/")) {
      parseCommand();
    } else {
      executeCommand(receivedMessage);
    }
  }

  @SneakyThrows
  public void sendMessage() {
    final var sendMessageRequest = new SendMessage()
        .setChatId(getChatId())
        .setText(getResponse());
    sendMessage.accept(sendMessageRequest);
  }

  @SneakyThrows
  public void sendDocument(
      final String documentName,
      final InputStream inputStream) {
    final var sendDocumentRequest = new SendDocument()
        .setChatId(getChatId())
        .setDocument(documentName, inputStream)
        .setCaption(documentName);
    sendDocument.accept(sendDocumentRequest);
  }

  @SneakyThrows
  public void close() {
    getCommandRunner().close();
    sessionFactory = new DefaultSessionFactory();
    commandRunner = new CommandRunner(sessionFactory);
    sftpRunner = new SftpRunner(sessionFactory);
    scpFile = new ScpFile(sessionFactory);
    currentDir = null;
    addResponse("[User info cleared]");
  }
}
