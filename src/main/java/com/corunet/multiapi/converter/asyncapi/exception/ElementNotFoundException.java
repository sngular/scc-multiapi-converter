package com.corunet.multiapi.converter.asyncapi.exception;

public class ElementNotFoundException extends RuntimeException {

  private static final String MESSAGE = "%s not found";

  public ElementNotFoundException(final String message) {
    super(String.format(MESSAGE, message));
  }
}
