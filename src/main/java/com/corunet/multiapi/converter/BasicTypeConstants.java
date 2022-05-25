package com.corunet.multiapi.converter;

import java.util.Random;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.cloud.contract.spec.internal.RegexPatterns;
import org.springframework.cloud.contract.spec.internal.RegexProperty;

public class BasicTypeConstants {

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

  public static final String SCHEMAS = "schemas";

  public static final String FORMAT = "format";

  public static final String EXAMPLE = "example";

  public static final String STRING_TYPE = "string";

  public static final String NUMBER_TYPE = "number";

  public static final String INT32_TYPE = "int32";

  public static final String INT64_TYPE = "int64";

  public static final String FLOAT_TYPE = "float";

  public static final String DOUBLE_TYPE = "double";

  public static final String BOOLEAN_TYPE = "boolean";

  public static final String TYPE = "type";

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
