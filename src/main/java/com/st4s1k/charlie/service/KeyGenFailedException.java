package com.st4s1k.charlie.service;

public class KeyGenFailedException extends RuntimeException {
  public KeyGenFailedException(final Exception e) {
    super(e);
  }
}
