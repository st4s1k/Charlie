package com.st4s1k.charlie.data.model;

import com.jcraft.jsch.*;
import com.st4s1k.charlie.service.CharlieTelegramBot;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.jcraft.jsch.KeyPair.RSA;
import static com.st4s1k.charlie.service.CharlieService.createFile;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.getProperty;
import static java.util.Objects.nonNull;
import static lombok.AccessLevel.NONE;
import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatSession {

  @EqualsAndHashCode.Include
  private final ChatSessionId id;
  private final String dotSsh;
  private final CharlieTelegramBot charlie;
  private final JSch jsch;
  private final Map<Integer, Task> tasks = new HashMap<>();

  private Update update;
  @Getter(NONE)
  private String currentDir;
  private String password;
  private String publicKeyPath;
  private String userName;
  private String hostName;
  private int port;

  private int connectionTimeout = 10_000;
  private long commandOutputTimeout = 5_000;

  public ChatSession(
      final ChatSessionId id,
      final String dotSsh,
      final CharlieTelegramBot charlie,
      final JSch jsch
  ) {
    this.id = id;
    this.dotSsh = dotSsh;
    this.charlie = charlie;
    this.jsch = jsch;
  }

  public Long getChatId() {
    return id.getChat().getId();
  }

  public void stopAllTasks() {
    tasks.keySet().forEach(this::stopTask);
  }

  public void stopTask(final int id) {
    if (tasks.containsKey(id)) {
      final var task = tasks.get(id);
      task.getFuture().whenComplete((r, t) ->
          sendMonoResponse("[Task stopped: " + id + "]"));
      task.stop();
    } else {
      sendResponse("Task with given id does not exist: " + id);
    }
  }

  public void killAllTasks() {
    tasks.keySet().forEach(this::killTask);
  }

  public void killTask(final int id) {
    if (tasks.containsKey(id)) {
      final var task = tasks.get(id);
      task.getFuture().whenComplete((r, t) ->
          sendMonoResponse("[Task killed: " + id + "]"));
      task.kill();
    } else {
      sendResponse("Task with given id does not exist: " + id);
    }
  }

  private <E extends Exception>
  void runSession(final ThrowingConsumer<Session, E> operation) {
    Session session = null;
    try {
      session = jsch.getSession(userName, hostName, port);
      if (nonNull(password)) {
        session.setPassword(password);
      }
      session.connect();
      operation.accept(session);
    } catch (final Exception e) {
      throw new SessionRunFailedException(e);
    } finally {
      if (nonNull(session)) {
        session.disconnect();
      }
    }
  }

  public void sendMonoResponse(final String response) {
    sendResponse("```\n" + response + "\n```", true);
  }

  public void sendResponse(final String response) {
    sendResponse(response, false);
  }

  public void sendResponse(
      final String response,
      final boolean markdown
  ) {
    if (nonNull(response) && !response.isBlank()) {
      final var sendMessageRequest = new SendMessage()
          .setChatId(getChatId())
          .setText(response)
          .enableMarkdown(markdown);
      try {
        charlie.execute(sendMessageRequest);
      } catch (final TelegramApiException e) {
        throw new ResponseSendFailedException(e);
      }
    }
  }

  public void sendDocument(
      final String documentName,
      final InputStream inputStream,
      final String caption
  ) {
    final var sendDocumentRequest = new SendDocument()
        .setChatId(getChatId())
        .setDocument(documentName, inputStream)
        .setCaption(caption);
    try {
      charlie.execute(sendDocumentRequest);
    } catch (final TelegramApiException e) {
      throw new DocumentSendFailedException(e);
    }
  }

  private void processCommandOutput(
      final InputStream commandOutput,
      final Task task
  ) throws InterruptedException, IOException {
    final var outputBuffer = new ByteArrayOutputStream();

    int readByte = readByte(commandOutput, task);
    long start = currentTimeMillis();

    while (!(readByte == -1 || task.isCancelled())) {
      outputBuffer.write(readByte);
      if ((currentTimeMillis() - start) > commandOutputTimeout && readByte == '\n') {
        start = currentTimeMillis();
        outputBuffer.flush();
        sendResponse("[" + task.getId() + "]\n"
            + outputBuffer.toString());
        outputBuffer.reset();
      }
      readByte = readByte(commandOutput, task);
    }

    if (outputBuffer.size() > 0) {
      outputBuffer.flush();
      sendResponse("[" + task.getId() + "]\n"
          + outputBuffer.toString());
    }
  }

  private int readByte(
      final InputStream commandOutput,
      final Task task
  ) throws InterruptedException {
    final var readByteFuture = getAsync(commandOutput::read);
    readByteFuture.thenRun(task::wake);
    task.sleepUntil(readByteFuture::isDone);
    return readByteFuture.getNow(-1);
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

  private <T, E extends Exception>
  CompletableFuture<T> getAsync(final ThrowingSupplier<T, E> operation) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return operation.get();
      } catch (final Exception e) {
        throw new AsyncSupplyFailedException(e);
      }
    });
  }

  private <E extends Exception> Task executeTaskAsync(
      final String taskName,
      final ThrowingConsumer<Task, E> operation
  ) {
    final var taskId = getNewTaskId();
    final var task = new Task(taskId, taskName,
        thisTask -> {
          try {
            operation.accept(thisTask);
          } catch (final Exception e) {
            Optional.ofNullable(getRootCause(e))
                .map(Throwable::getMessage)
                .ifPresent(this::sendResponse);
            throw new TaskExecutionFailedException(e);
          }
        });

    task.getFuture()
        .whenComplete((r, t) -> tasks.remove(taskId));

    tasks.put(taskId, task);

    sendResponse("[started task: " + taskId + "] " + taskName);

    return task;
  }

  public Task executeCommand(final String command) {
    return executeTaskAsync(command, task ->
        runSession(session -> {
          ChannelExec exec = null;
          try {
            exec = (ChannelExec) session.openChannel("exec");
            exec.setCommand(command);

            final var commandOutput = exec.getInputStream();

            exec.connect(connectionTimeout);
            processCommandOutput(commandOutput, task);
          } finally {
            if (nonNull(exec)) {
              exec.disconnect();
            }
          }
        }));
  }

  public Task executeSudoCommand(final String command) {
    return executeTaskAsync(command, task ->
        runSession(session -> {
          ChannelExec exec = null;
          try {
            exec = (ChannelExec) session.openChannel("exec");
            exec.setCommand("sudo -S -p '' sh -c \"" + command + "\"");

            final var commandOutput = exec.getInputStream();
            final var outputStream = exec.getOutputStream();

            exec.connect(connectionTimeout);
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

  public <E extends Exception> Task runSftp(
      final String taskName,
      final ThrowingConsumer<ChannelSftp, E> sftpRunner
  ) {
    return executeTaskAsync(taskName, task ->
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
        }));
  }

  public void genKeyPair() throws JSchException, IOException {
    final var file = dotSsh + "/id_rsa_" + userName + "_" + hostName;
    final var keyPair = KeyPair.genKeyPair(jsch, RSA);

    publicKeyPath = file + ".pub";

    createFile(file);
    keyPair.writePrivateKey(file);
    jsch.addIdentity(file);

    final var charlieUserName = getProperty("user.name");
    final var charlieHostName = InetAddress.getLocalHost().getHostName();

    keyPair.writePublicKey(publicKeyPath, charlieUserName + "@" + charlieHostName);
    keyPair.dispose();
  }

  @PreDestroy
  public void reset() {
    currentDir = null;
    update = null;
    userName = null;
    hostName = null;
    password = null;
    port = 0;
  }

  public Optional<String> getCurrentDir() {
    return Optional.ofNullable(currentDir);
  }
}
