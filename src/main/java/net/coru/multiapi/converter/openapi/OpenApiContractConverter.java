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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
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
import net.coru.multiapi.converter.utils.BasicTypeConstants;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.Body;
import org.springframework.cloud.contract.spec.internal.BodyMatchers;
import org.springframework.cloud.contract.spec.internal.Headers;
import org.springframework.cloud.contract.spec.internal.QueryParameters;
import org.springframework.cloud.contract.spec.internal.Request;
import org.springframework.cloud.contract.spec.internal.Response;
import org.springframework.cloud.contract.spec.internal.ResponseBodyMatchers;
import org.springframework.cloud.contract.spec.internal.Url;
import org.springframework.cloud.contract.spec.internal.UrlPath;

@Slf4j
public final class OpenApiContractConverter {

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

    final List<Contract> contracts = new ArrayList<>();

    for (Entry<String, PathItem> pathItem : openApi.getPaths().entrySet()) {
      if (Objects.nonNull(pathItem.getValue().getGet())) {
        processContract(contracts, openApi, pathItem, pathItem.getValue().getGet(), "Get");
      }
      if (Objects.nonNull(pathItem.getValue().getPost())) {
        processContract(contracts, openApi, pathItem, pathItem.getValue().getPost(), "Post");
      }
      if (Objects.nonNull(pathItem.getValue().getPut())) {
        processContract(contracts, openApi, pathItem, pathItem.getValue().getPut(), "Put");
      }
      if (Objects.nonNull(pathItem.getValue().getDelete())) {
        processContract(contracts, openApi, pathItem, pathItem.getValue().getDelete(), "Delete");
      }
      if (Objects.nonNull(pathItem.getValue().getPatch())) {
        processContract(contracts, openApi, pathItem, pathItem.getValue().getPatch(), "Patch");
      }
    }
    return contracts;
  }

  private void processContract(final List<Contract> contracts, final OpenAPI openAPI, final Entry<String, PathItem> pathItem, final Operation operation, final String name) {
    for (Entry<String, ApiResponse> response : operation.getResponses().entrySet()) {
      final String fileName = name + pathItem.getKey().replaceAll("[{}]", "") + response.getKey().substring(0, 1).toUpperCase() + response.getKey().substring(1) + "Response";
      final String contractName = fileName.replace("/", "");
      final String contractDescription = pathItem.getValue().getSummary();
      var requestList = processRequest(openAPI, pathItem, operation, name);
      var responseList = processResponse(openAPI, response.getKey(), response.getValue());
      final Contract contract = new Contract();
      contract.setName(contractName);
      contract.setDescription(contractDescription);
      contract.setRequest(requestList);
      contract.setResponse(responseList);
      contracts.add(contract);
    }
  }

  private Response processResponse(final OpenAPI openAPI, final String name, final ApiResponse apiResponse) {
    final Response response = new Response();
    if (Objects.nonNull(apiResponse)) {
      if ("default".equalsIgnoreCase(name)) {
        response.status(200);
      } else {
        response.status(Integer.parseInt(name));
      }
      processResponseContent(openAPI, apiResponse, response);
    }
    return response;
  }

  private void processResponseContent(final OpenAPI openAPI, final ApiResponse apiResponse, final Response response) {
    final ResponseBodyMatchers responseBodyMatchers = new ResponseBodyMatchers();
    if (Objects.nonNull(apiResponse.getContent())) {
      for (Entry<String, MediaType> content : apiResponse.getContent().entrySet()) {
        final Schema schema = content.getValue().getSchema();
        final Headers headers = new Headers();
        headers.contentType(content.getKey());
        headers.accept();
        response.setHeaders(headers);
        processResponseBody(openAPI, response, responseBodyMatchers, content, schema);
      }
    } else {
      response.body(response.anyAlphaNumeric());
    }
  }

  private void processResponseBody(
      final OpenAPI openAPI, final Response response, final ResponseBodyMatchers responseBodyMatchers,
      final Entry<String, MediaType> content, final Schema schema) {

    if (content.getValue().getSchema() instanceof ComposedSchema) {
      final ComposedSchema composedSchema = (ComposedSchema) content.getValue().getSchema();
      final var bodyMap = processComposedSchema(openAPI, responseBodyMatchers, composedSchema);
      response.setBody(new Body(bodyMap));
      response.setBodyMatchers(responseBodyMatchers);
    } else if (Objects.nonNull(schema.getType()) && BasicTypeConstants.BASIC_OBJECT_TYPE.contains(schema.getType())) {
      OpenApiContractConverterUtils.processBasicResponseTypeBody(response, schema);
    } else {
      final var bodyMap =processBodyAndMatchers(schema, openAPI, responseBodyMatchers);
      Schema<?> checkArraySchema = new Schema<>();
      if (Objects.nonNull(schema.get$ref())) {
        checkArraySchema = openAPI.getComponents().getSchemas().get(OpenApiContractConverterUtils.mapRefName(schema));
      }
      if (Objects.nonNull(schema.getType()) && "array".equalsIgnoreCase(schema.getType())
          || Objects.nonNull(checkArraySchema.getType()) && "array".equalsIgnoreCase(checkArraySchema.getType())) {
        response.setBody(new Body(bodyMap.values().toArray()[0]));
      } else {
        response.setBody(new Body(bodyMap));
      }
      response.setBodyMatchers(responseBodyMatchers);
    }
  }

  private Request processRequest(final OpenAPI openAPI, final Entry<String, PathItem> pathItem, final Operation operation, final String name) {
    final Request request = new Request();
    if (Objects.nonNull(operation.getParameters()) || Objects.nonNull(pathItem.getValue().getParameters())) {
      final UrlPath url = new UrlPath(pathItem.getKey());
      url.queryParameters(queryParameters -> processQueryParameters(queryParameters, operation.getParameters(), pathItem.getValue()));
      request.setUrlPath(url);
    } else {
      final Url url = new Url(pathItem.getKey());
      request.url(url);
    }
    request.method(name.toUpperCase(Locale.ROOT));
    if (Objects.nonNull(operation.getRequestBody()) && Objects.nonNull(operation.getRequestBody().getContent())) {
      processRequestContent(openAPI, operation, request);
    }
    return request;
  }

  private void processRequestContent(final OpenAPI openAPI, final Operation operation, final Request request) {
    final BodyMatchers bodyMatchers = new BodyMatchers();
    final HashMap<String, Object> bodyMap = new HashMap<>();
    for (Entry<String, MediaType> content : operation.getRequestBody().getContent().entrySet()) {
      final Schema schema = content.getValue().getSchema();
      final Headers headers = new Headers();
      headers.header("Content-Type", content.getKey());
      request.setHeaders(headers);
      if (content.getValue().getSchema() instanceof ComposedSchema) {
        final ComposedSchema composedSchema = (ComposedSchema) content.getValue().getSchema();
        bodyMap.putAll(processComposedSchema(openAPI, bodyMatchers, composedSchema));
        request.body(bodyMap);
        request.setBodyMatchers(bodyMatchers);
      } else if (Objects.nonNull(schema.getType()) && BasicTypeConstants.BASIC_OBJECT_TYPE.contains(schema.getType())) {
        OpenApiContractConverterUtils.processBasicRequestTypeBody(request, schema);
      } else {
        bodyMap.putAll(processBodyAndMatchers(schema, openAPI, bodyMatchers));
        request.body(bodyMap);
        request.setBodyMatchers(bodyMatchers);
      }
    }
  }

  private Map<String, Object> processBodyAndMatchers(final Schema schema, final OpenAPI openAPI, final BodyMatchers bodyMatchers) {

    final Map<String, Object> result = new HashMap<>();
    if (Objects.nonNull(schema.getType())) {
      result.putAll(processBodyAndMatchersByType(schema, openAPI, bodyMatchers));
    }
    if (Objects.nonNull(schema.get$ref())) {
      result.putAll(processBodyAndMatchersByRef(schema, openAPI, bodyMatchers));
    }
    return result;
  }

  private Map<String, Object> processBodyAndMatchersByRef(final Schema schema, final OpenAPI openAPI, final BodyMatchers bodyMatchers) {
    final String ref = OpenApiContractConverterUtils.mapRefName(schema);
    final var bodyMap = new HashMap<String, Object>();
    if (Objects.nonNull(openAPI.getComponents().getSchemas().get(ref).getProperties())) {
      final HashMap<String, Schema> properties = (HashMap<String, Schema>) openAPI.getComponents().getSchemas().get(ref).getProperties();
      for (Entry<String, Schema> property : properties.entrySet()) {
        if (property.getValue() instanceof ComposedSchema) {
          bodyMap.putAll(processComposedSchema(openAPI, bodyMatchers, (ComposedSchema) property.getValue()));
        } else if (Objects.nonNull(property.getValue().get$ref())) {
          final String subRef = OpenApiContractConverterUtils.mapRefName(property.getValue());
          final Schema<?> subSchema = openAPI.getComponents().getSchemas().get(subRef);
          if (Objects.nonNull(subSchema.getProperties())) {
            bodyMap.put(property.getKey(), processComplexBodyAndMatchers(property.getKey(), subSchema.getProperties(), openAPI, bodyMatchers));
          } else if (((ArraySchema) subSchema).getItems() instanceof ComposedSchema) {
            final Schema<?> arraySchema = ((ArraySchema) subSchema).getItems();
            bodyMap.putAll(processComposedSchema(openAPI, bodyMatchers, (ComposedSchema) arraySchema));
          } else {
            final Schema<?> arraySchema = ((ArraySchema) subSchema).getItems();
            bodyMap.put(ref, writeBodyMatcher(openAPI, bodyMatchers, null, ref, arraySchema, arraySchema.getType()));
          }
        } else {
          if (Objects.nonNull(property.getValue().getEnum())) {
            bodyMap.put(property.getKey(), writeBodyMatcher(openAPI, bodyMatchers, null, property.getKey(), property.getValue(), BasicTypeConstants.ENUM));
          } else {
            bodyMap.put(property.getKey(), writeBodyMatcher(openAPI, bodyMatchers, null, property.getKey(), property.getValue(), property.getValue().getType()));
          }
        }
      }
    } else {
      final Schema arraySchema = openAPI.getComponents().getSchemas().get(ref);
      bodyMap.put("[0]", writeBodyMatcher(openAPI, bodyMatchers, null, "[0]", arraySchema, arraySchema.getType()));
    }
    return bodyMap;
  }

  private Map<String, Object> processBodyAndMatchersByType(final Schema schema, final OpenAPI openAPI, final BodyMatchers bodyMatchers) {
    final Map<String, Object> bodyMap = new HashMap<>();
    if (Objects.nonNull(schema.getProperties())) {
      final Map<String, Schema> basicObjectProperties = schema.getProperties();
      for (Entry<String, Schema> property : basicObjectProperties.entrySet()) {
        if (Objects.nonNull(property.getValue().get$ref())) {
          final String subRef = OpenApiContractConverterUtils.mapRefName(property.getValue());
          final HashMap<String, Schema> subProperties = (HashMap<String, Schema>) openAPI.getComponents().getSchemas().get(subRef).getProperties();
          bodyMap.put(property.getKey(), processComplexBodyAndMatchers(property.getKey(), subProperties, openAPI, bodyMatchers));
        } else {
          bodyMap.put(property.getKey(), writeBodyMatcher(openAPI, bodyMatchers, null, property.getKey(), property.getValue(), property.getValue().getType()));
        }
      }
    } else {
      bodyMap.put("[0]", writeBodyMatcher(openAPI, bodyMatchers, null, "[0]", schema, schema.getType()));
    }
    return bodyMap;
  }

  private Object writeBodyMatcher(
      final OpenAPI openAPI, final BodyMatchers bodyMatchers, final Entry<String, Schema> property, final String fieldName, final Schema schema,
      final String type) {
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
          result = processObjectBodyMatcher(openAPI, property, bodyMatchers, fieldName, schema);
          break;
        case BasicTypeConstants.ARRAY:
          final var arraySchema = Objects.nonNull(property) ? null : (ArraySchema) schema;
          result = processArrayBodyMatcher(openAPI, bodyMatchers, property, fieldName, arraySchema);
          break;
        case BasicTypeConstants.ENUM:
          result = processEnum(bodyMatchers, mapKey, Objects.nonNull(property) ? property.getValue() : schema);
          break;
        default:
          bodyMatchers.jsonPath(mapKey, bodyMatchers.byRegex(BasicTypeConstants.DEFAULT_REGEX));
          result =  RandomStringUtils.random(5, true, true);
          break;
      }
    }
    return result;
  }

  private Object processArrayBodyMatcher(
      final OpenAPI openAPI, final BodyMatchers bodyMatchers, final Entry<String, Schema> property, final String fieldName,
      final ArraySchema schema) {
    final Object result;
    final Schema<?> arraySchema = Objects.nonNull(property) ? ((ArraySchema) property.getValue()).getItems() : schema.getItems();
    if (Objects.nonNull(arraySchema.getExample())) {
      result = arraySchema.getExample();
    } else {
      result = processArray(arraySchema, new ArrayList<>(), fieldName, bodyMatchers, openAPI);
    }
    return result;
  }

  private Object processObjectBodyMatcher(
      final OpenAPI openAPI, final Entry<String, Schema> property, final BodyMatchers bodyMatchers, final String fieldName,
      final Schema schema) {
    final Object result;
    final String ref = Objects.nonNull(property) ? property.getValue().get$ref() : schema.get$ref();
    final Schema internalRef = Objects.nonNull(property) ? property.getValue() : schema;
    final String subRef = OpenApiContractConverterUtils.mapRefName(internalRef);

    if (Objects.nonNull(ref)) {
      final HashMap<String, Schema> subPropertiesWithRef = (HashMap<String, Schema>) openAPI.getComponents().getSchemas().get(subRef).getProperties();
      result = processComplexBodyAndMatchers(fieldName, subPropertiesWithRef, openAPI, bodyMatchers);
    } else {
      final HashMap<String, Schema> subProperties = (HashMap<String, Schema>) internalRef.getProperties();
      result = processComplexBodyAndMatchers(fieldName, subProperties, openAPI, bodyMatchers);
    }
    return result;
  }

  private Object processNumberBodyMatcher(
      final BodyMatchers bodyMatchers, final Entry<String, Schema> property, final String fieldName,
      final Schema schema) {

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

  private Object processIntegerBodyMatcher(
      final BodyMatchers bodyMatchers, final Entry<String, Schema> property, final String fieldName,
      final Schema schema) {
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
    final Iterator enumObjects = property.getEnum().iterator();
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

  private HashMap<String, Object> processComplexBodyAndMatchers(
      final String objectName, final Map<String, Schema> properties, final OpenAPI openAPI,
      final BodyMatchers bodyMatchers) {

    final HashMap<String, Object> propertyMap = new HashMap<>();
    for (Entry<String, Schema> property : properties.entrySet()) {
      final String newObjectName = objectName + "." + property.getKey();
      if (Objects.nonNull(property.getValue().get$ref())) {
        final String ref = OpenApiContractConverterUtils.mapRefName(property.getValue());
        if (Objects.nonNull(openAPI.getComponents().getSchemas().get(ref).getProperties())) {
          final HashMap<String, Schema> subProperties = (HashMap<String, Schema>) openAPI.getComponents().getSchemas().get(ref).getProperties();
          propertyMap.put(property.getKey(), processComplexBodyAndMatchers(newObjectName, subProperties, openAPI, bodyMatchers));
        } else {
          final var subProperties = ((ArraySchema) openAPI.getComponents().getSchemas().get(ref)).getItems();
          final List<Object> propertyList = new ArrayList<>();
          propertyMap.put(property.getKey(), processArray(subProperties, propertyList, newObjectName, bodyMatchers, openAPI));
        }
      } else {
        final String type;
        if (Objects.nonNull(property.getValue().getEnum())) {
          type = BasicTypeConstants.ENUM;
        } else {
          type = property.getValue().getType();
        }
        propertyMap.put(property.getKey(), writeBodyMatcher(openAPI, bodyMatchers, property, newObjectName, null, type));
      }
    }
    return propertyMap;
  }

  private List<Object> processArray(final Schema<?> arraySchema, final List<Object> propertyList, final String objectName, final BodyMatchers bodyMatchers, final OpenAPI openAPI) {

    if (Objects.nonNull(arraySchema.get$ref())) {
      final String ref = OpenApiContractConverterUtils.mapRefName(arraySchema);
      final HashMap<String, Schema> subObject = (HashMap<String, Schema>) openAPI.getComponents().getSchemas().get(ref).getProperties();
      propertyList.add(processComplexBodyAndMatchers("[0]", subObject, openAPI, bodyMatchers));
    } else {
      final String type = arraySchema.getType();
      switch (type) {
        case BasicTypeConstants.STRING:
          processStringArray(arraySchema, propertyList, objectName, bodyMatchers);
          break;
        case BasicTypeConstants.INTEGER:
          processIntegerArray(arraySchema, propertyList, objectName, bodyMatchers);
          break;
        case BasicTypeConstants.NUMBER:
          processNumberArray(arraySchema, propertyList, objectName, bodyMatchers);
          break;
        case BasicTypeConstants.BOOLEAN:
          processBooleanArray(arraySchema, propertyList, objectName, bodyMatchers);
          break;
        case BasicTypeConstants.ARRAY:
          processArrayArray((ArraySchema) arraySchema, propertyList, objectName, bodyMatchers, openAPI);
          break;
        case BasicTypeConstants.OBJECT:
          processObjectArray(arraySchema, propertyList, objectName, bodyMatchers, openAPI);
          break;
        default:
          log.error("Format not supported");
          break;
      }
    }
    return propertyList;
  }

  private void processObjectArray(final Schema<?> arraySchema, final List<Object> propertyList, final String objectName, final BodyMatchers bodyMatchers, final OpenAPI openAPI) {
    final HashMap<String, Schema> subObject = (HashMap<String, Schema>) arraySchema.getProperties();
    propertyList.add(processComplexBodyAndMatchers(objectName + "[0]", subObject, openAPI, bodyMatchers));
  }

  private void processArrayArray(final ArraySchema arraySchema, final List<Object> propertyList, final String objectName, final BodyMatchers bodyMatchers, final OpenAPI openAPI) {
    final Schema<?> subArray = arraySchema.getItems();
    if (Objects.nonNull(subArray.getExample())) {
      propertyList.add(subArray.getExample());
    } else {
      final List<Object> subPropertyList = new ArrayList<>();
      propertyList.add(processArray(subArray, subPropertyList, objectName, bodyMatchers, openAPI));
    }
  }

  private void processBooleanArray(final Schema<?> arraySchema, final List<Object> propertyList, final String objectName, final BodyMatchers bodyMatchers) {
    if (Objects.nonNull(arraySchema.getName())) {
      bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
    } else {
      bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
    }
    propertyList.add(BasicTypeConstants.RANDOM.nextBoolean());
  }

  private void processNumberArray(final Schema<?> arraySchema, final List<Object> propertyList, final String objectName, final BodyMatchers bodyMatchers) {
    if (BasicTypeConstants.FLOAT.equalsIgnoreCase(arraySchema.getFormat())) {
      if (Objects.nonNull(arraySchema.getName())) {
        bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      } else {
        bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      }
      propertyList.add(BasicTypeConstants.RANDOM.nextFloat());
    } else if (BasicTypeConstants.DOUBLE.equalsIgnoreCase(arraySchema.getFormat())) {
      if (Objects.nonNull(arraySchema.getName())) {
        bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      } else {
        bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      }
      propertyList.add(Math.abs(BasicTypeConstants.RANDOM.nextDouble()));
    } else {
      if (Objects.nonNull(arraySchema.getName())) {
        bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      } else {
        bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      }
      propertyList.add(BasicTypeConstants.RANDOM.nextInt());
    }
  }

  private void processIntegerArray(final Schema<?> arraySchema, final List<Object> propertyList, final String objectName, final BodyMatchers bodyMatchers) {
    if (BasicTypeConstants.INT_32.equalsIgnoreCase(arraySchema.getFormat()) || !Objects.nonNull(arraySchema.getFormat())) {
      if (Objects.nonNull(arraySchema.getName())) {
        bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      } else {
        bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
      }
      propertyList.add(BasicTypeConstants.RANDOM.nextInt());
    } else {
      if (Objects.nonNull(arraySchema.getName())) {
        bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      } else {
        bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
      }
      propertyList.add(Math.abs(BasicTypeConstants.RANDOM.nextFloat()));
    }
  }

  private void processStringArray(final Schema<?> arraySchema, final List<Object> propertyList, final String objectName, final BodyMatchers bodyMatchers) {
    if (Objects.nonNull(arraySchema.getName())) {
      bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
    } else {
      bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
    }
    propertyList.add(RandomStringUtils.random(5, true, true));
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

  private Map<String, Object> processComposedSchema(final OpenAPI openAPI, final BodyMatchers bodyMatchers, final ComposedSchema composedSchema) {
    final Map<String, Object> result = new HashMap<>();
    if (Objects.nonNull(composedSchema.getAllOf())) {
      for (Schema schema : composedSchema.getAllOf()) {
        result.putAll(processBodyAndMatchers(schema, openAPI, bodyMatchers));
      }
    } else if (Objects.nonNull(composedSchema.getOneOf())) {
      final int oneOfNumber = BasicTypeConstants.RANDOM.nextInt(composedSchema.getOneOf().size());
      result.putAll(processBodyAndMatchers(composedSchema.getOneOf().get(oneOfNumber), openAPI, bodyMatchers));
    } else if (Objects.nonNull(composedSchema.getAnyOf())) {
      for (int i = 0; i < BasicTypeConstants.RANDOM.nextInt(composedSchema.getAnyOf().size()) + 1; i++) {
        result.putAll(processBodyAndMatchers(composedSchema.getAnyOf().get(i), openAPI, bodyMatchers));
      }
    }
    return result;
  }

}