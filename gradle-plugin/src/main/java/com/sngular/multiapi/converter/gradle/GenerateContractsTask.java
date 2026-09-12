/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sngular.multiapi.converter.gradle;

import java.io.File;
import java.util.Collection;

import com.sngular.multiapi.converter.MultiApiContractConverter;
import com.sngular.multiapi.converter.configuration.ConverterConfiguration;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.springframework.cloud.contract.spec.Contract;

public class GenerateContractsTask extends DefaultTask {

  private final Property<File> contractDirectory = getProject().getObjects().property(File.class);

  private final Property<File> outputDirectory = getProject().getObjects().property(File.class);

  private final Property<String> springBootVersion = getProject().getObjects().property(String.class)
      .convention("3.x");

  @Input
  public Property<File> getContractDirectory() {
    return contractDirectory;
  }

  public void setContractDirectory(final Property<File> contractDirectory) {
    this.contractDirectory.set(contractDirectory);
  }

  @Input
  public Property<File> getOutputDirectory() {
    return outputDirectory;
  }

  public void setOutputDirectory(final Property<File> outputDirectory) {
    this.outputDirectory.set(outputDirectory);
  }

  @Input
  @Optional
  public Property<String> getSpringBootVersion() {
    return springBootVersion;
  }

  public void setSpringBootVersion(final Property<String> springBootVersion) {
    this.springBootVersion.set(springBootVersion);
  }

  @TaskAction
  public void generateContracts() {
    final String version = springBootVersion.getOrElse("3.x");
    ConverterConfiguration.getInstance().setSpringBootVersion(version);

    getLogger().lifecycle("Generating Spring Cloud Contracts for Spring Boot " + version);

    final File contractDir = contractDirectory.getOrElse(new File("src/main/resources/contracts"));
    if (!contractDir.exists()) {
      throw new GradleException("Contract directory does not exist: " + contractDir);
    }

    final File outputDir = outputDirectory.getOrElse(new File("build/generated-spring-cloud-contracts"));
    outputDir.mkdirs();

    final MultiApiContractConverter converter = new MultiApiContractConverter();
    final File[] contractFiles = contractDir.listFiles(
        (dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".json"));

    if (contractFiles == null || contractFiles.length == 0) {
      getLogger().warn("No contract files found in " + contractDir);
      return;
    }

    int processedCount = 0;
    for (final File contractFile : contractFiles) {
      if (converter.isAccepted(contractFile)) {
        try {
          final Collection<Contract> contracts = converter.convertFrom(contractFile);
          getLogger().lifecycle("Processed " + contractFile.getName() + " -> " + contracts.size() + " contracts");
          processedCount++;
        } catch (final Exception e) {
          getLogger().error("Error processing " + contractFile.getName(), e);
        }
      }
    }

    getLogger().lifecycle("Processed " + processedCount + " contract files for Spring Boot " + version);
  }
}
