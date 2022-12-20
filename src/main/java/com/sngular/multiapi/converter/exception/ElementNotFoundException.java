/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.sngular.multiapi.converter.exception;

public class ElementNotFoundException extends RuntimeException {

  private static final String MESSAGE = "%s not found";

  public ElementNotFoundException(final String message) {
    super(String.format(MESSAGE, message));
  }
}
