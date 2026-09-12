# Specifications

The converter should generate Spring Cloud Contract stubs/tests for both Spring Boot 3.x and 4.x based on configuration properties. Spring Boot 3 uses Jackson 2.x (com.fasterxml.jackson), Spring Boot 4 uses Jackson 3.x (tools.jackson).

## Functional Requirements

- Add configuration property to select target Spring Boot version (3.x or 4.x)
- Generate Spring Boot 3 compatible code when version=3.x (Jackson 2.x imports)
- Generate Spring Boot 4 compatible code when version=4.x (Jackson 3.x imports)
- Default to Spring Boot 3.x for backwards compatibility
- Maven plugin configuration: <springBootVersion>3.x</springBootVersion> or 4.x
- Gradle plugin configuration: springBootVersion = '3.x' or '4.x'
- Maintain existing behavior when no version is specified

## Non-Functional Requirements

- Maintain 2-space indentation and checkstyle compliance
- Keep MPL 2.0 license headers
- Ensure all existing tests pass
- Add tests for both Spring Boot version outputs

## Acceptance Criteria

- Configuration property is read correctly in Maven and Gradle
- Generated code uses correct Jackson imports for selected version
- Generated code compiles with target Spring Boot version
- Default behavior unchanged (Spring Boot 3.x)
- Checkstyle passes

## Out of Scope

- Modifying Spring Cloud Contract library itself
- Supporting Spring Boot 2.x (EOL)
- Changing the project's Maven coordinates