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
public final class AsyncApiContractConverter {

  private File basePath;

  public Collection<Contract> convertFrom(final File file) {
    basePath = file;
    final Collection<Contract> sccContracts = new ArrayList<>();

    try {
      final var fileContent = BasicTypeConstants.OBJECT_MAPPER.readTree(basePath);
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
          processSubscribeOperation(contract, bodyProcessed, topicName);
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

  private void processSubscribeOperation(final Contract contract, final Map<String, Object> bodyProcessed, final String topicName) {
    final Input input = new Input();
    input.messageFrom(topicName);
    input.messageBody(bodyProcessed);
    input.messageHeaders(headers -> headers.accept("application/json"));
    contract.setInput(input);

    final OutputMessage outputMessage = new OutputMessage();
    outputMessage.headers(headers -> headers.accept("application/json"));
    outputMessage.sentTo(topicName);
    outputMessage.body(bodyProcessed);
    contract.setOutputMessage(outputMessage);
  }

  private Map<String, Object> processMessage(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode operationContent, final JsonNode fileContent, final String operationType)
      throws IOException {
    final JsonNode message;
    JsonNode propertiesJson;
    final String ref;
    final Map<String, Object> messageBody = new HashMap<>();

    message = operationContent.get("message");
    AsyncApiContractConverterUtils.checkIfReferenceWithProperties(message);

    if (Objects.nonNull(message.get(BasicTypeConstants.REF)) && message.get(BasicTypeConstants.REF).asText().startsWith("#")) {
      final String[] pathToRef = message.get(BasicTypeConstants.REF).asText().split("/");
      ref = pathToRef[pathToRef.length - 1];
      final var payload = fileContent.findPath(ref);

      messageBody.putAll(processSchemas(responseBodyMatchers, operationType, payload.fieldNames().next(), payload, fileContent, ""));

    } else if (Objects.nonNull(message.get(BasicTypeConstants.REF))) {
      final var fillProperties = processAvro(responseBodyMatchers, message);
      messageBody.putAll(fillProperties.getValue());
    } else {
      propertiesJson = message.get(message.fieldNames().next()).get(BasicTypeConstants.PROPERTIES);

      if (Objects.nonNull(propertiesJson.get(BasicTypeConstants.REF)) && propertiesJson.get(BasicTypeConstants.REF).asText().startsWith("#")) {
        final String[] pathToRef = propertiesJson.get(BasicTypeConstants.REF).asText().split("/");
        final var length = pathToRef.length;
        ref = pathToRef[length - 1];
        propertiesJson = fileContent.findPath(ref).get(BasicTypeConstants.PROPERTIES);
        messageBody.putAll(fillObjectProperties(responseBodyMatchers, propertiesJson, "", operationType, fileContent));
      } else if (Objects.nonNull(propertiesJson.get(BasicTypeConstants.REF)) && propertiesJson.get(BasicTypeConstants.REF).asText().contains(".yml")) {
        final String[] pathToRef = propertiesJson.get(BasicTypeConstants.REF).asText().split("#");
        messageBody.putAll(processExternalFile(pathToRef[0], pathToRef[1], responseBodyMatchers, operationType, ""));
      } else {
        messageBody.putAll(fillObjectProperties(responseBodyMatchers, propertiesJson, "", operationType, fileContent));
      }

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

    if (Objects.nonNull(payload.get(fieldName).get(BasicTypeConstants.REF)) && payload.get(fieldName).get(BasicTypeConstants.REF).asText().startsWith("#")) {
      final String[] pathToRef = payload.get(fieldName).get(BasicTypeConstants.REF).asText().split("/");
      final var length = pathToRef.length;
      ref = pathToRef[length - 1];
      properties = fileContent.findPath(ref).get(BasicTypeConstants.PROPERTIES);
      messageBody.putAll(processProperties(responseBodyMatchers, operationType, fileContent, bodyMatcherPath, properties));

    } else if (Objects.nonNull(payload.get(fieldName).get(BasicTypeConstants.REF)) && payload.get(fieldName).get(BasicTypeConstants.REF).asText().contains(".yml")) {
      final String[] pathToRef = payload.get(fieldName).get(BasicTypeConstants.REF).asText().split("#");
      messageBody.putAll(processExternalFile(pathToRef[0], pathToRef[1], responseBodyMatchers, operationType, bodyMatcherPath));
    } else if (Objects.nonNull(payload.get(fieldName).get(BasicTypeConstants.REF))) {
      final var fillProperties = processAvro(responseBodyMatchers, payload);
      messageBody.putAll(fillProperties.getValue());
    } else {
      if (Objects.nonNull(payload.get(BasicTypeConstants.PAYLOAD).get(BasicTypeConstants.PROPERTIES))) {
        properties = payload.get(BasicTypeConstants.PAYLOAD).get(BasicTypeConstants.PROPERTIES);
        messageBody.putAll(processProperties(responseBodyMatchers, operationType, fileContent, bodyMatcherPath, properties));

      } else {
        if (Objects.nonNull(payload.get(BasicTypeConstants.PAYLOAD))) {
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

      if (Objects.nonNull(properties.get(propertyName).get(BasicTypeConstants.REF))) {
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

      if (Objects.nonNull(schema.get(BasicTypeConstants.REF)) && schema.get(BasicTypeConstants.REF).asText().contains(".yml")) {
        final String[] pathToRef = schema.get(BasicTypeConstants.REF).asText().split("#");
        messageBody.putAll(processExternalFile(pathToRef[0], pathToRef[1], responseBodyMatchers, operationType, bodyMatcherPath));
      } else if (Objects.nonNull(schema.get(fieldName).get(BasicTypeConstants.REF))) {
        final var bodyMatcherPathObject = bodyMatcherPath + fieldName + ".";
        messageBody.put(fieldName, processSchemas(responseBodyMatchers, operationType, fieldName, schema, externalFileContent, bodyMatcherPathObject));
      } else {
        final ObjectNode propertiesToFill = BasicTypeConstants.OBJECT_MAPPER.createObjectNode();
        propertiesToFill.set(fieldName, schema.get(fieldName));
        messageBody.putAll(fillObjectProperties(responseBodyMatchers, propertiesToFill, bodyMatcherPath, operationType, externalFileContent));
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

  private Map<String, Object> fillObjectProperties(
      final ResponseBodyMatchers responseBodyMatchers, final JsonNode properties, final String rootProperty, final String operationType,
      final JsonNode fileContent) throws IOException {

    final Iterator<String> fieldNames = properties.fieldNames();
    final Map<String, Object> messageBody = new HashMap<>();

    while (fieldNames.hasNext()) {
      final var property = fieldNames.next();
      final var path = rootProperty + property;
      if (!Objects.nonNull(properties.get(property).get(BasicTypeConstants.PROPERTIES))
          || !Objects.nonNull(properties.get(property).get(BasicTypeConstants.PROPERTIES).get(BasicTypeConstants.REF))) {
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
    List<Object> resultArray = new ArrayList<>();
    JsonNode internalProperties = properties;

    AsyncApiContractConverterUtils.checkIfReferenceWithProperties(properties);

    if (Objects.nonNull(properties.get(BasicTypeConstants.REF)) && properties.get(BasicTypeConstants.REF).asText().contains(".yml")) {
      final String[] pathToSchema = properties.get(BasicTypeConstants.REF).asText().split("#");
      resultArray.add(processExternalFile(pathToSchema[0], pathToSchema[1], responseBodyMatchers, operationType, path));
    } else {
      if (Objects.nonNull(properties.get(BasicTypeConstants.REF)) && properties.get(BasicTypeConstants.REF).asText().startsWith("#")) {
        final String[] pathToObject = properties.get(BasicTypeConstants.REF).asText().split("/");
        final var body = pathToObject[pathToObject.length - 1];
        internalProperties = node.findPath(body);
      }
      resultArray = processInternalArray(responseBodyMatchers, property, internalProperties, path, operationType, node);
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
        case BasicTypeConstants.INT_32:
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

  private Pair<JsonNode, Map<String, Object>> processAvro(final ResponseBodyMatchers responseBodyMatchers, final JsonNode jsonNode) {
    final File avroFile = new File(jsonNode.get(BasicTypeConstants.REF).asText());
    JsonNode fileTree = null;
    try {
      fileTree = BasicTypeConstants.OBJECT_MAPPER.readTree(avroFile);
    } catch (final IOException e) {
      log.error("Error", e);
    }
    return fillObjectPropertiesFromAvro(responseBodyMatchers, (ArrayNode) fileTree.get("fields"), "");
  }
}
