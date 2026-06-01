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
package org.deltafi.core.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Pure helper functions exposed to SpEL parameter templates via {@code ParameterResolver.ExpressionInput}.
 * All inputs are treated as UTF-8 strings; all outputs are strings (or null when the input is null).
 * Methods are intentionally side-effect-free so they can be cached and safely called from any parameter
 * substitution context.
 */
public final class ExpressionHelpers {

    private static final HexFormat HEX = HexFormat.of();

    private ExpressionHelpers() {
    }

    // ---- Base64 (standard) ----

    public static String base64Encode(CharSequence value) {
        return value == null ? null : Base64.getEncoder().encodeToString(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String base64Decode(CharSequence value) {
        return value == null ? null : new String(Base64.getDecoder().decode(value.toString()), StandardCharsets.UTF_8);
    }

    // ---- Base64 (URL-safe, no padding) ----
    public static String base64UrlEncode(CharSequence value) {
        return value == null ? null : Base64.getUrlEncoder().withoutPadding().encodeToString(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String base64UrlDecode(CharSequence value) {
        return value == null ? null : new String(Base64.getUrlDecoder().decode(value.toString()), StandardCharsets.UTF_8);
    }

    // ---- Hex ----
    public static String hexEncode(CharSequence value) {
        return value == null ? null : HEX.formatHex(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static String hexDecode(CharSequence value) {
        return value == null ? null : new String(HEX.parseHex(value), StandardCharsets.UTF_8);
    }

    // ---- URL ----

    public static String urlEncode(CharSequence value) {
        return value == null ? null : URLEncoder.encode(value.toString(), StandardCharsets.UTF_8);
    }

    public static String urlDecode(CharSequence value) {
        return value == null ? null : URLDecoder.decode(value.toString(), StandardCharsets.UTF_8);
    }

    // ---- Hashing (hex output) ----
    public static String sha256(CharSequence value) {
        return digest(value, "SHA-256");
    }

    public static String sha512(CharSequence value) {
        return digest(value, "SHA-512");
    }

    public static String sha1(CharSequence value) {
        return digest(value, "SHA-1");
    }

    public static String md5(CharSequence value) {
        return digest(value, "MD5");
    }

    private static String digest(CharSequence value, String algorithm) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return HEX.formatHex(md.digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // every JVM ships the standard set; this is unreachable in practice
            throw new IllegalStateException("Missing digest algorithm: " + algorithm, e);
        }
    }

    // ---- Escaping (commons-text) ----
    public static String escapeJson(CharSequence value) {
        return value == null ? null : StringEscapeUtils.escapeJson(value.toString());
    }

    public static String escapeXml(CharSequence value) {
        return value == null ? null : StringEscapeUtils.escapeXml11(value.toString());
    }

    public static String escapeHtml(CharSequence value) {
        return value == null ? null : StringEscapeUtils.escapeHtml4(value.toString());
    }

    // ---- Utility ----
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String timestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    /**
     * Returns the first non-blank value from the supplied arguments, or null if all are blank/null.
     * Useful for falling back through a chain of metadata keys.
     */
    public static String coalesce(CharSequence... values) {
        if (values == null) {
            return null;
        }
        for (CharSequence v : values) {
            if (v != null && !StringUtils.isBlank(v)) {
                return v.toString();
            }
        }
        return null;
    }

    /**
     * Returns the value if non-blank, otherwise returns the fallback (which may itself be null).
     */
    public static String defaultIfBlank(CharSequence value, CharSequence fallback) {
        String useFallback = fallback != null ? fallback.toString() : null;
        return StringUtils.isBlank(value) ? useFallback : value.toString();
    }
}
