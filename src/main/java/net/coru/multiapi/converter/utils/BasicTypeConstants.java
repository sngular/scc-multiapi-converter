/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package net.coru.multiapi.converter.utils;

import java.util.Random;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.cloud.contract.spec.internal.RegexPatterns;
import org.springframework.cloud.contract.spec.internal.RegexProperty;

public final class BasicTypeConstants {

  public static final String ASYNCAPI = "asyncapi";

  public static final String OPENAPI = "openapi";

  public static final String INT_32 = "int32";

  public static final String STRING = "string";

  public static final String INTEGER = "integer";

  public static final String INT_64 = "int64";

  public static final String NUMBER = "number";

  public static final String FLOAT = "float";

  public static final String DOUBLE = "double";

  public static final String BOOLEAN = "boolean";

  public static final String OBJECT = "object";

  public static final String ARRAY = "array";

  public static final String ENUM = "enum";

  public static final String CHANNELS = "channels";

  public static final String SUBSCRIBE = "subscribe";

  public static final String PUBLISH = "publish";

  public static final String REF = "$ref";

  public static final String PROPERTIES = "properties";

  public static final String SCHEMA = "schema";

  public static final String SCHEMAS = "schemas";

  public static final String FORMAT = "format";

  public static final String EXAMPLE = "example";

  public static final String NAME = "name";

  public static final String TYPE = "type";

  public static final String PAYLOAD = "payload";

  public static final RegexProperty STRING_REGEX = RegexPatterns.alphaNumeric();

  public static final RegexProperty INT_REGEX = RegexPatterns.positiveInt();

  public static final RegexProperty DECIMAL_REGEX = RegexPatterns.number();

  public static final RegexProperty BOOLEAN_REGEX = RegexPatterns.anyBoolean();

  public static final String DEFAULT_REGEX = ".*";

  public static final Random RANDOM = new Random();

  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(new YAMLFactory());

  public static final Set<String> BASIC_OBJECT_TYPE = Set.of(NUMBER, STRING, BOOLEAN, INTEGER
  );

  private BasicTypeConstants() {

  }

}