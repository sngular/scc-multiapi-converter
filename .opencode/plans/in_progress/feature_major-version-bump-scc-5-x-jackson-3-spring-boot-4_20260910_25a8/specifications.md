# Specifications

Upgrade scc-multiapi-converter to v4.0.0 by migrating to SCC 5.x (Spring Boot 4 compatible), Jackson 3, openapi-generator 7.25.0, WireMock 3.13.2, and all other dependencies to latest stable.

## Functional Requirements

- All existing OpenAPI contract generation tests pass
- All existing AsyncAPI contract generation tests pass
- mvn clean install succeeds with checkstyle
- Published artifact is compatible with SCC 5.x consumers using Spring Boot 4

## Non-Functional Requirements

- Checkstyle rules still pass (2-space indent, final locals, no star imports, etc.)
- No System.out/err usage
- MPL 2.0 license headers preserved

## Acceptance Criteria

- mvn clean install passes all tests
- No com.fasterxml.jackson imports remain (except jackson-annotations if needed)
- SCC deps are at 5.0.3
- Jackson deps use tools.jackson groupId
- openapi-generator at 7.25.0
- WireMock at 3.13.2 with org.wiremock groupId

## Out of Scope

- OpenRewrite automated migration (manual review preferred)
- Changing project structure or package names
- Adding new features beyond dependency updates
- Updating AGENTS.md (separate task)