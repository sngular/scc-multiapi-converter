/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package net.coru.multiapi.converter.asyncapi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import net.coru.multiapi.converter.exception.ElementNotFoundException;
import net.coru.multiapi.converter.exception.MultiApiContractConverterException;
import net.coru.multiapi.converter.utils.BasicTypeConstants;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.Input;
import org.springframework.cloud.contract.spec.internal.OutputMessage;
import org.springframework.cloud.contract.spec.internal.ResponseBodyMatchers;

@Slf4j
public class AsyncApiContractConverter {

  public Collection<Contract> convertFrom(final File file) {
    Collection<Contract> sccContracts = new ArrayList<>();

    try {
      var fileContent = BasicTypeConstants.OBJECT_MAPPER.readTree(file);
      var channelsNode = fileContent.get(BasicTypeConstants.CHANNELS);

      Iterator<JsonNode> it = channelsNode.elements();
      Iterator<String> topicIterator = channelsNode.fieldNames();
      while (it.hasNext()) {
        Contract contract = new Contract();
        JsonNode operationContent;
        String operationType;
        Map<String, Object> bodyProcessed;

        JsonNode channel = it.next();
        operationType = channel.fieldNames().next();
        operationContent = subscribeOrPublishOperation(channel);
        String operationId = operationContent.get("operationId").asText();
        contract.setName(operationId);
        ResponseBodyMatchers responseBodyMatchers = new ResponseBodyMatchers();
        bodyProcessed = processMessage(responseBodyMatchers, operationContent, fileContent, operationType, file);
        contract.label(operationId);

        String topicName = topicIterator.next();
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          Input input = new Input();
          input.messageFrom(topicName);
          input.messageBody(bodyProcessed);
          input.messageHeaders(headers -> headers.accept("application/json"));
          contract.setInput(input);

          OutputMessage outputMessage = new OutputMessage();
          outputMessage.headers(headers -> headers.accept("application/json"));
          outputMessage.sentTo(topicName);
          outputMessage.body(bodyProcessed);
          contract.setOutputMessage(outputMessage);

        } else if (operationType.equals(BasicTypeConstants.PUBLISH)) {
          Input input = new Input();
          input.triggeredBy(operationId + "()");
          contract.setInput(input);

          OutputMessage outputMessage = new OutputMessage();
          outputMessage.sentTo(topicName);

          outputMessage.body(bodyProcessed);
          outputMessage.setBodyMatchers(responseBodyMatchers);
          contract.setOutputMessage(outputMessage);
        }

        sccContracts.add(contract);
      }


    } catch (Exception e) {
      log.error("Error", e);
    }
    return sccContracts;
  }

  private Map<String, Object> processMessage(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode operationContent, final JsonNode fileContent, final String operationType,
      final File basePath)
      throws IOException {
    JsonNode message;
    JsonNode propertiesJson;
    String ref;
    Map<String, Object> messageBody;

    message = operationContent.get("message");

    if (message.get(BasicTypeConstants.REF) != null && message.get(BasicTypeConstants.REF).asText().startsWith("#")) {
      String[] pathToRef = message.get(BasicTypeConstants.REF).asText().split("/");
      ref = pathToRef[pathToRef.length - 1];
      var payload = fileContent.findPath(ref);

      messageBody = processSchemas(responseBodyMatchers, operationType, null, payload, basePath, fileContent, "");

    } else if (message.get(BasicTypeConstants.REF) != null) {
      var fillProperties = processAvro(responseBodyMatchers, message);
      messageBody = fillProperties.getValue();
    } else {
      propertiesJson = message.get(message.fieldNames().next()).get(BasicTypeConstants.PROPERTIES);

      if (Objects.nonNull(propertiesJson.get(BasicTypeConstants.REF)) && propertiesJson.get(BasicTypeConstants.REF).asText().startsWith("#")) {
        String[] pathToRef = propertiesJson.get(BasicTypeConstants.REF).asText().split("/");
        var length = pathToRef.length;
        ref = pathToRef[length - 1];
        propertiesJson = fileContent.findPath(ref).get(BasicTypeConstants.PROPERTIES);
        messageBody = fillObjectProperties(responseBodyMatchers, propertiesJson, "", operationType, basePath, fileContent, null);
      } else if (propertiesJson.get(BasicTypeConstants.REF) != null && propertiesJson.get(BasicTypeConstants.REF).asText().contains(".yml")) {
        String[] pathToRef = propertiesJson.get(BasicTypeConstants.REF).asText().split("#");
        messageBody = processExternalFile(pathToRef[0], pathToRef[1], responseBodyMatchers, operationType, basePath, "");
      } else {
        messageBody = fillObjectProperties(responseBodyMatchers, propertiesJson, "", operationType, basePath, fileContent, null);
      }

    }
    return messageBody;
  }

  private Map<String, Object> processSchemas(
      final ResponseBodyMatchers responseBodyMatchers, final String operationType, String fieldName,
      final JsonNode payload, final File basePath, final JsonNode fileContent, final String bodyMatcherPath) throws IOException {
    JsonNode properties;
    String ref;
    Map<String, Object> messageBody = new HashMap<>();

    if (fieldName == null) {
      fieldName = payload.fieldNames().next();
    }

    if (Objects.nonNull(payload.get(fieldName).get(BasicTypeConstants.REF)) && payload.get(fieldName).get(BasicTypeConstants.REF).asText().startsWith("#")) {
      String[] pathToRef = payload.get(fieldName).get(BasicTypeConstants.REF).asText().split("/");
      var length = pathToRef.length;
      ref = pathToRef[length - 1];
      properties = fileContent.findPath(ref).get(BasicTypeConstants.PROPERTIES);
      messageBody = processProperties(responseBodyMatchers, operationType, basePath, fileContent, bodyMatcherPath, properties, messageBody);

    } else if (Objects.nonNull(payload.get(fieldName).get(BasicTypeConstants.REF)) && payload.get(fieldName).get(BasicTypeConstants.REF).asText().contains(".yml")) {
      String[] pathToRef = payload.get(fieldName).get(BasicTypeConstants.REF).asText().split("#");
      messageBody = processExternalFile(pathToRef[0], pathToRef[1], responseBodyMatchers, operationType, basePath, bodyMatcherPath);
    } else if (payload.get(fieldName).get(BasicTypeConstants.REF) != null) {
      var fillProperties = processAvro(responseBodyMatchers, payload);
      messageBody = fillProperties.getValue();
    } else {
      if (Objects.nonNull(payload.get(BasicTypeConstants.PAYLOAD).get(BasicTypeConstants.PROPERTIES))) {
        properties = payload.get(BasicTypeConstants.PAYLOAD).get(BasicTypeConstants.PROPERTIES);
        messageBody = processProperties(responseBodyMatchers, operationType, basePath, fileContent, bodyMatcherPath, properties, messageBody);

      } else {
        if (Objects.nonNull(payload.get(BasicTypeConstants.PAYLOAD))) {
          properties = payload.get(BasicTypeConstants.PAYLOAD).get(payload.get(BasicTypeConstants.PAYLOAD).fieldNames().next()).get(BasicTypeConstants.PROPERTIES);
        } else {
          properties = payload;
        }
        messageBody = fillObjectProperties(responseBodyMatchers, properties, bodyMatcherPath, operationType, basePath, fileContent, null);
      }

    }
    return messageBody;
  }

  private Map<String, Object> processProperties(
      final ResponseBodyMatchers responseBodyMatchers, final String operationType, final File basePath, final JsonNode fileContent, final String bodyMatcherPath,
      final JsonNode properties, Map<String, Object> messageBody) throws IOException {
    var propertiesName = properties.fieldNames();

    while (propertiesName.hasNext()) {
      var propertyName = propertiesName.next();

      if (Objects.nonNull(properties.get(propertyName).get(BasicTypeConstants.REF))) {
        messageBody.put(propertyName, processSchemas(responseBodyMatchers, operationType, propertyName, properties, basePath, fileContent, propertyName + "."));

      } else {
        final ObjectNode propertiesToFill = BasicTypeConstants.OBJECT_MAPPER.createObjectNode();
        propertiesToFill.set(propertyName, properties.get(propertyName));

        messageBody = fillObjectProperties(responseBodyMatchers, propertiesToFill, bodyMatcherPath, operationType, basePath, fileContent, messageBody);
      }
    }
    return messageBody;
  }

  private Map<String, Object> processExternalFile(
      final String externalFilePath, final String schemaPath, final ResponseBodyMatchers responseBodyMatchers, final String operationType, final File basePath,
      String bodyMatcherPath)
      throws IOException {
    Map<String, Object> messageBody = new HashMap<>();
    JsonNode schema;

    Path externalFile = composePath(basePath.toPath(), externalFilePath);
    var externalFileContent = BasicTypeConstants.OBJECT_MAPPER.readTree(externalFile.toFile());

    String[] splitSchemaPath = schemaPath.split("/");
    var length = splitSchemaPath.length;
    String ref = splitSchemaPath[length - 1];

    schema = externalFileContent.findPath(ref).get(BasicTypeConstants.PROPERTIES);
    var fieldNames = schema.fieldNames();

    while (fieldNames.hasNext()) {
      var fieldName = fieldNames.next();

      if (Objects.nonNull(schema.get(BasicTypeConstants.REF)) && schema.get(BasicTypeConstants.REF).asText().contains(".yml")) {
        String[] pathToRef = schema.get(BasicTypeConstants.REF).asText().split("#");
        messageBody = processExternalFile(pathToRef[0], pathToRef[1], responseBodyMatchers, operationType, basePath, bodyMatcherPath);
      } else if (Objects.nonNull(schema.get(fieldName).get(BasicTypeConstants.REF))) {
        var bodyMatcherPathObject = bodyMatcherPath + fieldName + ".";
        messageBody.put(fieldName, processSchemas(responseBodyMatchers, operationType, fieldName, schema, basePath, externalFileContent, bodyMatcherPathObject));
      } else {
        final ObjectNode propertiesToFill = BasicTypeConstants.OBJECT_MAPPER.createObjectNode();
        propertiesToFill.set(fieldName, schema.get(fieldName));
        messageBody = fillObjectProperties(responseBodyMatchers, propertiesToFill, bodyMatcherPath, operationType, basePath, externalFileContent, messageBody);
      }
    }

    return messageBody;
  }

  private Path composePath(final Path basePath, final String uriComponent) {
    Path finalFilePath = null;
    if (uriComponent.startsWith(".")) {
      finalFilePath = Paths.get(basePath.getParent().toAbsolutePath() + "/" + uriComponent.substring(2));
    } else if (uriComponent.startsWith("/")) {
      finalFilePath = Paths.get(basePath.getParent().toAbsolutePath() + uriComponent);
    } else if (uriComponent.startsWith("..")) {
      finalFilePath = Paths.get(basePath.getParent().getParent().toAbsolutePath() + uriComponent.substring(2));
    } else if (uriComponent.startsWith("http") || uriComponent.startsWith("https") || uriComponent.startsWith("ftp")) {
      throw new MultiApiContractConverterException("remote component retrieval");
    }
    return finalFilePath;
  }

  private JsonNode subscribeOrPublishOperation(final JsonNode rootNode) {
    JsonNode result;

    if (rootNode.has(BasicTypeConstants.SUBSCRIBE)) {
      result = rootNode.get(BasicTypeConstants.SUBSCRIBE);
    } else {
      result = rootNode.get(BasicTypeConstants.PUBLISH);
    }
    return result;
  }

  private Map<String, Object> fillObjectProperties(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String rootProperty, final String operationType,
      final File basePath, final JsonNode fileContent, Map<String, Object> messageBody)
      throws IOException {
    Iterator<String> fieldNames = properties.fieldNames();

    if (messageBody == null) {
      messageBody = new HashMap<>();
    }

    while (fieldNames.hasNext()) {
      var property = fieldNames.next();
      var path = rootProperty + property;

      if (!Objects.nonNull(properties.get(property).get(BasicTypeConstants.PROPERTIES)) ||
          !Objects.nonNull(properties.get(property).get(BasicTypeConstants.PROPERTIES).get(BasicTypeConstants.REF))) {
        String enumType = "";
        var type = getType(properties.get(property));

        if (isEnum(properties.get(property))) {
          enumType = type;
          type = BasicTypeConstants.ENUM;
        }

        switch (type) {
          case BasicTypeConstants.STRING:
            if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asText());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
              messageBody.put(property, RandomStringUtils.random(5, true, false));
            }
            break;
          case BasicTypeConstants.INT_32:
          case BasicTypeConstants.NUMBER:
            if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asInt());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
              messageBody.put(property, BasicTypeConstants.RANDOM.nextInt());
            }
            break;
          case BasicTypeConstants.INT_64:
          case BasicTypeConstants.FLOAT:
            if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asDouble());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
              messageBody.put(property, Math.abs(BasicTypeConstants.RANDOM.nextFloat()));
            }
            break;
          case BasicTypeConstants.DOUBLE:
            if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asDouble());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
              messageBody.put(property, BasicTypeConstants.RANDOM.nextDouble());
            }
            break;
          case BasicTypeConstants.BOOLEAN:
            if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asBoolean());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
              messageBody.put(property, BasicTypeConstants.RANDOM.nextBoolean());
            }
            break;
          case BasicTypeConstants.ENUM:
            if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
              messageBody.put(property, processEnumTypes(properties.get(property).get(BasicTypeConstants.EXAMPLE), enumType));
            } else {
              var enumList = properties.get(property).get(BasicTypeConstants.ENUM);
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(getEnumRegex(enumType, properties, property)));
              messageBody.put(property, processEnumTypes(enumList.get(BasicTypeConstants.RANDOM.nextInt(enumList.size())), enumType));
            }
            break;
          case BasicTypeConstants.OBJECT:
            messageBody.put(property, fillObjectProperties(responseBodyMatchers, properties.get(property).get(BasicTypeConstants.PROPERTIES), path + ".", operationType,
                                                           basePath, fileContent, null));
            break;
          case BasicTypeConstants.ARRAY:
            messageBody.put(property,
                            processArray(responseBodyMatchers, property, properties.get(property).get("items"), path, operationType, fileContent, basePath));
            break;
          default:
            throw new ElementNotFoundException(BasicTypeConstants.TYPE);
        }
      } else {
        var subProperties = properties.get(property).get(BasicTypeConstants.PROPERTIES);

        if (subProperties.get(BasicTypeConstants.REF).asText().contains(".yml")) {
          String[] pathToRef = subProperties.get(BasicTypeConstants.REF).asText().split("#");
          messageBody = processExternalFile(pathToRef[0], pathToRef[1], responseBodyMatchers, operationType, basePath, path);
        } else {
          String[] pathToObject = subProperties.get(BasicTypeConstants.REF).asText().split("/");
          var body = pathToObject[pathToObject.length - 1];
          var schema = fileContent.findPath(body).get(BasicTypeConstants.PROPERTIES);
          messageBody.put(property, fillObjectProperties(responseBodyMatchers, schema, path + ".", operationType, basePath, fileContent, null));
        }
      }
    }

    return messageBody;
  }

  private List<Object> processArray(
      final ResponseBodyMatchers responseBodyMatchers, final String property, final JsonNode properties, final String path,
      final String operationType, final JsonNode node, final File basePath) throws IOException {

    final List<Object> result = new ArrayList<>();
    final ObjectMapper objectMapper = new ObjectMapper();
    String enumType = "";
    String type;
    JsonNode objectProperties;

    if (properties.get(BasicTypeConstants.REF) != null && properties.get(BasicTypeConstants.REF).asText().contains(".yml")) {
      String[] pathToSchema = properties.get(BasicTypeConstants.REF).asText().split("#");
      result.add(processExternalFile(pathToSchema[0], pathToSchema[1], responseBodyMatchers, operationType, basePath, path));
    } else {
      if (properties.get(BasicTypeConstants.REF) != null && properties.get(BasicTypeConstants.REF).asText().startsWith("#")) {
        String[] pathToObject = properties.get(BasicTypeConstants.REF).asText().split("/");
        var body = pathToObject[pathToObject.length - 1];
        objectProperties = node.findPath(body).get(BasicTypeConstants.PROPERTIES);

        type = BasicTypeConstants.OBJECT;
      } else {
        objectProperties = properties.get(properties.fieldNames().next()).get(BasicTypeConstants.PROPERTIES);
        type = getType(properties);

        if (isEnum(properties)) {
          enumType = type;
          type = BasicTypeConstants.ENUM;
        }
      }
      processInternalArray(responseBodyMatchers, property, properties, path, operationType, node, basePath, result, objectMapper, enumType, type, objectProperties);
    }

    return result;
  }

  private void processInternalArray(
      final ResponseBodyMatchers responseBodyMatchers, final String property, final JsonNode properties, final String path, final String operationType, final JsonNode node,
      final File basePath, final List<Object> result, final ObjectMapper objectMapper, final String enumType, final String type, final JsonNode objectProperties)
      throws IOException {

    switch (type) {
      case BasicTypeConstants.STRING:
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(properties.toString()).get(BasicTypeConstants.EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(arrayNode.get(i).asText());
          }
        } else {
          result.add(RandomStringUtils.random(5, true, false));
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
          }
        }
        break;
      case BasicTypeConstants.INT_32:
      case BasicTypeConstants.NUMBER:
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(properties.toString()).get(BasicTypeConstants.EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(arrayNode.get(i).asInt());
          }
        } else {
          result.add(BasicTypeConstants.RANDOM.nextInt());
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
          }
        }
        break;
      case BasicTypeConstants.INT_64:
      case BasicTypeConstants.FLOAT:
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(properties.toString()).get(BasicTypeConstants.EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(arrayNode.get(i).asDouble());
          }
        } else {
          result.add(Math.abs(BasicTypeConstants.RANDOM.nextFloat()));
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
          }
        }
        break;
      case BasicTypeConstants.DOUBLE:
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(properties.toString()).get(BasicTypeConstants.EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(arrayNode.get(i).asDouble());
          }
        } else {
          result.add(BasicTypeConstants.RANDOM.nextDouble());
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
          }
        }
        break;
      case BasicTypeConstants.BOOLEAN:
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(properties.toString()).get(BasicTypeConstants.EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(arrayNode.get(i).asBoolean());
          }
        } else {
          result.add(BasicTypeConstants.RANDOM.nextBoolean());
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
          }
        }
        break;
      case BasicTypeConstants.ENUM:
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(properties.toString()).get(BasicTypeConstants.EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(processEnumTypes(arrayNode.get(i), enumType));
          }
        } else {
          var enumList = properties.get(BasicTypeConstants.ENUM);
          result.add(processEnumTypes(enumList.get(BasicTypeConstants.RANDOM.nextInt(enumList.size())), enumType));
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(getEnumRegex(enumType, properties, property)));
          }
        }
        break;
      case BasicTypeConstants.OBJECT:
        result.add(fillObjectProperties(responseBodyMatchers, objectProperties, path + ".", operationType, basePath, node, null));
        break;
      default:
        throw new ElementNotFoundException(BasicTypeConstants.TYPE);
    }
  }

  private String getType(final JsonNode node) {
    String type;
    if (node.get(BasicTypeConstants.FORMAT) != null) {
      type = node.get(BasicTypeConstants.FORMAT).asText();
    } else if (node.get(BasicTypeConstants.TYPE) != null) {
      type = node.get(BasicTypeConstants.TYPE).asText();
    } else {
      type = node.get(node.fieldNames().next()).get(BasicTypeConstants.TYPE).asText();
    }
    return type;
  }

  private Object processEnumTypes(final JsonNode value, final String type) {
    Object enumValue;

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

  private boolean isNotRegexIncluded(final ResponseBodyMatchers responseBodyMatchers, final String property) {
    var isIncluded = false;

    for (final org.springframework.cloud.contract.spec.internal.BodyMatcher bodyMatcher : responseBodyMatchers.matchers()) {
      if (Objects.equals(bodyMatcher.path(), property)) {
        isIncluded = true;
      }
    }
    return !isIncluded;
  }

  private String getEnumRegex(final String type, final JsonNode properties, final String property) {
    String regex = "";
    Iterator<JsonNode> enumObjects = properties.get(property).get(BasicTypeConstants.ENUM).iterator();
    while (enumObjects.hasNext()) {
      if (BasicTypeConstants.STRING.equalsIgnoreCase(type)) {
        while (enumObjects.hasNext()) {
          JsonNode nextObject = enumObjects.next();
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

  private boolean isEnum(final JsonNode properties) {
    boolean isEnum = false;
    Iterator<String> ite = properties.fieldNames();

    while (ite.hasNext()) {
      if (ite.next().equals(BasicTypeConstants.ENUM)) {
        isEnum = true;
      }
    }
    return isEnum;
  }

  private Pair<JsonNode, Map<String, Object>> fillObjectPropertiesFromAvro(ResponseBodyMatchers responseBodyMatchers, ArrayNode properties, String rootProperty) {
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode result = mapper.createObjectNode();
    Map<String, Object> messageBody = new HashMap<>();

    for (int i = 0; i < properties.size(); i++) {
      var type = getType(properties.get(i));
      if (type.equals("")) {
        type = properties.get(i).get(BasicTypeConstants.TYPE).get(BasicTypeConstants.TYPE).asText();
      }
      String fieldName = properties.get(i).get(BasicTypeConstants.NAME).asText();
      String path = rootProperty + properties.get(i).get(BasicTypeConstants.NAME).asText();

      switch (type) {
        case BasicTypeConstants.STRING:
          String randomString = RandomStringUtils.random(5, true, false);
          result.put(fieldName, randomString);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
          messageBody.put(fieldName, randomString);
          break;
        case BasicTypeConstants.INT_32:
          int randomInt = BasicTypeConstants.RANDOM.nextInt();
          result.put(properties.get(i).get(BasicTypeConstants.NAME).asText(), randomInt);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("[0-9]+"));
          messageBody.put(fieldName, randomInt);
          break;
        case BasicTypeConstants.BOOLEAN:
          List<Boolean> list = new ArrayList<>();
          list.add(true);
          list.add(false);
          Boolean randomBoolean = list.get(BasicTypeConstants.RANDOM.nextInt(2));
          result.put(fieldName, randomBoolean);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("^(true|false)$"));
          messageBody.put(fieldName, randomBoolean);
          break;
        case BasicTypeConstants.FLOAT:
          float randomDecimal = BasicTypeConstants.RANDOM.nextFloat() * BasicTypeConstants.RANDOM.nextInt();
          result.put(fieldName, randomDecimal);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("/^\\d*\\.?\\d*$/"));
          messageBody.put(fieldName, randomDecimal);
          break;
        case "record":
          var subObject = fillObjectPropertiesFromAvro(responseBodyMatchers, (ArrayNode) properties.get(i).get(BasicTypeConstants.TYPE).get("fields"), path + ".");
          result.putIfAbsent(fieldName, subObject.getKey());
          messageBody.put(fieldName, subObject.getValue());
          break;
        default:
          throw new ElementNotFoundException(BasicTypeConstants.TYPE);
      }
    }
    return new MutablePair<>(result, messageBody);
  }

  private Pair<JsonNode, Map<String, Object>> processAvro(ResponseBodyMatchers responseBodyMatchers, JsonNode jsonNode) {
    File avroFile = new File(jsonNode.get(BasicTypeConstants.REF).asText());
    ObjectMapper mapper = new ObjectMapper();
    JsonNode fileTree = null;
    try {
      fileTree = mapper.readTree(avroFile);
    } catch (IOException e) {
      log.error("Error", e);
    }
    return fillObjectPropertiesFromAvro(responseBodyMatchers, (ArrayNode) fileTree.get("fields"), "");
  }
}
