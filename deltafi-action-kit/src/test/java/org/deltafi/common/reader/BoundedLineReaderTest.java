/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
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
package org.deltafi.common.reader;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

class BoundedLineReaderTest {

    @Test
    void testMixedCrLf() {
        String input = "ab\r\ncde\rfghi\njk";
        BoundedLineReader boundedLineReader = new BoundedLineReader(new StringReader(input), 5);
        List<String> lines = boundedLineReader.lines().toList();
        Assertions.assertThat(lines).hasSize(4)
                .containsExactly("ab\r\n", "cde\r", "fghi\n", "jk");
        Assertions.assertThat(boundedLineReader.getBytesRead()).isEqualTo(15L);

        input = "ab\r\ncde\rfghi\njk\r\n";
        boundedLineReader = new BoundedLineReader(new StringReader(input), 5);
        lines = boundedLineReader.lines().toList();
        Assertions.assertThat(lines).hasSize(4)
                .containsExactly("ab\r\n", "cde\r", "fghi\n", "jk\r\n");
        Assertions.assertThat(boundedLineReader.getBytesRead()).isEqualTo(17L);
    }

    @Test
    void testCr() {
        String input = "ab\rcde\rfghi\rjk";
        BoundedLineReader boundedLineReader = new BoundedLineReader(new StringReader(input), 5);
        List<String> lines = boundedLineReader.lines().toList();
        Assertions.assertThat(lines).hasSize(4)
                .containsExactly("ab\r", "cde\r", "fghi\r", "jk");
        Assertions.assertThat(boundedLineReader.getBytesRead()).isEqualTo(14L);

        input = "ab\rcde\rfghi\rjk\r";
        boundedLineReader = new BoundedLineReader(new StringReader(input), 5);
        lines = boundedLineReader.lines().toList();
        Assertions.assertThat(lines).hasSize(4)
                .containsExactly("ab\r", "cde\r", "fghi\r", "jk\r");
        Assertions.assertThat(boundedLineReader.getBytesRead()).isEqualTo(15L);
    }

    @Test
    void testLf() {
        String input = "ab\ncde\nfghi\njk";
        BoundedLineReader boundedLineReader = new BoundedLineReader(new StringReader(input), 5);
        List<String> lines = boundedLineReader.lines().toList();
        Assertions.assertThat(lines).hasSize(4)
                .containsExactly("ab\n", "cde\n", "fghi\n", "jk");
        Assertions.assertThat(boundedLineReader.getBytesRead()).isEqualTo(14L);

        input = "ab\ncde\nfghi\njk\n";
        boundedLineReader = new BoundedLineReader(new StringReader(input), 5);
        lines = boundedLineReader.lines().toList();
        Assertions.assertThat(lines).hasSize(4)
                .containsExactly("ab\n", "cde\n", "fghi\n", "jk\n");
        Assertions.assertThat(boundedLineReader.getBytesRead()).isEqualTo(15L);
    }

    @Test
    void testMaxSize() {
        String input = "ab\ncde\nfghi\r\njk";
        BoundedLineReader maxTooSmall = new BoundedLineReader(new StringReader(input), 5);
        Assertions.assertThatThrownBy(() -> maxTooSmall.lines().toList())
                .hasMessage("java.io.IOException: The current line will not fit within the max size limit");
        Assertions.assertThat(maxTooSmall.getBytesRead()).isEqualTo(13L);

        BoundedLineReader maxFits = new BoundedLineReader(new StringReader(input), 6);
        Assertions.assertThatNoException().isThrownBy(() -> maxFits.lines().toList());
        Assertions.assertThat(maxFits.getBytesRead()).isEqualTo(15L);
    }

}