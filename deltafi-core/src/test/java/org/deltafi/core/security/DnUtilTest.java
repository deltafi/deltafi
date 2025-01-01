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
package org.deltafi.core.security;

import org.junit.jupiter.api.Test;

import javax.naming.InvalidNameException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DnUtilTest {

    @Test
    void normalizeDn() {
        assertThat(DnUtil.normalizeDn("CN=Alice,OU=Foo,  C=US")).isEqualTo("CN=Alice, OU=Foo, C=US");
        assertThat(DnUtil.normalizeDn("CN=Bob ,OU=Foo, C=US")).isEqualTo("CN=Bob, OU=Foo, C=US");
    }

    @Test
    void normalizeDnHandleErrors() {
        assertThatThrownBy(() -> DnUtil.normalizeDn("malformed DN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("improperly specified input name: malformed DN");
    }

    @Test
    void extractCommonName() throws InvalidNameException {
        assertThat(DnUtil.extractCommonName("CN=Alice,OU=Foo,  C=US")).isEqualTo("Alice");
        assertThat(DnUtil.extractCommonName("CN=Bob ,OU=Foo, C=US")).isEqualTo("Bob");
    }

    @Test
    void extractCommonNameHandleErrors() {
        assertThatThrownBy(() -> DnUtil.extractCommonName("malformed DN"))
                .isInstanceOf(InvalidNameException.class)
                .hasMessage("Invalid name: malformed DN");
    }
}