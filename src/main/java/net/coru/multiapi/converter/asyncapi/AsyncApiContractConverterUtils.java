/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package net.coru.multiapi.converter.asyncapi;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import net.coru.multiapi.converter.exception.ElementNotFoundException;
import net.coru.multiapi.converter.exception.MultiApiContractConverterException;
import net.coru.multiapi.converter.utils.BasicTypeConstants;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.cloud.contract.spec.internal.ResponseBodyMatchers;

public final class AsyncApiContractConverterUtils {

  private AsyncApiContractConverterUtils() {}

  public static void processEnumPropertyType(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType, final Map<String, Object> messageBody, final String property,
      final String path, final String enumType) {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      messageBody.put(property, processEnumTypes(properties.get(property).get(BasicTypeConstants.EXAMPLE), enumType));
    } else {
      final var enumList = properties.get(property).get(BasicTypeConstants.ENUM);
      responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(getEnumRegex(enumType, properties, property)));
      messageBody.put(property, processEnumTypes(enumList.get(BasicTypeConstants.RANDOM.nextInt(enumList.size())), enumType));
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
      messageBody.put(property, Float.parseFloat(properties.get(property).get(BasicTypeConstants.EXAMPLE).asText()));
    } else {
      responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      messageBody.put(property, Math.abs(BasicTypeConstants.RANDOM.nextFloat()));
    }
  }

  public static void processNumberPropertyType(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType, final Map<String, Object> messageBody, final String property,
      final String path) {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asInt());
    } else {
      responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      messageBody.put(property, BasicTypeConstants.RANDOM.nextInt());
    }
  }

  public static void processStringPropertyType(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType, final Map<String, Object> messageBody, final String property,
      final String path) {
    if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
      messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asText());
    } else {
      responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
      messageBody.put(property, RandomStringUtils.random(5, true, false));
    }
  }

  public static Object processEnumTypes(final JsonNode value, final String type) {
    final Object enumValue;

    switch (type) {
      case BasicTypeConstants.STRING:
        enumValue = value.asText();
        break;
      case BasicTypeConstants.INT_32:
      case BasicTypeConstants.NUMBER:
        enumValue = value.asInt();
        break;
      case BasicTypeConstants.INT_64:
      case BasicTypeConstants.FLOAT:
      case BasicTypeConstants.DOUBLE:
        enumValue = value.asDouble();
        break;
      case BasicTypeConstants.BOOLEAN:
        enumValue = value.asBoolean();
        break;
      default:
        throw new ElementNotFoundException(BasicTypeConstants.TYPE);
    }

    return enumValue;
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
        arrayValues.add(AsyncApiContractConverterUtils.processEnumTypes(arrayNode.get(i), enumType));
      }
    } else {
      final var enumList = internalProperties.get(BasicTypeConstants.ENUM);
      arrayValues.add(AsyncApiContractConverterUtils.processEnumTypes(enumList.get(BasicTypeConstants.RANDOM.nextInt(enumList.size())), enumType));
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
    boolean isEnum = false;
    final Iterator<String> ite = properties.fieldNames();

    while (ite.hasNext()) {
      if (ite.next().equals(BasicTypeConstants.ENUM)) {
        isEnum = true;
      }
    }
    return isEnum;
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
}
