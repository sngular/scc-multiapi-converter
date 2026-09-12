/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sngular.multiapi.converter.gradle;

import java.io.File;

public class SccMultiApiConverterExtension {

  private File contractDirectory = new File("src/main/resources/contracts");

  private File outputDirectory = new File("build/generated-spring-cloud-contracts");

  private String springBootVersion = "3.x";

  public File getContractDirectory() {
    return contractDirectory;
  }

  public void setContractDirectory(final File contractDirectory) {
    this.contractDirectory = contractDirectory;
  }

  public File getOutputDirectory() {
    return outputDirectory;
  }

  public void setOutputDirectory(final File outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  public String getSpringBootVersion() {
    return springBootVersion;
  }

  public void setSpringBootVersion(final String springBootVersion) {
    this.springBootVersion = springBootVersion;
  }
}
