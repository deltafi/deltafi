/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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

    /**
     * Create a CoercibleString wrapping the given value.
     * @param value the string value, may be null
     */
    public CoercibleString(String value) {
        this.value = value;
    }

    /**
     * Return the wrapped string value.
     * @return the wrapped string value
     */
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

    /**
     * Null-safe delegate to {@link String#toLowerCase()}.
     * @return the value converted to lower case, or null if value is null
     */
    public String toLowerCase() {
        return value == null ? null : value.toLowerCase();
    }

    /**
     * Null-safe delegate to {@link String#toUpperCase()}.
     * @return the value converted to upper case, or null if value is null
     */
    public String toUpperCase() {
        return value == null ? null : value.toUpperCase();
    }

    /**
     * Null-safe delegate to {@link String#contains(CharSequence)}.
     * @param s the sequence to search for
     * @return true if the value contains {@code s}, false if value is null
     */
    public boolean contains(CharSequence s) {
        return value != null && value.contains(s);
    }

    /**
     * Null-safe delegate to {@link String#startsWith(String)}.
     * @param prefix the prefix to test
     * @return true if the value starts with {@code prefix}, false if value is null
     */
    public boolean startsWith(String prefix) {
        return value != null && value.startsWith(prefix);
    }

    /**
     * Null-safe delegate to {@link String#endsWith(String)}.
     * @param suffix the suffix to test
     * @return true if the value ends with {@code suffix}, false if value is null
     */
    public boolean endsWith(String suffix) {
        return value != null && value.endsWith(suffix);
    }

    /**
     * Null-safe delegate to {@link String#matches(String)}.
     * @param regex the regular expression to match against
     * @return true if the value matches {@code regex}, false if value is null
     */
    public boolean matches(String regex) {
        return value != null && value.matches(regex);
    }

    /**
     * Null-safe delegate to {@link String#replace(CharSequence, CharSequence)}.
     * @param target the sequence to be replaced
     * @param replacement the replacement sequence
     * @return the resulting string, or null if value is null
     */
    public String replace(CharSequence target, CharSequence replacement) {
        return value == null ? null : value.replace(target, replacement);
    }

    /**
     * Null-safe delegate to {@link String#replaceAll(String, String)}.
     * @param regex the regular expression to match
     * @param replacement the replacement string
     * @return the resulting string, or null if value is null
     */
    public String replaceAll(String regex, String replacement) {
        return value == null ? null : value.replaceAll(regex, replacement);
    }

    /**
     * Null-safe delegate to {@link String#replaceFirst(String, String)}.
     * @param regex the regular expression to match
     * @param replacement the replacement string
     * @return the resulting string, or null if value is null
     */
    public String replaceFirst(String regex, String replacement) {
        return value == null ? null : value.replaceFirst(regex, replacement);
    }

    /**
     * Null-safe delegate to {@link String#split(String)}.
     * @param regex the delimiting regular expression
     * @return the array of strings, or an empty array if value is null
     */
    public String[] split(String regex) {
        return value == null ? new String[0] : value.split(regex);
    }

    /**
     * Null-safe delegate to {@link String#split(String, int)}.
     * @param regex the delimiting regular expression
     * @param limit the result threshold
     * @return the array of strings, or an empty array if value is null
     */
    public String[] split(String regex, int limit) {
        return value == null ? new String[0] : value.split(regex, limit);
    }

    /**
     * Null-safe delegate to {@link String#indexOf(String)}.
     * @param str the substring to search for
     * @return the index, or -1 if not found or value is null
     */
    public int indexOf(String str) {
        return value == null ? -1 : value.indexOf(str);
    }

    /**
     * Null-safe delegate to {@link String#indexOf(int)}.
     * @param ch the character (as Unicode code point) to search for
     * @return the index, or -1 if not found or value is null
     */
    public int indexOf(int ch) {
        return value == null ? -1 : value.indexOf(ch);
    }

    /**
     * Null-safe indexOf that accepts a Character.
     * @param ch the character to search for
     * @return the index, or -1 if not found or value/ch is null
     */
    public int indexOf(Character ch) {
        return value == null || ch == null ? -1 : value.indexOf(ch);
    }

    /**
     * Null-safe delegate to {@link String#lastIndexOf(String)}.
     * @param str the substring to search for
     * @return the index, or -1 if not found or value is null
     */
    public int lastIndexOf(String str) {
        return value == null ? -1 : value.lastIndexOf(str);
    }

    /**
     * Null-safe delegate to {@link String#lastIndexOf(int)}.
     * @param ch the character (as Unicode code point) to search for
     * @return the index, or -1 if not found or value is null
     */
    public int lastIndexOf(int ch) {
        return value == null ? -1 : value.lastIndexOf(ch);
    }

    /**
     * Null-safe lastIndexOf that accepts a Character.
     * @param ch the character to search for
     * @return the index, or -1 if not found or value/ch is null
     */
    public int lastIndexOf(Character ch) {
        return value == null || ch == null ? -1 : value.lastIndexOf(ch);
    }

    /**
     * Null-safe delegate to {@link String#equalsIgnoreCase(String)}.
     * @param other the string to compare against
     * @return true if the strings are equal ignoring case
     */
    public boolean equalsIgnoreCase(String other) {
        if (value == null) {
            return other == null;
        }
        return value.equalsIgnoreCase(other);
    }

    /**
     * Null-safe delegate to {@link String#substring(int)}.
     * @param beginIndex the beginning index, inclusive
     * @return the substring, or null if value is null
     */
    public String substring(int beginIndex) {
        return value == null ? null : value.substring(beginIndex);
    }

    /**
     * Null-safe delegate to {@link String#substring(int, int)}.
     * @param beginIndex the beginning index, inclusive
     * @param endIndex the ending index, exclusive
     * @return the substring, or null if value is null
     */
    public String substring(int beginIndex, int endIndex) {
        return value == null ? null : value.substring(beginIndex, endIndex);
    }

    /**
     * Null-safe delegate to {@link String#trim()}.
     * @return the trimmed string, or null if value is null
     */
    public String trim() {
        return value == null ? null : value.trim();
    }

    /**
     * Null-safe delegate to {@link String#strip()}.
     * @return the stripped string, or null if value is null
     */
    public String strip() {
        return value == null ? null : value.strip();
    }

    /**
     * Null-safe delegate to {@link String#stripLeading()}.
     * @return the string with leading whitespace removed, or null if value is null
     */
    public String stripLeading() {
        return value == null ? null : value.stripLeading();
    }

    /**
     * Null-safe delegate to {@link String#stripTrailing()}.
     * @return the string with trailing whitespace removed, or null if value is null
     */
    public String stripTrailing() {
        return value == null ? null : value.stripTrailing();
    }

    /**
     * Null-safe delegate to {@link String#repeat(int)}.
     * @param count the number of times to repeat
     * @return the repeated string, or null if value is null
     */
    public String repeat(int count) {
        return value == null ? null : value.repeat(count);
    }

    public boolean isEmpty() {
        return value == null || value.isEmpty();
    }

    /**
     * Null-safe delegate to {@link String#isBlank()}.
     * @return true if the value is null, empty, or contains only whitespace
     */
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
     * @param other the number to compare against
     * @return a negative integer, zero, or a positive integer as this value
     *         is less than, equal to, or greater than the given number
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
     * @param other the number to compare against
     * @return true if this value is greater than other
     */
    public boolean gt(Number other) {
        return compareToNumber(other) > 0;
    }

    /**
     * Check if this value is greater than or equal to the given number.
     * Parses the string as a number for comparison.
     * @param other the number to compare against
     * @return true if this value is greater than or equal to other
     */
    public boolean gte(Number other) {
        return compareToNumber(other) >= 0;
    }

    /**
     * Check if this value is less than the given number.
     * Parses the string as a number for comparison.
     * @param other the number to compare against
     * @return true if this value is less than other
     */
    public boolean lt(Number other) {
        return compareToNumber(other) < 0;
    }

    /**
     * Check if this value is less than or equal to the given number.
     * Parses the string as a number for comparison.
     * @param other the number to compare against
     * @return true if this value is less than or equal to other
     */
    public boolean lte(Number other) {
        return compareToNumber(other) <= 0;
    }
}
