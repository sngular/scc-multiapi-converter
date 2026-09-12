# Implementation Plan

1. Research Spring Cloud Contract versions and Jackson import differences. 2. Add configuration property for Spring Boot version selection. 3. Modify code generation to output correct imports based on version. 4. Create Gradle plugin. 5. Fix issues #86/#87. 6. Add tests.

## Research

- [ ] Research Spring Cloud Contract versions for Boot 3.x and 4.x
- [ ] Identify Jackson import differences between Boot 3 and Boot 4
- [ ] Review issues #86 and #87

## Configuration Property

- [ ] Add springBootVersion property to Maven plugin configuration
- [ ] Add springBootVersion property to Gradle plugin extension
- [ ] Pass version through converter chain

## Code Generation Updates

- [ ] Update OpenApiContractConverter to support version-based output
- [ ] Update AsyncApiContractConverter to support version-based output
- [ ] Create Jackson import resolver based on Spring Boot version

## Gradle Plugin

- [ ] Create Gradle plugin source structure
- [ ] Implement plugin with springBootVersion configuration
- [ ] Add Gradle plugin tests

## Bug Fixes

- [ ] Fix issue #86 - empty JSON response with required args
- [ ] Fix issue #87 - empty body with 200 response

## Verification

- [ ] Run mvn clean install
- [ ] Verify tests pass for both Spring Boot versions