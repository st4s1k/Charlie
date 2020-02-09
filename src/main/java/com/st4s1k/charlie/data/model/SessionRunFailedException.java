package com.st4s1k.charlie.data.model;

public class SessionRunFailedException extends RuntimeException {
  public SessionRunFailedException(final Exception e) {
    super(e);
  }
}
