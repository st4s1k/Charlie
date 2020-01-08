package com.st4s1k.charlie.data.model;

import com.jcraft.jsch.JSchException;
import com.pastdev.jsch.DefaultSessionFactory;
import com.pastdev.jsch.command.CommandRunner;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.io.IOException;
import java.util.Objects;

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

  public void addResponse(String response) {
    responseBuffer.append(response);
  }

  public void execute(String command) {
    try {
      addResponse(commandRunner
          .execute(command)
          .getStdout());
    } catch (JSchException | IOException e) {
      e.printStackTrace();
      addResponse(e.getMessage());
    }
  }

  public String getResponse() {
    return responseBuffer.toString();
  }

  public void clearResponseBuffer() {
    responseBuffer.delete(0, responseBuffer.length());
  }
}
