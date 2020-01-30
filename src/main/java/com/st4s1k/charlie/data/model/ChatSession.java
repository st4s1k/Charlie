package com.st4s1k.charlie.data.model;

import com.jcraft.jsch.*;
import com.st4s1k.charlie.service.CharlieTelegramBot;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.jcraft.jsch.KeyPair.RSA;
import static com.st4s1k.charlie.service.CharlieService.createFile;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.getProperty;
import static java.util.Objects.nonNull;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatSession {

  private static final int TIMEOUT = 0;

  @EqualsAndHashCode.Include
  private final ChatSessionId id;
  private final String dotSsh;
  private final CharlieTelegramBot charlie;
  private final JSch jsch;
  private final Map<Integer, Task> tasks;

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
    this.tasks = new HashMap<>();
  }

  public Long getChatId() {
    return id.getChat().getId();
  }

  public void killAllTasks() {
    tasks.keySet().forEach(this::killTask);
  }

  public void killTask(final int id) {
    if (tasks.containsKey(id)) {
      tasks.get(id).stop();
    } else {
      sendResponse("Task with given id does not exist: " + id);
    }
  }

  private void runSession(final ThrowingConsumer<Session> operation) {
    Session session = null;
    try {
      session = jsch.getSession(userName, hostName, port);
      if (nonNull(password)) {
        session.setPassword(password);
      }
      session.connect();
      operation.accept(session);
    } catch (final Exception e) {
      e.printStackTrace();
      sendResponse(e.getMessage());
    } finally {
      if (nonNull(session)) {
        session.disconnect();
      }
    }
  }

  public void sendResponse(final String response) {
    if (nonNull(response) && !response.isBlank()) {
      final var sendMessageRequest = new SendMessage()
          .setChatId(getChatId())
          .setText(response);
      try {
        charlie.execute(sendMessageRequest);
      } catch (final Exception e) {
        e.printStackTrace();
        sendResponse(e.getMessage());
      }
    }
  }

  public void sendDocument(
      final String documentName,
      final InputStream inputStream,
      final String caption) {
    final var sendDocumentRequest = new SendDocument()
        .setChatId(getChatId())
        .setDocument(documentName, inputStream)
        .setCaption(caption);
    try {
      charlie.execute(sendDocumentRequest);
    } catch (final Exception e) {
      e.printStackTrace();
      sendResponse(e.getMessage());
    }
  }

  private void processCommandOutput(
      final InputStream commandOutput,
      final Task task) {
    final var outputBuffer = new StringBuilder();

    int readByte;
    long start = currentTimeMillis();

    while (!((readByte = readByte(commandOutput, task)) == -1
        || task.isCancelled())) {
      outputBuffer.append((char) readByte);
      if ((currentTimeMillis() - start) > 5000 && readByte == '\n') {
        start = currentTimeMillis();
        sendResponse(outputBuffer.toString());
        outputBuffer.delete(0, outputBuffer.length());
      }
    }

    if (outputBuffer.length() > 0) {
      sendResponse(outputBuffer.toString());
    }
  }

  private int readByte(
      final InputStream commandOutput,
      final Task task) {
    final var readFuture = getAsync(commandOutput::read);
    while (!(readFuture.isDone() || task.isCancelled())) ;
    return readFuture.getNow(-1);
  }

  private int getNewTaskId() {
    int taskId = tasks.size() + 1;
    for (int i = 0; i < tasks.size() + 1; i++) {
      if (!tasks.containsKey(i)) {
        taskId = i;
        break;
      }
    }
    return taskId;
  }

  private void executeTaskAsync(
      final String taskName,
      final ThrowingConsumer<Task> operation) {
    final var taskId = getNewTaskId();
    tasks.put(taskId, new Task(taskId, taskName, task ->
        CompletableFuture.runAsync(() -> {
          sendResponse("Task with id: " + taskId + " [started]");
          try {
            operation.accept(task);
          } catch (final Exception e) {
            e.printStackTrace();
            sendResponse(e.getMessage());
          }
        }).thenRunAsync(() -> {
          tasks.remove(taskId);
          sendResponse("Task with id: " + taskId + " [stopped]");
        })));
  }

  private <T> CompletableFuture<T> getAsync(final ThrowingSupplier<T> operation) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return operation.get();
      } catch (final Exception e) {
        e.printStackTrace();
        sendResponse(e.getMessage());
      }
      return null;
    });
  }

  public void sendCommand(final String command) {
    executeTaskAsync(command, task -> runSession(session -> {
      ChannelExec exec = null;
      try {
        exec = (ChannelExec) session.openChannel("exec");
        exec.setCommand(command);
        final var commandOutput = exec.getInputStream();
        exec.connect(TIMEOUT);
        processCommandOutput(commandOutput, task);
      } finally {
        if (nonNull(exec)) {
          exec.disconnect();
        }
      }
    }));
  }

  public void sendSudoCommand(final String command) {
    executeTaskAsync(command, task -> runSession(session -> {
      ChannelExec exec = null;
      try {
        exec = (ChannelExec) session.openChannel("exec");
        exec.setCommand("sudo -S -p '' sh -c \"" + command + "\"");
        final var commandOutput = exec.getInputStream();
        final var outputStream = exec.getOutputStream();
        exec.connect(TIMEOUT);
        outputStream.write((password + "\n").getBytes());
        outputStream.flush();
        processCommandOutput(commandOutput, task);
      } finally {
        if (nonNull(exec)) {
          exec.disconnect();
        }
      }
    }));
  }

  public void runSftp(ThrowingConsumer<ChannelSftp> sftpRunner) {
    runSession(session -> {
      ChannelSftp sftp = null;
      try {
        sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();
        sftpRunner.accept(sftp);
      } finally {
        if (nonNull(sftp)) {
          sftp.exit();
        }
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

