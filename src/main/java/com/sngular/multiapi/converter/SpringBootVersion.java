/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sngular.multiapi.converter;

import java.util.Arrays;

public enum SpringBootVersion {

  SPRING_BOOT_3("3.x", "com.fasterxml.jackson"),
  SPRING_BOOT_4("4.x", "tools.jackson");

  private final String version;

  private final String jacksonPackage;

  SpringBootVersion(final String version, final String jacksonPackage) {
    this.version = version;
    this.jacksonPackage = jacksonPackage;
  }

  public String getVersion() {
    return version;
  }

  public String getJacksonPackage() {
    return jacksonPackage;
  }

  public static SpringBootVersion fromVersion(final String version) {
    return Arrays.stream(values())
        .filter(v -> v.version.equals(version))
        .findFirst()
        .orElse(SPRING_BOOT_3);
  }
}
