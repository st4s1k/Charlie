package com.st4s1k.charlie.data.model;

public class TaskExecutionFailedException extends RuntimeException {
  public TaskExecutionFailedException(final Exception e) {
    super(e);
    printStackTrace();
  }
}
