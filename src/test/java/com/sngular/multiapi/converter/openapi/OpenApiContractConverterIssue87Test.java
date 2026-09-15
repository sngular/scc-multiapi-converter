/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sngular.multiapi.converter.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import com.sngular.multiapi.converter.MultiApiContractConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.Body;

@Slf4j
class OpenApiContractConverterIssue87Test {

  private final MultiApiContractConverter multiApiContractConverter = new MultiApiContractConverter();

  @Test
  @DisplayName("Issue #87: Request body should not be empty when schema has required properties")
  void testRequestBodyWithRequiredProperties() {
    final File file = new File("src/test/resources/openapi/testIssue87.yml");
    final Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    final List<Contract> contractList = new ArrayList<>(contracts);

    assertThat(contractList).isNotEmpty();

    final Contract contract = contractList.get(0);
    assertThat(contract.getRequest()).isNotNull();
    assertThat(contract.getRequest().getBody()).isNotNull();

    final Body body = contract.getRequest().getBody();
    final Object serverValue = body.getServerValue();
    assertThat(serverValue).isNotNull();

    if (serverValue instanceof Map) {
      final Map<String, Object> bodyMap = (Map<String, Object>) serverValue;
      assertThat(bodyMap).isNotEmpty();
      assertThat(bodyMap).containsKey("name");
      assertThat(bodyMap).containsKey("email");
    }
  }
}
