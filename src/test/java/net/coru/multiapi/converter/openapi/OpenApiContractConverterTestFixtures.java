package net.coru.multiapi.converter.openapi;

import java.util.List;

public class OpenApiContractConverterTestFixtures {

  protected final String[] OPENAPI_TEXT_EXTERNAL_REF_KEYS = {"schemaRegistryName", "topic", "kafkaName", "schemaName", "repetitions"};

  protected final String AVAILABILITY = "availability";

  protected  final String ID = "id";

  protected final String PRICE = "price";

  protected final String SIMILAR_GAMES = "SimilarGames";

  protected final String OPENAPI_TEST_REF_INSIDE_ARRAYS_YML = "src/test/resources/openapi/testRefInsideArrays.yml";

  protected final String HANGMAN = "hangman";

  protected final String ROOMS = "rooms";

  protected final String STRING_PATTERN = "[a-zA-Z0-9]+";

  protected final String DIGIT_PATTERN = "([1-9]\\d*)";

  protected final String DESCRIPTION = "description";

  protected final String MESSAGE_DESCRIPTION = "message.description";

  protected final String MESSAGE = "message";

  protected final String GAME_NAME = "gameName";

  protected final String ROOM_ID = "roomId";

  protected final String CODE = "code";

  protected final String NEW_GAME_ID = "newGameId";

  protected final String FIRSTNAME = "firstname";

  protected final String LASTNAME = "lastname";

  protected final String OPENAPI_TEST_FALSE_YML = "src/test/resources/openapi/testFalse.yml";

  protected final String OPENAPI_TEST_COMPLETE_API_YML = "src/test/resources/openapi/testCompleteApi.yml";

  protected final String OPENAPI_TEST_REQUEST_HEADERS_YML = "src/test/resources/openapi/testRequestHeaders.yml";

  protected final String OPENAPI_TEST_EXTERNAL_REF = "src/test/resources/openapi/testExternalRef.yml";

  protected final String APPLICATION_JSON = "application/json";

  protected final String OPENAPI_TEST_REQUEST_QUERY_PARAMETERS_YML = "src/test/resources/openapi/testRequestQueryParameters.yml";

  protected final String GAME_ID = "gameId";

  protected final String OPENAPI_TEST_REQUEST_BODY_YML = "src/test/resources/openapi/testRequestBody.yml";

  protected final String OPENAPI_TEST_ENUMS_YML = "src/test/resources/openapi/testEnums.yml";

  protected final String OPENAPI_TEST_COMPLEX_OBJECTS_YML = "src/test/resources/openapi/testComplexObjects.yml";

  protected final String OPENAPI_TEST_ARRAYS_YML = "src/test/resources/openapi/testArrays.yml";

  protected final String OPENAPI_TEST_REFS_YML = "src/test/resources/openapi/testRefs.yml";

  protected final String OPENAPI_TEST_ONE_OFS_YML = "src/test/resources/openapi/testOneOfs.yml";

  protected final String TEST_ANY_OFS_YML = "src/test/resources/openapi/testAnyOfs.yml";

  protected final String TEST_ALL_OFS_YML = "src/test/resources/openapi/testAllOfs.yml";

  protected final String OPENAPI_TEST_BASIC_SCHEMA_YML = "src/test/resources/openapi/testBasicSchema.yml";

  protected final String OPENAPI_TEST_BASIC_OBJECT_AND_REF_YML = "src/test/resources/openapi/testBasicObjectAndRef.yml";

  protected final String OPENAPI_TEST_EXAMPLES_YML = "src/test/resources/openapi/testExamples.yml";

  protected final String NAME = "name";

  protected final String CONTENT_TYPE = "Content-Type";

  protected final String PLAYER_NAME = "playerName";

  protected final List<String> ENUM_VALUES = List.of("hangman", "chess");

  protected final String ADDRESS = "address";

}
