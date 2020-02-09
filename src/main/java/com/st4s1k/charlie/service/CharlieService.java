package com.st4s1k.charlie.service;

import com.jcraft.jsch.JSchException;
import com.st4s1k.charlie.data.model.ChatSession;
import com.st4s1k.charlie.data.model.Task;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.jcraft.jsch.ChannelSftp.APPEND;
import static java.lang.Integer.parseInt;

public class CharlieService {

  private static final String USER_NAME_REGEX = "" +
      "([A-Za-z0-9\\-.]+)";
  private static final String IP_ADDRESS_REGEX = "" +
      "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
      "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
  private static final String HOST_NAME_REGEX =
      "([a-z_][a-z0-9_\\-]*[$]?)";
  private static final String HOST_REGEX = "("
      + IP_ADDRESS_REGEX + "|"
      + HOST_NAME_REGEX + ")";
  private static final String PORT_REGEX = "" +
      "([0-9]{1,4}" +
      "|[1-5][0-9]{4}" +
      "|6[0-4][0-9]{3}" +
      "|65[0-4][0-9]{2}" +
      "|655[0-2][0-9]" +
      "|6553[0-5])";
  private static final String HOST_INFO_REGEX = "^"
      + USER_NAME_REGEX + "@"
      + HOST_REGEX + ":"
      + PORT_REGEX + "$";


  private final String startMessage;
  private final String helpMessage;
  private final String publicKeyFileName;
  private final String keyGenHint;
  private final Map<Predicate<String>, Consumer<ChatSession>> operations;

  public CharlieService(
      final String startMessage,
      final String helpMessage,
      final String publicKeyFileName,
      final String keyGenHint,
      final Map<Predicate<String>, Consumer<ChatSession>> operations
  ) {
    this.startMessage = startMessage;
    this.helpMessage = helpMessage;
    this.publicKeyFileName = publicKeyFileName;
    this.keyGenHint = keyGenHint;
    this.operations = operations;

    this.operations.put(msg -> msg.matches("^/start\\s*$"),
        this::showStartMessage);
    this.operations.put(msg -> msg.matches("^/help\\s*$"),
        this::showHelpMessage);
    this.operations.put(msg -> msg.matches("^/conn\\s+.+$"),
        this::parseConnectionInfo);
    this.operations.put(msg -> msg.matches("^/conn\\s*$"),
        this::showConnectionInfo);
    this.operations.put(msg -> msg.matches("^/password\\s+.+$"),
        this::setPassword);
    this.operations.put(msg -> msg.matches("^/password\\s*$"),
        this::showPassword);
    this.operations.put(msg -> msg.matches("^/keygen\\s*$"),
        this::keyGen);
    this.operations.put(msg -> msg.matches("^/sudo\\s+.+$"),
        this::executeSudoCommand);
    this.operations.put(msg -> msg.matches("^/keyauth\\s*$"),
        this::switchToKeyAuthMode);
    this.operations.put(msg -> msg.matches("^/cd\\s+.+$"),
        this::cd);
    this.operations.put(msg -> msg.matches("^/pwd\\s*$"),
        this::pwd);
    this.operations.put(msg -> msg.matches("^/home\\s*$"),
        this::home);
    this.operations.put(msg -> msg.matches("^/download\\s+.+$"),
        this::downloadAndSendDocumentToChat);
    this.operations.put(msg -> msg.matches("^/reset\\s*$"),
        this::reset);
    this.operations.put(msg -> msg.matches("^/tasks\\s*$"),
        this::showRunningTasks);
    this.operations.put(msg -> msg.matches("^/stopall\\s*$"),
        this::stopAllTasks);
    this.operations.put(msg -> msg.matches("^/stop\\s+\\d+$"),
        this::stopTask);
    this.operations.put(msg -> msg.matches("^/killall\\s*$"),
        this::killAllTasks);
    this.operations.put(msg -> msg.matches("^/kill\\s+\\d+$"),
        this::killTask);
  }

  private void showHelpMessage(final ChatSession chatSession) {
    chatSession.sendResponse(helpMessage);
  }

  private void showStartMessage(final ChatSession chatSession) {
    final var name = chatSession.getId().getUser().getFirstName();
    final var message = startMessage.replace(":userName", name);
    chatSession.sendResponse(message);
  }

  public static void createFile(final String filePath) {
    final var file = new File(filePath);
    createFileDirs(filePath);
    try {
      if (!file.exists() && !file.createNewFile()) {
        throw new FileCreationFailedException("File already exists: " + filePath);
      }
    } catch (final IOException e) {
      throw new FileCreationFailedException(e);
    }
  }

  public static void createFileDirs(final String filePath) {
    final var fileDir = new File(filePath.substring(0, filePath.lastIndexOf('/')));
    if (!fileDir.exists() && fileDir.mkdirs()) {
      throw new DirectoryCreationFailedException("Failed to create directory: " + filePath);
    }
  }

  public void parse(final ChatSession chatSession) {
    final var receivedText = getReceivedText(chatSession);
    if (receivedText.startsWith("/")) {
      parseCommand(chatSession);
    } else {
      executeCommand(chatSession);
    }
  }

  private void downloadAndSendDocumentToChat(final ChatSession chatSession) {
    final var remoteFilePath = getReceivedText(chatSession)
        .replaceFirst("^/download\\s+", "");

    chatSession.runSftp("[/download] " + remoteFilePath,
        sftp -> {
          if (chatSession.getCurrentDir().isPresent()) {
            sftp.cd(chatSession.getCurrentDir().get() + "/");
          }
          final var lastIndexOfForwardSlash = remoteFilePath.lastIndexOf('/');
          final var fileName = remoteFilePath.substring(lastIndexOfForwardSlash + 1);
          final var inputStream = sftp.get(remoteFilePath);
          chatSession.sendDocument(fileName, inputStream, fileName);
        });
  }

  private void executeCommand(
      final String command,
      final ChatSession chatSession,
      final Function<String, Task> commandExecutor
  ) {
    chatSession.getCurrentDir().ifPresentOrElse(
        currentDir -> commandExecutor
            .apply("cd \"" + currentDir + "\" && " + command),
        () -> commandExecutor.apply(command));
  }

  private void executeCommand(
      final String command,
      final ChatSession chatSession
  ) {
    executeCommand(command, chatSession, chatSession::executeCommand);
  }

  private void executeCommand(final ChatSession chatSession) {
    final var command = getReceivedText(chatSession);
    executeCommand(command, chatSession);
  }

  private void executeSudoCommand(
      final String command,
      final ChatSession chatSession
  ) {
    executeCommand(command, chatSession, chatSession::executeSudoCommand);
  }

  private void executeSudoCommand(final ChatSession chatSession) {
    final var command = getReceivedText(chatSession)
        .replaceFirst("^/sudo\\s+", "");
    executeSudoCommand(command, chatSession);
  }

  private void parseCommand(final ChatSession chatSession) {
    operations.entrySet().stream()
        .filter(e -> e.getKey().test(getReceivedText(chatSession)))
        .findFirst()
        .ifPresentOrElse(
            e -> e.getValue().accept(chatSession),
            () -> chatSession.sendMonoResponse("[Unknown command]")
        );
  }

  private String getReceivedText(final ChatSession chatSession) {
    return Optional.of(chatSession)
        .map(ChatSession::getUpdate)
        .filter(Update::hasMessage)
        .map(Update::getMessage)
        .filter(Message::hasText)
        .map(Message::getText).orElse("");
  }

  private void parseConnectionInfo(final ChatSession chatSession) {
    final var hostInfo = getReceivedText(chatSession)
        .replaceFirst("^/conn\\s+", "");
    if (hostInfo.matches(HOST_INFO_REGEX)) {
      final var userName = hostInfo.substring(0,
          hostInfo.indexOf('@'));
      final var hostName = hostInfo.substring(
          hostInfo.indexOf('@') + 1,
          hostInfo.indexOf(':'));
      final var port = hostInfo.substring(hostInfo.indexOf(':') + 1);
      chatSession.setUserName(userName);
      chatSession.setHostName(hostName);
      chatSession.setPort(parseInt(port));
      chatSession.sendMonoResponse("[Connection info is set]");
    } else {
      chatSession.sendMonoResponse("[Invalid connection info format]");
    }
  }

  private void cd(final ChatSession chatSession) {
    final var dir = getReceivedText(chatSession)
        .replaceFirst("^/cd\\s+", "");

    chatSession.runSftp("[/cd] " + dir,
        channelSftp -> {
          if (chatSession.getCurrentDir().isPresent()) {
            channelSftp.cd(chatSession.getCurrentDir().get());
          }
          channelSftp.cd(dir);
          chatSession.setCurrentDir(channelSftp.pwd());
        })
        .getFuture()
        .thenRun(() -> executeCommand("ls", chatSession));
  }

  private void pwd(final ChatSession chatSession) {
    chatSession.getCurrentDir()
        .ifPresentOrElse(
            chatSession::sendResponse,
            () -> chatSession.runSftp("[/cd] .",
                channelSftp -> {
                  channelSftp.cd(".");
                  chatSession.setCurrentDir(channelSftp.pwd());
                  chatSession.getCurrentDir()
                      .ifPresent(chatSession::sendResponse);
                })
        );
  }

  private void home(final ChatSession chatSession) {
    chatSession.runSftp("[/home]", channelSftp -> {
      channelSftp.cd(channelSftp.getHome());
      chatSession.setCurrentDir(channelSftp.pwd());
    }).getFuture().thenRun(() ->
        executeCommand("ls", chatSession));
  }

  private void setPassword(final ChatSession chatSession) {
    final var password = getReceivedText(chatSession)
        .replaceFirst("^/password\\s+", "");
    chatSession.setPassword(password);
    chatSession.sendMonoResponse("[Password set]");
  }

  private void switchToKeyAuthMode(final ChatSession chatSession) {
    chatSession.runSftp("[/keyauth]", sftp -> {
      chatSession.genKeyPair();
      final var publicKeyPath = chatSession.getPublicKeyPath();
      final var fileInputStream = new FileInputStream(publicKeyPath);
      final var home = sftp.getHome();
      final var authorizedKeysPath = home + "/.ssh/authorized_keys";
      sftp.put(fileInputStream, authorizedKeysPath, APPEND);
    });
  }

  private void keyGen(final ChatSession chatSession) {
    try {
      chatSession.genKeyPair();
      final var publicKeyPath = chatSession.getPublicKeyPath();
      try (
          final var fileInputStream = new FileInputStream(publicKeyPath)
      ) {
        final var bufferedInputStream = new BufferedInputStream(fileInputStream);
        chatSession.sendDocument(
            publicKeyFileName,
            bufferedInputStream,
            keyGenHint);
      }
    } catch (final JSchException | IOException e) {
      throw new KeyGenFailedException(e);
    }
  }

  private void stopAllTasks(final ChatSession chatSession) {
    chatSession.stopAllTasks();
  }

  private void stopTask(final ChatSession chatSession) {
    final var taskId = getReceivedText(chatSession)
        .replaceFirst("^/stop\\s+", "");
    chatSession.stopTask(Integer.parseInt(taskId));
  }

  private void killAllTasks(final ChatSession chatSession) {
    chatSession.killAllTasks();
  }

  private void killTask(final ChatSession chatSession) {
    final var taskId = getReceivedText(chatSession)
        .replaceFirst("^/kill\\s+", "");
    chatSession.killTask(Integer.parseInt(taskId));
  }

  private void showRunningTasks(final ChatSession chatSession) {
    final var tasks = chatSession.getTasks();
    if (tasks.isEmpty()) {
      chatSession.sendMonoResponse("[No running tasks]");
    } else {
      final var runningTasksList = tasks.values().stream()
          .reduce(new StringBuilder(),
              (output, task) -> output
                  .append(output.length() > 1 ? "\n\n" : "")
                  .append("Task ID: ").append(task.getId()).append('\n')
                  .append("Command: ").append(task.getName()),
              StringBuilder::append).toString();
      chatSession.sendMonoResponse(runningTasksList);
    }
  }

  private void showConnectionInfo(final ChatSession chatSession) {
    chatSession.sendMonoResponse("" +
        "User name: " + chatSession.getUserName() + "\n" +
        "Host name: " + chatSession.getHostName() + "\n" +
        "     Port: " + chatSession.getPort());
  }

  private void showPassword(final ChatSession chatSession) {
    chatSession.sendMonoResponse(
        "Password: " + chatSession.getPassword()
    );
  }

  private void reset(final ChatSession chatSession) {
    chatSession.reset();
    chatSession.sendMonoResponse(
        "[Connection info cleared]"
    );
  }
}
