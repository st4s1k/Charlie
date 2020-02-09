package com.st4s1k.charlie.data.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static lombok.AccessLevel.NONE;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Task {

  @EqualsAndHashCode.Include
  private final Integer id;
  private final String name;
  @Getter(NONE)
  private final AtomicBoolean cancelled;
  private final ExecutorService executor;
  private final CompletableFuture<Void> future;

  public Task(
      final Integer id,
      final String name,
      final Consumer<Task> operation) {
    this.id = id;
    this.name = name;
    this.executor = Executors.newSingleThreadExecutor();
    this.cancelled = new AtomicBoolean(false);
    this.future = CompletableFuture.runAsync(
        () -> operation.accept(this), executor
    );
  }

  public boolean isCancelled() {
    return cancelled.get();
  }

  public void stop() {
    wake();
    cancelled.set(true);
  }

  public void kill() {
    executor.shutdownNow();
  }

  public void sleepUntil(
      final BooleanSupplier condition
  ) throws InterruptedException {
    synchronized (this) {
      while (!condition.getAsBoolean()
          && !isCancelled()) {
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
