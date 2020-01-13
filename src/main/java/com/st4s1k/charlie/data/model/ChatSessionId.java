package com.st4s1k.charlie.data.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Objects;

@Getter
@RequiredArgsConstructor
public class ChatSessionId {

  private final Chat chat;

  private final User user;

  @Override
  @SuppressWarnings("ObjectComparison")
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
