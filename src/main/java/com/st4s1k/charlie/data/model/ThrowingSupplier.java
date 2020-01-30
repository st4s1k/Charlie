package com.st4s1k.charlie.data.model;

@FunctionalInterface
public interface ThrowingSupplier<T> {
  T get() throws Exception;
}
