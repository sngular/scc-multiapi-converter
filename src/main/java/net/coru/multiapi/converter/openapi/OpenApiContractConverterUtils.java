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
import org.springframework.cloud.contract.spec.internal.Body;
import org.springframework.cloud.contract.spec.internal.ClientDslProperty;
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

  public static Body processBasicResponseTypeBody(final Schema schema) {
    final Body body;
    if (Objects.nonNull(schema.getExample())) {
      body = new Body(schema.getExample());
    } else {
      switch (schema.getType()) {
        case BasicTypeConstants.STRING:
          body = new Body(new Response().anyAlphaNumeric());
          break;
        case BasicTypeConstants.INTEGER:
          body = new Body(processIntegerFormat(schema));
          break;
        case BasicTypeConstants.NUMBER:
          body = new Body(processNumberFormat(schema));
          break;
        case BasicTypeConstants.BOOLEAN:
          body = new Body(new Response().anyBoolean());
          break;
        default:
          body = new Body("Error");
          break;
      }
    }
    return body;
  }

  public static Body processBasicRequestTypeBody(final Schema schema) {
    final Body result;
    if (Objects.nonNull(schema.getExample())) {
      result = new Body(schema.getExample());
    } else {
      switch (schema.getType()) {
        case BasicTypeConstants.STRING:
          result = new Body(new Request().anyAlphaNumeric());
          break;
        case BasicTypeConstants.INTEGER:
          result = new Body(new ClientDslProperty(processIntegerFormat(schema)));
          break;
        case BasicTypeConstants.NUMBER:
          result = new Body(new ClientDslProperty(processNumberFormat(schema)));
          break;
        case BasicTypeConstants.BOOLEAN:
          result = new Body(new ClientDslProperty(new Request().anyBoolean()));
          break;
        default:
          result = new Body("Error");
          break;
      }
    }
    return result;
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

  public static Map<String, Object> processNumberFormat(final Schema schema) {
    return processNumberFormat(schema.getFormat(), schema.getName());
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

  public static Map<String, Object> processIntegerFormat(final Schema schema) {
    return processIntegerFormat(schema.getFormat(), schema.getName());
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