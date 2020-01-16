package com.st4s1k.charlie.data.model;

import com.jcraft.jsch.*;
import com.st4s1k.charlie.service.CharlieTelegramBot;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.Message;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.function.Consumer;

import static com.jcraft.jsch.KeyPair.RSA;
import static com.st4s1k.charlie.service.CharlieService.HOME;
import static com.st4s1k.charlie.service.CharlieService.createFile;
import static lombok.AccessLevel.NONE;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatSession {

  @EqualsAndHashCode.Include
  private final ChatSessionId id;
  private final String dotSsh;
  private final CharlieTelegramBot charlie;
  private final JSch jsch;

  private Session session;
  @Getter(NONE)
  private StringBuilder responseBuffer;
  private Message receivedMessage;
  private String currentDir;
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

  public void runSftp(Consumer<ChannelSftp> sftpRunner) throws JSchException {
    final var sftp = (ChannelSftp) session.openChannel("sftp");
    sftp.connect();
    sftpRunner.accept(sftp);
    sftp.exit();
  }

  public String sendCommand(String command) throws JSchException, IOException {
    session = jsch.getSession(userName, hostName, port);
    final var outputBuffer = new StringBuilder();
    final var exec = session.openChannel("exec");

    ((ChannelExec) exec).setCommand(command);
    exec.connect();

    final var commandOutput = exec.getInputStream();
    {
      var readByte = commandOutput.read();
      while (readByte != 0xffffffff) {
        outputBuffer.append((char) readByte);
        readByte = commandOutput.read();
      }
    }

    exec.disconnect();
    session.disconnect();
    return outputBuffer.toString();
  }

  public void genKeyPair()
      throws JSchException, IOException {
    final var file = dotSsh + "/id_rsa_" + userName + "_" + hostName;
    final var keyPair = KeyPair.genKeyPair(jsch, RSA);

    createFile(file);
    keyPair.writePrivateKey(file);
    publicKeyPath = file + ".pub";
    keyPair.writePublicKey(publicKeyPath, userName + "@" + hostName);
    keyPair.dispose();
  }

  @PreDestroy
  public void reset() {
    currentDir = HOME;
    receivedMessage = null;
    userName = null;
    hostName = null;
    port = 0;
  }
}

