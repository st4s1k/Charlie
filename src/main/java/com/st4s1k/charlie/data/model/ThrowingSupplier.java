package com.st4s1k.charlie.data.model;

@FunctionalInterface
public interface ThrowingSupplier<T, E extends Exception> {
  T get() throws E;
}
