package com.st4s1k.charlie.service;

import com.jcraft.jsch.JSchException;
import com.st4s1k.charlie.data.model.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.jcraft.jsch.ChannelSftp.APPEND;

@Service
@RequiredArgsConstructor
public class CharlieService {

  private static final String USER_NAME_REGEX = "([A-Za-z0-9\\-.]+)";
  private static final String IP_ADDRESS_REGEX =
      "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
          "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
  private static final String HOST_NAME_REGEX =
      "([a-z_][a-z0-9_\\-]*[$]?)";
  private static final String HOST_REGEX = "("
      + IP_ADDRESS_REGEX + "|"
      + HOST_NAME_REGEX + ")";
  private static final String PORT_REGEX =
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


  private final Map<Predicate<String>, Consumer<ChatSession>> operations = new HashMap<>();

  {
    operations.put(msg -> msg.matches("^/start$"), this::showStartMessage);
    operations.put(msg -> msg.matches("^/help$"), this::showHelpMessage);
    operations.put(msg -> msg.matches("^/ci\\s+.+$"), this::parseConnectionInfo);
    operations.put(msg -> msg.matches("^/password\\s+.+$"), this::setPassword);
    operations.put(msg -> msg.matches("^/keygen$"), this::keyGen);
    operations.put(msg -> msg.matches("^/sudo\\s+.+$"), this::executeSudoCommand);
    operations.put(msg -> msg.matches("^/keyauth$"), this::switchToKeyAuthMode);
    operations.put(msg -> msg.matches("^/cd\\s+.+$"), this::cd);
    operations.put(msg -> msg.matches("^/pwd$"), this::pwd);
    operations.put(msg -> msg.matches("^/download\\s+.+$"), this::downloadAndSendDocumentToChat);
    operations.put(msg -> msg.matches("^/reset$"), this::reset);
  }

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

  public void parse(final ChatSession chatSession) {
    final var receivedText = getReceivedText(chatSession);
    if (receivedText.startsWith("/")) {
      parseCommand(chatSession);
    } else {
      executeCommand(chatSession);
    }
  }

  private void showStartMessage(final ChatSession chatSession) {
    final var name = chatSession.getId().getUser().getFirstName();
    chatSession.addResponse("Hello, " + name + ", I'm Charlie!\n\n"
        + "A telegram bot SSH client.\n"
        + "Type /help, to get a list of available commands.\n\n"
        + "To connect to a server using password:\n"
        + "1. /ci <user>@<host>:<port>\n"
        + "2. /password <password>\n\n"
        + "To connect to a server using RSA key pair:\n"
        + "1. /ci <user>@<host>:<port>\n"
        + "2. /keygen\n"
        + "3. append obtained public key file content to authorized_keys\n"
        + " (ex: cat id_rsa_charlie.pub >> ~/.ssh/authorized_keys)\n\n"
        + "To execute a remote command, type anything without a leading forward slash \"/\""
    );
  }

  private void showHelpMessage(final ChatSession chatSession) {
    chatSession.addResponse("/help - display current message"
        + "\n\n"
        + "/ci <user>@<host>:<port> - set connection info"
        + "\n\n"
        + "/password <password> - remote user password"
        + "\n\n"
        + "/keygen - generate RSA key pair and get public key"
        + "\n\n"
        + "/sudo <command> - execute command as sudo (requires password set)"
        + "\n\n"
        + "/keyauth - generate RSA key pair and automatically set public key"
        + " on remote SSH server appending it to ~/.ssh/authorized_keys file"
        + "\n\n"
        + "/cd <directory> - regular \"cd\" command doesn't work,"
        + " because this SSH client runs a single command in a single session,"
        + " and the information about current directory is stored in a variable"
        + " associated with a specific telegram chat-user pair,"
        + " so this is a workaround"
        + "\n\n"
        + "/pwd - shows the value of the current directory variable"
        + "\n\n"
        + "/download <file_path> - download file from remote server into chat"
        + "\n\n"
        + "/reset - reset connection info"
        + "\n\n"
        + "<command> - execute command on remote SSH server"
    );
  }

  private void downloadAndSendDocumentToChat(final ChatSession chatSession) {
    chatSession.runSftp(sftp -> {
      final var remoteFilePath = getReceivedText(chatSession)
          .replaceFirst("^/download\\s+", "");
      sftp.cd(chatSession.getCurrentDir() + "/");
      final var fileName = remoteFilePath.substring(remoteFilePath.lastIndexOf('/') + 1);
      final var inputStream = sftp.get(remoteFilePath);
      sendDocument(fileName, inputStream, fileName, chatSession);
    });
  }

  private void executeCommand(
      final String command,
      final ChatSession chatSession) {
    chatSession.sendCommand("cd " + chatSession.getCurrentDir()
        + " && " + command);
  }

  private void executeCommand(final ChatSession chatSession) {
    final var command = getReceivedText(chatSession);
    executeCommand(command, chatSession);
  }

  private void executeSudoCommand(
      final String command,
      final ChatSession chatSession) {
    chatSession.sendSudoCommand("cd " + chatSession.getCurrentDir()
        + " && " + command);
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
        .ifPresentOrElse(e -> e.getValue().accept(chatSession),
            () -> chatSession.addResponse("Unknown command ..."));
  }

  private String getReceivedText(final ChatSession chatSession) {
    return Optional.of(chatSession)
        .map(ChatSession::getReceivedMessage)
        .filter(Message::hasText)
        .map(Message::getText).orElse("");
  }

  private void parseConnectionInfo(final ChatSession chatSession) {
    final var hostInfo = getReceivedText(chatSession)
        .replaceFirst("^/ci\\s+", "");
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

  private void sendDocument(
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

  private void pwd(final ChatSession chatSession) {
    chatSession.addResponse(chatSession.getCurrentDir());
  }

  private void cd(final ChatSession chatSession) {
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

  private void reset(final ChatSession chatSession) {
    chatSession.reset();
    chatSession.addResponse("[User info cleared]");
  }
}
