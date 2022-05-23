package com.corunet.multiapi.converter;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

import com.corunet.multiapi.converter.exception.MultiApiContractConverterException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.ContractConverter;

@Slf4j
public class MultiApiContractConverter implements ContractConverter<Collection<Contract>> {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

  private static final String ASYNCAPI = "asyncapi";

  @Override
  public boolean isAccepted(final File file) {
    String name = file.getName();
    boolean isAccepted = name.endsWith(".yml") || name.endsWith(".yaml");
    if (isAccepted) {
      try {
        JsonNode node;
        node = OBJECT_MAPPER.readTree(file);
        isAccepted = (node != null && node.size() > 0 && Objects.nonNull(node.get(ASYNCAPI)));
      } catch (IOException e) {
        isAccepted = false;
      }
      if (!isAccepted){
        OpenAPI openAPI = null;
        try {
          openAPI = getOpenApi(file);
        } catch (RuntimeException e) {
          e.printStackTrace();
        }
        if (openAPI == null) {
          log.error("Code generation failed why .yaml is empty");
        } else {
          isAccepted = true;
        }
      }
    }
    return isAccepted;
  }

  @Override
  public Collection<Contract> convertFrom(final File file) {

    //SI es ASYNCAPI: HACEMOS LO DE ASYNCAPI

    //SI es openAPI: HACEMOS LO DE OPENAPI

    //Excepcion, que no debería entrar nunca.

    return null;
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

  @Override
  public Collection<Contract> convertTo(Collection<Contract> contract) {
    return contract;
  }
}
