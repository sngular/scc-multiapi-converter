package com.corunet.multiapi.converter.asyncapi.exception;

public class DuplicatedOperationException extends RuntimeException {

  private static final String MESSAGE = "There are at least two operations that share OperationID: %s";
  public DuplicatedOperationException(final String message) {
    super(String.format(MESSAGE, message));
  }
}
