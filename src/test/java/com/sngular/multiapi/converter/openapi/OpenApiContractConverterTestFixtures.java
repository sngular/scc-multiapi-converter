package com.sngular.multiapi.converter.openapi;

import java.util.List;
import java.util.Set;

public final class OpenApiContractConverterTestFixtures {

  public static final Set<String> KEYSET = Set.of("country", "address", "birthdate", "nameKanaHankaku", "gender",
          "streetNumber", "nameKanaZenkaku", "idDocument", "givenName", "postalCode", "locality", "middleNames",
          "familyNameAtBirth", "houseNumberExtension", "streetName", "phoneNumber", "familyName", "name", "region", "email");
  static final String PLAYERS = "players";

  static final String[] OPENAPI_TEXT_EXTERNAL_REF_KEYS = {"schemaRegistryName", "topic", "kafkaName", "schemaName", "repetitions"};

  static final String AVAILABILITY = "availability";

  static final String ID = "id";

  static final String PRICE = "price";

  static final String OPENAPI_TEST_REF_INSIDE_ARRAYS_YML = "src/test/resources/openapi/testRefInsideArrays.yml";

  static final String HANGMAN = "hangman";

  static final String ROCKSCISSOR = "rockscissor";

  static final String ROOMS = "rooms";

  static final String STRING_PATTERN = "[a-zA-Z0-9]+";

  static final String DIGIT_PATTERN = "([1-9]\\d*)";

  static final String DESCRIPTION = "description";

  static final String MESSAGE_DESCRIPTION = "message.description";

  static final String MESSAGE = "message";

  static final String GAME_NAME = "gameName";

  static final String ROOM_ID = "roomId";

  static final String CODE = "code";

  static final String NEW_GAME_ID = "newGameId";

  static final String FIRSTNAME = "firstname";

  static final String LASTNAME = "lastname";

  static final String OPENAPI_TEST_FALSE_YML = "src/test/resources/openapi/testFalse.yml";

  static final String OPENAPI_TEST_COMPLETE_API_YML = "src/test/resources/openapi/testCompleteApi.yml";

  static final String OPENAPI_TEST_REQUEST_HEADERS_YML = "src/test/resources/openapi/testRequestHeaders.yml";

  static final String OPENAPI_TEST_EXTERNAL_REF = "src/test/resources/openapi/testExternalRef.yml";

  static final String APPLICATION_JSON = "application/json";

  static final String OPENAPI_TEST_REQUEST_QUERY_PARAMETERS_YML = "src/test/resources/openapi/testRequestQueryParameters.yml";

  static final String GAME_ID = "gameId";

  static final String OPENAPI_TEST_REQUEST_BODY_YML = "src/test/resources/openapi/testRequestBody.yml";

  static final String OPENAPI_TEST_ENUMS_YML = "src/test/resources/openapi/testEnums.yml";

  static final String OPENAPI_TEST_COMPLEX_OBJECTS_YML = "src/test/resources/openapi/testComplexObjects.yml";

  static final String OPENAPI_TEST_ARRAYS_YML = "src/test/resources/openapi/testArrays.yml";

  static final String OPENAPI_TEST_REFS_YML = "src/test/resources/openapi/testRefs.yml";

  static final String OPENAPI_TEST_ONE_OFS_YML = "src/test/resources/openapi/testOneOfs.yml";

  static final String TEST_ANY_OFS_YML = "src/test/resources/openapi/testAnyOfs.yml";

  static final String TEST_ALL_OFS_YML = "src/test/resources/openapi/testAllOfs.yml";

  static final String OPENAPI_TEST_BASIC_SCHEMA_YML = "src/test/resources/openapi/testBasicSchema.yml";

  static final String OPENAPI_TEST_BASIC_OBJECT_AND_REF_YML = "src/test/resources/openapi/testBasicObjectAndRef.yml";

  static final String OPENAPI_TEST_EXAMPLES_YML = "src/test/resources/openapi/testExamples.yml";

  static final String OPENAPI_TEST_SCHEMA_EXAMPLES_YML = "src/test/resources/openapi/testExamplesGlobal.yml";

  static final String OPENAPI_TEST_GENERATE_TESTS_YML = "src/test/resourcer/openapi/testGenerateTest.yml";

  static final String OPENAPI_TEST_SCHEMA_MULTI_EXAMPLES_YML = "src/test/resources/openapi/testMultiExamplesGlobal.yml";

  static final String OPENAPI_TEST_SCHEMA_MAPS_YML = "src/test/resources/openapi/testSupportMaps.yml";

  static final String OPENAPI_ANY_OF_WITH_ARRAYS = "src/test/resources/openapi/testAnyOfWithArrays.yml";

  static final String OPENAPI_ANY_OF_WITH_MAPS = "src/test/resources/openapi/testAnyOfWithMaps.yml";

  static final String OPENAPI_DUPLICATE_IDS = "src/test/resources/openapi/testDuplicateIds.yml";

  static final String NAME = "name";

  static final String CONTENT_TYPE = "Content-Type";

  static final String PLAYER_NAME = "playerName";

  static final List<String> ENUM_VALUES = List.of("hangman", "chess");

  static final String ADDRESS = "address";

}
