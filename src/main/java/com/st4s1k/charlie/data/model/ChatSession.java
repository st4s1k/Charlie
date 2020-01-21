package com.st4s1k.charlie.data.model;

import com.jcraft.jsch.*;
import com.st4s1k.charlie.service.CharlieTelegramBot;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.Message;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;

import static com.jcraft.jsch.KeyPair.RSA;
import static com.st4s1k.charlie.service.CharlieService.createFile;
import static java.util.Objects.nonNull;
import static lombok.AccessLevel.NONE;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatSession {

  private static final int TIMEOUT = 0;

  @EqualsAndHashCode.Include
  private final ChatSessionId id;
  private final String dotSsh;
  private final CharlieTelegramBot charlie;
  private final JSch jsch;

  @Getter(NONE)
  private StringBuilder responseBuffer;
  private Message receivedMessage;
  private String currentDir;
  private String password;
  private String publicKeyPath;
  private String userName;
  private String hostName;
  private int port;

  public ChatSession(
      final ChatSessionId id,
      final String dotSsh,
      final CharlieTelegramBot charlie,
      final JSch jsch) {
    this.id = id;
    this.dotSsh = dotSsh;
    this.charlie = charlie;
    this.jsch = jsch;
    this.currentDir = "~";
    this.responseBuffer = new StringBuilder();
  }

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

  public boolean responseExists() {
    return responseBuffer.length() > 0;
  }

  private void runSession(ThrowingConsumer<Session> operation) {
    Session session = null;
    try {
      session = jsch.getSession(userName, hostName, port);
      if (nonNull(password)) {
        session.setPassword(password);
      }
      session.connect();
      operation.accept(session);
    } catch (Exception e) {
      e.printStackTrace();
      addResponse(e.getMessage());
    } finally {
      Optional.ofNullable(session)
          .ifPresent(Session::disconnect);
    }
  }

  public void sendCommand(String command) {
    runSession(session -> {
      ChannelExec exec = null;
      try {
        exec = (ChannelExec) session.openChannel("exec");
        final var outputBuffer = new StringBuilder();
        final var commandOutput = exec.getInputStream();
        exec.setCommand(command);
        exec.connect(TIMEOUT);
        var readByte = commandOutput.read();
        while (readByte != 0xffffffff) {
          outputBuffer.append((char) readByte);
          readByte = commandOutput.read();
        }
        addResponse(outputBuffer.toString());
      } finally {
        Optional.ofNullable(exec)
            .ifPresent(Channel::disconnect);
      }
    });
  }

  public void runSftp(ThrowingConsumer<ChannelSftp> sftpRunner) {
    runSession(session -> {
      ChannelSftp sftp = null;
      try {
        sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();
        sftpRunner.accept(sftp);
      } finally {
        Optional.ofNullable(sftp)
            .ifPresent(ChannelSftp::exit);
      }
    });
  }

  public void genKeyPair()
      throws JSchException, IOException {
    final var file = dotSsh + "/id_rsa_" + userName + "_" + hostName;
    final var keyPair = KeyPair.genKeyPair(jsch, RSA);

    createFile(file);
    keyPair.writePrivateKey(file);
    publicKeyPath = file + ".pub";

    final var charlieUserName = System.getProperty("user.name");
    final var charlieHostName = InetAddress.getLocalHost().getHostName();
    keyPair.writePublicKey(publicKeyPath, charlieUserName + "@" + charlieHostName);
    keyPair.dispose();

    jsch.addIdentity(file);
  }

  @PreDestroy
  public void reset() {
    currentDir = "~";
    receivedMessage = null;
    userName = null;
    hostName = null;
    password = null;
    port = 0;
  }
}

