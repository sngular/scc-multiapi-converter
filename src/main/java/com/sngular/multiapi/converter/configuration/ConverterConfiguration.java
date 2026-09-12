package com.sngular.multiapi.converter.configuration;

import com.sngular.multiapi.converter.SpringBootVersion;

public final class ConverterConfiguration {

  private static final ConverterConfiguration INSTANCE = new ConverterConfiguration();

  public static final String SPRING_BOOT_VERSION_PROPERTY = "scc-multiapi-converter.spring-boot-version";

  private SpringBootVersion springBootVersion;

  private ConverterConfiguration() {
    this.springBootVersion = resolveSpringBootVersion();
  }

  public static ConverterConfiguration getInstance() {
    return INSTANCE;
  }

  public SpringBootVersion getSpringBootVersion() {
    return springBootVersion;
  }

  public void setSpringBootVersion(final SpringBootVersion springBootVersion) {
    this.springBootVersion = springBootVersion;
  }

  public void setSpringBootVersion(final String version) {
    this.springBootVersion = SpringBootVersion.fromVersion(version);
  }

  private static SpringBootVersion resolveSpringBootVersion() {
    final String version = System.getProperty(SPRING_BOOT_VERSION_PROPERTY);
    if (version != null && !version.isEmpty()) {
      return SpringBootVersion.fromVersion(version);
    }
    return SpringBootVersion.SPRING_BOOT_3;
  }
}
