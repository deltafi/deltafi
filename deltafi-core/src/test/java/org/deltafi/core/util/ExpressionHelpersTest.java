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

import org.deltafi.common.rules.CoercibleString;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionHelpersTest {

    @Test
    void base64EncodeAndDecodeRoundTrip() {
        String input = "hello world";
        String encoded = ExpressionHelpers.base64Encode(input);
        assertThat(encoded).isEqualTo("aGVsbG8gd29ybGQ=");
        assertThat(ExpressionHelpers.base64Decode(encoded)).isEqualTo(input);
    }

    @Test
    void base64UrlEncodeOmitsPaddingAndUrlSafeAlphabet() {
        // bytes whose standard base64 contains + / and trailing padding
        String input = "??>>>";
        String standard = ExpressionHelpers.base64Encode(input);
        String urlSafe = ExpressionHelpers.base64UrlEncode(input);

        assertThat(standard).contains("=");
        assertThat(urlSafe).doesNotContain("=").doesNotContain("+").doesNotContain("/");
        assertThat(ExpressionHelpers.base64UrlDecode(urlSafe)).isEqualTo(input);
    }

    @Test
    void hexEncodeAndDecodeRoundTrip() {
        assertThat(ExpressionHelpers.hexEncode("ABC")).isEqualTo("414243");
        assertThat(ExpressionHelpers.hexDecode("414243")).isEqualTo("ABC");
    }

    @Test
    void hexDecodeFailsLoudlyOnInvalidInput() {
        assertThatThrownBy(() -> ExpressionHelpers.hexDecode("not-hex"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void urlEncodeAndDecodeRoundTrip() {
        String raw = "key=value with spaces & specials/?";
        String encoded = ExpressionHelpers.urlEncode(raw);
        assertThat(encoded).doesNotContain(" ").doesNotContain("&");
        assertThat(ExpressionHelpers.urlDecode(encoded)).isEqualTo(raw);
    }

    @Test
    void hashesProduceKnownDigests() {
        // Verified against `printf '%s' 'abc' | <openssl|shasum> ...`
        assertThat(ExpressionHelpers.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(ExpressionHelpers.sha512("abc"))
                .isEqualTo("ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                        + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f");
        assertThat(ExpressionHelpers.sha1("abc"))
                .isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d");
        assertThat(ExpressionHelpers.md5("abc"))
                .isEqualTo("900150983cd24fb0d6963f7d28e17f72");
    }

    @Test
    void escapingPassesThroughCommonsText() {
        assertThat(ExpressionHelpers.escapeJson("she said \"hi\"\nthen left"))
                .isEqualTo("she said \\\"hi\\\"\\nthen left");
        assertThat(ExpressionHelpers.escapeXml("a < b & c > d"))
                .isEqualTo("a &lt; b &amp; c &gt; d");
        assertThat(ExpressionHelpers.escapeHtml("<script>"))
                .isEqualTo("&lt;script&gt;");
    }

    @Test
    void uuidGeneratesValidRandomUuid() {
        String generated = ExpressionHelpers.uuid();
        UUID parsed = UUID.fromString(generated);
        assertThat(parsed.version()).isEqualTo(4);
        assertThat(ExpressionHelpers.uuid()).isNotEqualTo(generated);
    }

    @Test
    void timestampReturnsMillisString() {
        long before = System.currentTimeMillis();
        long value = Long.parseLong(ExpressionHelpers.timestamp());
        long after = System.currentTimeMillis();
        assertThat(value).isBetween(before, after);
    }

    @Test
    void coalesceReturnsFirstNonBlank() {
        assertThat(ExpressionHelpers.coalesce(null, "", "  ", "first", "second")).isEqualTo("first");
        assertThat(ExpressionHelpers.coalesce(null, "", "  ")).isNull();
        assertThat(ExpressionHelpers.coalesce((CharSequence[]) null)).isNull();
    }

    @Test
    void defaultIfBlankFallsBackOnBlank() {
        assertThat(ExpressionHelpers.defaultIfBlank("value", "fallback")).isEqualTo("value");
        assertThat(ExpressionHelpers.defaultIfBlank("", "fallback")).isEqualTo("fallback");
        assertThat(ExpressionHelpers.defaultIfBlank("  ", "fallback")).isEqualTo("fallback");
        assertThat(ExpressionHelpers.defaultIfBlank(null, "fallback")).isEqualTo("fallback");
        assertThat(ExpressionHelpers.defaultIfBlank(null, null)).isNull();
    }

    @Test
    void nullInputsReturnNullForReversibleOps() {
        assertThat(ExpressionHelpers.base64Encode(null)).isNull();
        assertThat(ExpressionHelpers.base64Decode(null)).isNull();
        assertThat(ExpressionHelpers.base64UrlEncode(null)).isNull();
        assertThat(ExpressionHelpers.base64UrlDecode(null)).isNull();
        assertThat(ExpressionHelpers.hexEncode(null)).isNull();
        assertThat(ExpressionHelpers.hexDecode(null)).isNull();
        assertThat(ExpressionHelpers.urlEncode(null)).isNull();
        assertThat(ExpressionHelpers.urlDecode(null)).isNull();
        assertThat(ExpressionHelpers.sha256(null)).isNull();
        assertThat(ExpressionHelpers.sha512(null)).isNull();
        assertThat(ExpressionHelpers.sha1(null)).isNull();
        assertThat(ExpressionHelpers.md5(null)).isNull();
        assertThat(ExpressionHelpers.escapeJson(null)).isNull();
        assertThat(ExpressionHelpers.escapeXml(null)).isNull();
        assertThat(ExpressionHelpers.escapeHtml(null)).isNull();
    }

    @Test
    void acceptsCoercibleStringFromMetadata() {
        // Metadata in the expression context arrives as CoercibleString (CharSequence). Confirm
        // the CharSequence-typed helpers handle that wrapper without explicit toString() at the
        // call site.
        CoercibleString id = new CoercibleString("user-1234");
        assertThat(ExpressionHelpers.sha256(id))
                .isEqualTo(ExpressionHelpers.sha256("user-1234"));
        assertThat(ExpressionHelpers.base64Encode(id))
                .isEqualTo(ExpressionHelpers.base64Encode("user-1234"));
    }
}
