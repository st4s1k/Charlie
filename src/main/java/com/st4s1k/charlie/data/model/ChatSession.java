package com.st4s1k.charlie.data.model;

import com.pastdev.jsch.DefaultSessionFactory;
import com.pastdev.jsch.command.CommandRunner;
import com.pastdev.jsch.sftp.SftpRunner;
import com.st4s1k.charlie.service.CharlieTelegramBot;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import static lombok.AccessLevel.NONE;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatSession {

  @EqualsAndHashCode.Include
  private final ChatSessionId id;
  private final CharlieTelegramBot charlie;

  private DefaultSessionFactory sessionFactory;
  private CommandRunner commandRunner;
  private SftpRunner sftpRunner;
  @Getter(NONE)
  private StringBuilder responseBuffer;
  private String receivedMessage;
  private String currentDir;

  public ChatSession(
      final ChatSessionId id,
      final CharlieTelegramBot charlie) {
    this.id = id;
    this.charlie = charlie;
    this.sessionFactory = new DefaultSessionFactory();
    this.commandRunner = new CommandRunner(this.sessionFactory);
    this.sftpRunner = new SftpRunner(this.sessionFactory);
    this.responseBuffer = new StringBuilder();
  }

  public Long getChatId() {
    return id.getChat().getId();
  }

  public String getUserName() {
    return id.getUser().getUserName();
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
}

