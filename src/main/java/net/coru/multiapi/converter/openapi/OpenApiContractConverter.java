/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


package net.coru.multiapi.converter.openapi;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
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
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.Body;
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

  private final Map<String, Schema> componentsMap = new LinkedHashMap<>();

  private final Map<String, Example> examplesMap = new LinkedHashMap<>();

  private static boolean isArray(final Schema schema) {
    return Objects.nonNull(schema.getType()) && "array".equalsIgnoreCase(schema.getType());
  }

  private static Body getBody(final String property, final Object bodyValue) {
    final Body body;
    if (Objects.nonNull(property)) {
      body = new Body(Map.of(property, bodyValue));
    } else {
      body = new Body(bodyValue);
    }
    return body;
  }

  private static Body getBody(final String property, final List<Body> bodyValue) {
    final Body body;
    if (Objects.nonNull(property)) {
      body = new Body(Map.of(property, bodyValue));
    } else {
      body = new Body(bodyValue);
    }
    return body;
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
        examplesMap.putAll(openApi.getComponents().getExamples());
      }
    }
  }

  private void processContract(final List<Contract> contracts, final Entry<String, PathItem> pathItem, final Operation operation, final OperationType name) {
    for (Entry<String, ApiResponse> response : operation.getResponses().entrySet()) {
      final String fileName = name + pathItem.getKey().replaceAll("[{}]", "") + response.getKey().substring(0, 1).toUpperCase() + response.getKey().substring(1) + "Response";
      final String contractName = fileName.replace("/", "");
      final String contractDescription = pathItem.getValue().getSummary();
      final var requestListIt = processRequest(pathItem, operation, name.name()).listIterator();
      final var responseListIt = processResponse(response.getKey(), response.getValue()).listIterator();
      while (requestListIt.hasNext() && responseListIt.hasNext()) {
        contracts.add(createContract(contractName, contractDescription, requestListIt.next(), responseListIt.next()));
      }
      requestListIt.forEachRemaining(request -> contracts.add(createContract(contractName, contractDescription, request, null)));
      responseListIt.forEachRemaining(resp -> contracts.add(createContract(contractName, contractDescription, null, resp)));
    }
  }

  private Contract createContract(final String contractName, final String contractDescription, final Request request, final Response response) {
    final Contract contract = new Contract();
    contract.setName(contractName);
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
      final var responseBodyMatchers = new ResponseBodyMatchers();
      if (Objects.nonNull(apiResponse.getContent())) {
        for (Entry<String, MediaType> content : apiResponse.getContent().entrySet()) {
          final Schema schema = content.getValue().getSchema();
          final Headers headers = new Headers();
          headers.contentType(content.getKey());
          headers.accept();
          final var bodyList = processResponseBody(responseBodyMatchers, content, schema);
          for (var body : bodyList) {
            final var response = new Response();

            response.status(solveStatus(name));
            response.setHeaders(headers);
            response.setBody(body);
            response.setBodyMatchers(responseBodyMatchers);
            responseList.add(response);
          }
        }
      } else {
        final var response = new Response();
        response.body(response.anyAlphaNumeric());
        responseList.add(response);
      }
    }
    return responseList;
  }

  private Integer solveStatus(String name) {
    return "default".equalsIgnoreCase(name) ? 200 : Integer.parseInt(name);
  }
  private List<Body> processResponseBody(
      final ResponseBodyMatchers responseBodyMatchers,
      final Entry<String, MediaType> content, final Schema schema) {
    final var bodyList = new ArrayList<Body>();
    if (content.getValue().getSchema() instanceof ComposedSchema) {
      final ComposedSchema composedSchema = (ComposedSchema) content.getValue().getSchema();
      final var bodyMap = processComposedSchema(responseBodyMatchers, composedSchema);
      bodyList.addAll(bodyMap);
    } else if (Objects.nonNull(schema.getType()) && BasicTypeConstants.BASIC_OBJECT_TYPE.contains(schema.getType())) {
      bodyList.add(OpenApiContractConverterUtils.processBasicResponseTypeBody(schema));
    } else {
      final var bodyMap = processBodyAndMatchers(schema, responseBodyMatchers);
      /*Schema<?> checkArraySchema = new Schema<>();
      if (Objects.nonNull(schema.get$ref())) {
        checkArraySchema = getSchemaFromComponent(OpenApiContractConverterUtils.mapRefName(schema));
      }*/
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
    final BodyMatchers bodyMatchers = new BodyMatchers();
    for (Entry<String, MediaType> content : operation.getRequestBody().getContent().entrySet()) {
      final List<Body> bodyMap;
      final Schema schema = content.getValue().getSchema();
      final Headers headers = new Headers();
      headers.header("Content-Type", content.getKey());
      if (content.getValue().getSchema() instanceof ComposedSchema) {
        final ComposedSchema composedSchema = (ComposedSchema) content.getValue().getSchema();
        bodyMap = new ArrayList<>(processComposedSchema(bodyMatchers, composedSchema));
      } else if (Objects.nonNull(schema.getType()) && BasicTypeConstants.BASIC_OBJECT_TYPE.contains(schema.getType())) {
        bodyMap = List.of(OpenApiContractConverterUtils.processBasicRequestTypeBody(schema));
      } else {
        bodyMap = new ArrayList<>(processBodyAndMatchers(schema, bodyMatchers));
      }
      bodyMap.forEach(body -> {
        final Request request = new Request();
        request.setHeaders(headers);
        request.body(body);
        request.setBodyMatchers(bodyMatchers);
        requestList.add(request);
      });
    }
    return requestList;
  }

  private List<Body> processBodyAndMatchers(final Schema schema, final BodyMatchers bodyMatchers) {

    final var result = new ArrayList<Body>();
    if (Objects.nonNull(schema.getType())) {
      result.add(processBodyAndMatchersByType(schema, bodyMatchers));
    }
    if (Objects.nonNull(schema.get$ref())) {
      result.addAll(processBodyAndMatchersByRef(schema, bodyMatchers));
    }
    return result;
  }

  private List<Body> processBodyAndMatchersByRef(final Schema schema, final BodyMatchers bodyMatchers) {
    final String ref = OpenApiContractConverterUtils.mapRefName(schema);
    List<Body> bodyList = new LinkedList<>();
    if (existSchemaWithPropertiesInComponent(ref)) {
      final Map<String, Schema> properties = getSchemaFromComponent(ref).getProperties();
      for (Entry<String, Schema> property : properties.entrySet()) {
        if (property.getValue() instanceof ComposedSchema) {
          bodyList = applyMultiBody(bodyList, property.getKey(), processComposedSchema(bodyMatchers, (ComposedSchema) property.getValue()));
        } else if (Objects.nonNull(property.getValue().get$ref())) {
          final String subRef = OpenApiContractConverterUtils.mapRefName(property.getValue());
          final Schema<?> subSchema = getSchemaFromComponent(subRef);
          if (Objects.nonNull(subSchema.getProperties())) {
            bodyList = applyMultiBody(bodyList, property.getKey(), processComplexBodyAndMatchers(property.getKey(), subSchema.getProperties(), bodyMatchers));
          } else if (((ArraySchema) subSchema).getItems() instanceof ComposedSchema) {
            final Schema<?> arraySchema = ((ArraySchema) subSchema).getItems();
            bodyList = applyMultiBody(bodyList, property.getKey(), processComposedSchema(bodyMatchers, (ComposedSchema) arraySchema));
          } else {
            final Schema<?> arraySchema = ((ArraySchema) subSchema).getItems();
            bodyList = applyMultiBody(bodyList, ref, writeBodyMatcher(bodyMatchers, null, ref, arraySchema, arraySchema.getType()));
          }
        } else {
          if (Objects.nonNull(property.getValue().getEnum())) {
            bodyList = applyMultiBody(bodyList, property.getKey(), writeBodyMatcher(bodyMatchers, null, property.getKey(), property.getValue(), BasicTypeConstants.ENUM));
          } else {
            bodyList = applyMultiBody(bodyList, property.getKey(), writeBodyMatcher(bodyMatchers, null, property.getKey(), property.getValue(), property.getValue().getType()));
          }
        }
      }
    } else {
      final Schema arraySchema = getSchemaFromComponent(ref);
      bodyList = applyMultiBody(bodyList, null, writeBodyMatcher(bodyMatchers, null, "[0]", arraySchema, arraySchema.getType()));
    }
    return bodyList;
  }

  private List<Body> applyMultiBody(final List<Body> originalBodyList, final String property, final List<Body> valueBodyList) {
    final var bodyList = new LinkedList<Body>();
    if (originalBodyList.isEmpty()) {
      bodyList.add(getBody(property, valueBodyList));
    } else {
      for (var orgBody : originalBodyList) {
        for (var bodyValue : valueBodyList) {
          bodyList.add(combineProperties(orgBody, property, bodyValue));
        }
      }
    }
    return bodyList;
  }

  private List<Body> applyMultiBody(final List<Body> originalBodyList, final String property, final Object bodyValue) {
    final var bodyList = new LinkedList<Body>();
    if (originalBodyList.isEmpty()) {
      bodyList.add(getBody(property, bodyValue));
    } else {
      for (var orgBody : originalBodyList) {
        bodyList.add(combineProperties(orgBody, property, bodyValue));
      }
    }
    return bodyList;
  }

  private Body combineProperties(final Body orgBody, final String property, final Object bodyValue) {
    return new Body(new DslProperty(addProperty(orgBody.getClientValue(), property, bodyValue), addProperty(orgBody.getServerValue(), property, bodyValue)));
  }

  private Object addProperty(final Object dslValue, final String property, final Object bodyValue) {
    Object result;
    if (dslValue instanceof Map) {
      result = new HashMap<String, Object>((Map) dslValue);
      ((Map) result).put(property, bodyValue);
    } else if (dslValue instanceof List) {
      result = new ArrayList<>((List) dslValue);
      ((List)result).add(Map.of(property, bodyValue));
    } else {
      result = Map.of(property, bodyValue);
    }
    return result;
  }

  private List<Body> applyMultiBody(final List<Body> originalBodyList, final String property, final Map<String, Object> valueBodyMap) {
    final var bodyList = new LinkedList<Body>();
    if (originalBodyList.isEmpty()) {
      bodyList.add(getBody(property, valueBodyMap));
    } else {
      for (var orgBody : originalBodyList) {
        bodyList.add(combineProperties(orgBody, property, valueBodyMap));
      }
    }
    return bodyList;
  }

  private Body processBodyAndMatchersByType(final Schema schema, final BodyMatchers bodyMatchers) {
    Body body;
    if (Objects.nonNull(schema.getProperties())) {
      final Map<String, Object> bodyMap = new HashMap<>();
      final Map<String, Schema> basicObjectProperties = schema.getProperties();
      for (Entry<String, Schema> property : basicObjectProperties.entrySet()) {
        if (Objects.nonNull(property.getValue().get$ref())) {
          final String subRef = OpenApiContractConverterUtils.mapRefName(property.getValue());
          final HashMap<String, Schema> subProperties = (HashMap<String, Schema>) getSchemaFromComponent(subRef).getProperties();
          bodyMap.put(property.getKey(), processComplexBodyAndMatchers(property.getKey(), subProperties, bodyMatchers));
        } else {
          bodyMap.put(property.getKey(), writeBodyMatcher(bodyMatchers, null, property.getKey(), property.getValue(), property.getValue().getType()));
        }
      }
      body = new Body(bodyMap);
    } else {
      body = new Body(writeBodyMatcher(bodyMatchers, null, "[0]", schema, schema.getType()));
    }
    return body;
  }

  private Object writeBodyMatcher(final BodyMatchers bodyMatchers, final Entry<String, Schema> property, final String fieldName, final Schema schema, final String type) {
    final var example = Objects.nonNull(property) ? property.getValue().getExample() : schema.getExample();
    final Object result;
    if (Objects.nonNull(example)) {
      result = schema.getExample();
    } else {
      final String mapKey = Objects.nonNull(property) ? property.getKey() : fieldName;

      switch (type) {
        case BasicTypeConstants.STRING:
          bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
          result = RandomStringUtils.random(5, true, true);
          break;
        case BasicTypeConstants.INTEGER:
          result = processIntegerBodyMatcher(bodyMatchers, property, fieldName, schema);
          break;
        case BasicTypeConstants.NUMBER:
          result = processNumberBodyMatcher(bodyMatchers, property, fieldName, schema);
          break;
        case BasicTypeConstants.BOOLEAN:
          bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
          result = BasicTypeConstants.RANDOM.nextBoolean();
          break;
        case BasicTypeConstants.OBJECT:
          result = processObjectBodyMatcher(property, bodyMatchers, fieldName, schema);
          break;
        case BasicTypeConstants.ARRAY:
          final var arraySchema = Objects.nonNull(property) ? null : (ArraySchema) schema;
          result = processArrayBodyMatcher(bodyMatchers, property, fieldName, arraySchema);
          break;
        case BasicTypeConstants.ENUM:
          result = processEnum(bodyMatchers, mapKey, Objects.nonNull(property) ? property.getValue() : schema);
          break;
        default:
          bodyMatchers.jsonPath(mapKey, bodyMatchers.byRegex(BasicTypeConstants.DEFAULT_REGEX));
          result = RandomStringUtils.random(5, true, true);
          break;
      }
    }
    return result;
  }

  private Object processArrayBodyMatcher(final BodyMatchers bodyMatchers, final Entry<String, Schema> property, final String fieldName, final ArraySchema schema) {
    final Object result;
    final Schema<?> arraySchema = Objects.nonNull(property) ? ((ArraySchema) property.getValue()).getItems() : schema.getItems();
    if (Objects.nonNull(arraySchema.getExample())) {
      result = arraySchema.getExample();
    } else {
      result = processArray(arraySchema, fieldName, bodyMatchers);
    }
    return result;
  }

  private Object processObjectBodyMatcher(final Entry<String, Schema> property, final BodyMatchers bodyMatchers, final String fieldName, final Schema schema) {
    final Object result;
    final String ref = Objects.nonNull(property) ? property.getValue().get$ref() : schema.get$ref();
    final Schema internalRef = Objects.nonNull(property) ? property.getValue() : schema;
    final String subRef = OpenApiContractConverterUtils.mapRefName(internalRef);

    if (Objects.nonNull(ref)) {
      final Map<String, Schema> subPropertiesWithRef = getSchemaFromComponent(subRef).getProperties();
      result = processComplexBodyAndMatchers(fieldName, subPropertiesWithRef, bodyMatchers);
    } else {
      final Map<String, Schema> subProperties = internalRef.getProperties();
      result = processComplexBodyAndMatchers(fieldName, subProperties, bodyMatchers);
    }
    return result;
  }

  private Object processNumberBodyMatcher(final BodyMatchers bodyMatchers, final Entry<String, Schema> property, final String fieldName, final Schema schema) {

    final Object result;
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
    return result;
  }

  private Object processIntegerBodyMatcher(final BodyMatchers bodyMatchers, final Entry<String, Schema> property, final String fieldName, final Schema schema) {
    final Object result;
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
    return result;
  }

  private Object processEnum(final BodyMatchers bodyMatchers, final String enumName, final Schema property) {
    String regex = "";
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
    return property.getEnum().get(BasicTypeConstants.RANDOM.nextInt(property.getEnum().size()));
  }

  private Map<String, Object> processComplexBodyAndMatchers(final String objectName, final Map<String, Schema> properties, final BodyMatchers bodyMatchers) {

    final HashMap<String, Object> propertyMap = new HashMap<>();
    for (Entry<String, Schema> property : properties.entrySet()) {
      final String newObjectName = objectName + "." + property.getKey();
      if (isReferenced(property.getValue())) {
        final String ref = OpenApiContractConverterUtils.mapRefName(property.getValue());
        if (existSchemaWithPropertiesInComponent(ref)) {
          final Map<String, Schema> subProperties = getSchemaFromComponent(ref).getProperties();
          propertyMap.put(property.getKey(), processComplexBodyAndMatchers(newObjectName, subProperties, bodyMatchers));
        } else {
          final var subProperties = ((ArraySchema) getSchemaFromComponent(ref)).getItems();
          propertyMap.put(property.getKey(), processArray(subProperties, newObjectName, bodyMatchers));
        }
      } else {
        final String type;
        type = getPropertyType(property);
        propertyMap.put(property.getKey(), writeBodyMatcher(bodyMatchers, property, newObjectName, null, type));
      }
    }
    return propertyMap;
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

  private List<Object> processArray(final Schema<?> arraySchema, final String objectName, final BodyMatchers bodyMatchers) {
    final List<Object> propertyList = new LinkedList<>();
    if (Objects.nonNull(arraySchema.get$ref())) {
      final String ref = OpenApiContractConverterUtils.mapRefName(arraySchema);
      final Map<String, Schema> subObject = getSchemaFromComponent(ref).getProperties();
      propertyList.add(processComplexBodyAndMatchers("[0]", subObject, bodyMatchers));
    } else {
      final String type = arraySchema.getType();
      switch (type) {
        case BasicTypeConstants.STRING:
          propertyList.add(processStringArray(arraySchema, objectName, bodyMatchers));
          break;
        case BasicTypeConstants.INTEGER:
          propertyList.add(processIntegerArray(arraySchema, objectName, bodyMatchers));
          break;
        case BasicTypeConstants.NUMBER:
          propertyList.add(processNumberArray(arraySchema, objectName, bodyMatchers));
          break;
        case BasicTypeConstants.BOOLEAN:
          propertyList.add(processBooleanArray(arraySchema, objectName, bodyMatchers));
          break;
        case BasicTypeConstants.ARRAY:
          propertyList.add(processArrayArray((ArraySchema) arraySchema, objectName, bodyMatchers));
          break;
        case BasicTypeConstants.OBJECT:
          propertyList.add(processObjectArray(arraySchema, objectName, bodyMatchers));
          break;
        default:
          log.error("Format not supported");
          break;
      }
    }
    return propertyList;
  }

  private Object processObjectArray(final Schema<?> arraySchema, final String objectName, final BodyMatchers bodyMatchers) {
    final HashMap<String, Schema> subObject = (HashMap<String, Schema>) arraySchema.getProperties();
    return processComplexBodyAndMatchers(objectName + "[0]", subObject, bodyMatchers);
  }

  private List<Object> processArrayArray(final ArraySchema arraySchema, final String objectName, final BodyMatchers bodyMatchers) {
    final List<Object> result = new ArrayList<>();
    final Schema<?> subArray = arraySchema.getItems();
    if (Objects.nonNull(subArray.getExample())) {
      if (subArray.getExample() instanceof List) {
        result.addAll((List<Object>) subArray.getExample());
      } else {
        result.add(subArray.getExample());
      }
    } else {
      result.addAll(processArray(subArray, objectName, bodyMatchers));
    }
    return result;
  }

  private Object processBooleanArray(final Schema<?> arraySchema, final String objectName, final BodyMatchers bodyMatchers) {
    if (Objects.nonNull(arraySchema.getName())) {
      bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
    } else {
      bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
    }
    return BasicTypeConstants.RANDOM.nextBoolean();
  }

  private Object processNumberArray(final Schema<?> arraySchema, final String objectName, final BodyMatchers bodyMatchers) {
    final Object result;
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
    return result;
  }

  private Object processIntegerArray(final Schema<?> arraySchema, final String objectName, final BodyMatchers bodyMatchers) {
    final Object result;
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
    return result;
  }

  private Object processStringArray(final Schema<?> arraySchema, final String objectName, final BodyMatchers bodyMatchers) {
    if (Objects.nonNull(arraySchema.getName())) {
      bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
    } else {
      bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
    }
    return RandomStringUtils.random(5, true, true);
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

  private List<Body> processComposedSchema(final BodyMatchers bodyMatchers, final ComposedSchema composedSchema) {
    final List<Body> result = new ArrayList<>();
    if (Objects.nonNull(composedSchema.getAllOf())) {
      final List<Body> tempBody = new ArrayList<>();
      for (Schema<?> schema : composedSchema.getAllOf()) {
        tempBody.addAll(processBodyAndMatchers(schema, bodyMatchers));
      }
      result.add(new Body(tempBody));
    } else if (Objects.nonNull(composedSchema.getOneOf())) {
      for (var oneSchema : composedSchema.getOneOf()) {
        result.addAll(processBodyAndMatchers(oneSchema, bodyMatchers));
      }
    } else if (Objects.nonNull(composedSchema.getAnyOf())) {
      for (var anySchema : combineSchema(composedSchema.getAnyOf())) {
        result.addAll(processBodyAndMatchers(anySchema, bodyMatchers));
      }
    }
    return result;
  }

  private List<Schema<?>> combineSchema(final List<Schema> anyOfThis) {
    final List<Schema<?>> finalList = new LinkedList<>();
    var anySchema = solveReferenced(anyOfThis.remove(0));
    if (anyOfThis.isEmpty()) {
      finalList.add(anySchema);
    } else {
      final List<Schema<?>> tempList = combineSchema(anyOfThis);
      finalList.addAll(tempList);
      for (var temp : tempList) {
        temp.getProperties().putAll(anySchema.getProperties());
      }
      finalList.addAll(tempList);
    }

    return finalList;
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

  private Example getExampleFromComponent(final String ref) {
    return examplesMap.get(ref);
  }
}