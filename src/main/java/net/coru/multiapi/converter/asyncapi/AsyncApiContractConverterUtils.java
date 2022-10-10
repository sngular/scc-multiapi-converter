/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package net.coru.multiapi.converter.asyncapi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import net.coru.multiapi.converter.exception.MultiApiContractConverterException;
import net.coru.multiapi.converter.utils.BasicTypeConstants;
import net.coru.multiapi.converter.utils.RandomGenerator;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.cloud.contract.spec.internal.RegexProperty;
import org.springframework.cloud.contract.spec.internal.ResponseBodyMatchers;

public final class AsyncApiContractConverterUtils {

  private AsyncApiContractConverterUtils() {}

  public static void processEnumPropertyType(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType, final Map<String, Object> messageBody, final String property,
      final String path, final String enumType) {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      if (properties.get(property).has(BasicTypeConstants.EXAMPLE)) {
        messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).textValue());
      } else {
        messageBody.put(property, processEnumTypes(properties.get(property)));
      }
    } else {
      final var enumList = properties.get(property).get(BasicTypeConstants.ENUM);
      responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(getEnumRegex(enumType, properties, property)));
      messageBody.put(property, enumList.get(BasicTypeConstants.RANDOM.nextInt(enumList.size())).textValue());
    }
  }

  public static void processBooleanPropertyType(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType, final Map<String, Object> messageBody, final String property,
      final String path) {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asBoolean());
    } else {
      responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
      messageBody.put(property, BasicTypeConstants.RANDOM.nextBoolean());
    }
  }

  public static void processDoublePropertyType(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType, final Map<String, Object> messageBody, final String property,
      final String path) {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asDouble());
    } else {
      responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      messageBody.put(property, BasicTypeConstants.RANDOM.nextDouble());
    }
  }

  public static void processFloatPropertyType(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType, final Map<String, Object> messageBody, final String property,
      final String path) {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      if (properties.get(property).has(BasicTypeConstants.EXAMPLE)) {
        messageBody.put(property, Float.parseFloat(properties.get(property).get(BasicTypeConstants.EXAMPLE).asText()));
      } else {
        messageBody.put(property, RandomUtils.nextFloat());
      }
    } else {
      responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      messageBody.put(property, RandomUtils.nextFloat());
    }
  }

  public static void processNumberPropertyType(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType, final Map<String, Object> messageBody, final String property,
      final String path) {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      if (properties.get(property).has(BasicTypeConstants.EXAMPLE)) {
        messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asInt());
      } else {
        messageBody.put(property, RandomUtils.nextInt());
      }
    } else {
      responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      messageBody.put(property, RandomUtils.nextInt());
    }
  }

  public static void processStringPropertyType(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType, final Map<String, Object> messageBody, final String property,
      final String path) {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      if (properties.get(property).has(BasicTypeConstants.EXAMPLE)) {
        messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asText());
      } else {
        messageBody.put(property, RandomStringUtils.random(5, true, false));
      }
    } else {
      if (!properties.get(property).has("format")) {
        responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
      } else {
        responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(new RegexProperty(Pattern.compile(properties.get(property).get("format").asText())).asString()));
      }
      messageBody.put(property, RandomStringUtils.random(5, true, false));
    }
  }

  public static String processEnumTypes(final JsonNode value) {
    final List<String> enumValueList = new ArrayList<>();

    final var enumValuesIT = value.get("enum").elements();
    while (enumValuesIT.hasNext()) {
      enumValueList.add(enumValuesIT.next().textValue());
    }
    return enumValueList.get(RandomUtils.nextInt(0, enumValueList.size()));
  }

  public static String getEnumRegex(final String type, final JsonNode properties, final String property) {
    String regex = "";
    final Iterator<JsonNode> enumObjects = properties.get(property).get(BasicTypeConstants.ENUM).iterator();
    while (enumObjects.hasNext()) {
      if (BasicTypeConstants.STRING.equalsIgnoreCase(type)) {
        while (enumObjects.hasNext()) {
          final JsonNode nextObject = enumObjects.next();
          if (!enumObjects.hasNext()) {
            regex = regex.concat(nextObject.asText());
          } else {
            regex = regex.concat(nextObject.asText() + "|");
          }
        }
      }
    }
    return regex;
  }

  public static void processArrayEnumType(
      final ResponseBodyMatchers responseBodyMatchers, final String property, final String path, final String operationType, final List<Object> arrayValues, final String enumType,
      final JsonNode internalProperties) throws JsonProcessingException {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      final var arrayNode = BasicTypeConstants.OBJECT_MAPPER.readTree(internalProperties.toString()).get(BasicTypeConstants.EXAMPLE);
      for (int i = 0; i < arrayNode.size(); i++) {
        arrayValues.add(AsyncApiContractConverterUtils.processEnumTypes(arrayNode.get(i)));
      }
    } else {
      final var enumList = internalProperties.get(BasicTypeConstants.ENUM);
      arrayValues.add(AsyncApiContractConverterUtils.processEnumTypes(enumList.get(BasicTypeConstants.RANDOM.nextInt(enumList.size()))));
      if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
        responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(AsyncApiContractConverterUtils.getEnumRegex(enumType, internalProperties, property)));
      }
    }
  }

  public static void processArrayBooleanType(
      final ResponseBodyMatchers responseBodyMatchers, final String path, final String operationType, final List<Object> arrayValues, final JsonNode internalProperties)
      throws JsonProcessingException {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      final var arrayNode = BasicTypeConstants.OBJECT_MAPPER.readTree(internalProperties.toString()).get(BasicTypeConstants.EXAMPLE);
      for (int i = 0; i < arrayNode.size(); i++) {
        arrayValues.add(arrayNode.get(i).asBoolean());
      }
    } else {
      arrayValues.add(BasicTypeConstants.RANDOM.nextBoolean());
      if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
        responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
      }
    }
  }

  public static void processArrayDoubleType(
      final ResponseBodyMatchers responseBodyMatchers, final String path, final String operationType, final List<Object> arrayValues, final JsonNode internalProperties)
      throws JsonProcessingException {
    processArrayDecimalNumberType(responseBodyMatchers, path, operationType, arrayValues, internalProperties, BasicTypeConstants.DOUBLE);
  }

  public static void processArrayFloatType(
      final ResponseBodyMatchers responseBodyMatchers, final String path, final String operationType, final List<Object> arrayValues, final JsonNode internalProperties)
      throws JsonProcessingException {
    processArrayDecimalNumberType(responseBodyMatchers, path, operationType, arrayValues, internalProperties, BasicTypeConstants.FLOAT);
  }

  private static void processArrayDecimalNumberType(
      final ResponseBodyMatchers responseBodyMatchers, final String path, final String operationType, final List<Object> arrayValues, final JsonNode internalProperties,
      final String type) throws JsonProcessingException {

    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      final var arrayNode = BasicTypeConstants.OBJECT_MAPPER.readTree(internalProperties.toString()).get(BasicTypeConstants.EXAMPLE);
      for (int i = 0; i < arrayNode.size(); i++) {
        if (BasicTypeConstants.DOUBLE.equals(type)) {
          arrayValues.add(arrayNode.get(i).asDouble());
        } else {
          arrayValues.add(Float.parseFloat(arrayNode.get(i).textValue()));
        }
      }
    } else {
      if (BasicTypeConstants.DOUBLE.equals(type)) {
        arrayValues.add(BasicTypeConstants.RANDOM.nextDouble());
      } else {
        arrayValues.add(Math.abs(BasicTypeConstants.RANDOM.nextFloat()));
      }
      if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
        responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      }
    }
  }

  public static void processArrayNumberType(
      final ResponseBodyMatchers responseBodyMatchers, final String path, final String operationType, final List<Object> arrayValues, final JsonNode internalProperties)
      throws JsonProcessingException {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      final var arrayNode = BasicTypeConstants.OBJECT_MAPPER.readTree(internalProperties.toString()).get(BasicTypeConstants.EXAMPLE);
      for (int i = 0; i < arrayNode.size(); i++) {
        arrayValues.add(arrayNode.get(i).asInt());
      }
    } else {
      arrayValues.add(BasicTypeConstants.RANDOM.nextInt());
      if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
        responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      }
    }
  }

  public static void processArrayStringType(
      final ResponseBodyMatchers responseBodyMatchers, final String path, final String operationType, final List<Object> arrayValues, final JsonNode internalProperties)
      throws JsonProcessingException {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      final var arrayNode = BasicTypeConstants.OBJECT_MAPPER.readTree(internalProperties.toString()).get(BasicTypeConstants.EXAMPLE);
      for (int i = 0; i < arrayNode.size(); i++) {
        arrayValues.add(arrayNode.get(i).asText());
      }
    } else {
      arrayValues.add(RandomStringUtils.random(5, true, false));
      if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
        responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
      }
    }
  }

  public static void processArrayDateType(
      final ResponseBodyMatchers responseBodyMatchers, final String path, final String operationType, final List<Object> arrayValues, final JsonNode internalProperties)
      throws JsonProcessingException {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      final var arrayNode = BasicTypeConstants.OBJECT_MAPPER.readTree(internalProperties.toString()).get(BasicTypeConstants.EXAMPLE);
      for (int i = 0; i < arrayNode.size(); i++) {
        arrayValues.add(arrayNode.get(i).asText());
      }
    } else {
      arrayValues.add(RandomStringUtils.random(5, true, false));
      if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
        responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BasicTypeConstants.DATE_REGEX));
      }
    }
  }

  public static void processArrayDateTimeType(
      final ResponseBodyMatchers responseBodyMatchers, final String path, final String operationType, final List<Object> arrayValues, final JsonNode internalProperties)
      throws JsonProcessingException {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      final var arrayNode = BasicTypeConstants.OBJECT_MAPPER.readTree(internalProperties.toString()).get(BasicTypeConstants.EXAMPLE);
      for (int i = 0; i < arrayNode.size(); i++) {
        arrayValues.add(arrayNode.get(i).asText());
      }
    } else {
      arrayValues.add(RandomStringUtils.random(5, true, false));
      if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
        responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BasicTypeConstants.DATE_TIME_REGEX));
      }
    }
  }

  public static void processArrayTimeType(
      final ResponseBodyMatchers responseBodyMatchers, final String path, final String operationType, final List<Object> arrayValues, final JsonNode internalProperties)
      throws JsonProcessingException {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      final var arrayNode = BasicTypeConstants.OBJECT_MAPPER.readTree(internalProperties.toString()).get(BasicTypeConstants.EXAMPLE);
      for (int i = 0; i < arrayNode.size(); i++) {
        arrayValues.add(arrayNode.get(i).asText());
      }
    } else {
      arrayValues.add(RandomStringUtils.random(5, true, false));
      if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
        responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BasicTypeConstants.TIME_REGEX));
      }
    }
  }

  public static boolean isNotRegexIncluded(final ResponseBodyMatchers responseBodyMatchers, final String property) {
    var isIncluded = false;

    for (final org.springframework.cloud.contract.spec.internal.BodyMatcher bodyMatcher : responseBodyMatchers.matchers()) {
      if (Objects.equals(bodyMatcher.path(), property)) {
        isIncluded = true;
      }
    }
    return !isIncluded;
  }

  public static JsonNode getInternalProperties(final JsonNode properties, final String type) {
    final JsonNode internalProperties;
    if (type.equals(BasicTypeConstants.OBJECT)) {
      internalProperties = properties.get(BasicTypeConstants.PROPERTIES);
    } else {
      internalProperties = properties;
    }
    return internalProperties;
  }

  public static String getType(final JsonNode node) {
    final String type;
    if (node.get(BasicTypeConstants.FORMAT) != null) {
      type = node.get(BasicTypeConstants.FORMAT).asText();
    } else if (node.get(BasicTypeConstants.TYPE) != null) {
      type = node.get(BasicTypeConstants.TYPE).asText();
    } else {
      type = node.get(node.fieldNames().next()).get(BasicTypeConstants.TYPE).asText();
    }
    return type;
  }

  public static boolean isEnum(final JsonNode properties) {
    return properties.has("enum");
  }

  public static JsonNode subscribeOrPublishOperation(final JsonNode rootNode) {
    final JsonNode result;

    if (rootNode.has(BasicTypeConstants.SUBSCRIBE)) {
      result = rootNode.get(BasicTypeConstants.SUBSCRIBE);
    } else {
      result = rootNode.get(BasicTypeConstants.PUBLISH);
    }
    return result;
  }

  public static void checkIfReferenceWithProperties(final JsonNode jsonNode) {
    if (jsonNode.size() > 1 && Objects.nonNull(jsonNode.get(BasicTypeConstants.REF))) {
      throw new MultiApiContractConverterException("If reference exists no other additional properties are allowed");
    }
  }

  public static void processDatePropertyType(final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType,
      final Map<String, Object> messageBody, final String property, final String path) {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      if (properties.get(property).has(BasicTypeConstants.EXAMPLE)) {
        messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asText());
      } else {
        messageBody.put(property, RandomGenerator.randomEnumValue(properties.get(property)));
      }
    } else {
      responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.DATE_REGEX));
      messageBody.put(property, RandomGenerator.getRandomDate());
    }
  }

  public static void processDateTimePropertyType(final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType,
      final Map<String, Object> messageBody, final String property, final String path) {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      if (properties.get(property).has(BasicTypeConstants.EXAMPLE)) {
        messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asText());
      } else {
        messageBody.put(property, RandomGenerator.getRandomDateTime());
      }
    } else {
      responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.DATE_TIME_REGEX));
      messageBody.put(property, RandomStringUtils.random(5, true, false));
    }
  }

  public static void processTimePropertyType(final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType,
      final Map<String, Object> messageBody, final String property, final String path) {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      if (properties.get(property).has(BasicTypeConstants.EXAMPLE)) {
        messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asText());
      } else {
        messageBody.put(property, RandomGenerator.getRandomTime());
      }
    } else {
      responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.TIME_REGEX));
      messageBody.put(property, RandomStringUtils.random(5, true, false));
    }
  }
}
