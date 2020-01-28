package com.st4s1k.charlie.data.model;

@FunctionalInterface
public interface ThrowingRunnable {
  void run() throws Exception;
}
