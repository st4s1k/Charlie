package com.st4s1k.charlie;

import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

@Getter
class ChatSession {

  private final Chat chat;
  private final User user;
  private ConnectionInfo connectionInfo;

  public ChatSession(
      final Chat chat,
      final User user) {
    this.chat = chat;
    this.user = user;
    this.connectionInfo = new ConnectionInfo();
  }
}
