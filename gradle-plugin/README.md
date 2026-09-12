# SCC-MultiApi-Converter Gradle Plugin

Gradle plugin for generating Spring Cloud Contracts from OpenAPI 3.x and AsyncAPI 2.x specifications.

## Installation

### From Maven Local

```groovy
plugins {
    id 'com.sngular.multiapi.scc-multiapi-converter' version '4.0.0-SNAPSHOT'
}
```

### From Maven Central

```groovy
plugins {
    id 'com.sngular.multiapi.scc-multiapi-converter' version '4.0.0'
}
```

## Configuration

```groovy
sccMultiApi {
    contractDirectory = file('src/main/resources/contracts')
    outputDirectory = file('build/generated-spring-cloud-contracts')
    springBootVersion = '4.x'  // Options: '3.x' (default) or '4.x'
}
```

### Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `contractDirectory` | File | `src/main/resources/contracts` | Directory containing OpenAPI/AsyncAPI YAML files |
| `outputDirectory` | File | `build/generated-spring-cloud-contracts` | Output directory for generated contracts |
| `springBootVersion` | String | `3.x` | Target Spring Boot version (`3.x` or `4.x`) |

## Usage

### Generate Contracts

```bash
./gradlew generateSpringCloudContracts
```

### Spring Boot Version Selection

The `springBootVersion` property determines which Jackson version is used:

- **`3.x`** (default): Uses Jackson 2.x (`com.fasterxml.jackson.*`)
- **`4.x`**: Uses Jackson 3.x (`tools.jackson.*`)

This affects the runtime behavior of the converter when parsing OpenAPI/AsyncAPI files.

## Example

```groovy
plugins {
    id 'com.sngular.multiapi.scc-multiapi-converter' version '4.0.0-SNAPSHOT'
}

sccMultiApi {
    contractDirectory = file('src/main/resources/contracts')
    outputDirectory = file('build/generated-spring-cloud-contracts')
    springBootVersion = '4.x'
}

tasks.named('generateSpringCloudContracts') {
    // Task configuration if needed
}
```

## License

Mozilla Public License 2.0
