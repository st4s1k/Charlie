package com.st4s1k.charlie.data.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static lombok.AccessLevel.NONE;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Task {

  @EqualsAndHashCode.Include
  private final UUID id = UUID.randomUUID();
  @Getter(NONE)
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final CompletableFuture<Void> future;

  public Task(final Function<Task, CompletableFuture<Void>> operation) {
    this.future = operation.apply(this);
  }

  public boolean cancelled() {
    return cancelled.get();
  }

  public void stop() {
    cancelled.set(true);
  }
}
