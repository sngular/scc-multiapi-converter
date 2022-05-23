package com.corunet.multiapi.converter.openapi;

import java.util.Objects;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.cloud.contract.spec.internal.Request;
import org.springframework.cloud.contract.spec.internal.Response;

public class ContractConverterUtils {

  private ContractConverterUtils() {}

  public static String mapRefName(Schema schema) {
    String refName = "";
    if ("array".equalsIgnoreCase(schema.getType())) {
      ArraySchema arraySchema = (ArraySchema) schema;
      String[] wholeRef = arraySchema.getItems().get$ref().split("/");
      refName = wholeRef[wholeRef.length - 1];
    }
    if (Objects.nonNull(schema.get$ref())) {
      String[] wholeRef = schema.get$ref().split("/");
      refName = wholeRef[wholeRef.length - 1];

    }
    return refName;
  }

  public static void processBasicResponseTypeBody(Response response, Schema schema) {
    if (Objects.nonNull(schema.getExample())) {
      response.body(schema.getExample());
    } else {
      switch (schema.getType()) {
        case "string":
          response.body(response.anyAlphaNumeric());
          break;
        case "integer":
          if ("int32".equalsIgnoreCase(schema.getFormat()) || !Objects.nonNull(schema.getFormat())) {
            response.body(response.anyPositiveInt());
          } else if ("int64".equalsIgnoreCase(schema.getFormat())) {
            response.body(response.anyNumber());
          }
          break;
        case "number":
          if ("float".equalsIgnoreCase(schema.getFormat())) {
            response.body(response.anyNumber());
          } else if ("double".equalsIgnoreCase(schema.getFormat())) {
            response.body(response.anyDouble());
          } else if (schema.getFormat().isEmpty()) {
            response.body(response.anyPositiveInt());
          }
          break;
        case "boolean":
          response.body(response.anyBoolean());
          break;
        default:
          response.body(response.anyAlphaNumeric());
          break;
      }
    }
  }

  public static void processBasicRequestTypeBody(Request request, Schema schema) {

    if (Objects.nonNull(schema.getExample())) {
      request.body(schema.getExample());
    } else {
      switch (schema.getType()) {
        case "string":
          request.body(request.anyAlphaNumeric());
          break;
        case "integer":
          if ("int32".equalsIgnoreCase(schema.getFormat()) || !Objects.nonNull(schema.getFormat())) {
            request.body(request.anyPositiveInt());
          } else if ("int64".equalsIgnoreCase(schema.getFormat())) {
            request.body(request.anyNumber());
          }
          break;
        case "number":
          if ("float".equalsIgnoreCase(schema.getFormat())) {
            request.body(request.anyNumber());
          } else if ("double".equalsIgnoreCase(schema.getFormat())) {
            request.body(request.anyDouble());
          } else if (schema.getFormat().isEmpty()) {
            request.body(request.anyPositiveInt());
          }
          break;
        case "boolean":
          request.body(request.anyBoolean());
          break;
        default:
          request.body(request.anyAlphaNumeric());
          break;
      }
    }
  }
}