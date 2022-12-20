/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sngular.multiapi.converter.asyncapi;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sngular.multiapi.converter.exception.ElementNotFoundException;
import com.sngular.multiapi.converter.exception.MultiApiContractConverterException;
import lombok.extern.slf4j.Slf4j;
import com.sngular.multiapi.converter.utils.BasicTypeConstants;
import com.sngular.multiapi.converter.utils.RandomGenerator;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.Input;
import org.springframework.cloud.contract.spec.internal.OutputMessage;
import org.springframework.cloud.contract.spec.internal.ResponseBodyMatchers;

@Slf4j
public final class AsyncApiContractConverter {

  private File basePath;

  public Collection<Contract> convertFrom(final File file) {
    basePath = file.getParentFile();
    final Collection<Contract> sccContracts = new ArrayList<>();

    try {
      final var fileContent = BasicTypeConstants.OBJECT_MAPPER.readTree(file);
      final var channelsNode = fileContent.get(BasicTypeConstants.CHANNELS);

      final Iterator<JsonNode> it = channelsNode.elements();
      final Iterator<String> topicIterator = channelsNode.fieldNames();
      while (it.hasNext()) {
        final Contract contract = new Contract();

        final JsonNode channel = it.next();
        final String operationType = channel.fieldNames().next();
        final JsonNode operationContent = AsyncApiContractConverterUtils.subscribeOrPublishOperation(channel);
        final String operationId = operationContent.get("operationId").asText();
        contract.setName(operationId);
        final ResponseBodyMatchers responseBodyMatchers = new ResponseBodyMatchers();
        final Map<String, Object> bodyProcessed = processMessage(responseBodyMatchers, operationContent, fileContent, operationType);
        contract.label(operationId);

        final String topicName = topicIterator.next();
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          processSubscribeOperation(contract, bodyProcessed, topicName, operationId);
        } else if (operationType.equals(BasicTypeConstants.PUBLISH)) {
          processPublishOperation(contract, operationId, responseBodyMatchers, bodyProcessed, topicName);
        }
        sccContracts.add(contract);
      }
    } catch (final IOException e) {
      log.error("Error", e);
    }
    return sccContracts;
  }

  private void processPublishOperation(
      final Contract contract, final String operationId, final ResponseBodyMatchers responseBodyMatchers, final Map<String, Object> bodyProcessed, final String topicName) {
    final Input input = new Input();
    input.triggeredBy(operationId + "()");
    contract.setInput(input);

    final OutputMessage outputMessage = new OutputMessage();
    outputMessage.sentTo(topicName);

    outputMessage.body(bodyProcessed);
    outputMessage.setBodyMatchers(responseBodyMatchers);
    contract.setOutputMessage(outputMessage);
  }

  private void processSubscribeOperation(final Contract contract, final Map<String, Object> bodyProcessed, final String topicName, final String operationId) {
    final Input input = new Input();
    input.messageFrom(topicName);
    input.messageBody(bodyProcessed);
    input.messageHeaders(headers -> headers.accept("application/json"));
    input.assertThat(operationId + "Validation()");
    contract.setInput(input);
  }

  private Map<String, Object> processMessage(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode operationContent, final JsonNode fileContent, final String operationType)
      throws IOException {
    final JsonNode message;
    final String ref;
    final Map<String, Object> messageBody = new HashMap<>();

    message = operationContent.get("message");
    AsyncApiContractConverterUtils.checkIfReferenceWithProperties(message);

    if (message.has(BasicTypeConstants.REF)) {
      if (message.get(BasicTypeConstants.REF).asText().startsWith("#")) {
        final String[] pathToRef = message.get(BasicTypeConstants.REF).asText().split("/");
        ref = pathToRef[pathToRef.length - 1];
        final var payload = fileContent.findPath(ref);

        messageBody.putAll(processSchemas(responseBodyMatchers, operationType, payload.fieldNames().next(), payload, fileContent, ""));
      } else {
        final var fillProperties = processAvro(responseBodyMatchers, message);
        messageBody.putAll(fillProperties.getValue());
      }
    } else if (message.has(BasicTypeConstants.PAYLOAD)) {
      final var payload = message.get(BasicTypeConstants.PAYLOAD);

      if (payload.has(BasicTypeConstants.REF)) {
        final var referredPayload = payload.get(BasicTypeConstants.REF).asText();
        if (referredPayload.startsWith("#")) {
          final String[] pathToRef = referredPayload.split("/");
          final var length = pathToRef.length;
          ref = pathToRef[length - 1];
          messageBody.putAll(fillObjectProperties(responseBodyMatchers, fileContent.findPath(ref).get(BasicTypeConstants.PROPERTIES), "", operationType, fileContent));
        } else if (referredPayload.contains(".yml")) {
          final String[] pathToRef = referredPayload.split("#");
          messageBody.putAll(processExternalFile(pathToRef[0], pathToRef[1], responseBodyMatchers, operationType, ""));
        } else {
          messageBody.putAll(fillObjectProperties(responseBodyMatchers, payload, "", operationType, fileContent));
        }
      } else {
        messageBody.putAll(fillObjectProperties(responseBodyMatchers, payload, "", operationType, fileContent));
      }
    } else {
      messageBody.putAll(fillObjectProperties(responseBodyMatchers, message, "", operationType, fileContent));
    }
    return messageBody;
  }

  private Map<String, Object> processSchemas(
      final ResponseBodyMatchers responseBodyMatchers, final String operationType, final String fieldName,
      final JsonNode payload, final JsonNode fileContent, final String bodyMatcherPath) throws IOException {
    final JsonNode properties;
    final String ref;
    final Map<String, Object> messageBody = new HashMap<>();

    AsyncApiContractConverterUtils.checkIfReferenceWithProperties(payload.get(fieldName));

    if (payload.get(fieldName).has(BasicTypeConstants.REF)) {
      final var referencedNode = payload.get(fieldName).get(BasicTypeConstants.REF).asText();
      if (referencedNode.startsWith("#")) {
        final String[] pathToRef = payload.get(fieldName).get(BasicTypeConstants.REF).asText().split("/");
        final var length = pathToRef.length;
        ref = pathToRef[length - 1];
        properties = fileContent.findPath(ref).get(BasicTypeConstants.PROPERTIES);
        messageBody.putAll(processProperties(responseBodyMatchers, operationType, fileContent, bodyMatcherPath, properties));

      } else if (referencedNode.contains(".yml")) {
        final String[] pathToRef = referencedNode.split("#");
        messageBody.putAll(processExternalFile(pathToRef[0], pathToRef[1], responseBodyMatchers, operationType, bodyMatcherPath));
      } else {
        final var fillProperties = processAvro(responseBodyMatchers, payload);
        messageBody.putAll(fillProperties.getValue());
      }
    } else {
      if (payload.get(BasicTypeConstants.PAYLOAD).has(BasicTypeConstants.PROPERTIES)) {
        properties = payload.get(BasicTypeConstants.PAYLOAD).get(BasicTypeConstants.PROPERTIES);
        messageBody.putAll(processProperties(responseBodyMatchers, operationType, fileContent, bodyMatcherPath, properties));

      } else {
        if (payload.has(BasicTypeConstants.PAYLOAD)) {
          properties = payload.get(BasicTypeConstants.PAYLOAD).get(payload.get(BasicTypeConstants.PAYLOAD).fieldNames().next()).get(BasicTypeConstants.PROPERTIES);
        } else {
          properties = payload;
        }
        messageBody.putAll(fillObjectProperties(responseBodyMatchers, properties, bodyMatcherPath, operationType, fileContent));
      }

    }
    return messageBody;
  }

  private Map<String, Object> processProperties(
      final ResponseBodyMatchers responseBodyMatchers, final String operationType, final JsonNode fileContent, final String bodyMatcherPath,
      final JsonNode properties) throws IOException {
    final var propertiesName = properties.fieldNames();
    final Map<String, Object> messageBody = new HashMap<>();

    while (propertiesName.hasNext()) {
      final var propertyName = propertiesName.next();

      if (properties.get(propertyName).has(BasicTypeConstants.REF)) {
        messageBody.put(propertyName, processSchemas(responseBodyMatchers, operationType, propertyName, properties, fileContent, propertyName + "."));
      } else {
        final ObjectNode propertiesToFill = BasicTypeConstants.OBJECT_MAPPER.createObjectNode();
        propertiesToFill.set(propertyName, properties.get(propertyName));
        messageBody.putAll(fillObjectProperties(responseBodyMatchers, propertiesToFill, bodyMatcherPath, operationType, fileContent));
      }
    }
    return messageBody;
  }

  private Map<String, Object> processExternalFile(
      final String externalFilePath, final String schemaPath, final ResponseBodyMatchers responseBodyMatchers, final String operationType,
      final String bodyMatcherPath)
      throws IOException {
    final Map<String, Object> messageBody = new HashMap<>();
    final JsonNode schema;

    final Path externalFile = composePath(basePath.toPath(), externalFilePath);
    final var externalFileContent = BasicTypeConstants.OBJECT_MAPPER.readTree(externalFile.toFile());

    final String[] splitSchemaPath = schemaPath.split("/");
    final var length = splitSchemaPath.length;
    final String ref = splitSchemaPath[length - 1];

    schema = externalFileContent.findPath(ref).get(BasicTypeConstants.PROPERTIES);
    final var fieldNames = schema.fieldNames();

    while (fieldNames.hasNext()) {
      final var fieldName = fieldNames.next();
      final var fieldNode = schema.get(fieldName);
      if (fieldNode.has(BasicTypeConstants.REF)) {
        final var reference = fieldNode.get(BasicTypeConstants.REF).asText();
        if (reference.contains(".yml")) {
          final String[] pathToRef = reference.split("#");
          messageBody.put(fieldName, processExternalFile(pathToRef[0], pathToRef[1], responseBodyMatchers, operationType, bodyMatcherPath));
        } else {
          final var bodyMatcherPathObject = bodyMatcherPath + fieldName + ".";
          messageBody.put(fieldName, processSchemas(responseBodyMatchers, operationType, fieldName, schema, externalFileContent, bodyMatcherPathObject));
        }
      } else {
        final ObjectNode propertiesToFill = BasicTypeConstants.OBJECT_MAPPER.createObjectNode();
        propertiesToFill.set(fieldName, fieldNode);
        messageBody.putAll(fillObjectProperties(responseBodyMatchers, propertiesToFill, bodyMatcherPath, operationType, externalFileContent));
      }
    }

    return messageBody;
  }

  private Path composePath(final Path basePath, final String uriComponent) {
    final Path finalFilePath;
    if (uriComponent.startsWith(".")) {
      finalFilePath = basePath.resolve(uriComponent.substring(2));
    } else if (uriComponent.startsWith("/")) {
      finalFilePath = Paths.get(uriComponent);
    } else if (uriComponent.startsWith("..")) {
      finalFilePath = basePath.getParent().resolve(uriComponent.substring(2));
    } else if (uriComponent.startsWith("http") || uriComponent.startsWith("https") || uriComponent.startsWith("ftp")) {
      throw new MultiApiContractConverterException("remote component retrieval");
    } else {
      finalFilePath = basePath.resolve(uriComponent);
    }
    return finalFilePath;
  }

  private Path composePath(final String basePath, final String uriComponent) {
    Path finalFilePath = Paths.get(basePath);
    if (uriComponent.startsWith(".")) {
      finalFilePath = Paths.get(finalFilePath.toAbsolutePath() + "/" + uriComponent.substring(2));
    } else if (uriComponent.startsWith("/")) {
      finalFilePath = Paths.get(finalFilePath.toAbsolutePath() + uriComponent);
    } else if (uriComponent.startsWith("..")) {
      finalFilePath = Paths.get(finalFilePath.getParent().toAbsolutePath() + uriComponent.substring(2));
    } else if (uriComponent.startsWith("http") || uriComponent.startsWith("https") || uriComponent.startsWith("ftp")) {
      throw new MultiApiContractConverterException("remote component retrieval");
    } else {
      finalFilePath = Paths.get(finalFilePath.toAbsolutePath() + "/" + uriComponent);
    }
    return finalFilePath;
  }

  private Map<String, Object> fillObjectProperties(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String rootProperty, final String operationType,
      final JsonNode fileContent) throws IOException {

    final Iterator<String> fieldNames = properties.fieldNames();
    final Map<String, Object> messageBody = new HashMap<>();

    while (fieldNames.hasNext()) {
      final var property = fieldNames.next();
      final var path = rootProperty + property;
      if (!properties.get(property).has(BasicTypeConstants.PROPERTIES)
          || !properties.get(property).get(BasicTypeConstants.PROPERTIES).has(BasicTypeConstants.REF)) {
        messageBody.putAll(processObjectProperties(responseBodyMatchers, properties, operationType, fileContent, property, path));
      } else {
        final var subProperties = properties.get(property).get(BasicTypeConstants.PROPERTIES);

        if (subProperties.get(BasicTypeConstants.REF).asText().contains(".yml")) {
          final String[] pathToRef = subProperties.get(BasicTypeConstants.REF).asText().split("#");
          messageBody.putAll(processExternalFile(pathToRef[0], pathToRef[1], responseBodyMatchers, operationType, path));
        } else {
          final String[] pathToObject = subProperties.get(BasicTypeConstants.REF).asText().split("/");
          final var body = pathToObject[pathToObject.length - 1];
          final var schema = fileContent.findPath(body).get(BasicTypeConstants.PROPERTIES);
          messageBody.put(property, fillObjectProperties(responseBodyMatchers, schema, path + ".", operationType, fileContent));
        }
      }
    }

    return messageBody;
  }

  private Map<String, Object> processObjectProperties(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String operationType, final JsonNode fileContent,
      final String property, final String path) throws IOException {
    final Map<String, Object> messageBody = new HashMap<>();

    String enumType = "";
    var type = AsyncApiContractConverterUtils.getType(properties.get(property));

    if (AsyncApiContractConverterUtils.isEnum(properties.get(property))) {
      enumType = type;
      type = BasicTypeConstants.ENUM;
    }
    switch (type) {
      case BasicTypeConstants.STRING:
        AsyncApiContractConverterUtils.processStringPropertyType(responseBodyMatchers, properties, operationType, messageBody, property, path);
        break;
      case BasicTypeConstants.DATE:
        AsyncApiContractConverterUtils.processDatePropertyType(responseBodyMatchers, properties, operationType, messageBody, property, path);
        break;
      case BasicTypeConstants.DATE_TIME:
        AsyncApiContractConverterUtils.processDateTimePropertyType(responseBodyMatchers, properties, operationType, messageBody, property, path);
        break;
      case BasicTypeConstants.TIME:
        AsyncApiContractConverterUtils.processTimePropertyType(responseBodyMatchers, properties, operationType, messageBody, property, path);
        break;
      case BasicTypeConstants.INT_32:
      case BasicTypeConstants.NUMBER:
        AsyncApiContractConverterUtils.processNumberPropertyType(responseBodyMatchers, properties, operationType, messageBody, property, path);
        break;
      case BasicTypeConstants.INT_64:
      case BasicTypeConstants.FLOAT:
        AsyncApiContractConverterUtils.processFloatPropertyType(responseBodyMatchers, properties, operationType, messageBody, property, path);
        break;
      case BasicTypeConstants.DOUBLE:
        AsyncApiContractConverterUtils.processDoublePropertyType(responseBodyMatchers, properties, operationType, messageBody, property, path);
        break;
      case BasicTypeConstants.BOOLEAN:
        AsyncApiContractConverterUtils.processBooleanPropertyType(responseBodyMatchers, properties, operationType, messageBody, property, path);
        break;
      case BasicTypeConstants.ENUM:
        AsyncApiContractConverterUtils.processEnumPropertyType(responseBodyMatchers, properties, operationType, messageBody, property, path, enumType);
        break;
      case BasicTypeConstants.OBJECT:
        messageBody.put(property, fillObjectProperties(responseBodyMatchers, properties.get(property).get(BasicTypeConstants.PROPERTIES), path + ".", operationType,
                                                       fileContent));
        break;
      case BasicTypeConstants.ARRAY:
        messageBody.put(property,
                        processArray(responseBodyMatchers, property, properties.get(property).get("items"), path, operationType, fileContent));
        break;
      default:
        throw new ElementNotFoundException(BasicTypeConstants.TYPE);
    }
    return messageBody;
  }

  private List<Object> processArray(
      final ResponseBodyMatchers responseBodyMatchers, final String property, final JsonNode properties, final String path,
      final String operationType, final JsonNode node) throws IOException {
    final List<Object> resultArray = new ArrayList<>();
    JsonNode internalProperties = properties;

    AsyncApiContractConverterUtils.checkIfReferenceWithProperties(properties);

    if (properties.has(BasicTypeConstants.REF)) {
      if (properties.get(BasicTypeConstants.REF).asText().contains(".yml")) {
        final String[] pathToSchema = properties.get(BasicTypeConstants.REF).asText().split("#");
        resultArray.add(processExternalFile(pathToSchema[0], pathToSchema[1], responseBodyMatchers, operationType, path));
      } else if (properties.get(BasicTypeConstants.REF).asText().startsWith("#")) {
        final String[] pathToObject = properties.get(BasicTypeConstants.REF).asText().split("/");
        final var body = pathToObject[pathToObject.length - 1];
        internalProperties = node.findPath(body);
        resultArray.addAll(processInternalArray(responseBodyMatchers, property, internalProperties, path, operationType, node));
      } else {
        resultArray.addAll(processInternalArray(responseBodyMatchers, property, internalProperties, path, operationType, node));
      }
    } else {
      resultArray.addAll(processInternalArray(responseBodyMatchers, property, internalProperties, path, operationType, node));
    }

    return resultArray;
  }

  private List<Object> processInternalArray(
      final ResponseBodyMatchers responseBodyMatchers, final String property, final JsonNode properties, final String path, final String operationType, final JsonNode node)
      throws IOException {
    final List<Object> arrayValues = new ArrayList<>();
    String enumType = "";
    String type;
    type = AsyncApiContractConverterUtils.getType(properties);

    final JsonNode internalProperties = AsyncApiContractConverterUtils.getInternalProperties(properties, type);

    if (AsyncApiContractConverterUtils.isEnum(internalProperties)) {
      enumType = type;
      type = BasicTypeConstants.ENUM;
    }

    switch (type) {
      case BasicTypeConstants.STRING:
        AsyncApiContractConverterUtils.processArrayStringType(responseBodyMatchers, path, operationType, arrayValues, internalProperties);
        break;
      case BasicTypeConstants.DATE:
        AsyncApiContractConverterUtils.processArrayDateType(responseBodyMatchers, path, operationType, arrayValues, internalProperties);
        break;
      case BasicTypeConstants.DATE_TIME:
        AsyncApiContractConverterUtils.processArrayDateTimeType(responseBodyMatchers, path, operationType, arrayValues, internalProperties);
        break;
      case BasicTypeConstants.TIME:
        AsyncApiContractConverterUtils.processArrayTimeType(responseBodyMatchers, path, operationType, arrayValues, internalProperties);
        break;
      case BasicTypeConstants.INT_32:
      case BasicTypeConstants.NUMBER:
        AsyncApiContractConverterUtils.processArrayNumberType(responseBodyMatchers, path, operationType, arrayValues, internalProperties);
        break;
      case BasicTypeConstants.INT_64:
      case BasicTypeConstants.FLOAT:
        AsyncApiContractConverterUtils.processArrayFloatType(responseBodyMatchers, path, operationType, arrayValues, internalProperties);
        break;
      case BasicTypeConstants.DOUBLE:
        AsyncApiContractConverterUtils.processArrayDoubleType(responseBodyMatchers, path, operationType, arrayValues, internalProperties);
        break;
      case BasicTypeConstants.BOOLEAN:
        AsyncApiContractConverterUtils.processArrayBooleanType(responseBodyMatchers, path, operationType, arrayValues, internalProperties);
        break;
      case BasicTypeConstants.ENUM:
        AsyncApiContractConverterUtils.processArrayEnumType(responseBodyMatchers, property, path, operationType, arrayValues, enumType, internalProperties);
        break;
      case BasicTypeConstants.OBJECT:
        arrayValues.add(fillObjectProperties(responseBodyMatchers, internalProperties, path + ".", operationType, node));
        break;
      default:
        throw new ElementNotFoundException(BasicTypeConstants.TYPE);
    }
    return arrayValues;
  }

  private Pair<JsonNode, Map<String, Object>> fillObjectPropertiesFromAvro(final ResponseBodyMatchers responseBodyMatchers, final ArrayNode properties, final String rootProperty) {
    final ObjectNode result = BasicTypeConstants.OBJECT_MAPPER.createObjectNode();
    final Map<String, Object> messageBody = new HashMap<>();

    for (int i = 0; i < properties.size(); i++) {
      var type = AsyncApiContractConverterUtils.getType(properties.get(i));
      if (type.equals("")) {
        type = properties.get(i).get(BasicTypeConstants.TYPE).get(BasicTypeConstants.TYPE).asText();
      }
      final String fieldName = properties.get(i).get(BasicTypeConstants.NAME).asText();
      final String path = rootProperty + properties.get(i).get(BasicTypeConstants.NAME).asText();

      switch (type) {
        case BasicTypeConstants.STRING:
          final String randomString = RandomStringUtils.random(5, true, false);
          result.put(fieldName, randomString);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
          messageBody.put(fieldName, randomString);
          break;
        case BasicTypeConstants.DATE:
          final String randomDate = RandomGenerator.getRandomDate();
          result.put(fieldName, randomDate);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex(BasicTypeConstants.DATE_REGEX));
          messageBody.put(fieldName, randomDate);
          break;
        case BasicTypeConstants.DATE_TIME:
          final String randomDateTime = RandomGenerator.getRandomDateTime();
          result.put(fieldName, randomDateTime);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex(BasicTypeConstants.DATE_TIME_REGEX));
          messageBody.put(fieldName, randomDateTime);
          break;
        case BasicTypeConstants.TIME:
          final String randomTime = RandomGenerator.getRandomTime();
          result.put(fieldName, randomTime);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex(BasicTypeConstants.TIME_REGEX));
          messageBody.put(fieldName, randomTime);
          break;
        case BasicTypeConstants.INT_64:
        case BasicTypeConstants.LONG:
          final long randomLong = BasicTypeConstants.RANDOM.nextLong();
          result.put(properties.get(i).get(BasicTypeConstants.NAME).asText(), randomLong);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("[0-9]+"));
          messageBody.put(fieldName, randomLong);
          break;
        case BasicTypeConstants.INT_32:
        case BasicTypeConstants.INTEGER:
          final int randomInt = BasicTypeConstants.RANDOM.nextInt();
          result.put(properties.get(i).get(BasicTypeConstants.NAME).asText(), randomInt);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("[0-9]+"));
          messageBody.put(fieldName, randomInt);
          break;
        case BasicTypeConstants.BOOLEAN:
          final List<Boolean> list = new ArrayList<>();
          list.add(true);
          list.add(false);
          final Boolean randomBoolean = list.get(BasicTypeConstants.RANDOM.nextInt(2));
          result.put(fieldName, randomBoolean);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("^(true|false)$"));
          messageBody.put(fieldName, randomBoolean);
          break;
        case BasicTypeConstants.ENUM:
          final var symbols = (ArrayNode) properties.get(i).get("type").get("symbols");
          final var symbol = symbols.get(BasicTypeConstants.RANDOM.nextInt(symbols.size())).textValue();
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("^(" + joinValues(symbols, "|") + ")$"));
          messageBody.put(fieldName, symbol);
          break;
        case BasicTypeConstants.FLOAT:
          final float randomDecimal = BasicTypeConstants.RANDOM.nextFloat() * BasicTypeConstants.RANDOM.nextInt();
          result.put(fieldName, randomDecimal);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("/^\\d*\\.?\\d*$/"));
          messageBody.put(fieldName, randomDecimal);
          break;
        case "record":
          final var subObject = fillObjectPropertiesFromAvro(responseBodyMatchers, (ArrayNode) properties.get(i).get(BasicTypeConstants.TYPE).get("fields"), path + ".");
          result.putIfAbsent(fieldName, subObject.getKey());
          messageBody.put(fieldName, subObject.getValue());
          break;
        default:
          throw new ElementNotFoundException(BasicTypeConstants.TYPE);
      }
    }
    return new MutablePair<>(result, messageBody);
  }

  private String joinValues(final ArrayNode symbols, final String splitter) {
    final var iterator = symbols.elements();
    final var builder = new StringBuilder();
    while (iterator.hasNext()) {
      builder.append(iterator.next().textValue());
      if (iterator.hasNext()) {
        builder.append(splitter);
      }
    }
    return builder.toString();
  }

  private Pair<JsonNode, Map<String, Object>> processAvro(final ResponseBodyMatchers responseBodyMatchers, final JsonNode jsonNode) {
    var avroFilePath = jsonNode.get(BasicTypeConstants.REF).asText();
    if (avroFilePath.matches("^\\w.*$")) {
      avroFilePath = composePath(basePath.getPath(), avroFilePath).toString();
    }
    final var avroFile = new File(avroFilePath);
    JsonNode fileTree = null;

    try {
      fileTree = BasicTypeConstants.OBJECT_MAPPER.readTree(avroFile);
    } catch (final IOException e) {
      log.error("Error", e);
    }
    return fillObjectPropertiesFromAvro(responseBodyMatchers, (ArrayNode) fileTree.get("fields"), "");
  }
}
