package com.st4s1k.charlie.data.model;

public class AsyncTaskExecutionFailedException extends RuntimeException {
  public AsyncTaskExecutionFailedException(final Exception e) {
    super(e);
  }
}
