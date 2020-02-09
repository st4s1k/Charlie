package com.st4s1k.charlie.service;

public class DirectoryCreationFailedException extends RuntimeException {
  public DirectoryCreationFailedException(final String message) {
    super(message);
  }
}
