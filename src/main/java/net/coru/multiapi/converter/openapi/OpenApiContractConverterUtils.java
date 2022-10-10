/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package net.coru.multiapi.converter.openapi;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import net.coru.multiapi.converter.utils.BasicTypeConstants;
import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.plexus.util.StringUtils;
import org.springframework.cloud.contract.spec.internal.Body;
import org.springframework.cloud.contract.spec.internal.BodyMatchers;
import org.springframework.cloud.contract.spec.internal.MatchingStrategy;
import org.springframework.cloud.contract.spec.internal.MatchingStrategy.Type;
import org.springframework.cloud.contract.spec.internal.QueryParameters;
import org.springframework.cloud.contract.spec.internal.RegexProperty;
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

  public static String mapRefName(final Example example) {
    String refName = "";
    if (Objects.nonNull(example.get$ref())) {
      final String[] wholeRef = example.get$ref().split("/");
      refName = wholeRef[wholeRef.length - 1];

    }
    return refName;
  }

  public static Pair<Body, BodyMatchers> processBasicTypeBody(final Schema schema) {
    final Body body;
    final BodyMatchers bodyMatchers = new BodyMatchers();
    if (Objects.nonNull(schema.getExample())) {
      body = new Body(schema.getExample());
    } else {
      switch (schema.getType()) {
        case BasicTypeConstants.STRING:
          body = new Body(new Response().anyAlphaNumeric());
          bodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX);
          break;
        case BasicTypeConstants.INTEGER:
          body = new Body(processIntegerFormat(schema));
          bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX);
          break;
        case BasicTypeConstants.NUMBER:
          body = new Body(processNumberFormat(schema));
          bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX);
          break;
        case BasicTypeConstants.BOOLEAN:
          body = new Body(new Response().anyBoolean());
          bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX);
          break;
        default:
          body = new Body("Error");
          bodyMatchers.byRegex(BasicTypeConstants.DEFAULT_REGEX);
          break;
      }
    }
    return Pair.of(body, bodyMatchers);
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
          if (StringUtils.isEmpty(parameter.getSchema().getPattern())) {
            queryParameters.parameter(parameter.getName(), BasicTypeConstants.STRING_REGEX);
          } else {
            queryParameters.parameter(parameter.getName(), new RegexProperty(Pattern.compile(parameter.getSchema().getFormat())).asString());
          }
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