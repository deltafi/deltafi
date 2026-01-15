/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: A String wrapper that handles numeric comparisons in SpEL expressions.
// ABOUTME: Allows metadata['count'] == 5 to work when metadata value is "5".
package org.deltafi.common.rules;

import java.util.Objects;

/**
 * A String wrapper that enables numeric coercion in SpEL comparisons.
 * When compared to a Number, attempts to parse the string value as a number
 * for comparison. Implements CharSequence and provides common String methods
 * so SpEL method calls like .toLowerCase() work as expected.
 */
public class CoercibleString implements CharSequence {
    private final String value;

    public CoercibleString(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    // CharSequence implementation
    @Override
    public int length() {
        return value == null ? 0 : value.length();
    }

    @Override
    public char charAt(int index) {
        if (value == null) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for null value");
        }
        return value.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        if (value == null) {
            if (start == 0 && end == 0) {
                return "";
            }
            throw new IndexOutOfBoundsException("Range [" + start + "," + end + ") out of bounds for null value");
        }
        return value.subSequence(start, end);
    }

    @Override
    public String toString() {
        return value;
    }

    // Common String methods for SpEL compatibility
    public String toLowerCase() {
        return value == null ? null : value.toLowerCase();
    }

    public String toUpperCase() {
        return value == null ? null : value.toUpperCase();
    }

    public boolean contains(CharSequence s) {
        return value != null && value.contains(s);
    }

    public boolean startsWith(String prefix) {
        return value != null && value.startsWith(prefix);
    }

    public boolean endsWith(String suffix) {
        return value != null && value.endsWith(suffix);
    }

    public boolean matches(String regex) {
        return value != null && value.matches(regex);
    }

    public String replace(CharSequence target, CharSequence replacement) {
        return value == null ? null : value.replace(target, replacement);
    }

    public String replaceAll(String regex, String replacement) {
        return value == null ? null : value.replaceAll(regex, replacement);
    }

    public String replaceFirst(String regex, String replacement) {
        return value == null ? null : value.replaceFirst(regex, replacement);
    }

    public String[] split(String regex) {
        return value == null ? new String[0] : value.split(regex);
    }

    public String[] split(String regex, int limit) {
        return value == null ? new String[0] : value.split(regex, limit);
    }

    public int indexOf(String str) {
        return value == null ? -1 : value.indexOf(str);
    }

    public int indexOf(int ch) {
        return value == null ? -1 : value.indexOf(ch);
    }

    public int indexOf(Character ch) {
        return value == null || ch == null ? -1 : value.indexOf(ch);
    }

    public int lastIndexOf(String str) {
        return value == null ? -1 : value.lastIndexOf(str);
    }

    public int lastIndexOf(int ch) {
        return value == null ? -1 : value.lastIndexOf(ch);
    }

    public int lastIndexOf(Character ch) {
        return value == null || ch == null ? -1 : value.lastIndexOf(ch);
    }

    public boolean equalsIgnoreCase(String other) {
        if (value == null) {
            return other == null;
        }
        return value.equalsIgnoreCase(other);
    }

    public String substring(int beginIndex) {
        return value == null ? null : value.substring(beginIndex);
    }

    public String substring(int beginIndex, int endIndex) {
        return value == null ? null : value.substring(beginIndex, endIndex);
    }

    public String trim() {
        return value == null ? null : value.trim();
    }

    public String strip() {
        return value == null ? null : value.strip();
    }

    public String stripLeading() {
        return value == null ? null : value.stripLeading();
    }

    public String stripTrailing() {
        return value == null ? null : value.stripTrailing();
    }

    public String repeat(int count) {
        return value == null ? null : value.repeat(count);
    }

    public boolean isEmpty() {
        return value == null || value.isEmpty();
    }

    public boolean isBlank() {
        return value == null || value.isBlank();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return value == null;
        }
        if (other == this) {
            return true;
        }
        if (other instanceof CoercibleString cs) {
            return Objects.equals(value, cs.value);
        }
        if (other instanceof CharSequence cs) {
            return value != null && value.contentEquals(cs);
        }
        if (other instanceof Number num) {
            return compareToNumber(num) == 0;
        }
        return value != null && value.equals(other.toString());
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }

    /**
     * Compare this string value to a number. If the string is a valid number,
     * perform numeric comparison. Otherwise, compare as strings.
     */
    public int compareToNumber(Number other) {
        if (value == null) {
            return -1;
        }
        try {
            if (other instanceof Double || other instanceof Float) {
                return Double.compare(Double.parseDouble(value), other.doubleValue());
            }
            // Try to parse as long for integer types
            return Long.compare(Long.parseLong(value), other.longValue());
        } catch (NumberFormatException e) {
            // Value is not a valid number, compare as strings
            return value.compareTo(other.toString());
        }
    }

    /**
     * Check if this value is greater than the given number.
     * Parses the string as a number for comparison.
     */
    public boolean gt(Number other) {
        return compareToNumber(other) > 0;
    }

    /**
     * Check if this value is greater than or equal to the given number.
     * Parses the string as a number for comparison.
     */
    public boolean gte(Number other) {
        return compareToNumber(other) >= 0;
    }

    /**
     * Check if this value is less than the given number.
     * Parses the string as a number for comparison.
     */
    public boolean lt(Number other) {
        return compareToNumber(other) < 0;
    }

    /**
     * Check if this value is less than or equal to the given number.
     * Parses the string as a number for comparison.
     */
    public boolean lte(Number other) {
        return compareToNumber(other) <= 0;
    }
}
