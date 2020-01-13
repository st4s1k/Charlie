package com.st4s1k.charlie.service;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.pastdev.jsch.DefaultSessionFactory;
import com.pastdev.jsch.command.CommandRunner;
import com.pastdev.jsch.sftp.SftpRunner;
import com.st4s1k.charlie.data.model.ChatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class CharlieService {

  public static final String HOME = "~";

  private final CharlieTelegramBot charlie;

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
    final var receivedMessage = chatSession.getReceivedMessage();
    if (receivedMessage.startsWith("/")) {
      parseCommand(chatSession);
    } else {
      executeCommand(receivedMessage, chatSession);
    }
  }

  public void parseCommand(final ChatSession chatSession) {
    final var receivedMessage = chatSession.getReceivedMessage();

    if (receivedMessage.matches("/rsa\\s+.+")) {
      final var idRsa = receivedMessage.split("\\s+")[1];
      setIdentity(idRsa, chatSession);
    } else if (receivedMessage.matches("/ui\\s+.+")) {
      final var hostInfo = receivedMessage.split("\\s+")[1];
      parseConnectionInfo(hostInfo, chatSession);
    } else if (receivedMessage.matches("/cd\\s+.+")) {
      final var dir = receivedMessage.split("\\s+")[1];
      cd(dir, chatSession);
    } else if (receivedMessage.equals("/pwd")) {
      pwd(chatSession);
    } else if (receivedMessage.matches("/download\\s+.+")) {
      final var remoteFilePath = receivedMessage.split("\\s+")[1];
      sendDocumentToChat(remoteFilePath, chatSession);
    } else if (receivedMessage.equals("/disconnect")) {
      close(chatSession);
    } else {
      chatSession.addResponse("Unknown command ...");
    }
  }

  private void setIdentity(
      final String idRsa,
      final ChatSession chatSession) {
    try {
      final var userName = chatSession.getUserName();
      final var hostName = chatSession.getSessionFactory().getHostname();
      final var file = "id_rsa_" + userName + hostName;
      final var filePath = Path.of(file);
      Files.write(filePath, idRsa.getBytes());
      chatSession.getSessionFactory().setIdentityFromPrivateKey(file);
    } catch (IOException | JSchException e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
  }

  public void parseConnectionInfo(
      final String hostInfo,
      final ChatSession chatSession) {
    final var userNameRegex = "[A-Za-z0-9\\-.]+";
    final var hostNameRegex = "[a-z_][a-z0-9_\\-]*[$]?";
    final var portRegex = "^" +
        "([0-9]{1,4}" +
        "|[1-5][0-9]{4}" +
        "|6[0-4][0-9]{3}" +
        "|65[0-4][0-9]{2}" +
        "|655[0-2][0-9]" +
        "|6553[0-5])$";
    final var hostInfoRegex = userNameRegex + "@" + hostNameRegex + ":" + portRegex;
    if (hostInfo.matches(hostInfoRegex)) {
      final var sessionFactory = chatSession.getSessionFactory();
      final var username = hostInfo.substring(0, hostInfo.indexOf('@'));
      final var hostname = hostInfo.substring(hostInfo.indexOf('@') + 1, hostInfo.indexOf(':'));
      final var port = Integer.parseInt(hostInfo.substring(hostInfo.indexOf(':') + 1));
      sessionFactory.setUsername(username);
      sessionFactory.setHostname(hostname);
      sessionFactory.setPort(port);
      sessionFactory.setConfig("StrictHostKeyChecking", "no");
      chatSession.addResponse("[User info is set]");
    } else {
      chatSession.addResponse("[Invalid user info format]");
    }
  }

  public void sendResponse(final ChatSession chatSession) {
    final var sendMessageRequest = new SendMessage()
        .setChatId(chatSession.getChatId())
        .setText(chatSession.getResponse());
    try {
      charlie.execute(sendMessageRequest);
    } catch (Exception e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
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
      charlie.execute(sendDocumentRequest);
    } catch (Exception e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
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

  @PreDestroy
  public void close(final ChatSession chatSession) {
    try {
      chatSession.getCommandRunner().close();
    } catch (IOException e) {
      e.printStackTrace();
      chatSession.addResponse(e.getMessage());
    }
    chatSession.setSessionFactory(new DefaultSessionFactory());
    chatSession.setCommandRunner(new CommandRunner(chatSession.getSessionFactory()));
    chatSession.setSftpRunner(new SftpRunner(chatSession.getSessionFactory()));
    chatSession.setCurrentDir(HOME);
    chatSession.setReceivedMessage(null);
    chatSession.addResponse("[User info cleared]");
  }
}
