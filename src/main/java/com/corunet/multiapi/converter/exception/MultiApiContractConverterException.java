package com.corunet.multiapi.converter.exception;

public class MultiApiContractConverterException extends RuntimeException {

  public MultiApiContractConverterException(final String message) {super(message);}

  public MultiApiContractConverterException(final Exception e) {
    super(e);
  }
}
