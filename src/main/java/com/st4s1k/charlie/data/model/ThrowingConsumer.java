package com.st4s1k.charlie.data.model;

import java.util.Objects;

@FunctionalInterface
public interface ThrowingConsumer<T, E extends Exception> {

  void accept(T t) throws E;

  default ThrowingConsumer<T, E> andThen(ThrowingConsumer<? super T, E> after) {
    Objects.requireNonNull(after);
    return (T t) -> {
      accept(t);
      after.accept(t);
    };
  }
}
