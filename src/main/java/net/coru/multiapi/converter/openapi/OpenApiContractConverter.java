/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


package net.coru.multiapi.converter.openapi;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.exception.ReadContentException;
import lombok.extern.slf4j.Slf4j;
import net.coru.multiapi.converter.exception.MultiApiContractConverterException;
import net.coru.multiapi.converter.openapi.model.ConverterPathItem;
import net.coru.multiapi.converter.openapi.model.OperationType;
import net.coru.multiapi.converter.utils.BasicTypeConstants;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.Body;
import org.springframework.cloud.contract.spec.internal.BodyMatcher;
import org.springframework.cloud.contract.spec.internal.BodyMatchers;
import org.springframework.cloud.contract.spec.internal.DslProperty;
import org.springframework.cloud.contract.spec.internal.Headers;
import org.springframework.cloud.contract.spec.internal.QueryParameters;
import org.springframework.cloud.contract.spec.internal.Request;
import org.springframework.cloud.contract.spec.internal.Response;
import org.springframework.cloud.contract.spec.internal.ResponseBodyMatchers;
import org.springframework.cloud.contract.spec.internal.Url;
import org.springframework.cloud.contract.spec.internal.UrlPath;

@Slf4j
public final class OpenApiContractConverter {

  private static final Map<String, Example> EXAMPLES_MAP = new LinkedHashMap<>();
  private final Map<String, Schema> componentsMap = new LinkedHashMap<>();


  private static Pair<Body, BodyMatchers> getBodyFromMap(final String property, final Map<String, Object> bodyProperties, final BodyMatchers bodyMatchers) {
    final Body body;
    if (Objects.nonNull(property)) {
      body = new Body(Map.of(property, bodyProperties));
    } else {
      body = new Body(bodyProperties);
    }
    return Pair.of(body, bodyMatchers);
  }

  private static Pair<Body, BodyMatchers> getBodyMatcher(final String property, final Pair<Object, BodyMatchers> bodyValue) {
    final Body body;
    if (Objects.nonNull(property)) {
      body = new Body(Map.of(property, bodyValue.getLeft()));
    } else {
      body = new Body(bodyValue.getLeft());
    }
    return Pair.of(body, bodyValue.getRight());
  }

  private static Pair<Body, BodyMatchers> getBodyMatcher(final String property, final List<Pair<Body, BodyMatchers>> bodyValue) {
    final Body body;
    final List<Body> bodies = new LinkedList<>();
    final BodyMatchers bodyMatchers = new BodyMatchers();
    for (var propBody : bodyValue) {
      bodies.add(propBody.getLeft());
      bodyMatchers.matchers().addAll(propBody.getRight().matchers());
    }
    if (Objects.nonNull(property)) {
      body = new Body(Map.of(property, bodies));
    } else {
      body = new Body(bodies);
    }
    return Pair.of(body, bodyMatchers);
  }

  private static String getPropertyType(final Entry<String, Schema> property) {
    final String type;
    if (Objects.nonNull(property.getValue().getEnum())) {
      type = BasicTypeConstants.ENUM;
    } else {
      type = property.getValue().getType();
    }
    return type;
  }

  public Collection<Contract> convertFrom(final File file) {

    final Collection<Contract> contracts = new ArrayList<>();

    try {
      contracts.addAll(getContracts(getOpenApi(file)));
    } catch (final MultiApiContractConverterException e) {
      log.error("Error processing the file", e);
    }
    return contracts;
  }

  private Collection<Contract> getContracts(final OpenAPI openApi) {

    extractComponents(openApi);

    final List<Contract> contracts = new ArrayList<>();

    for (Entry<String, PathItem> pathItem : openApi.getPaths().entrySet()) {
      extractPathItem(pathItem.getValue())
          .forEach(converterPathItem ->
                   processContract(contracts, pathItem, converterPathItem.getOperation(), converterPathItem.getOperationType()));
    }
    return contracts;
  }

  private List<ConverterPathItem> extractPathItem(final PathItem pathItem) {
    final var pathItemList = new ArrayList<ConverterPathItem>();
    pathItem
        .readOperationsMap()
        .forEach((method, operation) -> {
          if (OperationType.isValid(method.name())) {
            pathItemList.add(ConverterPathItem.builder().operationType(OperationType.valueOf(method.name())).operation(operation).build());
          }
        });
    return pathItemList;
  }

  private void extractComponents(final OpenAPI openApi) {
    if (Objects.nonNull(openApi.getComponents())) {
      if (Objects.nonNull(openApi.getComponents().getSchemas())) {
        componentsMap.putAll(openApi.getComponents().getSchemas());
      }
      if (Objects.nonNull(openApi.getComponents().getExamples())) {
        EXAMPLES_MAP.putAll(openApi.getComponents().getExamples());
      }
    }
  }

  private void processContract(final List<Contract> contracts, final Entry<String, PathItem> pathItem, final Operation operation, final OperationType name) {
    for (Entry<String, ApiResponse> apiResponse : operation.getResponses().entrySet()) {
      final String fileName = name + pathItem.getKey().replaceAll("[{}]", "") + apiResponse.getKey().substring(0, 1).toUpperCase() + apiResponse.getKey().substring(1) + "Response";
      final String contractName = fileName.replace("/", "");
      final String contractDescription = pathItem.getValue().getSummary();
      final var requestList = processRequest(pathItem, operation, name.name());
      final var responseList = processResponse(apiResponse.getKey(), apiResponse.getValue());
      for (var request : requestList) {
        final var counter = new AtomicInteger(0);
        for (var response : responseList) {
          contracts.add(createContract(contractName, contractDescription, request, response, counter));
        }
      }
    }
  }

  private static Contract createContract(final String contractName, final String contractDescription, final Request request, final Response response, final AtomicInteger counter) {
    final Contract contract = new Contract();
    contract.setName(contractName + "_" + counter.getAndIncrement());
    contract.setDescription(contractDescription);
    if (Objects.nonNull(response)) {
      contract.setResponse(response);
    }
    if (Objects.nonNull(request)) {
      contract.setRequest(request);
    }
    return contract;
  }

  private List<Response> processResponse(final String name, final ApiResponse apiResponse) {
    final var responseList = new ArrayList<Response>();
    if (Objects.nonNull(apiResponse)) {
      if (Objects.nonNull(apiResponse.getContent())) {
        for (Entry<String, MediaType> content : apiResponse.getContent().entrySet()) {
          responseList.addAll(processContent(name, content));
        }
      } else {
        final var response = new Response();
        response.body(response.anyAlphaNumeric());
        response.setBodyMatchers(new ResponseBodyMatchers());
        responseList.add(response);
      }
    }
    return responseList;
  }

  private List<Response> processContent(final String name, final Entry<String, MediaType> content) {
    final List<Response> responseList = new LinkedList<>();
    final MediaType mediaType = content.getValue();
    final Headers headers = new Headers();
    headers.contentType(content.getKey());
    headers.accept();
    final var bodyList = processResponseBody(mediaType.getSchema());
    if (Objects.nonNull(mediaType.getExample())) {
      bodyList.add(buildFromExample(mediaType.getExample()));
    } else if (Objects.nonNull(mediaType.getExamples())) {
      mediaType.getExamples().forEach((key, example) -> bodyList.add(buildFromExample(example)));
    }
    for (var body : bodyList) {
      final var response = new Response();
      final var responseBodyMatcher = new ResponseBodyMatchers();
      responseBodyMatcher.matchers().addAll(body.getRight().matchers());
      response.status(solveStatus(name));
      response.setHeaders(headers);
      response.setBody(body.getLeft());
      response.setBodyMatchers(responseBodyMatcher);
      responseList.add(response);
    }
    return responseList;
  }

  private static Pair<Body, BodyMatchers> buildFromExample(final Object example) {
    final Body body;
    if (example instanceof Example) {
      final var castedExample = (Example) example;
      if (Objects.nonNull(castedExample.get$ref())) {
        final var referredExample = getExampleFromComponent(OpenApiContractConverterUtils.mapRefName(castedExample));
        body = new Body(referredExample.getValue());
      } else {
        body = new Body(((Example) example).getValue());
      }
    } else {
      body = new Body(example);
    }
    return Pair.of(body, new BodyMatchers());
  }

  private Integer solveStatus(final String name) {
    return "default".equalsIgnoreCase(name) ? 200 : Integer.parseInt(name);
  }

  private List<Pair<Body, BodyMatchers>> processResponseBody(
      final Schema schema) {
    final var bodyList = new ArrayList<Pair<Body, BodyMatchers>>();
    if (schema instanceof ComposedSchema) {
      final ComposedSchema composedSchema = (ComposedSchema) schema;
      final var bodyMap = processComposedSchema(composedSchema);
      bodyList.addAll(bodyMap);
    } else if (Objects.nonNull(schema.getType()) && BasicTypeConstants.BASIC_OBJECT_TYPE.contains(schema.getType())) {
      bodyList.add(OpenApiContractConverterUtils.processBasicTypeBody(schema));
    } else {
      final var bodyMap = processBodyAndMatchers(schema);
      bodyList.addAll(bodyMap);
    }
    return bodyList;
  }

  private List<Request> processRequest(final Entry<String, PathItem> pathItem, final Operation operation, final String name) {
    final List<Request> requestList = new LinkedList<>();
    if (Objects.nonNull(operation.getRequestBody()) && Objects.nonNull(operation.getRequestBody().getContent())) {
      requestList.addAll(processRequestContent(operation));
    }
    if (requestList.isEmpty()) {
      requestList.add(new Request());
    }
    requestList.forEach(enrichRequest(pathItem, operation, name));

    return requestList;
  }

  private Consumer<Request> enrichRequest(final Entry<String, PathItem> pathItem, final Operation operation, final String name) {
    return request -> {
      if (Objects.nonNull(operation.getParameters()) || Objects.nonNull(pathItem.getValue().getParameters())) {
        final UrlPath url = new UrlPath(pathItem.getKey());
        url.queryParameters(queryParameters -> processQueryParameters(queryParameters, operation.getParameters(), pathItem.getValue()));
        request.setUrlPath(url);
      } else {
        request.url(new Url(pathItem.getKey()));
      }
      request.method(name);
    };
  }

  private List<Request> processRequestContent(final Operation operation) {
    final List<Request> requestList = new LinkedList<>();
    for (Entry<String, MediaType> content : operation.getRequestBody().getContent().entrySet()) {
      final List<Pair<Body, BodyMatchers>> bodyMap;
      final MediaType mediaType = content.getValue();
      final Headers headers = new Headers();
      headers.header("Content-Type", content.getKey());
      bodyMap = processRequestBody(mediaType.getSchema());
      if (Objects.nonNull(mediaType.getExample())) {
        bodyMap.add(buildFromExample(mediaType.getExample()));
      } else if (Objects.nonNull(mediaType.getExamples())) {
        mediaType.getExamples().forEach((key, example) -> bodyMap.add(buildFromExample(example)));
      }
      bodyMap.forEach(body -> {
        final Request request = new Request();
        request.setHeaders(headers);
        request.body(body.getLeft());
        request.setBodyMatchers(body.getRight());
        requestList.add(request);
      });
    }
    return requestList;
  }

  private List<Pair<Body, BodyMatchers>> processRequestBody(final Schema schema) {
    final List<Pair<Body, BodyMatchers>> requestBody = new LinkedList<>();
    if (schema instanceof ComposedSchema) {
      final ComposedSchema composedSchema = (ComposedSchema) schema;
      requestBody.addAll(processComposedSchema(composedSchema));
    } else if (Objects.nonNull(schema.getType()) && BasicTypeConstants.BASIC_OBJECT_TYPE.contains(schema.getType())) {
      requestBody.add(OpenApiContractConverterUtils.processBasicTypeBody(schema));
    } else {
      requestBody.addAll(processBodyAndMatchers(schema));
    }
    return requestBody;
  }

  private List<Pair<Body, BodyMatchers>> processBodyAndMatchers(final Schema schema) {

    final var result = new LinkedList<Pair<Body, BodyMatchers>>();
    if (Objects.nonNull(schema.getType())) {
      result.add(processBodyAndMatchersByType(schema));
    }
    if (Objects.nonNull(schema.get$ref())) {
      result.addAll(processBodyAndMatchersByRef(schema));
    }
    return result;
  }

  private List<Pair<Body, BodyMatchers>> processBodyAndMatchersByRef(final Schema schema) {
    final String ref = OpenApiContractConverterUtils.mapRefName(schema);
    List<Pair<Body, BodyMatchers>> bodyList = new LinkedList<>();
    if (existSchemaWithPropertiesInComponent(ref)) {
      final Map<String, Schema> properties = getSchemaFromComponent(ref).getProperties();
      for (Entry<String, Schema> property : properties.entrySet()) {
        bodyList = createBodyForProperty(ref, bodyList, property);
      }
    } else {
      final Schema arraySchema = getSchemaFromComponent(ref);
      bodyList = this.applyObjectToBodyList(bodyList, null, writeBodyMatcher(null, "[0]", arraySchema, arraySchema.getType()));
    }
    return bodyList;
  }

  private List<Pair<Body, BodyMatchers>> createBodyForProperty(final String ref, final List<Pair<Body, BodyMatchers>> propertyBodyList, final Entry<String, Schema> property) {
    final List<Pair<Body, BodyMatchers>> bodyList;
    if (property.getValue() instanceof ComposedSchema) {
      bodyList = applyBodyToList(propertyBodyList, property.getKey(), processComposedSchema((ComposedSchema) property.getValue()));
    } else if (Objects.nonNull(property.getValue().get$ref())) {
      final String subRef = OpenApiContractConverterUtils.mapRefName(property.getValue());
      final Schema<?> subSchema = getSchemaFromComponent(subRef);
      if (Objects.nonNull(subSchema.getProperties())) {
        bodyList = applyMapToBodyList(propertyBodyList, property.getKey(), processComplexBodyAndMatchers(property.getKey(), subSchema.getProperties()));
      } else if (((ArraySchema) subSchema).getItems() instanceof ComposedSchema) {
        final Schema<?> arraySchema = ((ArraySchema) subSchema).getItems();
        bodyList = applyBodyToList(propertyBodyList, property.getKey(), processComposedSchema((ComposedSchema) arraySchema));
      } else {
        final Schema<?> arraySchema = ((ArraySchema) subSchema).getItems();
        bodyList = this.applyObjectToBodyList(propertyBodyList, ref, writeBodyMatcher(null, ref, arraySchema, arraySchema.getType()));
      }
    } else if (Objects.nonNull(property.getValue().getEnum())) {
      bodyList = this.applyObjectToBodyList(propertyBodyList, property.getKey(), writeBodyMatcher(null, property.getKey(), property.getValue(), BasicTypeConstants.ENUM));
    } else {
      bodyList = this.applyObjectToBodyList(propertyBodyList, property.getKey(), writeBodyMatcher(null, property.getKey(), property.getValue(), property.getValue().getType()));
    }
    return bodyList;
  }

  private List<Pair<Body, BodyMatchers>> applyBodyToList(final List<Pair<Body, BodyMatchers>> originalBodyList, final String property,
      final List<Pair<Body, BodyMatchers>> valueBodyList) {
    final var bodyList = new LinkedList<Pair<Body, BodyMatchers>>();
    if (originalBodyList.isEmpty()) {
      bodyList.add(getBodyMatcher(property, valueBodyList));
    } else {
      for (var orgBody : originalBodyList) {
        for (var bodyValue : valueBodyList) {
          bodyList.add(combineProperties(orgBody, property, bodyValue));
        }
      }
    }
    return bodyList;
  }

  private List<Pair<Body, BodyMatchers>> applyObjectToBodyList(final List<Pair<Body, BodyMatchers>> originalBodyList, final String property,
      final Pair<Object, BodyMatchers> bodyValue) {
    final var bodyList = new LinkedList<Pair<Body, BodyMatchers>>();
    if (originalBodyList.isEmpty()) {
      bodyList.add(getBodyMatcher(property, bodyValue));
    } else {
      for (var orgBody : originalBodyList) {
        bodyList.add(combineProperties(orgBody, property, Pair.of(new Body(bodyValue.getLeft()), bodyValue.getRight())));
      }
    }
    return bodyList;
  }

  private List<Pair<Body, BodyMatchers>> applyMapToBodyList(final List<Pair<Body, BodyMatchers>> originalBodyList, final String property,
      final Pair<Object, BodyMatchers> valueBodyMap) {
    final var bodyList = new LinkedList<Pair<Body, BodyMatchers>>();
    if (originalBodyList.isEmpty()) {
      if (valueBodyMap.getLeft() instanceof Map) {
        bodyList.add(getBodyFromMap(property, (Map<String, Object>) valueBodyMap.getLeft(), valueBodyMap.getRight()));
      }
    } else {
      for (var orgBody : originalBodyList) {
        bodyList.add(combineProperties(orgBody, property, Pair.of(new Body(valueBodyMap.getLeft()), valueBodyMap.getRight())));
      }
    }
    return bodyList;
  }

  private Pair<Body, BodyMatchers> combineProperties(final Pair<Body, BodyMatchers> orgBodyMatcher, final String property, final Pair<Body, BodyMatchers> bodyValue) {
    final var orgBody = orgBodyMatcher.getLeft();
    final var orgMatchers = orgBodyMatcher.getRight();
    orgMatchers.matchers().addAll(bodyValue.getRight().matchers());
    return Pair.of(
      new Body(new DslProperty(addProperty(orgBody.getClientValue(), property, bodyValue.getLeft().getClientValue()), addProperty(orgBody.getServerValue(), property,
                                                                                                                                  bodyValue.getLeft().getServerValue()))),
      orgMatchers);
  }

  private Body combineProperties(final Body orgBody, final Body newBody) {
    final Object clientValue = combineProperties(orgBody.getClientValue(), newBody.getClientValue());
    final Object serverValue = combineProperties(orgBody.getServerValue(), newBody.getServerValue());
    return new Body(new DslProperty(clientValue, serverValue));
  }

  private Object combineProperties(final Object clientValueOrig, final Object clientValueNew) {
    final Object property;
    if (clientValueOrig instanceof Map) {
      property = new HashMap<>((Map) clientValueOrig);
      ((Map) property).putAll((Map) clientValueNew);
    } else if (clientValueOrig instanceof List) {
      property = new LinkedList<>((List) clientValueOrig);
      ((List) property).addAll((List) clientValueNew);
    } else if (clientValueOrig instanceof DslProperty) {
      property = combineProperties(((DslProperty<?>) clientValueOrig).getClientValue(), clientValueNew);
    } else {
      property = List.of(clientValueOrig, clientValueNew);
    }
    return property;
  }

  private Object addProperty(final Object dslValue, final String property, final Object bodyValue) {
    final Object result;
    if (dslValue instanceof Map) {
      result = new HashMap<String, Object>((Map) dslValue);
      ((Map) result).put(property, bodyValue);
    } else if (dslValue instanceof List) {
      result = new ArrayList<>((List) dslValue);
      ((List) result).add(Map.of(property, bodyValue));
    } else {
      result = Map.of(property, bodyValue);
    }
    return result;
  }

  private Pair<Body, BodyMatchers> processBodyAndMatchersByType(final Schema schema) {
    final Body body;
    final BodyMatchers bodyMatchers = new BodyMatchers();
    if (Objects.nonNull(schema.getProperties())) {
      final Map<String, Object> bodyMap = new HashMap<>();
      final Map<String, Schema> basicObjectProperties = schema.getProperties();
      for (Entry<String, Schema> property : basicObjectProperties.entrySet()) {
        if (Objects.nonNull(property.getValue().get$ref())) {
          final String subRef = OpenApiContractConverterUtils.mapRefName(property.getValue());
          final HashMap<String, Schema> subProperties = (HashMap<String, Schema>) getSchemaFromComponent(subRef).getProperties();
          final var result = processComplexBodyAndMatchers(property.getKey(), subProperties);
          bodyMap.put(property.getKey(), result.getLeft());
          bodyMatchers.matchers().addAll(result.getRight().matchers());
        } else {
          final var result = writeBodyMatcher(null, property.getKey(), property.getValue(), property.getValue().getType());
          bodyMap.put(property.getKey(), result.getLeft());
          bodyMatchers.matchers().addAll(result.getRight().matchers());
        }
      }
      body = new Body(bodyMap);
    } else {
      final var result = writeBodyMatcher(null, "[0]", schema, schema.getType());
      body = new Body(result.getLeft());
      bodyMatchers.matchers().addAll(result.getRight().matchers());
    }
    return Pair.of(body, bodyMatchers);
  }

  private Pair<Object, BodyMatchers> writeBodyMatcher(final Entry<String, Schema> property, final String fieldName, final Schema schema, final String type) {
    final var example = Objects.nonNull(property) ? property.getValue().getExample() : schema.getExample();
    final var bodyMatchers = new BodyMatchers();
    final Pair<Object, BodyMatchers> result;
    if (Objects.nonNull(example)) {
      result = Pair.of(schema.getExample(), bodyMatchers);
    } else {
      final String mapKey = Objects.nonNull(property) ? property.getKey() : fieldName;

      switch (type) {
        case BasicTypeConstants.STRING:
          bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
          result = Pair.of(RandomStringUtils.random(5, true, true), bodyMatchers);
          break;
        case BasicTypeConstants.INTEGER:
          result = processIntegerBodyMatcher(property, fieldName, schema);
          break;
        case BasicTypeConstants.NUMBER:
          result = processNumberBodyMatcher(property, fieldName, schema);
          break;
        case BasicTypeConstants.BOOLEAN:
          bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
          result = Pair.of(BasicTypeConstants.RANDOM.nextBoolean(), bodyMatchers);
          break;
        case BasicTypeConstants.OBJECT:
          result = processObjectBodyMatcher(property, fieldName, schema);
          break;
        case BasicTypeConstants.ARRAY:
          final var arraySchema = Objects.nonNull(property) ? null : (ArraySchema) schema;
          result = processArrayBodyMatcher(property, fieldName, arraySchema);
          break;
        case BasicTypeConstants.ENUM:
          result = processEnumBodyMatcher(mapKey, Objects.nonNull(property) ? property.getValue() : schema);
          break;
        case BasicTypeConstants.MAP:
          result = processMapBodyMatcher(schema, fieldName);
          break;
        default:
          bodyMatchers.jsonPath(mapKey, bodyMatchers.byRegex(BasicTypeConstants.DEFAULT_REGEX));
          result = Pair.of(RandomStringUtils.random(5, true, true), bodyMatchers);
          break;
      }
    }
    return result;
  }

  private Pair<Object, BodyMatchers> processArrayBodyMatcher(final Entry<String, Schema> property, final String fieldName, final ArraySchema schema) {
    final Object result;
    final Schema<?> arraySchema = Objects.nonNull(property) ? ((ArraySchema) property.getValue()).getItems() : schema.getItems();
    if (Objects.nonNull(arraySchema.getExample())) {
      result = Pair.of(arraySchema.getExample(), new BodyMatchers());
    } else {
      result = processArray(arraySchema, fieldName);
    }
    return (Pair<Object, BodyMatchers>) result;
  }

  private Pair<Object, BodyMatchers> processObjectBodyMatcher(final Entry<String, Schema> property, final String fieldName, final Schema schema) {
    final Pair<Object, BodyMatchers> result;
    final String ref = Objects.nonNull(property) ? property.getValue().get$ref() : schema.get$ref();
    final Schema internalRef = Objects.nonNull(property) ? property.getValue() : schema;
    final String subRef = OpenApiContractConverterUtils.mapRefName(internalRef);

    if (Objects.nonNull(ref)) {
      final Map<String, Schema> subPropertiesWithRef = getSchemaFromComponent(subRef).getProperties();
      result = processComplexBodyAndMatchers(fieldName, subPropertiesWithRef);
    } else if (Objects.nonNull(internalRef.getProperties())) {
      final Map<String, Schema> subProperties = internalRef.getProperties();
      result = processComplexBodyAndMatchers(fieldName, subProperties);
    } else {
      final Schema subProperties = (Schema) internalRef.getAdditionalProperties();
      result = writeBodyMatcher(null, fieldName, subProperties, BasicTypeConstants.MAP);
    }
    return result;
  }

  private Pair<Object, BodyMatchers> processNumberBodyMatcher(final Entry<String, Schema> property, final String fieldName, final Schema schema) {

    final Object result;
    final BodyMatchers bodyMatchers = new BodyMatchers();
    final String format = Objects.nonNull(property) ? property.getValue().getFormat() : schema.getFormat();

    if (BasicTypeConstants.FLOAT.equalsIgnoreCase(format)) {
      bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      result = Math.abs(BasicTypeConstants.RANDOM.nextFloat());
    } else if (BasicTypeConstants.DOUBLE.equalsIgnoreCase(format)) {
      bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      result = Math.abs(BasicTypeConstants.RANDOM.nextDouble());
    } else if (!Objects.nonNull(format) || format.isEmpty()) {
      bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      result = BasicTypeConstants.RANDOM.nextInt();
    } else {
      result = null;
    }
    return Pair.of(result, bodyMatchers);
  }

  private Pair<Object, BodyMatchers> processIntegerBodyMatcher(final Entry<String, Schema> property, final String fieldName, final Schema schema) {
    final Object result;
    final BodyMatchers bodyMatchers = new BodyMatchers();
    final String format = Objects.nonNull(property) ? property.getValue().getFormat() : schema.getFormat();

    if (BasicTypeConstants.INT_32.equalsIgnoreCase(format) || !Objects.nonNull(format)) {
      bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      result = BasicTypeConstants.RANDOM.nextInt();
    } else if (BasicTypeConstants.INT_64.equalsIgnoreCase(format)) {
      bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      result = BasicTypeConstants.RANDOM.nextFloat();
    } else {
      result = null;
    }
    return Pair.of(result, bodyMatchers);
  }

  private Pair<Object, BodyMatchers> processEnumBodyMatcher(final String enumName, final Schema property) {
    String regex = "";
    final BodyMatchers bodyMatchers = new BodyMatchers();
    final Iterator<?> enumObjects = property.getEnum().iterator();
    while (enumObjects.hasNext()) {
      final Object nextObject = enumObjects.next();
      if (!enumObjects.hasNext()) {
        regex = regex.concat(nextObject.toString());
      } else {
        regex = regex.concat(nextObject.toString() + "|");
      }
    }
    bodyMatchers.jsonPath(enumName, bodyMatchers.byRegex(regex));
    return Pair.of(property.getEnum().get(BasicTypeConstants.RANDOM.nextInt(property.getEnum().size())), bodyMatchers);
  }

  private Pair<Object, BodyMatchers> processMapBodyMatcher(final Schema schema, final String fieldName) {
    final var value = writeBodyMatcher(null, fieldName + "[0][0]", schema, schema.getType());
    final var bodyMatcher = getBodyMatcher(RandomStringUtils.random(5, true, true), value);
    return Pair.of(bodyMatcher.getLeft(), bodyMatcher.getRight());
  }

  private Pair<Object, BodyMatchers> processComplexBodyAndMatchers(final String objectName, final Map<String, Schema> properties) {

    final HashMap<String, Object> propertyMap = new HashMap<>();
    final BodyMatchers bodyMatchers = new BodyMatchers();
    for (Entry<String, Schema> property : properties.entrySet()) {
      final String newObjectName = objectName + "." + property.getKey();
      if (isReferenced(property.getValue())) {
        final String ref = OpenApiContractConverterUtils.mapRefName(property.getValue());
        if (existSchemaWithPropertiesInComponent(ref)) {
          final Map<String, Schema> subProperties = getSchemaFromComponent(ref).getProperties();
          final var processedBody = processComplexBodyAndMatchers(newObjectName, subProperties);
          propertyMap.put(property.getKey(), processedBody.getLeft());
          bodyMatchers.matchers().addAll(processedBody.getRight().matchers());
        } else {
          final var subProperties = ((ArraySchema) getSchemaFromComponent(ref)).getItems();
          final Pair<List<Object>, BodyMatchers> processedArray = processArray(subProperties, objectName);
          propertyMap.put(property.getKey(), processedArray.getLeft());
          bodyMatchers.matchers().addAll(processedArray.getRight().matchers());
        }
      } else {
        final String type;
        type = getPropertyType(property);
        final var prop = writeBodyMatcher(property, newObjectName, null, type);
        propertyMap.put(property.getKey(), prop.getLeft());
        bodyMatchers.matchers().addAll(prop.getRight().matchers());
      }
    }
    return Pair.of(propertyMap, bodyMatchers);
  }

  private static boolean isReferenced(final Schema schema) {
    return Objects.nonNull(schema.get$ref());
  }

  private Schema<?> getReferencedProperties(final Schema<?> schema) {
    Schema<?> referencedSchema;
    final String ref = OpenApiContractConverterUtils.mapRefName(schema);
    referencedSchema = getSchemaFromComponent(ref);
    if (!existSchemaWithPropertiesInComponent(ref)) {
      referencedSchema = ((ArraySchema) referencedSchema).getItems();
    }
    return referencedSchema;
  }

  private Pair<List<Object>, BodyMatchers> processArray(final Schema<?> arraySchema, final String objectName) {
    final List<Object> propertyList = new LinkedList<>();
    final BodyMatchers bodyMatchers = new BodyMatchers();
    if (Objects.nonNull(arraySchema.get$ref())) {
      final String ref = OpenApiContractConverterUtils.mapRefName(arraySchema);
      final Map<String, Schema> subObject = getSchemaFromComponent(ref).getProperties();
      final var generatedObject = processComplexBodyAndMatchers("[0]", subObject);
      propertyList.add(generatedObject.getLeft());
      bodyMatchers.matchers().addAll(generatedObject.getRight().matchers());
    } else {
      final String type = arraySchema.getType();
      final Pair tempValue;
      switch (type) {
        case BasicTypeConstants.STRING:
          tempValue = processStringArray(arraySchema, objectName);
          break;
        case BasicTypeConstants.INTEGER:
          tempValue = processIntegerArray(arraySchema, objectName);
          break;
        case BasicTypeConstants.NUMBER:
          tempValue = processNumberArray(arraySchema, objectName);
          break;
        case BasicTypeConstants.BOOLEAN:
          tempValue = processBooleanArray(arraySchema, objectName);
          break;
        case BasicTypeConstants.ARRAY:
          tempValue = processArrayArray((ArraySchema) arraySchema, objectName);
          break;
        case BasicTypeConstants.OBJECT:
          tempValue = processObjectArray(arraySchema, objectName);
          break;
        default:
          tempValue = null;
          log.error("Format not supported");
          break;
      }
      if (Objects.nonNull(tempValue)) {
        propertyList.add(tempValue.getLeft());
        if (tempValue.getRight() instanceof BodyMatchers) {
          bodyMatchers.matchers().addAll(((BodyMatchers) tempValue.getRight()).matchers());
        } else {
          bodyMatchers.matchers().add((BodyMatcher) tempValue.getRight());

        }
      }
    }
    return Pair.of(propertyList, bodyMatchers);
  }

  private Pair<Object, BodyMatchers> processObjectArray(final Schema<?> arraySchema, final String objectName) {
    final HashMap<String, Schema> subObject = (HashMap<String, Schema>) arraySchema.getProperties();
    return processComplexBodyAndMatchers(objectName, subObject);
  }

  private Pair<List<Object>, BodyMatchers> processArrayArray(final ArraySchema arraySchema, final String objectName) {
    final List<Object> result = new ArrayList<>();
    final Schema<?> subArray = arraySchema.getItems();
    final BodyMatchers bodyMatchers = new BodyMatchers();
    if (Objects.nonNull(subArray.getExample())) {
      if (subArray.getExample() instanceof List) {
        result.addAll((List<Object>) subArray.getExample());
      } else {
        result.add(subArray.getExample());
      }
    } else {
      final var temp = processArray(subArray, objectName + "[0]");
      result.addAll(temp.getLeft());
      bodyMatchers.matchers().addAll(temp.getRight().matchers());
    }
    return Pair.of(result, bodyMatchers);
  }

  private Pair<Object, BodyMatcher> processBooleanArray(final Schema<?> arraySchema, final String objectName) {
    final BodyMatchers bodyMatchers = new BodyMatchers();
    if (Objects.nonNull(arraySchema.getName())) {
      bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
    } else {
      bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
    }
    return Pair.of(BasicTypeConstants.RANDOM.nextBoolean(), bodyMatchers.matchers().get(0));
  }

  private Pair<Object, BodyMatcher> processNumberArray(final Schema<?> arraySchema, final String objectName) {
    final Object result;
    final BodyMatchers bodyMatchers = new BodyMatchers();
    if (BasicTypeConstants.FLOAT.equalsIgnoreCase(arraySchema.getFormat())) {
      if (Objects.nonNull(arraySchema.getName())) {
        bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      } else {
        bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      }
      result = BasicTypeConstants.RANDOM.nextFloat();
    } else if (BasicTypeConstants.DOUBLE.equalsIgnoreCase(arraySchema.getFormat())) {
      if (Objects.nonNull(arraySchema.getName())) {
        bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      } else {
        bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      }
      result = Math.abs(BasicTypeConstants.RANDOM.nextDouble());
    } else {
      if (Objects.nonNull(arraySchema.getName())) {
        bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      } else {
        bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      }
      result = BasicTypeConstants.RANDOM.nextInt();
    }
    return Pair.of(result, bodyMatchers.matchers().get(0));
  }

  private Pair<Object, BodyMatcher> processIntegerArray(final Schema<?> arraySchema, final String objectName) {
    final Object result;
    final BodyMatchers bodyMatchers = new BodyMatchers();
    if (BasicTypeConstants.INT_32.equalsIgnoreCase(arraySchema.getFormat()) || !Objects.nonNull(arraySchema.getFormat())) {
      if (Objects.nonNull(arraySchema.getName())) {
        bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      } else {
        bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      }
      result = BasicTypeConstants.RANDOM.nextInt();
    } else {
      if (Objects.nonNull(arraySchema.getName())) {
        bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      } else {
        bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      }
      result = Math.abs(BasicTypeConstants.RANDOM.nextFloat());
    }
    return Pair.of(result, bodyMatchers.matchers().get(0));
  }

  private Pair<Object, BodyMatcher> processStringArray(final Schema<?> arraySchema, final String objectName) {
    final var bodyMatcher = new BodyMatchers();
    if (Objects.nonNull(arraySchema.getName())) {
      bodyMatcher.jsonPath(arraySchema.getName() + "[0]", bodyMatcher.byRegex(BasicTypeConstants.STRING_REGEX));
    } else {
      bodyMatcher.jsonPath(objectName + "[0]", bodyMatcher.byRegex(BasicTypeConstants.STRING_REGEX));
    }
    return Pair.of(RandomStringUtils.random(5, true, true), bodyMatcher.matchers().get(0));
  }

  private void processQueryParameters(final QueryParameters queryParameters, final List<Parameter> parameters, final PathItem pathItem) {
    if (Objects.nonNull(pathItem.getParameters())) {
      if (Objects.nonNull(parameters) && Objects.nonNull(pathItem.getParameters())) {
        throw new MultiApiContractConverterException("Defining parameters in both Path and Operations is not supported in this plugin");
      } else {
        mapQueryParameters(queryParameters, pathItem.getParameters());
      }
    } else {
      mapQueryParameters(queryParameters, parameters);
    }
  }

  private void mapQueryParameters(final QueryParameters queryParameters, final List<Parameter> parameters) {
    for (Parameter parameter : parameters) {
      OpenApiContractConverterUtils.processBasicQueryParameterTypeBody(queryParameters, parameter);
    }
  }

  private OpenAPI getOpenApi(final File file) throws MultiApiContractConverterException {
    final OpenAPI openAPI;
    final ParseOptions options = new ParseOptions();
    options.setResolve(true);
    try {
      final SwaggerParseResult result = new OpenAPIParser().readLocation(file.getPath(), null, options);
      openAPI = result.getOpenAPI();
    } catch (final ReadContentException e) {
      throw new MultiApiContractConverterException("Code generation failed when parser the .yaml file ");
    }
    if (!Objects.nonNull(openAPI)) {
      throw new MultiApiContractConverterException("Code generation failed why .yaml is empty");
    }
    return openAPI;
  }

  private List<Pair<Body, BodyMatchers>> processComposedSchema(final ComposedSchema composedSchema) {
    final List<Pair<Body, BodyMatchers>> result = new LinkedList<>();
    if (Objects.nonNull(composedSchema.getAllOf())) {
      final List<Pair<Body, BodyMatchers>> tempBody = new LinkedList<>();
      for (Schema<?> schema : composedSchema.getAllOf()) {
        tempBody.addAll(processBodyAndMatchers(schema));
      }
      result.add(unify(tempBody));
    } else if (Objects.nonNull(composedSchema.getOneOf())) {
      for (var oneSchema : composedSchema.getOneOf()) {
        result.addAll(processBodyAndMatchers(oneSchema));
      }
    } else if (Objects.nonNull(composedSchema.getAnyOf())) {
      for (var anySchema : combineSchema(composedSchema.getAnyOf())) {
        result.addAll(processBodyAndMatchers(anySchema));
      }
    }
    return result;
  }

  private Pair<Body, BodyMatchers> unify(final List<Pair<Body, BodyMatchers>> tempBody) {
    Body body = new Body(Collections.emptyMap());
    final BodyMatchers bodyMatchers = new BodyMatchers();
    for (var data : tempBody) {
      body = combineProperties(body, data.getLeft());
      bodyMatchers.matchers().addAll(data.getRight().matchers());
    }

    return Pair.of(body, bodyMatchers);
  }

  private List<Schema<?>> combineSchema(final List<Schema> anyOfThis) {
    final List<Schema<?>> finalList = new LinkedList<>();
    final var anySchema = solveReferenced(anyOfThis.remove(0));
    if (anyOfThis.isEmpty()) {
      finalList.add(cloneSchema(anySchema));
    } else {
      finalList.add(anySchema);
      final List<Schema<?>> tempList = combineSchema(anyOfThis);
      finalList.addAll(tempList);
      for (var temp : tempList) {
        final var swap = cloneSchema(temp);
        swap.getProperties().putAll(anySchema.getProperties());
        finalList.add(swap);
      }
    }

    return finalList;
  }

  private Schema cloneSchema(final Schema origSchema) {
    final var schema = new Schema();
    schema.setDefault(origSchema.getDefault());
    schema.setDeprecated(origSchema.getDeprecated());
    schema.setDescription(origSchema.getDescription());
    schema.setDiscriminator(origSchema.getDiscriminator());
    schema.setEnum(origSchema.getEnum());
    schema.setExample(origSchema.getExample());
    schema.setExclusiveMaximum(origSchema.getExclusiveMaximum());
    schema.setExclusiveMinimum(origSchema.getExclusiveMinimum());
    schema.setExtensions(origSchema.getExtensions());
    schema.setExternalDocs(origSchema.getExternalDocs());
    schema.setFormat(origSchema.getFormat());
    schema.setMaximum(origSchema.getMaximum());
    schema.setMaxItems(origSchema.getMaxItems());
    schema.setMaxLength(origSchema.getMaxLength());
    schema.setMaxProperties(origSchema.getMaxProperties());
    schema.setMinimum(origSchema.getMinimum());
    schema.setMinItems(origSchema.getMinItems());
    schema.setMinLength(origSchema.getMinLength());
    schema.setMinProperties(origSchema.getMinProperties());
    schema.setMultipleOf(origSchema.getMultipleOf());
    schema.setName(origSchema.getName());
    schema.setNot(origSchema.getNot());
    schema.setNullable(origSchema.getNullable());
    schema.setPattern(origSchema.getPattern());
    schema.setProperties(cloneProperties(origSchema.getProperties()));
    schema.setReadOnly(origSchema.getReadOnly());
    schema.setRequired(origSchema.getRequired());
    schema.setTitle(origSchema.getTitle());
    schema.setType(origSchema.getType());
    schema.setUniqueItems(origSchema.getUniqueItems());
    schema.setWriteOnly(origSchema.getWriteOnly());
    schema.xml(origSchema.getXml());
    return schema;
  }

  private Map<String, Schema> cloneProperties(final Map<String, Schema> properties) {
    final var propertiesMap = new LinkedHashMap<String, Schema>();
    if (Objects.nonNull(properties)) {
      properties.forEach((key, property) -> propertiesMap.put(key, cloneSchema(property)));
    }
    return propertiesMap;
  }

  private Schema solveReferenced(final Schema schema) {
    Schema solvedSchema = schema;
    if (isReferenced(schema)) {
      solvedSchema = getReferencedProperties(schema);
    }
    return solvedSchema;
  }

  private boolean existSchemaWithPropertiesInComponent(final String ref) {
    return Objects.nonNull(componentsMap.get(ref).getProperties());
  }

  private Schema<?> getSchemaFromComponent(final String ref) {
    return componentsMap.get(ref);
  }

  private static Example getExampleFromComponent(final String ref) {
    return EXAMPLES_MAP.get(ref);
  }
}