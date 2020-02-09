package com.st4s1k.charlie.data.model;

public class DocumentSendFailedException extends RuntimeException {
  public DocumentSendFailedException(final Exception e) {
    super(e);
  }
}
