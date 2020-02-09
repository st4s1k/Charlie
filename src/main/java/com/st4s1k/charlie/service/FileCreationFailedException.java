package com.st4s1k.charlie.service;

public class FileCreationFailedException extends RuntimeException {

  public FileCreationFailedException(final Exception e) {
    super(e);
  }

  public FileCreationFailedException(final String message) {
    super(message);
  }
}
