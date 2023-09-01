/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2023 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.deltafi.common.types;

import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public enum VariableDataType {
  STRING,
  BOOLEAN {
    @Override
    public String formatString(String value) {
      return null != value ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    @Override
    public String runValidateValue(String value) {
      if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
        return "A boolean value must be true or false";
      }
      return null;
    }

    @Override
    public Object convertValue(String value) {
      return Boolean.parseBoolean(value);
    }
  },
  NUMBER {
    @Override
    public String runValidateValue(String value) {
      if (!NumberUtils.isCreatable(value)) {
        return "The value " + value + " cannot be converted to a number";
      }
      return null;
    }

    @Override
    public Object convertValue(String value) {
      return NumberUtils.createNumber(value);
    }
  },
  LIST {
    @Override
    public String formatString(String value) {
      return wrapIfMissing(value, "[", "]");
    }

    @Override
    public Object convertValue(String value) {
      return VariableDataType.readStringAsList(value);
    }
  },
  MAP {
    @Override
    public String formatString(String value) {
      return wrapIfMissing(value, "{", "}");
    }

    @Override
    public String runValidateValue(String value) {
      if (!value.isBlank() && readStringAsMap(value).isEmpty()) {
        return "A map value must be of the form of 'key: value, nextKey: nextValue'";
      }
      return null;
    }

    @Override
    public Object convertValue(String value) {
      return VariableDataType.readStringAsMap(value);
    }
  };

  /**
   * Format the value in a way that it can be mapped to json
   * @param value to format
   * @return formatted string that can be converted to json
   */
  public String formatString(String value) {
    return null != value ? value.trim() : null;
  }

  /**
   * Verify that the value can be converted to the data type
   * @param value to verify
   * @return null if there are no issues or an error message if the value is invalid
   */
  public String validateValue(String value) {
    return null == value ? null : runValidateValue(value.trim());
  }

  public Object convertValue(String value) {
      return value;
  }

  String runValidateValue(String value) {
    return null;
  }

  static String wrapIfMissing(String value, String start, String close) {
    if (value == null) {
      return null;
    }

    value = value.trim();
    if (!isWrapped(value, start, close)) {
      return start + value + close;
    }
    return value;
  }

  static String stripIfWrapped(@NotNull String value, String start, String close) {
    value = value.trim();
    if (isWrapped(value, start, close)) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  static boolean isWrapped(String value, String start, String close) {
    return value.startsWith(start) && value.endsWith(close);
  }

  /**
   * Read a list of key value pairs into a Map where keys are delimited from values by colons and pairs are delimited by commas
   * @param value string to convert to map
   * @return map representation of the given string
   */
  public static Map<Object, Object> readStringAsMap(String value) {
    if (value == null) {
      return Map.of();
    }

    value = stripIfWrapped(value.trim(), "{", "}");
    if (value.isBlank()) {
      return Map.of();
    }

    return readStringAsList(value).stream()
            .map(VariableDataType::toEntry)
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private static Map.Entry<Object, Object> toEntry(String value) {
    List<String> pair = splitDelimitedValue(value, ':');
    return switch (pair.size()) {
      case 0 -> null;
      case 1 -> throw new IllegalArgumentException("The value '" + value + "' contains an invalid key value pair, no delimiters found. The key value pair must be of the format key: value");
      case 2 -> Map.entry(pair.get(0).trim(), pair.get(1).trim());
      default -> throw new IllegalArgumentException("The value '" + value + "' contains an invalid key value pair, multiple delimiters found. The key value pair must be of the format key: value");
    };
  }

  /**
   * Takes in a comma seperated list in a string and splits it into a list of strings.
   * Each value will be trimmed, empty values are dropped from the list
   * @param value string containing a comma separated
   * @return value split into a list
   */
  public static List<String> readStringAsList(String value) {
    if (value == null) {
      return List.of();
    }

    value = stripIfWrapped(value, "[", "]");
    if (value.isBlank()) {
      return List.of();
    }

    return splitDelimitedValue(value, ',').stream()
            .filter(s -> !s.isEmpty())
            .toList();
  }

  /**
   * Split the given value by the given delimiter. The delimiter
   * is considered to be escaped if it is preceded by a '\'.
   * @param value to split
   * @param delimiter to split by
   * @return list of split values
   */
  private static List<String> splitDelimitedValue(String value, char delimiter) {
    boolean escaped = false;
    List<String> splitValues = new ArrayList<>();

    int idx = 0;
    for (char c : value.toCharArray()) {
      if (!escaped && c == '\\') {
        escaped = true;
        idx++;
      } else if (!escaped && c == delimiter) {
        splitValues.add(replaceEscapedDelimiterAndTrim(value.substring(0, idx), delimiter));
        value = value.substring(idx + 1);
        idx = 0; // reset the index to 0 to start from the beginning of the updated value
      } else {
        escaped = false;
        idx++;
      }
    }

    splitValues.add(replaceEscapedDelimiterAndTrim(value, delimiter));

    return splitValues;
  }

  private static String replaceEscapedDelimiterAndTrim(String value, char delimiter) {
    return value.replaceAll("\\\\" + delimiter, String.valueOf(delimiter)).trim();
  }
}