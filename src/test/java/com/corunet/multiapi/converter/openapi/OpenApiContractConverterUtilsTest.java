/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.corunet.multiapi.converter.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;

import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.contract.spec.internal.Request;
import org.springframework.cloud.contract.spec.internal.Response;

class OpenApiContractConverterUtilsTest {

  @Test
  @DisplayName("Check that mapRefName returns the name we want ")
  void testMapRefName() {
    final Schema schema = new Schema();
    schema.set$ref("#/components/schemas/Game");
    final String ref = OpenApiContractConverterUtils.mapRefName(schema);
    assertThat(ref).hasToString("Game");
  }

  @Test
  @DisplayName("Check that processBasicResponseTypeBody gives us the right body Matcher ")
  void testProcessBasicResponseTypeBody() {
    final Response response = new Response();
    Schema schema = new Schema();
    schema.setType("string");
    OpenApiContractConverterUtils.processBasicResponseTypeBody(response, schema);
    assertThat(response.getBody().getClientValue()).isInstanceOf(String.class);
    assertThat(response.getBody().getServerValue()).isInstanceOf(Pattern.class);
  }

  @Test
  @DisplayName("Check that processBasicRequestTypeBody gives us the right body Matcher ")
  void testProcessBasicRequestTypeBody() {
    final Request request = new Request();
    Schema schema = new Schema();
    schema.setType("string");
    OpenApiContractConverterUtils.processBasicRequestTypeBody(request, schema);
    assertThat(request.getBody().getServerValue()).isInstanceOf(String.class);
    assertThat(request.getBody().getClientValue()).isInstanceOf(Pattern.class);
  }

}
