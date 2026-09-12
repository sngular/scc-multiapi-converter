# Implementation Plan



## Phase 1: pom.xml dependency updates

- [ ] Update SCC dependencies: spring-cloud-contract-verifier 4.0.3→5.0.3, spring-cloud-contract-spec-java 4.0.3→5.0.3
- [ ] Update Jackson dependencies: jackson-dataformat-yaml 2.15.0→tools.jackson.dataformat:jackson-dataformat-yaml:3.1.0, jackson-databind 2.15.2→tools.jackson.core:jackson-databind:3.1.6
- [ ] Update openapi-generator 6.6.0→7.25.0
- [ ] Update WireMock: com.github.tomakehurst:wiremock-jre8-standalone:2.35.1→org.wiremock:wiremock:3.13.2
- [ ] Update commons-lang3 3.12.0→3.20.0, commons-collections4 4.4→4.6.0, commons-codec 1.15→1.22.1
- [ ] Update guava 32.0.0-jre→33.7.1-jre, json-path 2.9.0→3.0.0
- [ ] Update logback 1.4.12→1.6.3, lombok 1.18.26→latest stable
- [ ] Update maven-surefire-plugin 2.19.1→3.5.6, other maven plugin versions
- [ ] Update test deps: assertj-core 3.24.2→latest, json-unit-assertj 3.2.4→5.x (Jackson 3 compatible)
- [ ] Update maven-plugin-api, maven-plugin-annotations, maven-core, maven-project to latest stable
- [ ] Bump project version from 3.4.1 to 4.0.0-SNAPSHOT

## Phase 2: Jackson 3 migration

- [ ] BasicTypeConstants.java: Update imports (com.fasterxml.jackson→tools.jackson), YAMLFactory→YAMLFactory.builder().build(), ObjectMapper constructor change
- [ ] MultiApiContractConverter.java: Update JsonNode import
- [ ] AsyncApiContractConverter.java: Update JsonNode, ArrayNode, ObjectNode imports
- [ ] AsyncApiContractConverterUtils.java: Update JsonProcessingException, JsonNode imports
- [ ] RandomGenerator.java: Update JsonNode import
- [ ] OpenApiContractConverterTest.java: Update ObjectNode import
- [ ] Verify Jackson 3 API compatibility: readTree, createObjectNode, JsonNode.get(), .elements(), .put(), .set(), .add() all still work
- [ ] Check if JsonProcessingException moved packages in Jackson 3

## Phase 3: SCC 5.x API migration

- [ ] Verify ContractConverter interface unchanged in SCC 5.x
- [ ] Check DslProperty, Body, Response, Request, Header API changes in SCC 5.x
- [ ] Check BodyMatchers, ResponseBodyMatchers, RegexProperty, RegexPatterns API changes
- [ ] Check Input, OutputMessage (AsyncAPI) API changes
- [ ] Update any changed method signatures or constructor patterns

## Phase 4: openapi-generator/swagger-parser migration

- [ ] Verify swagger-parser/swagger-models transitive deps still work with openapi-generator 7.25.0
- [ ] Check OpenAPIParser, SwaggerParseResult, ParseOptions API compatibility
- [ ] Check Schema, ComposedSchema, ArraySchema, Operation, PathItem, Parameter, ApiResponse model compatibility
- [ ] If openapi-generator 7.x broke swagger APIs, consider switching to direct swagger-parser dependency

## Phase 5: WireMock migration

- [ ] Update any WireMock imports if groupId changed affects import paths
- [ ] Verify wiremock:3.13.2 is compatible (WireMock used in tests only, minimal usage expected)

## Phase 6: Build verification

- [ ] Run mvn clean install and fix any compilation errors
- [ ] Run mvn checkstyle:check and fix any style violations
- [ ] Fix any test failures
- [ ] Update AGENTS.md with new version info