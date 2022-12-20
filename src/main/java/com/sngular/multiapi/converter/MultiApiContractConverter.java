/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sngular.multiapi.converter;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.sngular.multiapi.converter.asyncapi.AsyncApiContractConverter;
import com.sngular.multiapi.converter.exception.MultiApiContractConverterException;
import com.sngular.multiapi.converter.openapi.OpenApiContractConverter;
import com.sngular.multiapi.converter.utils.BasicTypeConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.ContractConverter;

@Slf4j
public final class MultiApiContractConverter implements ContractConverter<Collection<Contract>> {

  private static final OpenApiContractConverter OPEN_API_CONTRACT_CONVERTER = new OpenApiContractConverter();

  private static final AsyncApiContractConverter ASYNC_API_CONTRACT_CONVERTER = new AsyncApiContractConverter();

  @Override
  public boolean isAccepted(final File file) {
    final String name = file.getName();
    boolean isAccepted = name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json");
    if (isAccepted) {
      try {
        final JsonNode node;
        node = BasicTypeConstants.OBJECT_MAPPER.readTree(file);
        isAccepted = node != null && node.size() > 0 && (Objects.nonNull(node.get(BasicTypeConstants.ASYNCAPI)) || Objects.nonNull(node.get(BasicTypeConstants.OPENAPI)));
      } catch (final IOException e) {
        isAccepted = false;
      }
    }
    return isAccepted;
  }

  @Override
  public Collection<Contract> convertFrom(final File file) {

    Collection<Contract> contracts = null;
    final JsonNode node;
    if (isAccepted(file)) {
      try {
        node = BasicTypeConstants.OBJECT_MAPPER.readTree(file);
        if (node != null && node.size() > 0) {
          if (Objects.nonNull(node.get(BasicTypeConstants.ASYNCAPI))) {
            contracts = ASYNC_API_CONTRACT_CONVERTER.convertFrom(file);
          } else if (Objects.nonNull(node.get(BasicTypeConstants.OPENAPI))) {
            contracts = OPEN_API_CONTRACT_CONVERTER.convertFrom(file);
          }
        } else {
          throw new MultiApiContractConverterException("Yaml file is not correct");
        }
      } catch (final IOException e) {
        throw new MultiApiContractConverterException(e);
      }
    }
    return contracts;
  }

  @Override
  public Collection<Contract> convertTo(final Collection<Contract> contract) {
    return contract;
  }
}
