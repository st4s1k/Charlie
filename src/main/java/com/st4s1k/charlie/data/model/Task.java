package com.st4s1k.charlie.data.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static lombok.AccessLevel.NONE;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Task {

  @EqualsAndHashCode.Include
  private final Integer id;
  private final String name;
  @Getter(NONE)
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final CompletableFuture<Void> future;

  public Task(
      final Integer id,
      final String name,
      final Function<Task, CompletableFuture<Void>> operation) {
    this.id = id;
    this.name = name;
    this.future = operation.apply(this);
  }

  public boolean isCancelled() {
    return cancelled.get();
  }

  public void stop() {
    wake();
    cancelled.set(true);
  }

  public void sleepUntil(final Supplier<Boolean> condition) throws InterruptedException {
    synchronized (this) {
      while (!(condition.get() || isCancelled())) {
        wait();
      }
    }
  }

  public void wake() {
    synchronized (this) {
      notifyAll();
    }
  }
}
