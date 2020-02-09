package com.st4s1k.charlie.data.model;

public class AsyncSupplyFailedException extends RuntimeException {
  public AsyncSupplyFailedException(final Exception e) {
    super(e);
  }
}
