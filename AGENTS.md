# AGENTS.md

## Project

Java 17 Maven library. Generates Spring Cloud Contract stubs/tests from OpenAPI 3.x and AsyncAPI 2.x YAML files. Published to Maven Central as `com.sngular:scc-multiapi-converter`.

Entry point: `MultiApiContractConverter` (implements `ContractConverter`). Delegates to `OpenApiContractConverter` or `AsyncApiContractConverter` based on which top-level key (`openapi` / `asyncapi`) is present.

## Build & Test

```bash
mvn clean install          # full build (checkstyle + compile + test + package)
mvn test                   # tests only (skip checkstyle)
mvn checkstyle:check       # checkstyle only
mvn test -Dtest=OpenApiContractConverterTest#testEnums   # single test method
```

Order matters: checkstyle runs at `validate` phase, so `mvn install` includes it. Use `-Dcheckstyle.skip=true` to skip checkstyle during dev iteration.

## Key Conventions

- **2-space indentation** everywhere (not 4). Enforced by checkstyle.
- **No star imports.** Custom import order: `static` → `java/javax` → third-party, alphabetically sorted within groups.
- **No `System.out`/`System.err`.** Use SLF4J (`@Slf4j` from Lombok).
- **MPL 2.0 license header** required on all source files.
- **`final` locals** are enforced by checkstyle (warning severity) — write `final` on local variables and parameters where practical.
- **Maven XML uses 2-space indent** as well (follow existing `pom.xml` style).

## Checkstyle Gotchas

Config: `styles/checkstyle/OSS_checkstyle.xml`. Suppressions: `styles/checkstyle/OSS_checkstyle_suppressions.xml`.

- `VisibilityModifier` and `DesignForExtension` are suppressed for files under `**/model/**`.
- `ReturnCount` max is 1 — methods must have a single return.
- `NestedForDepth` and `NestedIfDepth` max is 3.
- Line length max is 180 chars (ignores imports and URLs).
- `HiddenField` allows ignoring constructor params and setters.
- Javadoc checks are all severity=ignore (no Javadoc required).

## Testing

- JUnit 5 + AssertJ. Tests are in `src/test/java/.../converter/{openapi,asyncapi}/`.
- Test fixtures are in separate `*TestFixtures.java` classes (constants + helpers).
- Test YAML resources: `src/test/resources/openapi/` and `src/test/resources/asyncapi/`.
- **OpenApi tests** reference fixtures as relative file paths: `"src/test/resources/openapi/testFoo.yml"`.
- **AsyncApi tests** use `FileHelper.getFile()` with classpath paths: `"/asyncapi/event-api.yml"`.

## Architecture

```
src/main/java/com/sngular/multiapi/converter/
  MultiApiContractConverter.java          # Entry point, routes to OpenAPI or AsyncAPI
  openapi/
    OpenApiContractConverter.java         # OpenAPI → Contract conversion
    OpenApiContractConverterUtils.java    # OpenAPI helpers
    model/                                # ConverterPathItem, OperationType
  asyncapi/
    AsyncApiContractConverter.java        # AsyncAPI → Contract conversion
    AsyncApiContractConverterUtils.java   # AsyncAPI helpers
  utils/
    BasicTypeConstants.java               # Shared constants, regex patterns, ObjectMapper
    RandomGenerator.java                  # Random value generation for examples
  exception/                              # Custom exceptions
```

## Limitations to Keep in Mind

- AsyncAPI: no allOf/oneOf/anyOf support yet. No avro support.
- OpenAPI: external `$ref` files have limitations on repeated `$ref` usage within the same file.
- Examples must use the `example` tag on individual properties (not schema-level example objects).
