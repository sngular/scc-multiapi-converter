/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.corunet.multiapi.converter;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

import com.corunet.multiapi.converter.asyncapi.AsyncApiContractConverter;
import com.corunet.multiapi.converter.exception.MultiApiContractConverterException;
import com.corunet.multiapi.converter.openapi.OpenApiContractConverter;
import com.corunet.multiapi.converter.utils.BasicTypeConstants;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.ContractConverter;

@Slf4j
public class MultiApiContractConverter implements ContractConverter<Collection<Contract>> {

  private static final OpenApiContractConverter openApiContractConverter = new OpenApiContractConverter();

  private static final AsyncApiContractConverter asyncApiContractConverter = new AsyncApiContractConverter();

  @Override
  public boolean isAccepted(final File file) {
    String name = file.getName();
    boolean isAccepted = name.endsWith(".yml") || name.endsWith(".yaml");
    if (isAccepted) {
      try {
        JsonNode node;
        node = BasicTypeConstants.OBJECT_MAPPER.readTree(file);
        isAccepted = (node != null && node.size() > 0 && (Objects.nonNull(node.get(BasicTypeConstants.ASYNCAPI)) || Objects.nonNull(node.get(BasicTypeConstants.OPENAPI))));
      } catch (IOException e) {
        isAccepted = false;
      }
    }
    return isAccepted;
  }

  @Override
  public Collection<Contract> convertFrom(final File file) {
    Collection<Contract> contracts = null;
    JsonNode node;
    try {
      node = BasicTypeConstants.OBJECT_MAPPER.readTree(file);
      if (node != null && node.size() > 0) {
        if (Objects.nonNull(node.get(BasicTypeConstants.ASYNCAPI))) {
          contracts = asyncApiContractConverter.convertFrom(file);
        } else if (Objects.nonNull(node.get(BasicTypeConstants.OPENAPI))) {
          contracts = openApiContractConverter.convertFrom(file);
        }
      } else {
        throw new MultiApiContractConverterException("Yaml file is not correct");
      }
    } catch (IOException e) {
      throw new MultiApiContractConverterException(e);
    }

    return contracts;
  }

  @Override
  public Collection<Contract> convertTo(Collection<Contract> contract) {return contract;}
}
