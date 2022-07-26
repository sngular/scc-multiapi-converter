/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package net.coru.multiapi.converter.openapi;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import net.coru.multiapi.converter.utils.BasicTypeConstants;
import org.springframework.cloud.contract.spec.internal.MatchingStrategy;
import org.springframework.cloud.contract.spec.internal.MatchingStrategy.Type;
import org.springframework.cloud.contract.spec.internal.QueryParameters;
import org.springframework.cloud.contract.spec.internal.Request;
import org.springframework.cloud.contract.spec.internal.Response;

public final class OpenApiContractConverterUtils {

  private OpenApiContractConverterUtils() {}

  public static String mapRefName(final Schema schema) {
    String refName = "";
    if (BasicTypeConstants.ARRAY.equalsIgnoreCase(schema.getType())) {
      final ArraySchema arraySchema = (ArraySchema) schema;
      final String[] wholeRef = arraySchema.getItems().get$ref().split("/");
      refName = wholeRef[wholeRef.length - 1];
    }
    if (Objects.nonNull(schema.get$ref())) {
      final String[] wholeRef = schema.get$ref().split("/");
      refName = wholeRef[wholeRef.length - 1];

    }
    return refName;
  }

  public static void processBasicResponseTypeBody(final Response response, final Schema schema) {
    if (Objects.nonNull(schema.getExample())) {
      response.body(schema.getExample());
    } else {
      switch (schema.getType()) {
        case BasicTypeConstants.STRING:
          response.body(response.anyAlphaNumeric());
          break;
        case BasicTypeConstants.INTEGER:
          processIntegerFormat(response, schema);
          break;
        case BasicTypeConstants.NUMBER:
          processNumberFormat(response, schema);
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

  public static void processBasicRequestTypeBody(final Request request, final Schema schema) {

    if (Objects.nonNull(schema.getExample())) {
      request.body(schema.getExample());
    } else {
      switch (schema.getType()) {
        case BasicTypeConstants.STRING:
          request.body(request.anyAlphaNumeric());
          break;
        case BasicTypeConstants.INTEGER:
          processIntegerFormat(request, schema);
          break;
        case BasicTypeConstants.NUMBER:
          processNumberFormat(request, schema);
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

  public static void processBasicQueryParameterTypeBody(final QueryParameters queryParameters, final Parameter parameter) {
    if (Objects.nonNull(parameter.getExample())) {
      queryParameters.parameter(parameter.getName(), new MatchingStrategy(parameter.getExample(), Type.EQUAL_TO));
    } else if (Objects.nonNull(parameter.getSchema().getExample())) {
      queryParameters.parameter(parameter.getName(), new MatchingStrategy(parameter.getSchema().getExample(), Type.EQUAL_TO));
    } else {
      final String type = parameter.getSchema().getType();
      switch (type) {
        case BasicTypeConstants.STRING:
          queryParameters.parameter(parameter.getName(), BasicTypeConstants.STRING_REGEX);
          break;
        case BasicTypeConstants.INTEGER:
          OpenApiContractConverterUtils.processIntegerFormat(queryParameters, parameter);
          break;
        case BasicTypeConstants.NUMBER:
          OpenApiContractConverterUtils.processNumberFormat(queryParameters, parameter);
          break;
        case BasicTypeConstants.BOOLEAN:
          queryParameters.parameter(parameter.getName(), BasicTypeConstants.BOOLEAN_REGEX);
          break;
        default:
          queryParameters.parameter(parameter.getName(), BasicTypeConstants.DEFAULT_REGEX);
          break;
      }
    }
  }

  public static void processNumberFormat(final Response response, final Schema schema) {
    response.body(processNumberFormat(schema.getFormat(), schema.getName()));
  }

  public static void processNumberFormat(final Request request, final Schema schema) {
    request.body(processNumberFormat(schema.getFormat(), schema.getName()));
  }

  public static void processNumberFormat(final QueryParameters queryParameters, final Parameter parameter) {
    queryParameters.parameter(processNumberFormat(parameter.getSchema().getFormat(), parameter.getName()));
  }

  private static Map<String, Object> processNumberFormat(final String format, final String name) {
    final Map<String, Object> parameter = new HashMap<>();
    if (BasicTypeConstants.FLOAT.equalsIgnoreCase(format)) {
      parameter.put(name, BasicTypeConstants.INT_REGEX);
    } else if (BasicTypeConstants.DOUBLE.equalsIgnoreCase(format)) {
      parameter.put(name, BasicTypeConstants.DECIMAL_REGEX);
    } else {
      parameter.put(name, BasicTypeConstants.INT_REGEX);
    }
    return parameter;
  }

  public static void processIntegerFormat(final Response response, final Schema schema) {
    response.body(processIntegerFormat(schema.getFormat(), schema.getName()));
  }

  public static void processIntegerFormat(final Request request, final Schema schema) {
    request.body(processIntegerFormat(schema.getFormat(), schema.getName()));
  }

  public static void processIntegerFormat(final QueryParameters queryParameters, final Parameter parameter) {
    queryParameters.parameter(processIntegerFormat(parameter.getSchema().getFormat(), parameter.getName()));
  }

  private static Map<String, Object> processIntegerFormat(final String format, final String name) {
    final Map<String, Object> parameter = new HashMap<>();
    if (BasicTypeConstants.INT_32.equalsIgnoreCase(format) || !Objects.nonNull(format)) {
      parameter.put(name, BasicTypeConstants.INT_REGEX);
    } else if (BasicTypeConstants.INT_64.equalsIgnoreCase(format)) {
      parameter.put(name, BasicTypeConstants.DECIMAL_REGEX);
    }
    return parameter;
  }

}