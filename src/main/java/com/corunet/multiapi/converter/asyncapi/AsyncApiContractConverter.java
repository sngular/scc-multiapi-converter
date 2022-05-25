package com.corunet.multiapi.converter.asyncapi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.corunet.multiapi.converter.BasicTypeConstants;
import com.corunet.multiapi.converter.asyncapi.exception.DuplicatedOperationException;
import com.corunet.multiapi.converter.asyncapi.exception.ElementNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.Input;
import org.springframework.cloud.contract.spec.internal.OutputMessage;
import org.springframework.cloud.contract.spec.internal.ResponseBodyMatchers;

@Slf4j
public class AsyncApiContractConverter  {

  private final List<String> processedOperationIds = new ArrayList<>();


  public Collection<Contract> convertFrom(final File file) {
    Collection<Contract> sccContracts = new ArrayList<>();

    try {
      var node = BasicTypeConstants.OBJECT_MAPPER.readTree(file);
      var channelsNode = node.get(BasicTypeConstants.CHANNELS);

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
        String operationId = getOperationId(operationContent);
        contract.setName(operationId);
        ResponseBodyMatchers responseBodyMatchers = new ResponseBodyMatchers();
        bodyProcessed = processMessage(responseBodyMatchers, operationContent, node, operationType);
        contract.label(operationId);

        String topicName = topicIterator.next();
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          Input input = new Input();
          input.messageFrom(topicName);
          input.messageBody(bodyProcessed);
          input.messageHeaders(headers -> headers.accept("application/json"));
          input.assertThat(operationId + "()");
          contract.setInput(input);

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

  private String getOperationId(JsonNode operationType) {
    String operationId;

    operationId = operationType.get("operationId").asText();

    if (processedOperationIds.contains(operationId)) {
      throw new DuplicatedOperationException(operationId);
    } else {
      processedOperationIds.add(operationId);
    }
    return operationId;
  }

  private Map<String, Object> processMessage(ResponseBodyMatchers responseBodyMatchers, JsonNode operationContent, JsonNode node, String operationType) throws JsonProcessingException {
    JsonNode message;
    JsonNode component;
    JsonNode schema;
    JsonNode propertiesJson;
    String body;
    Map<String, Object> messageBody;

    message = operationContent.get("message");

    if (message.get(BasicTypeConstants.REF) != null && message.get(BasicTypeConstants.REF).asText().startsWith("#")) {
      String[] pathToObject = message.get(BasicTypeConstants.REF).asText().split("/");
      body = pathToObject[pathToObject.length - 1];
      component = node.get("components");
      var payload = component.get("messages").get(body).get("payload");

      if (payload.get(BasicTypeConstants.REF) != null && payload.get(BasicTypeConstants.REF).asText().startsWith("#")) {
        String[] pathToObject2 = payload.get(BasicTypeConstants.REF).asText().split("/");
        body = pathToObject2[pathToObject.length - 1];
        schema = component.get(BasicTypeConstants.SCHEMAS).get(body);
        propertiesJson = schema.get(BasicTypeConstants.PROPERTIES);
        messageBody = fillObjectProperties(responseBodyMatchers, propertiesJson, component.get(BasicTypeConstants.SCHEMAS), "", operationType);
      } else if (payload.get(BasicTypeConstants.REF) != null) {
        var fillProperties = processAvro(responseBodyMatchers, payload);
        messageBody = fillProperties.getValue();
      } else {
        propertiesJson = payload.get(payload.fieldNames().next()).get(BasicTypeConstants.PROPERTIES);
        messageBody = fillObjectProperties(responseBodyMatchers, propertiesJson, component.get(BasicTypeConstants.SCHEMAS), "", operationType);
      }

    } else if (message.get(BasicTypeConstants.REF) != null) {
      var fillProperties = processAvro(responseBodyMatchers, message);
      messageBody = fillProperties.getValue();
    } else {
      propertiesJson = message.get(message.fieldNames().next()).get(BasicTypeConstants.PROPERTIES);
      messageBody = fillObjectProperties(responseBodyMatchers, propertiesJson, null, "", operationType);
    }
    return messageBody;
  }

  private JsonNode subscribeOrPublishOperation(JsonNode rootNode) {
    JsonNode result;

    if (rootNode.has(BasicTypeConstants.SUBSCRIBE)) {
      result = rootNode.get(BasicTypeConstants.SUBSCRIBE);
    } else {
      result = rootNode.get(BasicTypeConstants.PUBLISH);
    }
    return result;
  }

  private Map<String, Object> fillObjectProperties(ResponseBodyMatchers responseBodyMatchers, JsonNode properties, JsonNode schemas, String rootProperty, String operationType)
      throws JsonProcessingException {
    Iterator<String> it = properties.fieldNames();
    Map<String, Object> messageBody = new HashMap<>();

    while (it.hasNext()) {
      var property = it.next();
      var path = rootProperty + property;

      if (!Objects.nonNull(properties.get(property).get(BasicTypeConstants.PROPERTIES)) || !Objects.nonNull(properties.get(property).get(BasicTypeConstants.PROPERTIES).get(BasicTypeConstants.REF))) {
        String enumType = "";
        var type = (properties.get(String.valueOf(property)).get(BasicTypeConstants.FORMAT) != null) ? properties.get(String.valueOf(property)).get(BasicTypeConstants.FORMAT).asText() :
            properties.get(String.valueOf(property)).get(BasicTypeConstants.TYPE).asText();

        if (isEnum(properties.get(property))) {
          enumType = type;
          type = "enum";
        }

        switch (type) {
          case BasicTypeConstants.STRING_TYPE:
            if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asText());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
              messageBody.put(property, RandomStringUtils.random(5, true, false));
            }
            break;
          case BasicTypeConstants.INT32_TYPE:
          case BasicTypeConstants.NUMBER_TYPE:
            if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asInt());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
              messageBody.put(property, BasicTypeConstants.RANDOM.nextInt());
            }
            break;
          case BasicTypeConstants.INT64_TYPE:
          case BasicTypeConstants.FLOAT_TYPE:
            if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asDouble());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
              messageBody.put(property, Math.abs(BasicTypeConstants.RANDOM.nextFloat()));
            }
            break;
          case BasicTypeConstants.DOUBLE_TYPE:
            if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asDouble());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
              messageBody.put(property, BasicTypeConstants.RANDOM.nextDouble());
            }
            break;
          case BasicTypeConstants.BOOLEAN_TYPE:
            if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(BasicTypeConstants.EXAMPLE).asBoolean());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex("^(true|false)$"));
              messageBody.put(property, BasicTypeConstants.RANDOM.nextBoolean());
            }
            break;
          case "enum":
            if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
              messageBody.put(property, processEnumTypes(properties.get(property).get(BasicTypeConstants.EXAMPLE), enumType));
            } else {
              var enumList = properties.get(property).get("enum");
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(getEnumRegex(enumType, properties, property)));
              messageBody.put(property, processEnumTypes(enumList.get(BasicTypeConstants.RANDOM.nextInt(enumList.size())), enumType));
            }
            break;
          case "object":
            messageBody.put(property, fillObjectProperties(responseBodyMatchers, properties.get(property).get(BasicTypeConstants.PROPERTIES), schemas, path + ".", operationType));
            break;
          case "array":
            messageBody.put(property, processArray(responseBodyMatchers, property, properties.get(property).get("items"), path, operationType));
            break;
          default:
            throw new ElementNotFoundException(BasicTypeConstants.TYPE);
        }
      } else {
        String[] pathToObject = properties.get(property).get(BasicTypeConstants.PROPERTIES).get(BasicTypeConstants.REF).asText().split("/");
        var body = pathToObject[pathToObject.length - 1];
        var schema = schemas.get(body).get(BasicTypeConstants.PROPERTIES);
        messageBody.put(property, fillObjectProperties(responseBodyMatchers, schema, schemas, path + ".", operationType));
      }
    }

    return messageBody;
  }

  private List<Object> processArray(ResponseBodyMatchers responseBodyMatchers, String property, JsonNode node, String path, String operationType) throws JsonProcessingException {
    final List<Object> result = new ArrayList<>();
    final ObjectMapper objectMapper = new ObjectMapper();
    String enumType = "";
    String type;

    if (node.get(BasicTypeConstants.FORMAT) != null) {
      type = node.get(BasicTypeConstants.FORMAT).asText();
    } else if (node.get(BasicTypeConstants.TYPE) != null) {
      type = node.get(BasicTypeConstants.TYPE).asText();
    } else {
      type = node.get(node.fieldNames().next()).get(BasicTypeConstants.TYPE).asText();
    }

    if (isEnum(node)) {
      enumType = type;
      type = "enum";
    }

    switch (type) {
      case BasicTypeConstants.STRING_TYPE:
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(node.toString()).get(BasicTypeConstants.EXAMPLE);
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
      case BasicTypeConstants.INT32_TYPE:
      case BasicTypeConstants.NUMBER_TYPE:
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(node.toString()).get(BasicTypeConstants.EXAMPLE);
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
      case BasicTypeConstants.INT64_TYPE:
      case BasicTypeConstants.FLOAT_TYPE:
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(node.toString()).get(BasicTypeConstants.EXAMPLE);
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
      case BasicTypeConstants.DOUBLE_TYPE:
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(node.toString()).get(BasicTypeConstants.EXAMPLE);
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
      case BasicTypeConstants.BOOLEAN_TYPE:
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(node.toString()).get(BasicTypeConstants.EXAMPLE);
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
      case "enum":
        if (operationType.equals(BasicTypeConstants.SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(node.toString()).get(BasicTypeConstants.EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(processEnumTypes(arrayNode.get(i), enumType));
          }
        } else {
          var enumList = node.get("enum");
          result.add(processEnumTypes(enumList.get(BasicTypeConstants.RANDOM.nextInt(enumList.size())), enumType));
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(getEnumRegex(enumType, node, property)));
          }
        }
        break;
      case "object":
        result.add(fillObjectProperties(responseBodyMatchers, node.get(node.fieldNames().next()).get(BasicTypeConstants.PROPERTIES), node, path + ".", operationType));
        break;
      default:
        throw new ElementNotFoundException(BasicTypeConstants.TYPE);
    }
    return result;
  }

  private Object processEnumTypes(JsonNode value, String type) {
    Object enumValue;

    switch (type) {
      case BasicTypeConstants.STRING_TYPE:
        enumValue = value.asText();
        break;
      case BasicTypeConstants.INT32_TYPE:
      case BasicTypeConstants.NUMBER_TYPE:
        enumValue = value.asInt();
        break;
      case BasicTypeConstants.INT64_TYPE:
      case BasicTypeConstants.FLOAT_TYPE:
      case BasicTypeConstants.DOUBLE_TYPE:
        enumValue = value.asDouble();
        break;
      case BasicTypeConstants.BOOLEAN_TYPE:
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
    Iterator<JsonNode> enumObjects = properties.get(property).get("enum").iterator();
    while (enumObjects.hasNext()) {
      if (BasicTypeConstants.STRING_TYPE.equalsIgnoreCase(type)) {
        while (enumObjects.hasNext()) {
          JsonNode nextObject = enumObjects.next();
          if (!enumObjects.hasNext()) {
            log.info(nextObject.asText());
            regex = regex.concat(nextObject.asText());
          } else {
            regex = regex.concat(nextObject.asText() + "|");
          }
        }
      }
    }
    log.info(regex);
    return regex;
  }

  private boolean isEnum(final JsonNode properties) {
    boolean isEnum = false;
    Iterator<String> ite = properties.fieldNames();

    while (ite.hasNext()) {
      if (ite.next().equals("enum")) {
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
      var type = (properties.get(i).get(BasicTypeConstants.FORMAT) != null) ? properties.get(i).get(BasicTypeConstants.FORMAT).asText() :
          properties.get(i).get(BasicTypeConstants.TYPE).asText();
      if (type.equals("")) {
        type = properties.get(i).get(BasicTypeConstants.TYPE).get(BasicTypeConstants.TYPE).asText();
      }
      String fieldName = properties.get(i).get("name").asText();
      String path = rootProperty + properties.get(i).get("name").asText();

      switch (type) {
        case BasicTypeConstants.STRING_TYPE:
          String randomString = RandomStringUtils.random(5, true, false);
          result.put(fieldName, randomString);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
          messageBody.put(fieldName, randomString);
          break;
        case "int":
          int randomInt = BasicTypeConstants.RANDOM.nextInt();
          result.put(properties.get(i).get("name").asText(), randomInt);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("[0-9]+"));
          messageBody.put(fieldName, randomInt);
          break;
        case BasicTypeConstants.BOOLEAN_TYPE:
          List<Boolean> list = new ArrayList<>();
          list.add(true);
          list.add(false);
          Boolean randomBoolean = list.get(BasicTypeConstants.RANDOM.nextInt(2));
          result.put(fieldName, randomBoolean);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("^(true|false)$"));
          messageBody.put(fieldName, randomBoolean);
          break;
        case "decimal":
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
