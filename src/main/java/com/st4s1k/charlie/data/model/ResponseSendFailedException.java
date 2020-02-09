package com.st4s1k.charlie.data.model;

public class ResponseSendFailedException extends RuntimeException {
  public ResponseSendFailedException(final Exception e) {
    super(e);
  }
}
