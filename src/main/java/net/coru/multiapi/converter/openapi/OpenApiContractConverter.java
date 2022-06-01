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

import net.coru.multiapi.converter.exception.MultiApiContractConverterException;
import net.coru.multiapi.converter.utils.BasicTypeConstants;
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
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.Body;
import org.springframework.cloud.contract.spec.internal.BodyMatchers;
import org.springframework.cloud.contract.spec.internal.Headers;
import org.springframework.cloud.contract.spec.internal.MatchingStrategy;
import org.springframework.cloud.contract.spec.internal.MatchingStrategy.Type;
import org.springframework.cloud.contract.spec.internal.QueryParameters;
import org.springframework.cloud.contract.spec.internal.Request;
import org.springframework.cloud.contract.spec.internal.Response;
import org.springframework.cloud.contract.spec.internal.ResponseBodyMatchers;
import org.springframework.cloud.contract.spec.internal.Url;
import org.springframework.cloud.contract.spec.internal.UrlPath;

@Slf4j
public class OpenApiContractConverter {

  public Collection<Contract> convertFrom(final File file) {

    final Collection<Contract> contracts = new ArrayList<>();

    try {
      contracts.addAll(getContracts(getOpenApi(file)));
    } catch (MultiApiContractConverterException e) {
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

  private void processContract(final List<Contract> contracts, OpenAPI openAPI, Entry<String, PathItem> pathItem, Operation operation, String name) {
    for (Entry<String, ApiResponse> response : operation.getResponses().entrySet()) {
      final Contract contract = new Contract();
      final String fileName = name + pathItem.getKey().replaceAll("[{}]", "") + response.getKey().substring(0, 1).toUpperCase() + response.getKey().substring(1) + "Response";
      contract.setName(fileName.replace("/", ""));
      contract.setDescription(pathItem.getValue().getSummary());
      contract.setRequest(processRequest(openAPI, pathItem, operation, name));
      contract.setResponse(processResponse(openAPI, response.getKey(), response.getValue()));
      contracts.add(contract);
    }
  }

  private Response processResponse(final OpenAPI openAPI, String name, ApiResponse apiResponse) {
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

  private void processResponseContent(OpenAPI openAPI, ApiResponse apiResponse, Response response) {
    final ResponseBodyMatchers responseBodyMatchers = new ResponseBodyMatchers();
    final HashMap<String, Object> bodyMap = new HashMap<>();
    if (Objects.nonNull(apiResponse.getContent())) {
      for (Entry<String, MediaType> content : apiResponse.getContent().entrySet()) {
        final Schema schema = content.getValue().getSchema();
        final Headers headers = new Headers();
        headers.contentType(content.getKey());
        headers.accept();
        response.setHeaders(headers);
        if (content.getValue().getSchema() instanceof ComposedSchema) {
          final ComposedSchema composedSchema = (ComposedSchema) content.getValue().getSchema();
          processComposedSchema(openAPI, responseBodyMatchers, bodyMap, composedSchema);
          response.setBody(new Body(bodyMap));
          response.setBodyMatchers(responseBodyMatchers);
        } else if (Objects.nonNull(schema.getType()) && BasicTypeConstants.BASIC_OBJECT_TYPE.contains(schema.getType())) {
          OpenApiContractConverterUtils.processBasicResponseTypeBody(response, schema);
        } else {
          processBodyAndMatchers(bodyMap, schema, openAPI, responseBodyMatchers);
          response.setBody(new Body(bodyMap));
          response.setBodyMatchers(responseBodyMatchers);
        }
      }
    } else {
      response.body(response.anyAlphaNumeric());
    }

  }

  private Request processRequest(final OpenAPI openAPI, Entry<String, PathItem> pathItem, Operation operation, String name) {
    final Request request = new Request();
    if (Objects.nonNull(operation.getParameters())) {
      final UrlPath url = new UrlPath(pathItem.getKey());
      url.queryParameters(queryParameters -> processQueryParameters(queryParameters, operation.getParameters()));
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

  private void processRequestContent(final OpenAPI openAPI, Operation operation, Request request) {
    final BodyMatchers bodyMatchers = new BodyMatchers();
    HashMap<String, Object> bodyMap = new HashMap<>();
    for (Entry<String, MediaType> content : operation.getRequestBody().getContent().entrySet()) {
      Schema schema = content.getValue().getSchema();
      Headers headers = new Headers();
      headers.header("Content-Type", content.getKey());
      request.setHeaders(headers);
      if (content.getValue().getSchema() instanceof ComposedSchema) {
        ComposedSchema composedSchema = (ComposedSchema) content.getValue().getSchema();
        processComposedSchema(openAPI, bodyMatchers, bodyMap, composedSchema);
        request.body(bodyMap);
        request.setBodyMatchers(bodyMatchers);
      } else if (Objects.nonNull(schema.getType()) && BasicTypeConstants.BASIC_OBJECT_TYPE.contains(schema.getType())) {
        OpenApiContractConverterUtils.processBasicRequestTypeBody(request, schema);
      } else {
        processBodyAndMatchers(bodyMap, schema, openAPI, bodyMatchers);
        request.body(bodyMap);
        request.setBodyMatchers(bodyMatchers);
      }
    }

  }

  private void processBodyAndMatchers(final Map<String, Object> bodyMap, Schema schema, OpenAPI openAPI, BodyMatchers bodyMatchers) {

    if (Objects.nonNull(schema.getType())) {
      final Map<String, Schema> basicObjectProperties = schema.getProperties();
      for (Entry<String, Schema> property : basicObjectProperties.entrySet()) {
        if (Objects.nonNull(property.getValue().get$ref())) {
          final String subRef = OpenApiContractConverterUtils.mapRefName(property.getValue());
          final HashMap<String, Schema> subProperties = (HashMap<String, Schema>) openAPI.getComponents().getSchemas().get(subRef).getProperties();
          bodyMap.put(property.getKey(), processComplexBodyAndMatchers(property.getKey(), subProperties, openAPI, bodyMatchers));
        } else {
          writeBodyMatcher(bodyMap, openAPI, bodyMatchers, property.getKey(), property.getValue(), property.getValue().getType());
        }
      }
    }
    if (Objects.nonNull(schema.get$ref())) {
      final String ref = OpenApiContractConverterUtils.mapRefName(schema);
      final HashMap<String, Schema> properties = (HashMap<String, Schema>) openAPI.getComponents().getSchemas().get(ref).getProperties();
      for (Entry<String, Schema> property : properties.entrySet()) {
        if (property.getValue() instanceof ComposedSchema) {
          final ComposedSchema subComposedSchema = (ComposedSchema) property.getValue();
          processComposedSchema(openAPI, bodyMatchers, bodyMap, subComposedSchema);
        } else if (Objects.nonNull(property.getValue().get$ref())) {
          String subRef = OpenApiContractConverterUtils.mapRefName(property.getValue());
          HashMap<String, Schema> subProperties = (HashMap<String, Schema>) openAPI.getComponents().getSchemas().get(subRef).getProperties();
          bodyMap.put(property.getKey(), processComplexBodyAndMatchers(property.getKey(), subProperties, openAPI, bodyMatchers));
        } else {
          final String refType;
          if (Objects.nonNull(property.getValue().getEnum())) {
            refType = BasicTypeConstants.ENUM;
          } else {
            refType = property.getValue().getType();
          }
          writeBodyMatcher(bodyMap, openAPI, bodyMatchers, property.getKey(), property.getValue(), refType);
        }
      }
    }
  }

  private void writeBodyMatcher(final Map<String, Object> bodyMap, OpenAPI openAPI, BodyMatchers bodyMatchers, String fieldName, Schema schema, String type) {
    if (Objects.nonNull(schema.getExample())) {
      bodyMap.put(fieldName, schema.getExample());
    } else {
      switch (type) {
        case BasicTypeConstants.STRING:
          bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
          bodyMap.put(fieldName, RandomStringUtils.random(5, true, true));
          break;
        case BasicTypeConstants.INTEGER:
          if (BasicTypeConstants.INT_32.equalsIgnoreCase(schema.getFormat()) || !Objects.nonNull(schema.getFormat())) {
            bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
            bodyMap.put(fieldName, BasicTypeConstants.RANDOM.nextInt());
          } else if (BasicTypeConstants.INT_64.equalsIgnoreCase(schema.getFormat())) {
            bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
            bodyMap.put(fieldName, BasicTypeConstants.RANDOM.nextFloat());
          }
          break;
        case BasicTypeConstants.NUMBER:
          if (BasicTypeConstants.FLOAT.equalsIgnoreCase(schema.getFormat())) {
            bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
            bodyMap.put(fieldName, Math.abs(BasicTypeConstants.RANDOM.nextFloat()));
          } else if (BasicTypeConstants.DOUBLE.equalsIgnoreCase(schema.getFormat())) {
            bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
            bodyMap.put(fieldName, Math.abs(BasicTypeConstants.RANDOM.nextDouble()));
          } else if (schema.getFormat().isEmpty()) {
            bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
            bodyMap.put(fieldName, BasicTypeConstants.RANDOM.nextInt());
          }
          break;
        case BasicTypeConstants.BOOLEAN:
          bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
          bodyMap.put(fieldName, BasicTypeConstants.RANDOM.nextBoolean());
          break;
        case BasicTypeConstants.OBJECT:
          if (Objects.nonNull(schema.get$ref())) {
            final String subRef = OpenApiContractConverterUtils.mapRefName(schema);
            final HashMap<String, Schema> subPropertiesWithRef = (HashMap<String, Schema>) openAPI.getComponents().getSchemas().get(subRef).getProperties();
            bodyMap.put(fieldName, processComplexBodyAndMatchers(fieldName, subPropertiesWithRef, openAPI, bodyMatchers));
          } else {
            final HashMap<String, Schema> subProperties = (HashMap<String, Schema>) schema.getProperties();
            bodyMap.put(fieldName, processComplexBodyAndMatchers(fieldName, subProperties, openAPI, bodyMatchers));
          }
          break;
        case BasicTypeConstants.ARRAY:
          final Schema<?> arraySchema = ((ArraySchema) schema).getItems();
          if (Objects.nonNull(arraySchema.getExample())) {
            bodyMap.put(fieldName, arraySchema.getExample());
          } else {
            final List<Object> propertyList = new ArrayList<>();
            bodyMap.put(fieldName, processArray(arraySchema, propertyList, fieldName, bodyMatchers, openAPI));
          }
          break;
        case BasicTypeConstants.ENUM:
          processEnum(bodyMap, bodyMatchers, fieldName, schema);
          break;
        default:
          bodyMatchers.jsonPath(fieldName, bodyMatchers.byRegex(BasicTypeConstants.DEFAULT_REGEX));
          bodyMap.put(fieldName, RandomStringUtils.random(5, true, true));
          break;
      }
    }
  }

  private HashMap<String, Object> processComplexBodyAndMatchers(final String objectName, Map<String, Schema> properties, OpenAPI openAPI, BodyMatchers bodyMatchers) {

    final HashMap<String, Object> propertyMap = new HashMap<>();
    for (Entry<String, Schema> property : properties.entrySet()) {
      final String newObjectName = objectName + "." + property.getKey();
      if (Objects.nonNull(property.getValue().get$ref())) {
        final String ref = OpenApiContractConverterUtils.mapRefName(property.getValue());
        final HashMap<String, Schema> subProperties = (HashMap<String, Schema>) openAPI.getComponents().getSchemas().get(ref).getProperties();
        propertyMap.put(property.getKey(), processComplexBodyAndMatchers(newObjectName, subProperties, openAPI, bodyMatchers));
      } else {
        final String type;
        if (Objects.nonNull(property.getValue().getEnum())) {
          type = BasicTypeConstants.ENUM;
        } else {
          type = property.getValue().getType();
        }
        if (Objects.nonNull(property.getValue().getExample())) {
          propertyMap.put(property.getKey(), property.getValue().getExample());
        } else {
          switch (type) {
            case BasicTypeConstants.STRING:
              bodyMatchers.jsonPath(newObjectName, bodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
              propertyMap.put(property.getKey(), RandomStringUtils.random(5, true, true));
              break;
            case BasicTypeConstants.INTEGER:
              if (BasicTypeConstants.INT_32.equalsIgnoreCase(property.getValue().getFormat()) || !Objects.nonNull(property.getValue().getFormat())) {
                bodyMatchers.jsonPath(newObjectName, bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
                propertyMap.put(property.getKey(), BasicTypeConstants.RANDOM.nextInt());
              } else if (BasicTypeConstants.INT_64.equalsIgnoreCase(property.getValue().getFormat())) {
                bodyMatchers.jsonPath(newObjectName, bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
                propertyMap.put(property.getKey(), Math.abs(BasicTypeConstants.RANDOM.nextFloat()));
              }
              break;
            case BasicTypeConstants.NUMBER:
              if (BasicTypeConstants.FLOAT.equalsIgnoreCase(property.getValue().getFormat())) {
                bodyMatchers.jsonPath(newObjectName, bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
                propertyMap.put(property.getKey(), BasicTypeConstants.RANDOM.nextFloat());
              } else if (BasicTypeConstants.DOUBLE.equalsIgnoreCase(property.getValue().getFormat())) {
                bodyMatchers.jsonPath(property.getKey(), bodyMatchers.byRegex(BasicTypeConstants.DECIMAL_REGEX));
                propertyMap.put(property.getKey(), BasicTypeConstants.RANDOM.nextDouble());
              } else if (property.getValue().getFormat().isEmpty()) {
                bodyMatchers.jsonPath(newObjectName, bodyMatchers.byRegex(BasicTypeConstants.INT_REGEX));
                propertyMap.put(property.getKey(), BasicTypeConstants.RANDOM.nextInt());
              }
              break;
            case BasicTypeConstants.BOOLEAN:
              bodyMatchers.jsonPath(newObjectName, bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
              propertyMap.put(property.getKey(), BasicTypeConstants.RANDOM.nextBoolean());
              break;
            case BasicTypeConstants.ENUM:
              processEnum(propertyMap, bodyMatchers, property.getKey(), property.getValue());
              break;
            case BasicTypeConstants.OBJECT:
              if (Objects.nonNull(property.getValue().get$ref())) {
                String subRef = OpenApiContractConverterUtils.mapRefName(property.getValue());
                HashMap<String, Schema> subPropertiesWithRef = (HashMap<String, Schema>) openAPI.getComponents().getSchemas().get(subRef).getProperties();
                propertyMap.put(property.getKey(), processComplexBodyAndMatchers(newObjectName, subPropertiesWithRef, openAPI, bodyMatchers));
              } else {
                final HashMap<String, Schema> subProperties = (HashMap<String, Schema>) property.getValue().getProperties();
                propertyMap.put(property.getKey(), processComplexBodyAndMatchers(newObjectName, subProperties, openAPI, bodyMatchers));
              }
              break;
            case BasicTypeConstants.ARRAY:
              final Schema<?> arraySchema = ((ArraySchema) property.getValue()).getItems();
              if (Objects.nonNull(arraySchema.getExample())) {
                propertyMap.put(property.getKey(), arraySchema.getExample());
              } else {
                List<Object> propertyList = new ArrayList<>();
                propertyMap.put(property.getKey(), processArray(arraySchema, propertyList, newObjectName, bodyMatchers, openAPI));
              }
              break;
            default:
              bodyMatchers.jsonPath(property.getKey(), bodyMatchers.byRegex(BasicTypeConstants.DEFAULT_REGEX));
              propertyMap.put(property.getKey(), RandomStringUtils.random(5, true, true));
              break;
          }
        }
      }
    }
    return propertyMap;
  }

  private List<Object> processArray(final Schema<?> arraySchema, List<Object> propertyList, String objectName, BodyMatchers bodyMatchers, OpenAPI openAPI) {

    if (Objects.nonNull(arraySchema.get$ref())) {
      final String ref = OpenApiContractConverterUtils.mapRefName(arraySchema);
      final HashMap<String, Schema> subObject = (HashMap<String, Schema>) openAPI.getComponents().getSchemas().get(ref).getProperties();
      propertyList.add(processComplexBodyAndMatchers(objectName + "[0]", subObject, openAPI, bodyMatchers));
    } else {
      final String type = arraySchema.getType();
      switch (type) {
        case BasicTypeConstants.STRING:
          if (Objects.nonNull(arraySchema.getName())) {
            bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
          } else {
            bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.STRING_REGEX));
          }
          propertyList.add(RandomStringUtils.random(5, true, true));
          break;
        case BasicTypeConstants.INTEGER:
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
          break;
        case BasicTypeConstants.NUMBER:
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
          break;
        case BasicTypeConstants.BOOLEAN:
          if (Objects.nonNull(arraySchema.getName())) {
            bodyMatchers.jsonPath(arraySchema.getName() + "[0]", bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
          } else {
            bodyMatchers.jsonPath(objectName + "[0]", bodyMatchers.byRegex(BasicTypeConstants.BOOLEAN_REGEX));
          }
          propertyList.add(BasicTypeConstants.RANDOM.nextBoolean());
          break;
        case BasicTypeConstants.ARRAY:
          final Schema<?> subArray = ((ArraySchema) arraySchema).getItems();
          if (Objects.nonNull(subArray.getExample())) {
            propertyList.add(subArray.getExample());
          } else {
            List<Object> subPropertyList = new ArrayList<>();
            propertyList.add(processArray(subArray, subPropertyList, objectName, bodyMatchers, openAPI));
          }
          break;
        case BasicTypeConstants.OBJECT:
          HashMap<String, Schema> subObject = (HashMap<String, Schema>) arraySchema.getProperties();
          propertyList.add(processComplexBodyAndMatchers(objectName + "[0]", subObject, openAPI, bodyMatchers));
          break;
        default:
          log.error("Format not supported");
          break;
      }
    }
    return propertyList;
  }

  private void processQueryParameters(QueryParameters queryParameters, List<Parameter> parameters) {
    for (Parameter parameter : parameters) {
      if (Objects.nonNull(parameter.getExample())) {
        queryParameters.parameter(parameter.getName(), new MatchingStrategy(parameter.getExample(), Type.EQUAL_TO));
      } else if (Objects.nonNull(parameter.getSchema().getExample())) {
        queryParameters.parameter(parameter.getName(), new MatchingStrategy(parameter.getSchema().getExample(), Type.EQUAL_TO));
      } else {
        String type = parameter.getSchema().getType();
        switch (type) {
          case BasicTypeConstants.STRING:
            queryParameters.parameter(parameter.getName(), BasicTypeConstants.STRING_REGEX);
            break;
          case BasicTypeConstants.INTEGER:
            if (BasicTypeConstants.INT_32.equalsIgnoreCase(parameter.getSchema().getFormat()) || !Objects.nonNull(parameter.getSchema().getFormat())) {
              queryParameters.parameter(parameter.getName(), BasicTypeConstants.INT_REGEX);
            } else if (BasicTypeConstants.INT_64.equalsIgnoreCase(parameter.getSchema().getFormat())) {
              queryParameters.parameter(parameter.getName(), BasicTypeConstants.DECIMAL_REGEX);
            }
            break;
          case BasicTypeConstants.NUMBER:
            if (BasicTypeConstants.FLOAT.equalsIgnoreCase(parameter.getSchema().getFormat())) {
              queryParameters.parameter(parameter.getName(), BasicTypeConstants.INT_REGEX);
            } else if (BasicTypeConstants.DOUBLE.equalsIgnoreCase(parameter.getSchema().getFormat())) {
              queryParameters.parameter(parameter.getName(), BasicTypeConstants.DECIMAL_REGEX);
            } else {
              queryParameters.parameter(parameter.getName(), BasicTypeConstants.INT_REGEX);
            }
            break;
          case BasicTypeConstants.BOOLEAN:
            queryParameters.parameter(parameter.getName(), BasicTypeConstants.BOOLEAN_REGEX);
            break;
          default:
            queryParameters.parameter(parameter.getName(), BasicTypeConstants.DEFAULT_REGEX);
            break;
        }
      }
    }
  }

  private void processEnum(Map<String, Object> bodyMap, BodyMatchers bodyMatchers, String enumName, Schema property) {
    String regex = "";
    Iterator enumObjects = property.getEnum().iterator();
    while (enumObjects.hasNext()) {
      Object nextObject = enumObjects.next();
      if (!enumObjects.hasNext()) {
        regex = regex.concat(nextObject.toString());
      } else {
        regex = regex.concat(nextObject.toString() + "|");
      }
    }
    bodyMatchers.jsonPath(enumName, bodyMatchers.byRegex(regex));
    bodyMap.put(enumName, property.getEnum().get(BasicTypeConstants.RANDOM.nextInt(property.getEnum().size())));
  }

  private OpenAPI getOpenApi(File file) throws MultiApiContractConverterException {
    OpenAPI openAPI;
    try {
      SwaggerParseResult result = new OpenAPIParser().readLocation(file.getPath(), null, null);
      openAPI = result.getOpenAPI();
    } catch (Exception e) {
      throw new MultiApiContractConverterException("Code generation failed when parser the .yaml file ");
    }
    if (openAPI == null) {
      throw new MultiApiContractConverterException("Code generation failed why .yaml is empty");
    }
    return openAPI;
  }

  private void processComposedSchema(OpenAPI openAPI, BodyMatchers bodyMatchers, Map<String, Object> bodyMap, ComposedSchema composedSchema) {
    if (Objects.nonNull(composedSchema.getAllOf())) {
      for (Schema schema : composedSchema.getAllOf()) {
        processBodyAndMatchers(bodyMap, schema, openAPI, bodyMatchers);
      }
    } else if (Objects.nonNull(composedSchema.getOneOf())) {
      int oneOfNumber = BasicTypeConstants.RANDOM.nextInt(composedSchema.getOneOf().size());
      processBodyAndMatchers(bodyMap, composedSchema.getOneOf().get(oneOfNumber), openAPI, bodyMatchers);
    } else if (Objects.nonNull(composedSchema.getAnyOf())) {
      for (int i = 0; i < BasicTypeConstants.RANDOM.nextInt(composedSchema.getAnyOf().size()) + 1; i++) {
        processBodyAndMatchers(bodyMap, composedSchema.getAnyOf().get(i), openAPI, bodyMatchers);
      }
    }
  }

}