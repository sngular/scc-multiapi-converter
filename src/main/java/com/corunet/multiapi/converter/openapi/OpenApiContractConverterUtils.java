/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.corunet.multiapi.converter.openapi;

import java.util.Objects;

import com.corunet.multiapi.converter.utils.BasicTypeConstants;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.cloud.contract.spec.internal.Request;
import org.springframework.cloud.contract.spec.internal.Response;

public class OpenApiContractConverterUtils {

  private OpenApiContractConverterUtils() {}

  public static String mapRefName(Schema schema) {
    String refName = "";
    if (BasicTypeConstants.ARRAY.equalsIgnoreCase(schema.getType())) {
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
        case BasicTypeConstants.STRING:
          response.body(response.anyAlphaNumeric());
          break;
        case BasicTypeConstants.INTEGER:
          if (BasicTypeConstants.INT_32.equalsIgnoreCase(schema.getFormat()) || !Objects.nonNull(schema.getFormat())) {
            response.body(response.anyPositiveInt());
          } else if (BasicTypeConstants.INT_64.equalsIgnoreCase(schema.getFormat())) {
            response.body(response.anyNumber());
          }
          break;
        case BasicTypeConstants.NUMBER:
          if (BasicTypeConstants.FLOAT.equalsIgnoreCase(schema.getFormat())) {
            response.body(response.anyNumber());
          } else if (BasicTypeConstants.DOUBLE.equalsIgnoreCase(schema.getFormat())) {
            response.body(response.anyDouble());
          } else if (schema.getFormat().isEmpty()) {
            response.body(response.anyPositiveInt());
          }
          break;
        case BasicTypeConstants.BOOLEAN:
          response.body(response.anyBoolean());
          break;
        default:
          response.body("Error");
          break;
      }
    }
  }

  public static void processBasicRequestTypeBody(Request request, Schema schema) {

    if (Objects.nonNull(schema.getExample())) {
      request.body(schema.getExample());
    } else {
      switch (schema.getType()) {
        case BasicTypeConstants.STRING:
          request.body(request.anyAlphaNumeric());
          break;
        case BasicTypeConstants.INTEGER:
          if (BasicTypeConstants.INT_32.equalsIgnoreCase(schema.getFormat()) || !Objects.nonNull(schema.getFormat())) {
            request.body(request.anyPositiveInt());
          } else if (BasicTypeConstants.INT_64.equalsIgnoreCase(schema.getFormat())) {
            request.body(request.anyNumber());
          }
          break;
        case BasicTypeConstants.NUMBER:
          if (BasicTypeConstants.FLOAT.equalsIgnoreCase(schema.getFormat())) {
            request.body(request.anyNumber());
          } else if (BasicTypeConstants.DOUBLE.equalsIgnoreCase(schema.getFormat())) {
            request.body(request.anyDouble());
          } else if (schema.getFormat().isEmpty()) {
            request.body(request.anyPositiveInt());
          }
          break;
        case BasicTypeConstants.BOOLEAN:
          request.body(request.anyBoolean());
          break;
        default:
          request.body("Error");
          break;
      }
    }
  }
}