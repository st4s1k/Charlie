package com.st4s1k.charlie.data.model;

import com.jcraft.jsch.*;
import com.st4s1k.charlie.service.CharlieTelegramBot;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.jcraft.jsch.KeyPair.RSA;
import static com.st4s1k.charlie.service.CharlieService.createFile;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.getProperty;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatSession {

  private static final int TIMEOUT = 0;

  @EqualsAndHashCode.Include
  private final ChatSessionId id;
  private final String dotSsh;
  private final CharlieTelegramBot charlie;
  private final JSch jsch;
  private final Set<CompletableFuture<Void>> tasks;

  private Update update;
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
    this.tasks = new HashSet<>();
  }

  public Long getChatId() {
    return id.getChat().getId();
  }

  public void killAllTasks() {
    tasks.forEach(cf -> cf.complete(null));
  }

  private void runSession(final ThrowingConsumer<Session> operation) {
    Session session = null;
    try {
      session = jsch.getSession(userName, hostName, port);
      Optional.ofNullable(password)
          .ifPresent(session::setPassword);
      session.connect();
      operation.accept(session);
    } catch (Exception e) {
      e.printStackTrace();
      sendResponse(e.getMessage());
    } finally {
      Optional.ofNullable(session)
          .ifPresent(Session::disconnect);
    }
  }

  public void sendResponse(final String response) {
    final var sendMessageRequest = new SendMessage()
        .setChatId(getChatId())
        .setText(response);
    try {
      charlie.execute(sendMessageRequest);
    } catch (Exception e) {
      e.printStackTrace();
      sendResponse(e.getMessage());
    }
  }

  private void executeAsync(final ThrowingRunnable operation) {
    tasks.add(CompletableFuture.runAsync(() -> {
      try {
        operation.run();
      } catch (Exception e) {
        e.printStackTrace();
        sendResponse(e.getMessage());
      }
    }));
  }

  private void processCommandOutput(final InputStream commandOutput)
      throws IOException {
    final var outputBuffer = new StringBuilder();
    var readByte = commandOutput.read();
    var start = currentTimeMillis();
    while (readByte != -1) {
      outputBuffer.append((char) readByte);
      if ((currentTimeMillis() - start) > 5000 && readByte == '\n') {
        start = currentTimeMillis();
        sendResponse(outputBuffer.toString());
        outputBuffer.delete(0, outputBuffer.length());
      }
      readByte = commandOutput.read();
    }
    if (outputBuffer.length() > 0) {
      sendResponse(outputBuffer.toString());
    }
  }

  public void sendCommand(String command) {
    executeAsync(() -> runSession(session -> {
      ChannelExec exec = null;
      try {
        exec = (ChannelExec) session.openChannel("exec");
        exec.setCommand(command);
        final var commandOutput = exec.getInputStream();
        exec.connect(TIMEOUT);
        processCommandOutput(commandOutput);
      } finally {
        Optional.ofNullable(exec)
            .ifPresent(Channel::disconnect);
      }
    }));
  }

  public void sendSudoCommand(String command) {
    runSession(session -> {
      ChannelExec exec = null;
      try {
        exec = (ChannelExec) session.openChannel("exec");
        exec.setCommand("sudo -S -p '' sh -c \"" + command + "\"");
        final var commandOutput = exec.getInputStream();
        final var outputStream = exec.getOutputStream();
        exec.connect(TIMEOUT);
        outputStream.write((password + "\n").getBytes());
        outputStream.flush();
        processCommandOutput(commandOutput);
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
    final var charlieUserName = getProperty("user.name");
    final var charlieHostName = InetAddress.getLocalHost().getHostName();
    keyPair.writePublicKey(publicKeyPath, charlieUserName + "@" + charlieHostName);
    keyPair.dispose();
    jsch.addIdentity(file);
  }

  @PreDestroy
  public void reset() {
    currentDir = "~";
    update = null;
    userName = null;
    hostName = null;
    password = null;
    port = 0;
  }
}

