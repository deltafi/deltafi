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
package org.deltafi.common.util;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A pure-Java replacement for {@code org.springframework.util.unit.DataSize}.
 * Represents a data size as a non-negative number of bytes with factory methods
 * and a string parser accepting forms like {@code "10MB"}, {@code "5gb"}, or {@code "200B"}.
 */
public final class DataSize implements Comparable<DataSize>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final long BYTES_PER_KB = 1024L;
    private static final long BYTES_PER_MB = BYTES_PER_KB * 1024L;
    private static final long BYTES_PER_GB = BYTES_PER_MB * 1024L;
    private static final long BYTES_PER_TB = BYTES_PER_GB * 1024L;

    private static final Pattern PATTERN = Pattern.compile(
            "^\\s*([+-]?\\d+)\\s*([a-zA-Z]{0,2})\\s*$");

    private final long bytes;

    private DataSize(long bytes) {
        this.bytes = bytes;
    }

    public static DataSize ofBytes(long bytes) {
        return new DataSize(bytes);
    }

    public static DataSize ofKilobytes(long kilobytes) {
        return new DataSize(Math.multiplyExact(kilobytes, BYTES_PER_KB));
    }

    public static DataSize ofMegabytes(long megabytes) {
        return new DataSize(Math.multiplyExact(megabytes, BYTES_PER_MB));
    }

    public static DataSize ofGigabytes(long gigabytes) {
        return new DataSize(Math.multiplyExact(gigabytes, BYTES_PER_GB));
    }

    public static DataSize ofTerabytes(long terabytes) {
        return new DataSize(Math.multiplyExact(terabytes, BYTES_PER_TB));
    }

    /**
     * Parse a string like {@code "10MB"}, {@code "5 gb"}, {@code "200B"}, or a bare number.
     * Bare numbers are interpreted as bytes. Suffix matching is case-insensitive.
     */
    public static DataSize parse(CharSequence text) {
        Objects.requireNonNull(text, "text must not be null");
        Matcher matcher = PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("'" + text + "' is not a valid data size");
        }
        long amount = Long.parseLong(matcher.group(1));
        String suffix = matcher.group(2);
        return switch (suffix.toUpperCase()) {
            case "", "B" -> ofBytes(amount);
            case "KB" -> ofKilobytes(amount);
            case "MB" -> ofMegabytes(amount);
            case "GB" -> ofGigabytes(amount);
            case "TB" -> ofTerabytes(amount);
            default -> throw new IllegalArgumentException("'" + text + "' has unsupported unit '" + suffix + "'");
        };
    }

    public long toBytes() {
        return bytes;
    }

    public long toKilobytes() {
        return bytes / BYTES_PER_KB;
    }

    public long toMegabytes() {
        return bytes / BYTES_PER_MB;
    }

    public long toGigabytes() {
        return bytes / BYTES_PER_GB;
    }

    public long toTerabytes() {
        return bytes / BYTES_PER_TB;
    }

    @Override
    public int compareTo(DataSize other) {
        return Long.compare(this.bytes, other.bytes);
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof DataSize d) && d.bytes == this.bytes;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(bytes);
    }

    @Override
    public String toString() {
        return bytes + "B";
    }
}
