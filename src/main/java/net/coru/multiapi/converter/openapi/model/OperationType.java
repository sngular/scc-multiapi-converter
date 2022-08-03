package net.coru.multiapi.converter.openapi.model;

public enum OperationType {
  POST, GET, PATCH, PUT, DELETE;

  public static boolean isValid(final String name) {
    boolean result;
    switch (name.toUpperCase()) {
      case "POST":
      case "PUT":
      case "GET":
      case "PATCH":
      case "DELETE":
        result = true; break;
      default: result = false; break;
    }
    return result;
  }
}
