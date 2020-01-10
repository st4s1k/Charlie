package com.st4s1k.charlie.data.model;

import com.jcraft.jsch.JSchException;
import com.pastdev.jsch.DefaultSessionFactory;
import com.pastdev.jsch.command.CommandRunner;
import lombok.*;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.io.IOException;
import java.util.Objects;

import static java.util.Objects.isNull;
import static lombok.AccessLevel.NONE;

@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChatSession {

  @Getter
  @RequiredArgsConstructor
  public static class ChatSessionId {


    private final Chat chat;

    private final User user;

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof ChatSessionId)) return false;
      final ChatSessionId that = (ChatSessionId) o;
      return Objects.equals(chat.getId(), that.chat.getId()) &&
          Objects.equals(user.getId(), that.user.getId());
    }

    @Override
    public int hashCode() {
      return Objects.hash(chat.getId(), user.getId());
    }


  }

  @EqualsAndHashCode.Include
  private final ChatSessionId id;
  private final DefaultSessionFactory sessionFactory = new DefaultSessionFactory();
  private final CommandRunner commandRunner = new CommandRunner(sessionFactory);
  @Getter(NONE)
  private final StringBuilder responseBuffer = new StringBuilder();
  @Setter
  private String lastReceivedMessage;
  private String currentDir;

  public ChatSession(
      final Chat chat,
      final User user) {
    this.id = new ChatSessionId(chat, user);
  }

  public Chat getChat() {
    return id.getChat();
  }

  public Long getChatId() {
    return id.getChat().getId();
  }

  public void addResponse(final String response) {
    responseBuffer.append(response);
  }

  public void execute(final String command) {
    try {
      addResponse(commandRunner
          .execute(isNull(currentDir) ? command
              : "cd " + currentDir + " && " + command)
          .getStdout());
    } catch (JSchException | IOException e) {
      e.printStackTrace();
      addResponse(e.getMessage());
    }
  }

  public String getResponse() {
    final var response = responseBuffer.toString();
    clearResponseBuffer();
    return response;
  }

  public void clearResponseBuffer() {
    responseBuffer.delete(0, responseBuffer.length());
  }

  public void cd(final String dir) {

    if (currentDir != null) {
      execute("cd " + dir + " && pwd");
      currentDir = getResponse();
    } else {
      currentDir = dir;
    }

    execute("ls");
  }

  @SneakyThrows
  public void close() {
    sessionFactory.setUsername(null);
    sessionFactory.setHostname(null);
    sessionFactory.setPort(0);
    sessionFactory.setUserInfo(null);
    getCommandRunner().close();
    currentDir = null;
    addResponse("[User info cleared]");
  }
}
