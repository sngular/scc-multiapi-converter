/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package net.coru.multiapi.converter;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

import net.coru.multiapi.converter.exception.MultiApiContractConverterException;
import net.coru.multiapi.converter.openapi.OpenApiContractConverter;
import net.coru.multiapi.converter.utils.BasicTypeConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.ContractConverter;

@Slf4j
public class MultiApiContractConverter implements ContractConverter<Collection<Contract>> {

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

  private static final OpenApiContractConverter OPEN_API_CONTRACT_CONVERTER = new OpenApiContractConverter();

  @Override
  public boolean isAccepted(final File file) {
    final String name = file.getName();
    boolean isAccepted = name.endsWith(".yml") || name.endsWith(".yaml");
    if (isAccepted) {
      try {
        final JsonNode node;
        node = OBJECT_MAPPER.readTree(file);
        isAccepted = (node != null && node.size() > 0 && Objects.nonNull(node.get(BasicTypeConstants.OPENAPI)));
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
        if (Objects.nonNull(node.get(BasicTypeConstants.OPENAPI))) {
          contracts = OPEN_API_CONTRACT_CONVERTER.convertFrom(file);
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
  public Collection<Contract> convertTo(final Collection<Contract> contract) {return contract;}
}
