package com.corunet.multiapi.converter.asyncapi;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import com.corunet.multiapi.converter.asyncapi.exception.DuplicatedOperationException;
import com.corunet.multiapi.converter.asyncapi.exception.ElementNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.PathItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.ContractConverter;
import org.springframework.cloud.contract.spec.internal.Input;
import org.springframework.cloud.contract.spec.internal.OutputMessage;
import org.springframework.cloud.contract.spec.internal.RegexPatterns;
import org.springframework.cloud.contract.spec.internal.RegexProperty;
import org.springframework.cloud.contract.spec.internal.ResponseBodyMatchers;

@Slf4j
public class AsyncApiContractConverter implements ContractConverter<Collection<PathItem>> {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

  private static final String ASYNCAPI = "asyncapi";

  private static final String CHANNELS = "channels";

  private static final String SUBSCRIBE = "subscribe";

  private static final String PUBLISH = "publish";

  private static final String REF = "$ref";

  private static final String PROPERTIES = "properties";

  private static final String SCHEMAS = "schemas";

  private static final String FORMAT = "format";

  private static final String EXAMPLE = "example";

  private static final String STRING_TYPE = "string";

  private static final String NUMBER_TYPE = "number";

  private static final String INT32_TYPE = "int32";

  private static final String INT64_TYPE = "int64";

  private static final String FLOAT_TYPE = "float";

  private static final String DOUBLE_TYPE = "double";

  private static final String BOOLEAN_TYPE = "boolean";

  private static final String TYPE = "type";

  public static final Random RANDOM = new Random();

  public static final RegexProperty STRING_REGEX = RegexPatterns.alphaNumeric();

  public static final RegexProperty INT_REGEX = RegexPatterns.positiveInt();

  public static final RegexProperty DECIMAL_REGEX = RegexPatterns.number();

  public static final RegexProperty BOOLEAN_REGEX = RegexPatterns.anyBoolean();


  private final List<String> processedOperationIds = new ArrayList<>();

  @Override
  public boolean isAccepted(final File file) {
    String name = file.getName();
    boolean isAccepted = name.endsWith(".yml") || name.endsWith(".yaml");
    if (isAccepted) {
      JsonNode node;
      try {
        node = OBJECT_MAPPER.readTree(file);
        isAccepted = (node != null && node.size() > 0 && Objects.nonNull(node.get(ASYNCAPI)));
      } catch (IOException e) {
        isAccepted = false;
      }
    }
    return isAccepted;
  }

  @Override
  public Collection<Contract> convertFrom(final File file) {
    Collection<Contract> sccContracts = new ArrayList<>();

    try {
      var node = OBJECT_MAPPER.readTree(file);
      var channelsNode = node.get(CHANNELS);

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
        if (operationType.equals(SUBSCRIBE)) {
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

        } else if (operationType.equals(PUBLISH)) {
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

  @Override
  public Collection<PathItem> convertTo(final Collection<Contract> contract) {
    return Collections.emptyList();
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

    if (message.get(REF) != null && message.get(REF).asText().startsWith("#")) {
      String[] pathToObject = message.get(REF).asText().split("/");
      body = pathToObject[pathToObject.length - 1];
      component = node.get("components");
      var payload = component.get("messages").get(body).get("payload");

      if (payload.get(REF) != null && payload.get(REF).asText().startsWith("#")) {
        String[] pathToObject2 = payload.get(REF).asText().split("/");
        body = pathToObject2[pathToObject.length - 1];
        schema = component.get(SCHEMAS).get(body);
        propertiesJson = schema.get(PROPERTIES);
        messageBody = fillObjectProperties(responseBodyMatchers, propertiesJson, component.get(SCHEMAS), "", operationType);
      } else if (payload.get(REF) != null) {
        var fillProperties = processAvro(responseBodyMatchers, payload);
        messageBody = fillProperties.getValue();
      } else {
        propertiesJson = payload.get(payload.fieldNames().next()).get(PROPERTIES);
        messageBody = fillObjectProperties(responseBodyMatchers, propertiesJson, component.get(SCHEMAS), "", operationType);
      }

    } else if (message.get(REF) != null) {
      var fillProperties = processAvro(responseBodyMatchers, message);
      messageBody = fillProperties.getValue();
    } else {
      propertiesJson = message.get(message.fieldNames().next()).get(PROPERTIES);
      messageBody = fillObjectProperties(responseBodyMatchers, propertiesJson, null, "", operationType);
    }
    return messageBody;
  }

  private JsonNode subscribeOrPublishOperation(JsonNode rootNode) {
    JsonNode result;

    if (rootNode.has(SUBSCRIBE)) {
      result = rootNode.get(SUBSCRIBE);
    } else {
      result = rootNode.get(PUBLISH);
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

      if (!Objects.nonNull(properties.get(property).get(PROPERTIES)) || !Objects.nonNull(properties.get(property).get(PROPERTIES).get(REF))) {
        String enumType = "";
        var type = (properties.get(String.valueOf(property)).get(FORMAT) != null) ? properties.get(String.valueOf(property)).get(FORMAT).asText() :
            properties.get(String.valueOf(property)).get(TYPE).asText();

        if (isEnum(properties.get(property))) {
          enumType = type;
          type = "enum";
        }

        switch (type) {
          case STRING_TYPE:
            if (operationType.equals(SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(EXAMPLE).asText());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(STRING_REGEX));
              messageBody.put(property, RandomStringUtils.random(5, true, false));
            }
            break;
          case INT32_TYPE:
          case NUMBER_TYPE:
            if (operationType.equals(SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(EXAMPLE).asInt());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(INT_REGEX));
              messageBody.put(property, RANDOM.nextInt());
            }
            break;
          case INT64_TYPE:
          case FLOAT_TYPE:
            if (operationType.equals(SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(EXAMPLE).asDouble());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(DECIMAL_REGEX));
              messageBody.put(property, Math.abs(RANDOM.nextFloat()));
            }
            break;
          case DOUBLE_TYPE:
            if (operationType.equals(SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(EXAMPLE).asDouble());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(DECIMAL_REGEX));
              messageBody.put(property, RANDOM.nextDouble());
            }
            break;
          case BOOLEAN_TYPE:
            if (operationType.equals(SUBSCRIBE)) {
              messageBody.put(property, properties.get(property).get(EXAMPLE).asBoolean());
            } else {
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex("^(true|false)$"));
              messageBody.put(property, RANDOM.nextBoolean());
            }
            break;
          case "enum":
            if (operationType.equals(SUBSCRIBE)) {
              messageBody.put(property, processEnumTypes(properties.get(property).get(EXAMPLE), enumType));
            } else {
              var enumList = properties.get(property).get("enum");
              responseBodyMatchers.jsonPath(path, responseBodyMatchers.byRegex(getEnumRegex(enumType, properties, property)));
              messageBody.put(property, processEnumTypes(enumList.get(RANDOM.nextInt(enumList.size())), enumType));
            }
            break;
          case "object":
            messageBody.put(property, fillObjectProperties(responseBodyMatchers, properties.get(property).get(PROPERTIES), schemas, path + ".", operationType));
            break;
          case "array":
            messageBody.put(property, processArray(responseBodyMatchers, property, properties.get(property).get("items"), path, operationType));
            break;
          default:
            throw new ElementNotFoundException(TYPE);
        }
      } else {
        String[] pathToObject = properties.get(property).get(PROPERTIES).get(REF).asText().split("/");
        var body = pathToObject[pathToObject.length - 1];
        var schema = schemas.get(body).get(PROPERTIES);
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

    if (node.get(FORMAT) != null) {
      type = node.get(FORMAT).asText();
    } else if (node.get(TYPE) != null) {
      type = node.get(TYPE).asText();
    } else {
      type = node.get(node.fieldNames().next()).get(TYPE).asText();
    }

    if (isEnum(node)) {
      enumType = type;
      type = "enum";
    }

    switch (type) {
      case STRING_TYPE:
        if (operationType.equals(SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(node.toString()).get(EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(arrayNode.get(i).asText());
          }
        } else {
          result.add(RandomStringUtils.random(5, true, false));
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(STRING_REGEX));
          }
        }
        break;
      case INT32_TYPE:
      case NUMBER_TYPE:
        if (operationType.equals(SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(node.toString()).get(EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(arrayNode.get(i).asInt());
          }
        } else {
          result.add(RANDOM.nextInt());
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(INT_REGEX));
          }
        }
        break;
      case INT64_TYPE:
      case FLOAT_TYPE:
        if (operationType.equals(SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(node.toString()).get(EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(arrayNode.get(i).asDouble());
          }
        } else {
          result.add(Math.abs(RANDOM.nextFloat()));
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(DECIMAL_REGEX));
          }
        }
        break;
      case DOUBLE_TYPE:
        if (operationType.equals(SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(node.toString()).get(EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(arrayNode.get(i).asDouble());
          }
        } else {
          result.add(RANDOM.nextDouble());
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(DECIMAL_REGEX));
          }
        }
        break;
      case BOOLEAN_TYPE:
        if (operationType.equals(SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(node.toString()).get(EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(arrayNode.get(i).asBoolean());
          }
        } else {
          result.add(RANDOM.nextBoolean());
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(BOOLEAN_REGEX));
          }
        }
        break;
      case "enum":
        if (operationType.equals(SUBSCRIBE)) {
          var arrayNode = objectMapper.readTree(node.toString()).get(EXAMPLE);
          for (int i = 0; i < arrayNode.size(); i++) {
            result.add(processEnumTypes(arrayNode.get(i), enumType));
          }
        } else {
          var enumList = node.get("enum");
          result.add(processEnumTypes(enumList.get(RANDOM.nextInt(enumList.size())), enumType));
          if (isNotRegexIncluded(responseBodyMatchers, path + "[0]")) {
            responseBodyMatchers.jsonPath(path + "[0]", responseBodyMatchers.byRegex(getEnumRegex(enumType, node, property)));
          }
        }
        break;
      case "object":
        result.add(fillObjectProperties(responseBodyMatchers, node.get(node.fieldNames().next()).get(PROPERTIES), node, path + ".", operationType));
        break;
      default:
        throw new ElementNotFoundException(TYPE);
    }
    return result;
  }

  private Object processEnumTypes(JsonNode value, String type) {
    Object enumValue;

    switch (type) {
      case STRING_TYPE:
        enumValue = value.asText();
        break;
      case INT32_TYPE:
      case NUMBER_TYPE:
        enumValue = value.asInt();
        break;
      case INT64_TYPE:
      case FLOAT_TYPE:
      case DOUBLE_TYPE:
        enumValue = value.asDouble();
        break;
      case BOOLEAN_TYPE:
        enumValue = value.asBoolean();
        break;
      default:
        throw new ElementNotFoundException(TYPE);
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
      if (STRING_TYPE.equalsIgnoreCase(type)) {
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
      var type = (properties.get(i).get(FORMAT) != null) ? properties.get(i).get(FORMAT).asText() :
          properties.get(i).get(TYPE).asText();
      if (type.equals("")) {
        type = properties.get(i).get(TYPE).get(TYPE).asText();
      }
      String fieldName = properties.get(i).get("name").asText();
      String path = rootProperty + properties.get(i).get("name").asText();

      switch (type) {
        case STRING_TYPE:
          String randomString = RandomStringUtils.random(5, true, false);
          result.put(fieldName, randomString);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex(STRING_REGEX));
          messageBody.put(fieldName, randomString);
          break;
        case "int":
          int randomInt = RANDOM.nextInt();
          result.put(properties.get(i).get("name").asText(), randomInt);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("[0-9]+"));
          messageBody.put(fieldName, randomInt);
          break;
        case BOOLEAN_TYPE:
          List<Boolean> list = new ArrayList<>();
          list.add(true);
          list.add(false);
          Boolean randomBoolean = list.get(RANDOM.nextInt(2));
          result.put(fieldName, randomBoolean);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("^(true|false)$"));
          messageBody.put(fieldName, randomBoolean);
          break;
        case "decimal":
          float randomDecimal = RANDOM.nextFloat() * RANDOM.nextInt();
          result.put(fieldName, randomDecimal);
          responseBodyMatchers.jsonPath("$." + path, responseBodyMatchers.byRegex("/^\\d*\\.?\\d*$/"));
          messageBody.put(fieldName, randomDecimal);
          break;
        case "record":
          var subObject = fillObjectPropertiesFromAvro(responseBodyMatchers, (ArrayNode) properties.get(i).get(TYPE).get("fields"), path + ".");
          result.putIfAbsent(fieldName, subObject.getKey());
          messageBody.put(fieldName, subObject.getValue());
          break;
        default:
          throw new ElementNotFoundException(TYPE);
      }
    }
    return new MutablePair<>(result, messageBody);
  }

  private Pair<JsonNode, Map<String, Object>> processAvro(ResponseBodyMatchers responseBodyMatchers, JsonNode jsonNode) {
    File avroFile = new File(jsonNode.get(REF).asText());
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
