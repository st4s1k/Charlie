package com.st4s1k.charlie.data.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Objects;

@Getter
@RequiredArgsConstructor
public class ChatSessionId {

  private final Long chatId;

  private final Integer userId;

  public ChatSessionId(final Chat chat, final User user) {
    this.chatId = chat.getId();
    this.userId = user.getId();
  }

  @Override
  @SuppressWarnings("ObjectComparison")
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof ChatSessionId)) return false;
    final ChatSessionId that = (ChatSessionId) o;
    return Objects.equals(chatId, that.chatId) &&
        Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(chatId, userId);
  }
}
