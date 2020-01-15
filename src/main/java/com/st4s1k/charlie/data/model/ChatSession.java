package com.st4s1k.charlie.data.model;

import com.jcraft.jsch.*;
import com.st4s1k.charlie.service.CharlieTelegramBot;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.Message;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

import static com.jcraft.jsch.KeyPair.RSA;
import static com.st4s1k.charlie.service.CharlieService.HOME;
import static lombok.AccessLevel.NONE;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatSession {

  private final JSch jsch;
  @EqualsAndHashCode.Include
  private final ChatSessionId id;
  private final CharlieTelegramBot charlie;

  private Session session;
  @Getter(NONE)
  private StringBuilder responseBuffer;
  private Message receivedMessage;
  private String currentDir;
  private String publicKeyPath;
  private String dotSsh;

  public ChatSession(
      final JSch jsch,
      final ChatSessionId id,
      final CharlieTelegramBot charlie) {
    this.id = id;
    this.jsch = jsch;
    this.charlie = charlie;
    this.responseBuffer = new StringBuilder();
    this.dotSsh = System.getProperty("jsch.dotSsh");
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
    session.connect();
    final var outputBuffer = new StringBuilder();
    final var exec = (ChannelExec) session.openChannel("exec");

    exec.setCommand(command);
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

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private void createFile(final String filePath) throws IOException {
    final var file = new File(filePath);
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

  private void genKeyPair()
      throws JSchException, IOException {
    final var userName = session.getUserName();
    final var hostName = session.getHost();
    final var file = dotSsh + "/id_rsa_" + userName + "_" + hostName;
    final var keyPair = KeyPair.genKeyPair(jsch, RSA);

    createFile(file);
    keyPair.writePrivateKey(file);
    this.publicKeyPath = file + ".pub";
    keyPair.writePublicKey(publicKeyPath, userName + "@" + hostName);
    keyPair.dispose();
  }

  public void setSession(
      final String userName,
      final String hostName,
      final int port) throws JSchException, IOException {
    session = jsch.getSession(userName, hostName, port);
    session.setConfig("StrictHostKeyChecking", "no");
    session.setConfig("PreferredAuthentications", "publickey,password");
    genKeyPair();
  }

  @PreDestroy
  public void reset() {
    currentDir = HOME;
    receivedMessage = null;
  }
}

