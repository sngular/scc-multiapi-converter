/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sngular.multiapi.converter.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class SccMultiApiConverterPlugin implements Plugin<Project> {

  @Override
  public void apply(final Project project) {
    final SccMultiApiConverterExtension extension = project.getExtensions()
        .create("sccMultiApi", SccMultiApiConverterExtension.class);

    project.getTasks().register("generateSpringCloudContracts", GenerateContractsTask.class, task -> {
      task.setGroup("Spring Cloud Contract");
      task.setDescription("Generates Spring Cloud Contracts from OpenAPI and AsyncAPI specifications");
      task.setContractDirectory(project.provider(extension::getContractDirectory));
      task.setOutputDirectory(project.provider(extension::getOutputDirectory));
      task.setSpringBootVersion(project.provider(extension::getSpringBootVersion));
    });
  }
}
