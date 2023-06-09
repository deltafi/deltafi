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
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Properties;
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
  },
  NUMBER {
    @Override
    public String runValidateValue(String value) {
      if (!NumberUtils.isCreatable(value)) {
        return "The value " + value + " cannot be converted to a number";
      }
      return null;
    }
  },
  LIST {
    @Override
    public String formatString(String value) {
      return wrapIfMissing(value, "[", "]");
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

  String runValidateValue(String value) {
    return null;
  }

  static String wrapIfMissing(String value, String start, String close) {
    if (!(value.startsWith(start) && value.endsWith(close))) {
      return start + value + close;
    }
    return value;
  }

  /**
   * Read a list of key value pairs into a Map where keys are delimited from values by colons and pairs are delimited by commas
   * @param value string to convert to map
   * @return map representation of the given string
   */
  public static Map<Object, Object> readStringAsMap(String value) {
    Properties properties = StringUtils.splitArrayElementsIntoProperties(StringUtils.delimitedListToStringArray(value, ","), ":");
    return null == properties ? Map.of():
            properties.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}