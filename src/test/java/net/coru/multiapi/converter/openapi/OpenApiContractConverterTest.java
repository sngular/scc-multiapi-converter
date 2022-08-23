/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package net.coru.multiapi.converter.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import net.coru.multiapi.converter.MultiApiContractConverter;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.contract.spec.Contract;
import org.springframework.cloud.contract.spec.internal.Body;
import org.springframework.cloud.contract.spec.internal.BodyMatcher;
import org.springframework.cloud.contract.spec.internal.BodyMatchers;
import org.springframework.cloud.contract.spec.internal.Header;
import org.springframework.cloud.contract.spec.internal.MatchingStrategy;
import org.springframework.cloud.contract.spec.internal.MatchingType;
import org.springframework.cloud.contract.spec.internal.QueryParameter;
import org.springframework.cloud.contract.spec.internal.QueryParameters;
import org.springframework.cloud.contract.spec.internal.Request;
import org.springframework.cloud.contract.spec.internal.Response;
import org.springframework.cloud.contract.spec.internal.ResponseBodyMatchers;

@Slf4j
class OpenApiContractConverterTest {

  private final MultiApiContractConverter multiApiContractConverter = new MultiApiContractConverter();

  @Test
  @DisplayName("OpenApi: Testing the method that checks if the yaml is incorrect")
  void isAcceptedFalse() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_FALSE_YML);
    final Boolean isAccepted = multiApiContractConverter.isAccepted(file);
    assertThat(isAccepted).isFalse();
  }

  @Test
  @DisplayName("OpenApi: Testing the method that checks if the yaml is correct")
  void isAcceptedTrue() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_COMPLETE_API_YML);
    Boolean isAccepted = multiApiContractConverter.isAccepted(file);
    assertThat(isAccepted).isTrue();
  }

  @Test
  @DisplayName("OpenApi: Check if a contract is returned")
  void convertFromTest() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_COMPLETE_API_YML);
    final Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    final List<Contract> contractList = new ArrayList<>(contracts);
    assertThat(contractList).hasSize(6);
    assertThat(contractList.get(0).getResponse()).isNotNull();
    assertThat(contractList.get(0).getRequest()).isNotNull();
    assertThat(contractList.get(0).getName()).isInstanceOf(String.class);
    assertThat(contractList.get(0).getDescription()).isInstanceOf(String.class);
  }

  @Test
  @DisplayName("OpenApi: Check if RequestHeaders are being processed okay")
  void testRequestHeaders() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_REQUEST_HEADERS_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    ArrayList<Contract> contractList = new ArrayList<>(contracts);
    final Contract contract = contractList.get(0);
    final Header header = contract.getRequest().getHeaders().getEntries().stream().iterator().next();
    assertThat(header.getName()).isEqualTo(OpenApiContractConverterTestFixtures.CONTENT_TYPE);
    assertThat(header.getClientValue()).isEqualTo(OpenApiContractConverterTestFixtures.APPLICATION_JSON);
    assertThat(header.getServerValue()).isEqualTo(OpenApiContractConverterTestFixtures.APPLICATION_JSON);
  }

  @Test
  @DisplayName("OpenApi: Check if QueryParameters are being processed okay")
  void testRequestQueryParameters() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_REQUEST_QUERY_PARAMETERS_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    final QueryParameters queryParameters = contract.getRequest().getUrlPath().getQueryParameters();
    final QueryParameter parameter = queryParameters.getParameters().get(0);
    assertThat(parameter.getName()).isEqualTo(OpenApiContractConverterTestFixtures.GAME_ID);
    assertThat(parameter.getClientValue()).isInstanceOf(Pattern.class);
    Assertions.assertTrue(OpenApiContractConverterTestFixtures.DIGIT_PATTERN.equalsIgnoreCase(parameter.getClientValue().toString()));
  }

  @Test
  @DisplayName("OpenApi: Check if a simple Request Body is being processed okay")
  void testRequestBody() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_REQUEST_BODY_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    final Body body = contract.getRequest().getBody();
    final Map<String, Object> bodyServerValueMap = (Map<String, Object>) body.getServerValue();
    assertThat(bodyServerValueMap).isNotEmpty();
    assertThat(bodyServerValueMap.get(OpenApiContractConverterTestFixtures.GAME_ID)).isInstanceOf(Integer.class);
    assertThat(bodyServerValueMap.get(OpenApiContractConverterTestFixtures.PLAYER_NAME)).isInstanceOf(String.class);
  }

  @Test
  @DisplayName("OpenApi: Check if a simple Request BodyMatcher is being processed okay")
  void testRequestBodyMatcher() {
    File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_REQUEST_BODY_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    final BodyMatchers bodyMatchers = contract.getRequest().getBodyMatchers();
    final List<BodyMatcher> bodyMatcherList = bodyMatchers.matchers();
    for (BodyMatcher bodyMatcher : bodyMatcherList) {
      assertThat(bodyMatcher.path()).isInstanceOf(String.class);
      assertThat(bodyMatcher.matchingType()).isInstanceOf(MatchingType.class);
    }
  }

  @Test
  @DisplayName("OpenApi: Check if the enum logic is being processed okay")
  void testEnums() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_ENUMS_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    final Body body = contract.getResponse().getBody();
    Map<String, Object> bodyServerValueMap = (Map<String, Object>) body.getServerValue();
    assertThat(bodyServerValueMap).isNotEmpty();
    final String name = (String) bodyServerValueMap.get(OpenApiContractConverterTestFixtures.NAME);
    assertThat(name)
        .isInstanceOf(String.class)
        .isIn(OpenApiContractConverterTestFixtures.ENUM_VALUES);
  }

  @Test
  @DisplayName("OpenApi: Check if complex objects are being processed okay")
  void testComplexObjects() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_COMPLEX_OBJECTS_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    assertThat(contract)
        .isNotNull()
        .isInstanceOf(Contract.class);
    assertThat(contract.getRequest()).isNotNull();
    assertThat(contract.getResponse()).isNotNull();
    Body body = contract.getResponse().getBody();
    Map<String, Object> bodyServerValueMap = (Map<String, Object>) body.getServerValue();
    assertThat(bodyServerValueMap)
        .isNotNull()
        .containsKey(OpenApiContractConverterTestFixtures.NAME)
        .hasSize(2);
    final Map<String, Object> nameSubMap = (Map<String, Object>) bodyServerValueMap.get(OpenApiContractConverterTestFixtures.NAME);
    assertThat(nameSubMap)
        .containsKey(OpenApiContractConverterTestFixtures.LASTNAME)
        .hasSize(2);
    assertThat(nameSubMap.get(OpenApiContractConverterTestFixtures.FIRSTNAME)).isInstanceOf(String.class);
    assertThat(nameSubMap.get(OpenApiContractConverterTestFixtures.LASTNAME)).isInstanceOf(String.class);

  }

  @Test
  @DisplayName("OpenApi: Check if Arrays are being processed okay")
  void testArrays() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_ARRAYS_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    assertThat(contract).isNotNull();
    assertThat(contract.getRequest()).isNotNull();
    assertThat(contract.getResponse()).isNotNull();
    final Map<String, Object> bodyServerValueMap = ((List<Map<String, Object>>) contract.getResponse().getBody().getServerValue()).get(0);
    assertThat(bodyServerValueMap)
        .containsKey(OpenApiContractConverterTestFixtures.NAME)
        .containsKey(OpenApiContractConverterTestFixtures.ADDRESS)
        .isNotNull();
    final List<Map<String, String>> nameSubList = (List<Map<String, String>>) bodyServerValueMap.get(OpenApiContractConverterTestFixtures.NAME);
    final Map<String, String> lastNameMap = nameSubList.get(0);
    assertThat(lastNameMap).containsKey(OpenApiContractConverterTestFixtures.LASTNAME);
    assertThat(lastNameMap.get(OpenApiContractConverterTestFixtures.LASTNAME)).isInstanceOf(String.class);
    final List<List<String>> addressSubList = (List<List<String>>) bodyServerValueMap.get(OpenApiContractConverterTestFixtures.ADDRESS);
    assertThat(addressSubList.get(0)).isInstanceOf(ArrayList.class);
    assertThat(addressSubList.get(0).get(0)).isInstanceOf(String.class);
  }

  @Test
  @DisplayName("OpenApi: Check if Refs are being processed okay")
  void testRef() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_REFS_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    assertThat(contract).isNotNull();
    assertThat(contract.getRequest()).isNotNull();
    assertThat(contract.getResponse()).isNotNull();
    Map<String, Object> bodyServerValueMap = (Map<String, Object>) contract.getResponse().getBody().getServerValue();
    assertThat(bodyServerValueMap)
        .containsKey("player");
    final Map<String, Object> playerMap = (Map<String, Object>) bodyServerValueMap.get("player");
    assertThat(playerMap).containsKey(OpenApiContractConverterTestFixtures.NAME);
    final Map<String, Object> nameMap = (Map<String, Object>) playerMap.get(OpenApiContractConverterTestFixtures.NAME);
    assertThat(nameMap).containsKey(OpenApiContractConverterTestFixtures.FIRSTNAME)
        .containsKey(OpenApiContractConverterTestFixtures.LASTNAME);
    assertThat(nameMap.get(OpenApiContractConverterTestFixtures.FIRSTNAME)).isInstanceOf(String.class);
    assertThat(nameMap.get(OpenApiContractConverterTestFixtures.LASTNAME)).isInstanceOf(String.class);
  }

  @Test
  @DisplayName("OpenApi: Check if oneOfs are being processed okay")
  void testOneOfs() {

    final var file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_ONE_OFS_YML);
    final var contracts = multiApiContractConverter.convertFrom(file);
    final var contractList = new ArrayList<>(contracts);
    assertThat(contractList).hasSize(2);
    Contract contract = contractList.get(0);
    assertThat(contract.getResponse()).isNotNull();
    Map bodyServerValueMap = (Map) contract.getResponse().getBody().getServerValue();
    assertThat(bodyServerValueMap)
      .hasSize(2)
      .containsOnlyKeys(OpenApiContractConverterTestFixtures.GAME_ID,
         OpenApiContractConverterTestFixtures.PLAYER_NAME);

    contract = contractList.get(1);
    assertThat(contract.getResponse()).isNotNull();
    bodyServerValueMap = (Map) contract.getResponse().getBody().getServerValue();
    assertThat(bodyServerValueMap).hasSize(3)
                                  .containsOnlyKeys(OpenApiContractConverterTestFixtures.NEW_GAME_ID,
                                                    OpenApiContractConverterTestFixtures.GAME_NAME,
                                                    OpenApiContractConverterTestFixtures.ROOM_ID);

  }

  @Test
  @DisplayName("OpenApi: Check if anyOfs are being processed okay")
  void testAnyOfs() {

    File file = new File(OpenApiContractConverterTestFixtures.TEST_ANY_OFS_YML);
    Contract[] contractList = multiApiContractConverter.convertFrom(file).toArray(new Contract[3]);
    assertThat(contractList).hasSize(3);
    Contract contract = contractList[0];
    assertThat(contract).isNotNull();
    assertThat(contract.getResponse()).isNotNull();
    Map<String, Object> bodyServerValueMap = (Map<String, Object>) contract.getResponse().getBody().getServerValue();
    assertThat(bodyServerValueMap).containsOnlyKeys(OpenApiContractConverterTestFixtures.GAME_ID, OpenApiContractConverterTestFixtures.PLAYER_NAME);

    contract = contractList[1];
    assertThat(contract).isNotNull();
    assertThat(contract.getResponse()).isNotNull();
    bodyServerValueMap = (Map<String, Object>) contract.getResponse().getBody().getServerValue();
    assertThat(bodyServerValueMap)
      .containsOnlyKeys(OpenApiContractConverterTestFixtures.GAME_NAME, OpenApiContractConverterTestFixtures.ROOM_ID, OpenApiContractConverterTestFixtures.NEW_GAME_ID);

    contract = contractList[2];
    assertThat(contract).isNotNull();
    assertThat(contract.getResponse()).isNotNull();
    bodyServerValueMap = (Map<String, Object>) contract.getResponse().getBody().getServerValue();
    assertThat(bodyServerValueMap)
      .containsOnlyKeys(OpenApiContractConverterTestFixtures.GAME_ID,
                         OpenApiContractConverterTestFixtures.GAME_NAME,
                         OpenApiContractConverterTestFixtures.ROOM_ID,
                         OpenApiContractConverterTestFixtures.NEW_GAME_ID,
                         OpenApiContractConverterTestFixtures.PLAYER_NAME);

  }

  @Test
  @DisplayName("OpenApi: Check if AllOfs are being processed okay")
  void testAllOfs() {
    final File file = new File(OpenApiContractConverterTestFixtures.TEST_ALL_OFS_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    assertThat(contract).isNotNull();
    assertThat(contract.getResponse()).isNotNull();
    Map<String, Object> bodyServerValueMap = (Map<String, Object>) contract.getResponse().getBody().getServerValue();
    assertThat(bodyServerValueMap)
      .hasSize(5)
      .containsOnlyKeys(OpenApiContractConverterTestFixtures.GAME_ID,
                        OpenApiContractConverterTestFixtures.PLAYER_NAME,
                        OpenApiContractConverterTestFixtures.GAME_NAME,
                        OpenApiContractConverterTestFixtures.ROOM_ID,
                        OpenApiContractConverterTestFixtures.NEW_GAME_ID);
  }

  @Test
  @DisplayName("OpenApi: Check that BasicSchemas are being processed okay")
  void testBasicSchema() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_BASIC_SCHEMA_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    assertThat(contract).isNotNull();
    final Response response = contract.getResponse();
    assertThat(response).isNotNull();
    assertThat(response.getBody().getServerValue()).isNotNull();
    assertThat(response.getBody().getServerValue().toString()).hasToString(OpenApiContractConverterTestFixtures.STRING_PATTERN);
  }

  @Test
  @DisplayName("OpenApi: Check that Basic Schemas and $refs processing works well with both. ")
  void testBasicObjectAndRef() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_BASIC_OBJECT_AND_REF_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    Response response = contract.getResponse();
    final Map<String, Object> bodyServerValueMap = (Map<String, Object>) response.getBody().getServerValue();
    final Map<String, Object> messageMap = (Map<String, Object>) bodyServerValueMap.get(OpenApiContractConverterTestFixtures.MESSAGE);
    final ResponseBodyMatchers responseBodyMatchers = response.getBodyMatchers();
    final List<BodyMatcher> bodyMatchers = responseBodyMatchers.matchers();
    assertThat(contract).isNotNull();
    assertThat(bodyServerValueMap.get(OpenApiContractConverterTestFixtures.CODE)).isInstanceOf(Integer.class);
    assertThat(bodyMatchers.get(0).path()).isEqualTo(OpenApiContractConverterTestFixtures.CODE);
    assertThat(bodyMatchers.get(0).value().toString()).hasToString(OpenApiContractConverterTestFixtures.DIGIT_PATTERN);
    assertThat(bodyMatchers.get(1).path()).isEqualTo(OpenApiContractConverterTestFixtures.MESSAGE_DESCRIPTION);
    assertThat(bodyMatchers.get(1).value().toString()).hasToString(OpenApiContractConverterTestFixtures.STRING_PATTERN);
    assertThat(messageMap).hasSize(1);
    assertThat(messageMap.get(OpenApiContractConverterTestFixtures.DESCRIPTION)).isInstanceOf(String.class);
  }

  @Test
  @DisplayName("OpenApi: Check that Examples are being processed okay")
  void testExamples() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_EXAMPLES_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    QueryParameters queryParameters = contract.getRequest().getUrlPath().getQueryParameters();
    QueryParameter parameter = queryParameters.getParameters().get(0);
    assertThat(parameter.getName()).isEqualTo(OpenApiContractConverterTestFixtures.GAME_ID);
    final MatchingStrategy matchingStrategy = (MatchingStrategy) parameter.getServerValue();
    assertThat(matchingStrategy.getType().toString()).hasToString("EQUAL_TO");
    assertThat(matchingStrategy.getServerValue()).isEqualTo(1);
    assertThat(matchingStrategy.getClientValue()).isEqualTo(1);
    final Map<String, Object> serverValueMap = (Map<String, Object>) contract.getResponse().getBody().getServerValue();
    assertThat(serverValueMap)
        .containsKeys(OpenApiContractConverterTestFixtures.ROOMS, OpenApiContractConverterTestFixtures.GAME_NAME)
        .containsValues(1, OpenApiContractConverterTestFixtures.HANGMAN);
  }

  @Test
  @DisplayName("OpenApi: Check that Schema Examples are being processed okay")
  void testSchemaExamples() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_SCHEMA_EXAMPLES_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    QueryParameters queryParameters = contract.getRequest().getUrlPath().getQueryParameters();
    QueryParameter parameter = queryParameters.getParameters().get(0);
    assertThat(parameter.getName()).isEqualTo(OpenApiContractConverterTestFixtures.GAME_ID);
    final MatchingStrategy matchingStrategy = (MatchingStrategy) parameter.getServerValue();
    assertThat(matchingStrategy.getType().toString()).hasToString("EQUAL_TO");
    assertThat(matchingStrategy.getServerValue()).isEqualTo(1);
    assertThat(matchingStrategy.getClientValue()).isEqualTo(1);
    final Map serverValueMap = (Map) contract.getResponse().getBody().getServerValue();
    assertThat(serverValueMap)
      .containsOnlyKeys(OpenApiContractConverterTestFixtures.ROOMS, OpenApiContractConverterTestFixtures.GAME_NAME)
      .containsValues(1, OpenApiContractConverterTestFixtures.HANGMAN);
  }

  @Test
  @DisplayName("OpenApi: Check that Schema Examples are being processed okay")
  void testSchemaMultiExamples() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_SCHEMA_MULTI_EXAMPLES_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    assertThat(contractList).hasSize(5);
    Contract contract = contractList.get(0);
    QueryParameters queryParameters = contract.getRequest().getUrlPath().getQueryParameters();
    QueryParameter parameter = queryParameters.getParameters().get(0);
    assertThat(parameter.getName()).isEqualTo(OpenApiContractConverterTestFixtures.GAME_ID);
    MatchingStrategy matchingStrategy = (MatchingStrategy) parameter.getServerValue();
    assertThat(matchingStrategy.getType().toString()).hasToString("EQUAL_TO");
    assertThat(matchingStrategy.getServerValue()).isEqualTo(1);
    assertThat(matchingStrategy.getClientValue()).isEqualTo(1);
     Map serverValueMap = (Map) contract.getResponse().getBody().getServerValue();
    assertThat(serverValueMap)
      .containsOnlyKeys(OpenApiContractConverterTestFixtures.ROOMS, OpenApiContractConverterTestFixtures.GAME_NAME)
      .containsValues(1, OpenApiContractConverterTestFixtures.HANGMAN);

    contract = contractList.get(2);
    queryParameters = contract.getRequest().getUrlPath().getQueryParameters();
    parameter = queryParameters.getParameters().get(0);
    assertThat(parameter.getName()).isEqualTo(OpenApiContractConverterTestFixtures.GAME_ID);
    matchingStrategy = (MatchingStrategy) parameter.getServerValue();
    assertThat(matchingStrategy.getType().toString()).hasToString("EQUAL_TO");
    assertThat(matchingStrategy.getServerValue()).isEqualTo(1);
    assertThat(matchingStrategy.getClientValue()).isEqualTo(1);
    ObjectNode example = (ObjectNode) contract.getResponse().getBody().getServerValue();
    assertThatObject(example)
                 .extracting(ex -> ex.at("/" + OpenApiContractConverterTestFixtures.ROOMS).asText(),
                             ex -> ex.at("/" + OpenApiContractConverterTestFixtures.GAME_NAME).asText())
                 .containsExactly("2", OpenApiContractConverterTestFixtures.ROCKSCISSOR);
  }
  @Test
  @DisplayName("OpenApi: Check that Refs inside arrays are being processed okay")
  void testRefsInsideArrays() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_REF_INSIDE_ARRAYS_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    Response response = contract.getResponse();
    final List<Map<String,Object>> serverValueList = (List<Map<String, Object>>) response.getBody().getServerValue();
    final Map<String, Object> bodyServerValueMap = serverValueList.get(0);
    assertThat(response).isNotNull();
    assertThat(bodyServerValueMap)
        .isNotNull()
        .containsKeys(OpenApiContractConverterTestFixtures.PRICE,
                      OpenApiContractConverterTestFixtures.AVAILABILITY,
                      OpenApiContractConverterTestFixtures.ID,
                      OpenApiContractConverterTestFixtures.NAME)
        .isInstanceOf(HashMap.class);
  }

  @Test
  @DisplayName("OpenApi: Check that Refs inside arrays are being processed okay")
  void testExternalRefs() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_EXTERNAL_REF);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    Contract contract = contractList.get(0);
    Response response = contract.getResponse();
    final Map<String, Object> bodyServerValueMap = (Map<String, Object>) response.getBody().getServerValue();
    assertThat(bodyServerValueMap)
        .isNotNull()
        .hasSize(5)
        .containsKeys(OpenApiContractConverterTestFixtures.OPENAPI_TEXT_EXTERNAL_REF_KEYS);
    assertThat(contract).isNotNull();
  }

  @Test
  @DisplayName("OpenApi: Check that Refs inside arrays are being processed okay")
  void testMapsSupport() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_TEST_SCHEMA_MAPS_YML);
    Collection<Contract> contracts = multiApiContractConverter.convertFrom(file);
    List<Contract> contractList = new ArrayList<>(contracts);
    assertThat(contractList).hasSize(2);
    Contract contract = contractList.get(0);
    Request request = contract.getRequest();
    final Map<String, Object> bodyServerValueMap = (Map<String, Object>) request.getBody().getServerValue();
    assertThat(bodyServerValueMap)
        .isNotNull()
        .hasSize(2)
        .containsKeys("options", "movement")
      .extractingByKey("options")
      .isInstanceOf(Map.class)
      .asInstanceOf(InstanceOfAssertFactories.MAP)
      .hasSize(1);
  }

  @Test
  @DisplayName("OpenApi: Check that Refs inside arrays are being processed okay")
  void testAnyOfWithArraysInside() {
    final File file = new File(OpenApiContractConverterTestFixtures.OPENAPI_ANY_OF_WITH_ARRAYS);
    Contract[] contractList = multiApiContractConverter.convertFrom(file).toArray(new Contract[3]);
    assertThat(contractList).hasSize(3);
    Contract contract = contractList[0];
    assertThat(contract).isNotNull();
    assertThat(contract.getResponse()).isNotNull();
    Map<String, Object> bodyServerValueMap = (Map<String, Object>) contract.getResponse().getBody().getServerValue();
    assertThat(bodyServerValueMap).containsOnlyKeys(OpenApiContractConverterTestFixtures.ID, OpenApiContractConverterTestFixtures.NAME);

    contract = contractList[1];
    assertThat(contract).isNotNull();
    assertThat(contract.getResponse()).isNotNull();
    bodyServerValueMap = (Map<String, Object>) contract.getResponse().getBody().getServerValue();
    assertThat(bodyServerValueMap)
        .containsOnlyKeys(OpenApiContractConverterTestFixtures.ROOMS, OpenApiContractConverterTestFixtures.GAME_NAME, OpenApiContractConverterTestFixtures.PLAYERS);
    assertThat(bodyServerValueMap.get("players")).isInstanceOf(LinkedList.class);
    contract = contractList[2];
    assertThat(contract).isNotNull();
    assertThat(contract.getResponse()).isNotNull();
    bodyServerValueMap = (Map<String, Object>) contract.getResponse().getBody().getServerValue();
    assertThat(bodyServerValueMap)
        .containsOnlyKeys(OpenApiContractConverterTestFixtures.ID,
                          OpenApiContractConverterTestFixtures.NAME,
                          OpenApiContractConverterTestFixtures.ROOMS,
                          OpenApiContractConverterTestFixtures.GAME_NAME,
                          OpenApiContractConverterTestFixtures.PLAYERS);
    assertThat(bodyServerValueMap.get("players")).isInstanceOf(LinkedList.class);
  }

}