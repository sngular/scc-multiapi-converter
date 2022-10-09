package net.coru.multiapi.converter.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.RandomUtils;

public final class RandomGenerator {

  private RandomGenerator() {
  }

  public static String getRandomDateTime() {
    return getRandomLocalDateTime().format(DateTimeFormatter.ISO_DATE_TIME);
  }

  public static String getRandomDate() {
    return getRandomLocalDateTime().format(DateTimeFormatter.ISO_DATE);
  }

  public static String getRandomTime() {
    return getRandomLocalDateTime().format(DateTimeFormatter.ISO_TIME);
  }

  public static String getRandomDateTimeOffset() {
    return getRandomLocalDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }

  private static LocalDateTime getRandomLocalDateTime() {
    final long minDay = LocalDateTime.of(1900, 1, 1, 0, 0).toEpochSecond(ZoneOffset.UTC);
    final long maxDay = LocalDateTime.of(2100, 1, 1, 0, 0).toEpochSecond(ZoneOffset.UTC);
    final long randomSeconds = minDay + RandomUtils.nextLong(0, maxDay - minDay);

    return LocalDateTime.ofEpochSecond(randomSeconds, RandomUtils.nextInt(0, 1_000_000_000 - 1), ZoneOffset.UTC);

  }

  public static String randomEnumValue(final JsonNode jsonNode) {
    final String[] enumValues = jsonNode.get("enum").asText().split(",");
    return enumValues[RandomUtils.nextInt(0, enumValues.length - 1)];
  }
}
